/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.PHI;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.query.snapshot.AbstractSnapshotProvider;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.query.snapshot.QuerySnapshotForm;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;
import org.labkey.study.StudyServiceImpl;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.assay.AssayController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.DatasetManager;
import org.labkey.study.model.ParticipantCategoryImpl;
import org.labkey.study.model.ParticipantCategoryListener;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.StudySnapshot;
import org.labkey.study.query.DatasetQuerySettings;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.writer.DatasetDataWriter;
import org.springframework.validation.BindException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
/*
 * User: Karl Lum
 * Date: Jul 9, 2008
 * Time: 4:57:40 PM
 */

public class DatasetSnapshotProvider extends AbstractSnapshotProvider implements QuerySnapshotService.AutoUpdateable, DatasetManager.DatasetListener, ParticipantCategoryListener
{
    private static final DatasetSnapshotProvider INSTANCE = new DatasetSnapshotProvider();
    private static final Logger LOG = Logger.getLogger(DatasetSnapshotProvider.class);
    private static final BlockingQueue<SnapshotDependency.SourceDataType> QUEUE = new LinkedBlockingQueue<>(1000);
    private static final QuerySnapshotDependencyThread DEPENDENCY_THREAD = new QuerySnapshotDependencyThread();

    // query snapshot dependency checkers
    private static SnapshotDependency.Dataset _datasetDependency = new SnapshotDependency.Dataset();
    private static SnapshotDependency.ParticipantCategoryDependency _categoryDependency = new SnapshotDependency.ParticipantCategoryDependency();

    static
    {
        DEPENDENCY_THREAD.start();
    }

    private DatasetSnapshotProvider()
    {
        DatasetManager.addDatasetListener(this);
        ParticipantGroupManager.addCategoryListener(this);
    }

    public static DatasetSnapshotProvider getInstance()
    {
        return INSTANCE;
    }

    public String getName()
    {
        return "Study Dataset Snapshot";
    }

    public List<DisplayColumn> getDisplayColumns(QueryForm form, BindException errors)
    {
        QueryView view = QueryView.create(form, errors);
        List<DisplayColumn> columns = new ArrayList<>();

        for (DisplayColumn c : view.getDisplayColumns())
        {
            /*
              The ObjectId column is present in datasets that are created from assays so that they can get back to
              the original assay and the assay can tell which studies the data has been copied to. Having more than
              one copy of the same object id value per study is illegal.
             */
            if ("objectid".equalsIgnoreCase(c.getName()))
                continue;

            columns.add(c);
        }
        return columns;
    }

    public ActionURL getCreateWizardURL(QuerySettings settings, ViewContext context)
    {
        SimpleFilter filter = new SimpleFilter(context.getActionURL(), settings.getDataRegionName());

        QuerySettings qs = new QuerySettings(context, QueryView.DATAREGIONNAME_DEFAULT);
        ActionURL result = new ActionURL(StudyController.CreateSnapshotAction.class, context.getContainer()).
                addParameter("ff_snapshotName", settings.getQueryName() + " Snapshot").
                addParameter(qs.param(QueryParam.schemaName), settings.getSchemaName()).
                addParameter(qs.param(QueryParam.queryName), settings.getQueryName()).
                addParameter(qs.param(QueryParam.viewName), settings.getViewName()).
                addParameter(DatasetDefinition.DATASETKEY, context.getActionURL().getParameter(DatasetDefinition.DATASETKEY)).
                addParameter(ActionURL.Param.redirectUrl, PageFlowUtil.encode(context.getActionURL().getLocalURIString()));
        filter.applyToURL(result, qs.getDataRegionName());
        return result;
    }

    private SimpleFilter createParticipantGroupFilter(ViewContext context, QuerySnapshotDefinition qsDef)
    {
        // create a filter for any participant groups associated with this snapshot
        List<Integer> groups = qsDef.getParticipantGroups();
        SimpleFilter filter = new SimpleFilter();
        if (!groups.isEmpty())
        {
            Set<String> ptids = new HashSet<>();

            for (Integer groupId : groups)
            {
                ParticipantGroup group = ParticipantGroupManager.getInstance().getParticipantGroup(qsDef.getContainer(), context.getUser(), groupId);
                if (group != null)
                {
                    ptids.addAll(Arrays.asList(group.getParticipantIds()));
                }
            }
            SimpleFilter.InClause inClause = new SimpleFilter.InClause(FieldKey.fromParts(StudyService.get().getSubjectColumnName(qsDef.getContainer())), ptids);
            filter.addClause(inClause);
        }
        return filter;
    }

