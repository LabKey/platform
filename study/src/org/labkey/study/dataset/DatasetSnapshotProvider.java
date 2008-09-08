/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.*;
import org.labkey.api.query.snapshot.AbstractSnapshotProvider;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.query.snapshot.QuerySnapshotForm;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudyServiceImpl;
import org.labkey.study.assay.AssayPublishManager;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyCachable;
import org.labkey.study.model.StudyManager;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import java.util.*;
/*
 * User: Karl Lum
 * Date: Jul 9, 2008
 * Time: 4:57:40 PM
 */

public class DatasetSnapshotProvider extends AbstractSnapshotProvider implements QuerySnapshotService.AutoUpdateable, StudyManager.StudyCachableListener
{
    private static final DatasetSnapshotProvider _instance = new DatasetSnapshotProvider();
    private static Logger _log = Logger.getLogger(DatasetSnapshotProvider.class);

    // map of property uri to dataset id
    private static final Map<String, Map<String, Integer>> _datasetPropertyMap = new HashMap<String, Map<String, Integer>>();
    private static Timer _timer = new Timer("SnapshotUpdateTimer", true);

    private DatasetSnapshotProvider()
    {
        StudyManager.addCachableListener(this);
    }

    private void generateDependencies(int snapshotId, QueryView view)
    {
        _log.info("Creating dependency tree for snapshot: " + snapshotId);
        Map<String, Integer> props;

        synchronized(_datasetPropertyMap)
        {
            if (!_datasetPropertyMap.containsKey(view.getContainer().getId()))
            {
                props = new HashMap<String, Integer>();
                _datasetPropertyMap.put(view.getContainer().getId(), props);
                Study study = StudyManager.getInstance().getStudy(view.getContainer());

                if (study != null)
                {
                    for (DataSetDefinition dsDef : StudyManager.getInstance().getDataSetDefinitions(study))
                    {
                        Domain d = PropertyService.get().getDomain(view.getContainer(), dsDef.getTypeURI());
                        if (d != null)
                        {
                            for (DomainProperty dp : d.getProperties())
                                props.put(dp.getPropertyURI(), dsDef.getDataSetId());
                        }
                    }
                }
            }
            else
                props = _datasetPropertyMap.get(view.getContainer().getId());
        }

        for (DisplayColumn dc : view.getDisplayColumns())
        {
            ColumnInfo info = dc.getColumnInfo();
            if (info != null)
            {
                if (props.containsKey(info.getPropertyURI()))
                {
                    int dsId = props.get(info.getPropertyURI());
                    _log.info("This view dependent on dataset: " + dsId);
                }
            }
        }
    }

    public static DatasetSnapshotProvider getInstance()
    {
        return _instance;
    }

    public String getName()
    {
        return "Study Dataset Snapshot";
    }

    public List<DisplayColumn> getDisplayColumns(QueryForm form) throws Exception
    {
        Study study = StudyManager.getInstance().getStudy(form.getViewContext().getContainer());
        QueryView view = QueryView.create(form);
        List<DisplayColumn> columns = new ArrayList<DisplayColumn>();

        for (DisplayColumn c : view.getDisplayColumns())
        {
            if (!DataSetDefinition.isDefaultFieldName(c.getName(), study))
            {
                columns.add(c);
            }
        }
        return columns;
    }

    public ActionURL getCreateWizardURL(QuerySettings settings, ViewContext context)
    {
        QuerySettings qs = new QuerySettings(context, QueryView.DATAREGIONNAME_DEFAULT);
        return new ActionURL(StudyController.CreateSnapshotAction.class, context.getContainer()).
                addParameter(qs.param(QueryParam.schemaName), settings.getSchemaName()).
                addParameter(qs.param(QueryParam.queryName), settings.getQueryName()).
                addParameter(qs.param(QueryParam.viewName), settings.getViewName()).
                addParameter(DataSetDefinition.DATASETKEY, context.getActionURL().getParameter(DataSetDefinition.DATASETKEY));
    }

