/*
 * Copyright (c) 2008-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.study.dataset;

import org.apache.log4j.Logger;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UnionTableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.query.snapshot.AbstractSnapshotProvider;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.query.snapshot.QuerySnapshotForm;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.IdentifierString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.StudyServiceImpl;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.visitmanager.VisitManager;
import org.springframework.validation.BindException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
/*
 * User: Karl Lum
 * Date: Jul 9, 2008
 * Time: 4:57:40 PM
 */

public class DatasetSnapshotProvider extends AbstractSnapshotProvider implements QuerySnapshotService.AutoUpdateable, StudyManager.DataSetListener
{
    private static final DatasetSnapshotProvider _instance = new DatasetSnapshotProvider();
    private static final Logger _log = Logger.getLogger(DatasetSnapshotProvider.class);
    private static final BlockingQueue<DataSet> _queue = new LinkedBlockingQueue<DataSet>(1000);
    private static final QuerySnapshotDependencyThread _dependencyThread = new QuerySnapshotDependencyThread();

    static
    {
        _dependencyThread.start();
    }

    private DatasetSnapshotProvider()
    {
        StudyManager.addDataSetListener(this);
    }

    public static DatasetSnapshotProvider getInstance()
    {
        return _instance;
    }

    public String getName()
    {
        return "Study Dataset Snapshot";
    }

    public List<DisplayColumn> getDisplayColumns(QueryForm form, BindException errors) throws Exception
    {
        QueryView view = QueryView.create(form, errors);
        List<DisplayColumn> columns = new ArrayList<DisplayColumn>();

        for (DisplayColumn c : view.getDisplayColumns())
        {
            /**
             * The ObjectId column is present in datasets that are created from assays so that they can get back to
             * the original assay and the assay can tell which studies the data has been copied to. Having more than
             * one copy of the same object id value per study is illegal.
             */
            if ("objectid".equalsIgnoreCase(c.getName()))
                continue;

            columns.add(c);
        }
        return columns;
    }

    public ActionURL getCreateWizardURL(QuerySettings settings, ViewContext context)
    {
        QuerySettings qs = new QuerySettings(context, QueryView.DATAREGIONNAME_DEFAULT);
        return new ActionURL(StudyController.CreateSnapshotAction.class, context.getContainer()).
                addParameter("ff_snapshotName", settings.getQueryName() + " Snapshot").
                addParameter(qs.param(QueryParam.schemaName), settings.getSchemaName()).
                addParameter(qs.param(QueryParam.queryName), settings.getQueryName()).
                addParameter(qs.param(QueryParam.viewName), settings.getViewName()).
                addParameter(DataSetDefinition.DATASETKEY, context.getActionURL().getParameter(DataSetDefinition.DATASETKEY)).
                addParameter(ActionURL.Param.redirectUrl, PageFlowUtil.encode(context.getActionURL().getLocalURIString()));
    }