    public void createSnapshot(ViewContext context, QuerySnapshotDefinition qsDef, BindException errors) throws Exception
    {
        DbSchema schema = StudySchema.getInstance().getSchema();

        if (qsDef != null)
        {
            QueryDefinition queryDef = qsDef.getQueryDefinition(context.getUser());

            // dataset snapshots must have an underlying dataset definition defined
            StudyImpl study = StudyManager.getInstance().getStudy(qsDef.getContainer());
            DatasetDefinition def = StudyManager.getInstance().getDatasetDefinitionByName(study, qsDef.getName());
            if (def != null)
            {
                QueryView view = createQueryView(context, qsDef, errors);

                if (view != null && !errors.hasErrors() && view.getTable() != null)
                {
                    StudyQuerySchema studySchema = StudyQuerySchema.createSchema(study, context.getUser(), false);
                    TableInfo table = view.getTable();
                    if (table instanceof ContainerFilterable && table.supportsContainerFilter())
                        ((ContainerFilterable)table).setContainerFilter(studySchema.getDefaultContainerFilter());

                    // TODO call updateSnapshot() instead of duplicating code
                    Results results = getResults(context, view, qsDef, def);

                    // TODO: Create class ResultSetDataLoader and use it here instead of round-tripping through a TSV StringBuilder
                    StringBuilder sb = new StringBuilder();
                    TSVGridWriter tsvWriter = new TSVGridWriter(results);
                    tsvWriter.setApplyFormats(false);
                    tsvWriter.setColumnHeaderType(ColumnHeaderType.DisplayFieldKey); // CONSIDER: Use FieldKey instead
                    tsvWriter.write(sb);
                    Map<FieldKey,ColumnInfo> fieldMap = tsvWriter.getFieldMap();

                    try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
                    {
                        DataIteratorContext dataIteratorContext = new DataIteratorContext();
                        dataIteratorContext.setInsertOption(QueryUpdateService.InsertOption.IMPORT);
                        if (study.isDataspaceStudy())
                        {
                            if (!fieldMap.containsKey(new FieldKey(null, "container")))
                            {
                                errors.reject(SpringActionController.ERROR_MSG, "Dataspace snapshot query must have a column called 'container'");
                                return;
                            }
                            Map<Enum,Object> config = Collections.singletonMap(QueryUpdateService.ConfigParameters.TargetMultipleContainers, Boolean.TRUE);
                            dataIteratorContext.setConfigParameters(config);
                        }
                        StudyManager.getInstance().importDatasetData(context.getUser(), def,
                                new TabLoader(sb, true), new CaseInsensitiveHashMap<>(),
                                dataIteratorContext,
                                study.isDataspaceStudy() ? DatasetDefinition.CheckForDuplicates.never : DatasetDefinition.CheckForDuplicates.sourceOnly,
                                null, null);

                        for (ValidationException e : dataIteratorContext.getErrors().getRowErrors())
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
                            transaction.commit();
                        }
                    }
                }
            }
            else
                errors.reject(SpringActionController.ERROR_MSG, "A dataset definition does not exist for query snapshot: " + qsDef.getName());
        }
        else
            throw new IllegalArgumentException("QuerySnapshotDefinition cannot be null");
    }

    private Results getResults(ViewContext context, QueryView view, QuerySnapshotDefinition qsDef, DatasetDefinition def) throws SQLException
    {
        TableInfo tinfo = view.getTable();
        SimpleFilter filter = createParticipantGroupFilter(context, qsDef);

        // Merge in any filters the user had when creating the snapshot
        ActionURL filterURL = new ActionURL(AssayController.BeginAction.class, context.getContainer());
        view.getCustomView().applyFilterAndSortToURL(filterURL, QueryView.DATAREGIONNAME_DEFAULT);
        filter.addUrlFilters(filterURL, QueryView.DATAREGIONNAME_DEFAULT);

        Map<FieldKey, ColumnInfo> colMap = new HashMap<>();
        Integer optionsId = qsDef.getOptionsId();
        StudySnapshot snapshot = null;

        if (optionsId != null)
            snapshot = StudyManager.getInstance().getRefreshStudySnapshot(optionsId);

        PHI snapshotPhiLevel = (snapshot != null) ? snapshot.getSnapshotSettings().getPhiLevel() : PHI.NotPHI;
        Collection<ColumnInfo> columns = DatasetDataWriter.getColumnsToExport(tinfo, def, false, snapshotPhiLevel);

        if (snapshot != null && snapshot.getSnapshotSettings().isShiftDates())
        {
            DatasetDataWriter.createDateShiftColumns(tinfo, columns, view.getContainer());
        }
        if (snapshot != null && snapshot.getSnapshotSettings().isUseAlternateParticipantIds())
        {
            Study study = StudyManager.getInstance().getStudy(view.getContainer());
            if (study != null)
            {
                StudyManager.getInstance().generateNeededAlternateParticipantIds(study, context.getUser());
                DatasetDataWriter.createAlternateIdColumns(tinfo, columns, view.getContainer());
            }
        }

        for (ColumnInfo column : columns)
        {
            colMap.put(column.getFieldKey(), column);
        }

        for (DisplayColumn dc : view.getDisplayColumns())
        {
            ColumnInfo col = dc.getColumnInfo();
            if (col != null && !colMap.containsKey(col.getFieldKey()))
            {
                colMap.put(col.getFieldKey(), col);
            }
        }
        return QueryService.get().select(tinfo, colMap.values(), filter, null);
    }

    @Nullable
    static QueryView createQueryView(ViewContext context, QuerySnapshotDefinition qsDef, BindException errors)
    {
        QueryDefinition queryDef = qsDef.getQueryDefinition(context.getUser());
        if (queryDef != null)
        {
            UserSchema schema = QueryService.get().getUserSchema(context.getUser(), queryDef.getContainer(), queryDef.getSchemaPath());
            if (schema == null)
                return null;

            DatasetQuerySettings settings = new DatasetQuerySettings(context.getBindPropertyValues(), QueryView.DATAREGIONNAME_DEFAULT);
            settings.setQueryName(queryDef.getName());

            QueryView view = schema.createView(context, settings, errors);

            // create a temporary custom view to add additional display columns to the base query definition
            CustomView custView = queryDef.createCustomView(context.getUser(), "tempCustomView");

            if (!qsDef.getColumns().isEmpty())
                custView.setColumns(qsDef.getColumns());

            if (!StringUtils.isBlank(qsDef.getFilter()))
                custView.setFilterAndSort(qsDef.getFilter());

            view.setCustomView(custView);

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
                DatasetDefinition def = StudyManager.getInstance().getDatasetDefinitionByName(study, qsDef.getName());
                return new ActionURL(StudyController.DatasetAction.class, qsDef.getContainer()).
                        addParameter(DatasetDefinition.DATASETKEY, def.getDatasetId());
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
        Map<String, String> columnMap = new CaseInsensitiveHashMap<>();
        Study study = StudyManager.getInstance().getStudy(view.getContainer());

        if (fieldMap != null)
        {
            if (fieldKeys.isEmpty())
            {
                fieldKeys = new ArrayList<>();
                for (DisplayColumn dc : view.getDisplayColumns())
                {
                    ColumnInfo colInfo = dc.getColumnInfo();
                    if (colInfo != null)
                        fieldKeys.add(colInfo.getFieldKey());
                }
            }

            for (FieldKey fieldKey : fieldKeys)
            {
                ColumnInfo col = fieldMap.get(fieldKey);
                if (col != null && !DatasetDefinition.isDefaultFieldName(col.getName(), study))
                {
                    // The key of the entry is the same code that generates the TSV header lines for
                    // TSVGridWriter.ColumnHeaderType.queryColumnName. It would be nice to use the code directly.
                    columnMap.put(FieldKey.fromString(col.getName()).toDisplayString(), getPropertyURI(d, col));
                }
            }
        }
        return columnMap;
    }

    public synchronized ActionURL updateSnapshot(QuerySnapshotForm form, BindException errors, boolean suppressVisitManagerRecalc) throws Exception
    {
        QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(form.getViewContext().getContainer(), form.getSchemaName(), form.getSnapshotName());
        if (def != null)
        {
            StudyImpl study = StudyManager.getInstance().getStudy(form.getViewContext().getContainer());
            if (null == study)
                throw new IllegalStateException("study not found");

            // purge the dataset rows then recreate the new one...
            DatasetDefinition dsDef = StudyManager.getInstance().getDatasetDefinitionByName(study, def.getName());
            if (dsDef != null)
            {
                DbSchema schema = StudySchema.getInstance().getSchema();

                try
                {
                    QueryView view = createQueryView(form.getViewContext(), def, errors);

                    if (view != null && !errors.hasErrors())
                    {
                        if (view.getTable() == null)
                        {
                            errors.reject(SpringActionController.ERROR_MSG, "Unable to create a TableInfo for the source query, it may no longer exist.");
                            return null;
                        }
                        StudyQuerySchema studySchema = StudyQuerySchema.createSchema(study, form.getViewContext().getUser(), false);
                        TableInfo table = view.getTable();
                        if (table instanceof ContainerFilterable && table.supportsContainerFilter())
                            ((ContainerFilterable)table).setContainerFilter(studySchema.getDefaultContainerFilter());

                        Results results = getResults(form.getViewContext(), view, def, dsDef);

                        // TODO: Create class ResultSetDataLoader and use it here instead of round-tripping through a TSV StringBuilder
                        StringBuilder sb = new StringBuilder();
                        TSVGridWriter tsvWriter = new TSVGridWriter(results);
                        tsvWriter.setApplyFormats(false);
                        tsvWriter.setColumnHeaderType(ColumnHeaderType.DisplayFieldKey); // CONSIDER: Use FieldKey instead
                        tsvWriter.write(sb);
                        Map<FieldKey,ColumnInfo> fieldMap = tsvWriter.getFieldMap();

                        try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
                        {
                            int numRowsDeleted;
                            List<String> newRows;

                            numRowsDeleted = StudyManager.getInstance().purgeDataset(dsDef, form.getViewContext().getUser());

                            DataIteratorContext dataIteratorContext = new DataIteratorContext();
                            dataIteratorContext.setInsertOption(QueryUpdateService.InsertOption.IMPORT);
                            if (study.isDataspaceStudy())
                            {
                                if (!fieldMap.containsKey(new FieldKey(null, "container")))
                                {
                                    errors.reject(SpringActionController.ERROR_MSG, "Dataspace snapshot query must have a column called 'container'");
                                    return null;
                                }
                                Map<Enum,Object> config = Collections.singletonMap(QueryUpdateService.ConfigParameters.TargetMultipleContainers, Boolean.TRUE);
                                dataIteratorContext.setConfigParameters(config);
                            }
                            newRows = StudyManager.getInstance().importDatasetData(form.getViewContext().getUser(), dsDef,
                                new TabLoader(sb, true), new CaseInsensitiveHashMap<>(),
                                dataIteratorContext,
                                study.isDataspaceStudy() ? DatasetDefinition.CheckForDuplicates.never : DatasetDefinition.CheckForDuplicates.sourceOnly,
                                null, null);

                            for (ValidationException error : dataIteratorContext.getErrors().getRowErrors())
                                errors.reject(SpringActionController.ERROR_MSG, error.getMessage());

                            if (errors.hasErrors())
                                return null;

                            if (!suppressVisitManagerRecalc)
                                StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(form.getViewContext().getUser(), Collections.singleton(dsDef));

                            ViewContext context = form.getViewContext();
                            StudyServiceImpl.addDatasetAuditEvent(context.getUser(), context.getContainer(), dsDef,
                                    "Dataset snapshot was updated. " + numRowsDeleted + " rows were removed and replaced with " + newRows.size() + " rows.", null);

                            def.setLastUpdated(new Date());
                            def.save(form.getViewContext().getUser());

                            transaction.commit();

                            return new ActionURL(StudyController.DatasetAction.class, form.getViewContext().getContainer()).
                                    addParameter(DatasetDefinition.DATASETKEY, dsDef.getDatasetId());
                        }
                    }
                }
                catch (SQLException e)
                {
                    ViewContext context = form.getViewContext();
                    StudyServiceImpl.addDatasetAuditEvent(context.getUser(), context.getContainer(), dsDef,
                            "Dataset snapshot was not updated. Cause of failure: " + e.getMessage(), null);
                }
            }
        }
        errors.reject(SpringActionController.ERROR_MSG, "Unable to create a QueryDefinition for the source query, it may no longer exist.");
        return null;
    }

    public ActionURL updateSnapshotDefinition(ViewContext context, QuerySnapshotDefinition def, BindException errors) throws Exception
    {
        ActionURL ret = super.updateSnapshotDefinition(context, def, errors);

        // update the study dataset columns
/*
        Study study = StudyManager.getInstance().getStudy(context.getContainer());
        DatasetDefinition dsDef = StudyManager.getInstance().getDatasetDefinition(study, def.getName());
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
        QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(context.getContainer(), form.getSchemaName(), form.getSnapshotName());

        if (def != null)
        {
            Study study = StudyManager.getInstance().getStudy(context.getContainer());
            DatasetDefinition dsDef = StudyManager.getInstance().getDatasetDefinitionByName(study, def.getName());
            if (dsDef != null)
            {
                UserSchema schema = AuditLogService.getAuditLogSchema(context.getUser(), context.getContainer());
                if (schema != null)
                {
                    QuerySettings settings = new QuerySettings(context, QueryView.DATAREGIONNAME_DEFAULT);
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(DatasetAuditProvider.COLUMN_NAME_DATASET_ID), dsDef.getRowId());

                    settings.setBaseFilter(filter);
                    settings.setQueryName(DatasetAuditProvider.DATASET_AUDIT_EVENT);

                    return schema.createView(context, settings);
                }
                return null;
            }
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

    static ViewContext getViewContext(QuerySnapshotDefinition def, boolean pushViewContext)
    {
        if (HttpView.hasCurrentView())
            return HttpView.currentContext();
        else
        {
            User user = def.getModifiedBy() != null ? def.getModifiedBy() : def.getCreatedBy();

            return ViewContext.getMockViewContext(user, def.getContainer(), new ActionURL(StudyController.CreateSnapshotAction.class, def.getContainer()), pushViewContext);
        }
    }

    public void datasetChanged(final Dataset def)
    {
        LOG.debug("Cache cleared notification on dataset : " + def.getDatasetId());

        _sourceDataChanged(new SnapshotDependency.SourceDataType(def.getContainer(), SnapshotDependency.SourceDataType.Type.dataset, def));
    }

    private void _sourceDataChanged(SnapshotDependency.SourceDataType type)
    {
        if (_coalesceMap.containsKey(type.getContainer()))
            deferReload(type);
        else
            QUEUE.add(type);
    }

    @Override
    public void categoryDeleted(User user, ParticipantCategoryImpl category) throws Exception
    {
    }

    @Override
    public void categoryCreated(User user, ParticipantCategoryImpl category) throws Exception
    {
    }

    @Override
    public void categoryUpdated(User user, ParticipantCategoryImpl category) throws Exception
    {
        LOG.debug("Category updated notification on participant category : " + category.getLabel());

        Container c = ContainerManager.getForId(category.getContainerId());
        _sourceDataChanged(new SnapshotDependency.SourceDataType(c, SnapshotDependency.SourceDataType.Type.participantCategory, category));
    }

    private void deferReload(SnapshotDependency.SourceDataType sourceData)
    {
        Map<Container, List<QuerySnapshotDefinition>> deferredStudies = _coalesceMap.get(sourceData.getContainer());
        for (QuerySnapshotDefinition snapshotDef : getDependencies(sourceData))
        {
            List<QuerySnapshotDefinition> deferredQuerySnapshots = deferredStudies.get(snapshotDef.getContainer());
            if (deferredQuerySnapshots == null)
            {
                deferredQuerySnapshots = new LinkedList<>();
                deferredStudies.put(snapshotDef.getContainer(), deferredQuerySnapshots);
            }
            deferredQuerySnapshots.add(snapshotDef);
        }
    }

    private static List<QuerySnapshotDefinition> getDependencies(SnapshotDependency.SourceDataType sourceData)
    {
        List<QuerySnapshotDefinition> dependencies = new ArrayList<>();

        switch (sourceData.getType())
        {
            case dataset:
                dependencies = _datasetDependency.getDependencies(sourceData);
                break;
            case participantCategory:
                dependencies = _categoryDependency.getDependencies(sourceData);
                break;
        }
        return dependencies;
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
                    SnapshotDependency.SourceDataType data = QUEUE.take();
                    if (data != null)
                    {
                        try
                        {
                            List<QuerySnapshotDefinition> dependencies = getDependencies(data);
                            for (QuerySnapshotDefinition snapshotDef : dependencies)
                            {
                                LOG.info("Updating snapshot definition : " + snapshotDef.getName());
                                autoUpdateSnapshot(snapshotDef);
                            }
                        }
                        catch (Throwable e)
                        {
                            LOG.error(e);
                            ExceptionUtil.logExceptionToMothership(null, e);
                        }
                    }
                }
            }
            catch (InterruptedException e)
            {
                LOG.info(getClass().getSimpleName() + " is terminating due to interruption");
            }
        }

        public void shutdownPre()
        {
            interrupt();
        }

        public void shutdownStarted()
        {
        }
    }

    // Unfortunately complex generics here.  In English, the container key of the outer map is the source
    // container where the dataset change events are generated.  The value is a map from the study that
    // contains the snapshot datasets to a list of snapshots that need to be refreshed.
    private static final Map<Container, Map<Container, List<QuerySnapshotDefinition>>> _coalesceMap =
            new HashMap<>();

    public void pauseUpdates(Container sourceContainer)
    {
        if (_coalesceMap.containsKey(sourceContainer))
            throw new IllegalStateException("Already coalescing for container " + sourceContainer.getPath());
        _coalesceMap.put(sourceContainer, new HashMap<>());
    }

    public void resumeUpdates(User user, Container sourceContainer)
    {
        if (!_coalesceMap.containsKey(sourceContainer))
            throw new IllegalStateException("Not coalescing for container " + sourceContainer.getPath());
        Map<Container, List<QuerySnapshotDefinition>> snapshotDefSet = _coalesceMap.remove(sourceContainer);
        // update each study container than contains relevant snapshots
        for (Map.Entry<Container, List<QuerySnapshotDefinition>> snapshotEntry : snapshotDefSet.entrySet())
        {
            Container snapshotContainer = snapshotEntry.getKey();
            StudyImpl study = StudyManager.getInstance().getStudy(snapshotContainer);
            List<QuerySnapshotDefinition> snapshotDefs = snapshotEntry.getValue();
            Set<DatasetDefinition> deferredDatasets = new HashSet<>(snapshotDefs.size());
            for (QuerySnapshotDefinition def : snapshotDefs)
            {
                DatasetDefinition deferredDataset = StudyManager.getInstance().getDatasetDefinitionByName(study, def.getName());
                if (deferredDataset == null)
                {
                    LOG.warn("Unable to find dataset " + def.getName() + " to update for query snapshot " + def.getName() + " in study in " + snapshotContainer.getPath() + ", skipping");
                }
                else
                {
                    deferredDatasets.add(deferredDataset);
                    TimerTask task = new SnapshotUpdateTask(def, true);
                    task.run();
                }
            }
            StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user, deferredDatasets);
        }
    }

    private static void autoUpdateSnapshot(QuerySnapshotDefinition def) throws Exception
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

                TimerTask task = new SnapshotUpdateTask(def, false);
                Timer timer = new Timer("QuerySnapshot Update Timer", true);
                timer.schedule(task, startTime.getTime());
            }
        }
    }

    private static class SnapshotUpdateTask extends TimerTask
    {
        private final QuerySnapshotDefinition _def;
        private final boolean _suppressVisitManagerRecalc;

        public SnapshotUpdateTask(QuerySnapshotDefinition def, boolean suppressVisitManagerRecalc)
        {
            _def = def;
            _suppressVisitManagerRecalc = suppressVisitManagerRecalc;
        }

        public void run()
        {
            LOG.debug("Automatically Updating Dataset Snapshot : " + _def.getName());

            try
            {
                _def.setNextUpdate(null);
                _def.save(_def.getModifiedBy());

                QuerySnapshotForm form = new QuerySnapshotForm();
                ViewContext context = getViewContext(_def, true);

                form.setViewContext(context);
                form.init(_def, _def.getCreatedBy());

                BindException errors = new NullSafeBindException(new Object(), "command");
                QuerySnapshotService.get(StudySchema.getInstance().getSchemaName()).updateSnapshot(form, errors, _suppressVisitManagerRecalc);
            }
            catch(Exception e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }
    }
}