    public ActionURL createSnapshot(QuerySnapshotForm form, List<String> errors) throws Exception
    {
        DbSchema schema = StudyManager.getSchema();
        boolean startedTransaction = false;

        try
        {
            QueryView view = QueryView.create(form);
            StringBuilder sb = new StringBuilder();
            TSVGridWriter tsvWriter = new TSVGridWriter(view.getResultset());
            tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.queryColumnName);
            tsvWriter.write(sb);

            if (!schema.getScope().isTransactionActive())
            {
                schema.getScope().beginTransaction();
                startedTransaction = true;
            }
            // create the dataset definition
            Study study = StudyManager.getInstance().getStudy(form.getViewContext().getContainer());
            boolean isDemographicData = false;
            int datasetId = NumberUtils.toInt(form.getViewContext().getActionURL().getParameter(DataSetDefinition.DATASETKEY), -1);
            if (datasetId != -1)
            {
                DataSetDefinition currentDef = study.getDataSet(datasetId);
                if (currentDef != null)
                {
                    isDemographicData = currentDef.isDemographicData();
                }
            }

            DataSetDefinition def = AssayPublishManager.getInstance().createAssayDataset(form.getViewContext().getUser(),
                    study, form.getSnapshotName(), null, null, isDemographicData);
            if (def != null)
            {
                QuerySnapshotDefinition snapshot = createSnapshotDef(form);
                if (snapshot == null)
                    throw new IllegalArgumentException("Unable to create the query snapshot definition");

                snapshot.save(form.getViewContext().getUser(), form.getViewContext().getContainer());
                String domainURI = def.getTypeURI();
                OntologyManager.ensureDomainDescriptor(domainURI, form.getSnapshotName(), form.getViewContext().getContainer());
                Domain d = PropertyService.get().getDomain(form.getViewContext().getContainer(), domainURI);
                Map<String, String> columnMap = new CaseInsensitiveHashMap<String>();
                
                for (ColumnInfo col : QueryService.get().getColumns(view.getTable(), snapshot.getColumns()).values())
                {
                    addAsDomainProperty(d, col);
                    columnMap.put(col.getAlias(), getPropertyURI(d, col));
                }
                d.save(form.getViewContext().getUser());

                // import the data
                StudyManager.getInstance().importDatasetTSV(study, form.getViewContext().getUser(), def, sb.toString(),
                        System.currentTimeMillis(), columnMap, errors, true, null);

                if (errors.isEmpty())
                {
                    if (startedTransaction)
                        schema.getScope().commitTransaction();

                    return new ActionURL(StudyController.DatasetAction.class, form.getViewContext().getContainer()).
                            addParameter(DataSetDefinition.DATASETKEY, def.getDataSetId());
                }
            }
            return null;
        }
        finally
        {
            if (startedTransaction && schema.getScope().isTransactionActive())
                schema.getScope().rollbackTransaction();
        }
    }

    private Map<String, String> getColumnMap(Domain d, QueryView view, List<FieldKey> columns)
    {
        Map<String, String> columnMap = new CaseInsensitiveHashMap<String>();

        for (ColumnInfo col : QueryService.get().getColumns(view.getTable(), columns).values())
        {
            columnMap.put(col.getAlias(), getPropertyURI(d, col));
        }
        return columnMap;
    }

    private QueryForm getQueryForm(QuerySnapshotDefinition snapshotDef, ViewContext context)
    {
        QueryDefinition def = snapshotDef.getQueryDefinition();

        QueryForm form = new QueryForm();
        form.setSchemaName(def.getSchemaName());
        form.setQueryName(def.getName());
        form.setViewContext(context);
        form.setViewName(snapshotDef.getViewName());

        return form;
    }

    public ActionURL updateSnapshot(QuerySnapshotForm form, List<String> errors) throws Exception
    {
        QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(form.getViewContext().getContainer(), form.getSchemaName(), form.getSnapshotName());
        if (def != null)
        {
            QueryForm sourceForm = getQueryForm(def, form.getViewContext());
            Study study = StudyManager.getInstance().getStudy(form.getViewContext().getContainer());

            // purge the dataset rows then recreate the new one...
            DataSetDefinition dsDef = StudyManager.getInstance().getDataSetDefinition(study, def.getName());
            if (dsDef != null)
            {
                DbSchema schema = StudyManager.getSchema();
                boolean startedTransaction = false;

                try
                {
                    QueryView view = QueryView.create(sourceForm);
                    view.getSettings().setAllowChooseQuery(false);
                    view.getSettings().setAllowChooseView(false);
                    view.setShowExportButtons(false);
                    
                    StringBuilder sb = new StringBuilder();
                    TSVGridWriter tsvWriter = new TSVGridWriter(view.getResultset());
                    tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.queryColumnName);
                    tsvWriter.write(sb);

                    if (!schema.getScope().isTransactionActive())
                    {
                        schema.getScope().beginTransaction();
                        startedTransaction = true;
                    }
                    int numRowsDeleted = StudyManager.getInstance().purgeDataset(study, dsDef);
                    Domain d = PropertyService.get().getDomain(form.getViewContext().getContainer(), dsDef.getTypeURI());
                    Map<String, String> columnMap = getColumnMap(d, view, def.getColumns());

                    // import the new data
                    String[] newRows = StudyManager.getInstance().importDatasetTSV(study, form.getViewContext().getUser(), dsDef, sb.toString(), System.currentTimeMillis(),
                            columnMap, errors, true, null);

                    if (!errors.isEmpty())
                        return null;

                    if (startedTransaction)
                        schema.getScope().commitTransaction();

                    ViewContext context = form.getViewContext();
                    StudyServiceImpl.addDatasetAuditEvent(context.getUser(), context.getContainer(), dsDef,
                            "Dataset snapshot was updated. " + numRowsDeleted + " rows were removed and replaced with " + newRows.length + " rows.", null);

                    return new ActionURL(StudyController.DatasetAction.class, form.getViewContext().getContainer()).
                            addParameter(DataSetDefinition.DATASETKEY, dsDef.getDataSetId());
                }
                finally
                {
                    if (startedTransaction && schema.getScope().isTransactionActive())
                        schema.getScope().rollbackTransaction();
                    else
                    {
                        def.setLastUpdated(new Date());
                        def.save(form.getViewContext().getUser(), form.getViewContext().getContainer());
                    }
                }
            }
        }
        return null;
    }

    public ActionURL updateSnapshotDefinition(ViewContext context, QuerySnapshotDefinition def, List<String> errors) throws Exception
    {
        ActionURL ret = super.updateSnapshotDefinition(context, def, errors);

        // update the study dataset columns
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
        return ret;
    }

    public HttpView createAuditView(QuerySnapshotForm form) throws Exception
    {
        ViewContext context = form.getViewContext();
        QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(context.getContainer(), form.getSchemaName(), form.getSnapshotName());

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
                addParameter(qs.param(QueryParam.queryName), settings.getQueryName());
    }

    private List<QuerySnapshotDefinition> getDependencies(DataSetDefinition dsDef)
    {
        Map<QuerySnapshotDefinition, QuerySnapshotDefinition> dependencies = new HashMap<QuerySnapshotDefinition, QuerySnapshotDefinition>();
        Domain d = PropertyService.get().getDomain(dsDef.getContainer(), dsDef.getTypeURI());
        if (d != null)
        {
            try {
                List<QuerySnapshotDefinition> snapshots = QueryService.get().getQuerySnapshotDefs(dsDef.getContainer(), StudyManager.getSchemaName());
                for (DomainProperty prop : d.getProperties())
                {
                    for (QuerySnapshotDefinition snapshot : snapshots)
                    {
                        if (!dependencies.containsKey(snapshot) && hasDependency(snapshot, prop.getPropertyURI()))
                        {
                            dependencies.put(snapshot, snapshot);
                        }
                    }
                }
            }
            catch (ServletException e)
            {
                throw new RuntimeException(e);
            }
        }
        return new ArrayList<QuerySnapshotDefinition>(dependencies.values());
    }

    // map of property uri to dataset id
    private static final Map<Integer, Map<String, String>> _snapshotPropertyMap = new HashMap<Integer, Map<String, String>>();

    private boolean hasDependency(QuerySnapshotDefinition def, String propertyURI) throws ServletException
    {
        Map<String, String> propertyMap;

        synchronized (_snapshotPropertyMap)
        {
            if (!_snapshotPropertyMap.containsKey(def.getId()))
            {
                propertyMap = new HashMap<String, String>();
                _snapshotPropertyMap.put(def.getId(), propertyMap);

                QueryView view = QueryView.create(getQueryForm(def, HttpView.currentContext()));
                for (DisplayColumn dc : view.getDisplayColumns())
                {
                    ColumnInfo info = dc.getColumnInfo();
                    if (info != null)
                    {
                        propertyMap.put(info.getPropertyURI(), info.getPropertyURI());
                    }
                }
            }
            else
                propertyMap = _snapshotPropertyMap.get(def.getId());
        }
        return propertyMap.containsKey(propertyURI);
    }

    public void cacheCleared(final StudyCachable c)
    {
        Runnable task = new Runnable()
        {
            public void run()
            {
                int id = NumberUtils.toInt(String.valueOf(c.getPrimaryKey()), -1);
                if (id != -1)
                {
                    Study study = StudyManager.getInstance().getStudy(c.getContainer());
                    if (study != null)
                    {
                        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, id);
                        if (def != null)
                        {
                            _log.debug("Cache cleared notification on dataset : " + id);
                            for (QuerySnapshotDefinition snapshotDef : getDependencies(def))
                            {
                                try {
                                    _log.debug("Updating snapshot definition : " + snapshotDef.getName());
                                    autoUpdateSnapshot(snapshotDef, HttpView.currentContext().getActionURL());
                                }
                                catch (Exception e)
                                {
                                    _log.error(e);
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                }
            }
        };
        DbSchema schema = StudyManager.getSchema();

        if (schema.getScope().isTransactionActive())
            schema.getScope().addCommitTask(task);
        else
            task.run();
    }

    private void autoUpdateSnapshot(QuerySnapshotDefinition def, ActionURL url) throws Exception
    {
        if (def.getUpdateDelay() > 0 && def.getNextUpdate() == null)
        {
            // calculate the update time
            Calendar startTime = Calendar.getInstance();
            startTime.setTime(new Date());
            startTime.add(Calendar.SECOND, def.getUpdateDelay());

            def.setNextUpdate(startTime.getTime());
            User user = def.getModifiedBy();
            if (user != null)
            {
                def.save(user, def.getContainer());

                TimerTask task = new SnapshotUpdateTask(def, url);
                _timer.schedule(task, startTime.getTime());
            }
        }
    }

    private static class SnapshotUpdateTask extends TimerTask implements ShutdownListener
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
                _def.save(_def.getModifiedBy(), _def.getContainer());

                QuerySnapshotForm form = new QuerySnapshotForm();
                ViewContext context = new ViewContext();
                context.setUser(_def.getModifiedBy());
                context.setContainer(_def.getContainer());
                context.setActionURL(_url);

                HttpView.initForRequest(context, AppProps.getInstance().createMockRequest(), null);
                form.setViewContext(context);
                form.init(_def);

                QuerySnapshotService.get(StudyManager.getSchemaName()).updateSnapshot(form, new ArrayList<String>());
            }
            catch(Exception e)
            {
                ExceptionUtil.logExceptionToMothership(AppProps.getInstance().createMockRequest(), e);
            }
        }

        public void shutdownStarted(ServletContextEvent servletContextEvent)
        {
            ContextListener.removeShutdownListener(this);
            _timer.cancel();
        }
    }
}