    public void createSnapshot(ViewContext context, QuerySnapshotDefinition qsDef, BindException errors) throws Exception
    {
        DbSchema schema = StudyManager.getSchema();

        try
        {
            if (qsDef != null)
            {
                QueryDefinition queryDef = qsDef.getQueryDefinition(context.getUser());

                // dataset snapshots must have an underlying dataset definition defined
                StudyImpl study = StudyManager.getInstance().getStudy(qsDef.getContainer());
                DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, qsDef.getName());

                if (def != null)
                {
                    QueryView view = createQueryView(context, qsDef, errors);

                    if (view != null && !errors.hasErrors())
                    {
                        // TODO: Create class ResultSetDataLoader and use it here instead of round-tripping through a TSV StringBuilder
                        StringBuilder sb = new StringBuilder();
                        TSVGridWriter tsvWriter = view.getTsvWriter();
                        tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.queryColumnName);
                        tsvWriter.write(sb);

                        schema.getScope().ensureTransaction();

                        String domainURI = def.getTypeURI();
                        Domain d = PropertyService.get().getDomain(qsDef.getContainer(), domainURI);
                        Map<String, String> columnMap = getColumnMap(d, view, qsDef.getColumns(), tsvWriter.getFieldMap());

                        // import the data
                        BatchValidationException ve = new BatchValidationException();
                        StudyManager.getInstance().importDatasetData(study, context.getUser(), def, new TabLoader(sb, true),
                                columnMap, ve, true, null, null);

                        for (ValidationException e : ve.getRowErrors())
                            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());

                        if (!errors.hasErrors())
                        {
                            // if the source of the snapshot (query definition) is in a different
                            // container, make sure the participants and visits for the study for the
                            // snapshot are updated
                            if (!queryDef.getContainer().equals(qsDef.getContainer()))
                            {
                                StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(context.getUser(),
                                        Collections.singletonList(def));
                            }
                            schema.getScope().commitTransaction();
                        }
                    }
                }
                else
                    errors.reject(SpringActionController.ERROR_MSG, "A dataset definition does not exist for query snapshot: " + qsDef.getName());
            }
            else
                throw new IllegalArgumentException("QuerySnapshotDefinition cannot be null");
        }
        finally
        {
            schema.getScope().closeConnection();
        }
    }

    private static QueryView createQueryView(ViewContext context, QuerySnapshotDefinition qsDef, BindException errors)
    {
        QueryDefinition queryDef = qsDef.getQueryDefinition(context.getUser());
        QuerySchema querySchema = DefaultSchema.get(context.getUser(), queryDef.getContainer()).getSchema(queryDef.getSchemaName());

        if (querySchema instanceof UserSchema)
        {
            QuerySettings settings = new QuerySettings(context, QueryView.DATAREGIONNAME_DEFAULT);

            settings.setQueryName(queryDef.getName());
            QueryView view = ((UserSchema)querySchema).createView(context, settings, errors);

            if (!qsDef.getColumns().isEmpty())
            {
                // create a temporary custom view to add additional display columns to the base query definition
                CustomView custView = queryDef.createCustomView(context.getUser(), "tempCustomView");
                custView.setColumns(qsDef.getColumns());
                custView.setFilterAndSort(qsDef.getFilter());

                view.setCustomView(custView);
            }
            return view;
        }
        return null;
    }

    public ActionURL createSnapshot(QuerySnapshotForm form, BindException errors) throws Exception
    {
        QuerySnapshotDefinition qsDef = createSnapshotDef(form);

        if (qsDef != null)
        {
            qsDef.save(form.getViewContext().getUser());
            createSnapshot(form.getViewContext(), qsDef, errors);

            if (!errors.hasErrors())
            {
                Study study = StudyManager.getInstance().getStudy(qsDef.getContainer());
                DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, qsDef.getName());

                return new ActionURL(StudyController.DatasetAction.class, qsDef.getContainer()).
                        addParameter(DataSetDefinition.DATASETKEY, def.getDataSetId());
            }
        }
        return null;
    }

    /**
     * Create a column map for parsing the generated tsv from the query view. We only want to map
     * non-default columns, and need to build a map that goes from TSV header to PropertyURI
     */
    private Map<String, String> getColumnMap(Domain d, QueryView view, List<FieldKey> fieldKeys, Map<FieldKey, ColumnInfo> fieldMap)
    {
        Map<String, String> columnMap = new CaseInsensitiveHashMap<String>();
        Study study = StudyManager.getInstance().getStudy(view.getContainer());

        if (fieldMap != null)
        {
            for (FieldKey fieldKey : fieldKeys)
            {
                ColumnInfo col = fieldMap.get(fieldKey);
                if (col != null && !DataSetDefinition.isDefaultFieldName(col.getName(), study))
                {
                    // The key of the entry is the same code that generates the TSV header lines for
                    // TSVGridWriter.ColumnHeaderType.queryColumnName. It would be nice to use the code directly.
                    columnMap.put(FieldKey.fromString(col.getName()).getDisplayString(), getPropertyURI(d, col));
                }
            }
        }
        return columnMap;
    }

    public synchronized ActionURL updateSnapshot(QuerySnapshotForm form, BindException errors) throws Exception
    {
        QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(form.getViewContext().getContainer(), form.getSchemaName().toString(), form.getSnapshotName());
        if (def != null)
        {
            Study study = StudyManager.getInstance().getStudy(form.getViewContext().getContainer());

            // purge the dataset rows then recreate the new one...
            DataSetDefinition dsDef = StudyManager.getInstance().getDataSetDefinition(study, def.getName());
            if (dsDef != null)
            {
                DbSchema schema = StudyManager.getSchema();

                try
                {
                    QueryView view = createQueryView(form.getViewContext(), def, errors);

                    if (view != null && !errors.hasErrors())
                    {
                        // TODO: Create and use a ResultSetDataLoader here instead of round-tripping through a TSV StringBuilder
                        StringBuilder sb = new StringBuilder();
                        TSVGridWriter tsvWriter = view.getTsvWriter();
                        tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.queryColumnName);
                        tsvWriter.write(sb);

                        schema.getScope().ensureTransaction();

                        int numRowsDeleted;
                        List<String> newRows;

                        List<String> importErrors = new ArrayList<String>();
                        numRowsDeleted = StudyManager.getInstance().purgeDataset(study, dsDef, form.getViewContext().getUser());
                        Domain d = PropertyService.get().getDomain(form.getViewContext().getContainer(), dsDef.getTypeURI());
                        Map<String, String> columnMap = getColumnMap(d, view, def.getColumns(), tsvWriter.getFieldMap());

                        // import the new data
                        newRows = StudyManager.getInstance().importDatasetData(study, form.getViewContext().getUser(), dsDef, new TabLoader(sb, true), columnMap, importErrors, true, null, null);

                        for (String error : importErrors)
                            errors.reject(SpringActionController.ERROR_MSG, error);

                        if (errors.hasErrors())
                            return null;

                        schema.getScope().commitTransaction();

                        ViewContext context = form.getViewContext();
                        StudyServiceImpl.addDatasetAuditEvent(context.getUser(), context.getContainer(), dsDef,
                                "Dataset snapshot was updated. " + numRowsDeleted + " rows were removed and replaced with " + newRows.size() + " rows.", null);

                        def.setLastUpdated(new Date());
                        def.save(form.getViewContext().getUser());

                        return new ActionURL(StudyController.DatasetAction.class, form.getViewContext().getContainer()).
                                addParameter(DataSetDefinition.DATASETKEY, dsDef.getDataSetId());
                    }
                }
                catch (SQLException e)
                {
                    ViewContext context = form.getViewContext();
                    StudyServiceImpl.addDatasetAuditEvent(context.getUser(), context.getContainer(), dsDef,
                            "Dataset snapshot was not updated. Cause of failure: " + e.getMessage(), null);
                }
                finally
                {
                    schema.getScope().closeConnection();
                }
            }
        }
        return null;
    }

    public ActionURL updateSnapshotDefinition(ViewContext context, QuerySnapshotDefinition def, BindException errors) throws Exception
    {
        ActionURL ret = super.updateSnapshotDefinition(context, def, errors);

        // update the study dataset columns
/*
        Study study = StudyManager.getInstance().getStudy(context.getContainer());
        DataSetDefinition dsDef = StudyManager.getInstance().getDataSetDefinition(study, def.getName());
        if (dsDef != null)
        {
            String domainURI = dsDef.getTypeURI();
            Domain domain = PropertyService.get().getDomain(context.getContainer(), domainURI);

            if (domain != null)
            {
                Map<String, DomainProperty> propertyMap = new HashMap<String, DomainProperty>();

                for (DomainProperty prop : domain.getProperties())
                    propertyMap.put(prop.getName(), prop);

                for (ColumnInfo col : QueryService.get().getColumns(dsDef.getTableInfo(context.getUser()), def.getColumns()).values())
                {
                    if (propertyMap.containsKey(col.getName()))
                        propertyMap.remove(col.getName());
                    else
                        addAsDomainProperty(domain, col);
                }

                for (DomainProperty prop : propertyMap.values())
                    prop.delete();
            }
        }
*/
        return ret;
    }

    public HttpView createAuditView(QuerySnapshotForm form) throws Exception
    {
        ViewContext context = form.getViewContext();
        QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(context.getContainer(), form.getSchemaName().toString(), form.getSnapshotName());

        if (def != null)
        {
            Study study = StudyManager.getInstance().getStudy(context.getContainer());
            DataSetDefinition dsDef = StudyManager.getInstance().getDataSetDefinition(study, def.getName());
            if (dsDef != null)
                return DatasetAuditViewFactory.getInstance().createDatasetView(context, dsDef);
        }
        return null;
    }

    public ActionURL getEditSnapshotURL(QuerySettings settings, ViewContext context)
    {
        QuerySettings qs = new QuerySettings(context, QueryView.DATAREGIONNAME_DEFAULT);
        return new ActionURL(StudyController.EditSnapshotAction.class, context.getContainer()).
                addParameter(qs.param(QueryParam.schemaName), settings.getSchemaName()).
                addParameter("snapshotName", settings.getQueryName()).
                addParameter(qs.param(QueryParam.queryName), settings.getQueryName()).
                addParameter(ActionURL.Param.redirectUrl.name(), PageFlowUtil.encode(context.getActionURL().getLocalURIString()));
    }

    private static boolean isContainerValid(Container c)
    {
        if (c != null)
        {
            return ContainerManager.getForId(c.getId()) != null;
        }
        return false;
    }

    private static List<QuerySnapshotDefinition> getDependencies(DataSet dsDef)
    {
        // check if container is still valid
        Map<Integer, QuerySnapshotDefinition> dependencies = new HashMap<Integer, QuerySnapshotDefinition>();
        if (isContainerValid(dsDef.getContainer()))
        {
            List<QuerySnapshotDefinition> snapshots = QueryService.get().getQuerySnapshotDefs(null, StudyManager.getSchemaName());
            if (!snapshots.isEmpty())
            {
                Domain d = PropertyService.get().getDomain(dsDef.getContainer(), dsDef.getTypeURI());
                if (d != null)
                {
                    try {
                        for (DomainProperty prop : d.getProperties())
                        {
                            for (QuerySnapshotDefinition snapshot : snapshots)
                            {
                                if (!dependencies.containsKey(snapshot.getId()) && hasDependency(snapshot, prop.getPropertyURI()))
                                {
                                    dependencies.put(snapshot.getId(), snapshot);
                                }
                            }
                        }
                    }
                    catch (ServletException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        else
            _log.info("Failed checking dependencies for container: " + dsDef.getContainer().getPath() + ", it has been deleted.");

        return new ArrayList<QuerySnapshotDefinition>(dependencies.values());
    }

    // map of property uri to dataset id
    private static final Map<Integer, Map<String, String>> _snapshotPropertyMap = new HashMap<Integer, Map<String, String>>();

    private static boolean hasDependency(QuerySnapshotDefinition def, String propertyURI) throws ServletException
    {
        Map<String, String> propertyMap;

        synchronized (_snapshotPropertyMap)
        {
            if (!_snapshotPropertyMap.containsKey(def.getId()))
            {
                propertyMap = new HashMap<String, String>();
                _snapshotPropertyMap.put(def.getId(), propertyMap);

                // can't assume that the dependency check is coming from the same container that
                // the snapshot is defined in.
                ViewContext context = new ViewContext(getViewContext(def, false));
                context.setContainer(def.getContainer());

                BindException errors = new NullSafeBindException(def, "snapshot");
                QueryView view = createQueryView(context, def, errors);
                TableInfo tinfo = view.getTable();

                if (tinfo instanceof UnionTableInfo)
                {
                    for (ColumnInfo info : ((UnionTableInfo)tinfo).getUnionColumns())
                    {
                        propertyMap.put(info.getPropertyURI(), info.getPropertyURI());
                    }
                }
                else
                {
                    for (DisplayColumn dc : view.getDisplayColumns())
                    {
                        ColumnInfo info = dc.getColumnInfo();
                        if (info != null)
                        {
                            propertyMap.put(info.getPropertyURI(), info.getPropertyURI());
                        }
                    }
                }
            }
            else
                propertyMap = _snapshotPropertyMap.get(def.getId());
        }
        return propertyMap.containsKey(propertyURI);
    }

    private static ViewContext getViewContext(QuerySnapshotDefinition def, boolean pushViewContext)
    {
        if (HttpView.hasCurrentView())
            return HttpView.currentContext();
        else
        {
            User user = def.getModifiedBy() != null ? def.getModifiedBy() : def.getCreatedBy();

            return ViewContext.getMockViewContext(user, def.getContainer(), new ActionURL(StudyController.CreateSnapshotAction.class, def.getContainer()), pushViewContext);
        }
    }

    public void dataSetChanged(final DataSet def)
    {
        _queue.add(def);
    }

    private static class QuerySnapshotDependencyThread extends Thread implements ShutdownListener
    {
        private QuerySnapshotDependencyThread()
        {
            setDaemon(true);
            setName(QuerySnapshotDependencyThread.class.getSimpleName());
            ContextListener.addShutdownListener(this);
        }

        public void run()
        {
            try
            {
                while (true)
                {
                    DataSet def = _queue.take();
                    if (def != null)
                    {
                        try
                        {
                            _log.debug("Cache cleared notification on dataset : " + def.getDataSetId());
                            for (QuerySnapshotDefinition snapshotDef : getDependencies(def))
                            {
                                _log.debug("Updating snapshot definition : " + snapshotDef.getName());
                                autoUpdateSnapshot(snapshotDef, null);//HttpView.currentContext().getActionURL());
                            }
                        }
                        catch (Throwable e)
                        {
                            _log.error(e);
                            ExceptionUtil.logExceptionToMothership(null, e);
                        }
                    }
                }
            }
            catch (InterruptedException e)
            {
                _log.debug(getClass().getSimpleName() + " is terminating due to interruption");
            }
        }

        public void shutdownPre(ServletContextEvent servletContextEvent)
        {
            interrupt();
        }

        public void shutdownStarted(ServletContextEvent servletContextEvent)
        {
        }
    }

    private static void autoUpdateSnapshot(QuerySnapshotDefinition def, ActionURL url) throws Exception
    {
        Calendar startTime = Calendar.getInstance();
        startTime.setTime(new Date());

        // add a new timer task set to the snapshot's configured delay time
        //
        // 12903 : next update time gets cleared once the snapshot is updated, need an additional check
        // to ensure that a snapshot doesn't get into a state where it no longer automatically updates.
        //
        if (def.getUpdateDelay() > 0 && (def.getNextUpdate() == null || startTime.getTime().after(def.getNextUpdate())))
        {
            // calculate the update time
            startTime.add(Calendar.SECOND, def.getUpdateDelay());

            def.setNextUpdate(startTime.getTime());
            User user = def.getModifiedBy();
            if (user != null)
            {
                def.save(user);

                TimerTask task = new SnapshotUpdateTask(def, url);
                Timer timer = new Timer("QuerySnapshot Update Timer", true);
                timer.schedule(task, startTime.getTime());
            }
        }
    }

    private static class SnapshotUpdateTask extends TimerTask
    {
        private QuerySnapshotDefinition _def;
        private ActionURL _url;

        public SnapshotUpdateTask(QuerySnapshotDefinition def, ActionURL url)
        {
            _def = def;
            _url = url;
        }

        public void run()
        {
            _log.debug("Automatically Updating Dataset Snapshot : " + _def.getName());

            try
            {
                _def.setNextUpdate(null);
                _def.save(_def.getModifiedBy());

                QuerySnapshotForm form = new QuerySnapshotForm();
                ViewContext context = getViewContext(_def, true);

                form.setViewContext(context);
                form.init(_def, _def.getCreatedBy());

                BindException errors = new NullSafeBindException(new Object(), "command");
                QuerySnapshotService.get(StudyManager.getSchemaName()).updateSnapshot(form, errors);
            }
            catch(Exception e)
            {
                ExceptionUtil.logExceptionToMothership(AppProps.getInstance().createMockRequest(), e);
            }
        }
    }
}
