/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.api.study.assay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DelegatingContainerFilter;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpExperimentTable;
import org.labkey.api.exp.query.ExpQCFlagTable;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpTable;
import org.labkey.api.gwt.client.ui.PropertiesEditorUtil;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.actions.AssayDetailRedirectAction;
import org.labkey.api.study.actions.AssayResultDetailsAction;
import org.labkey.api.study.actions.AssayRunDetailsAction;
import org.labkey.api.study.actions.ShowSelectedRunsAction;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.data.xml.TableType;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A child schema of AssayProviderSchema. Scoped to a single assay design (AKA ExpProtocol).
 * Exposes tables for Runs, Batches, etc.
 * User: kevink
 * Date: 9/15/12
 */
public abstract class AssayProtocolSchema extends AssaySchema
{
    public static final String RUNS_TABLE_NAME = "Runs";
    public static final String DATA_TABLE_NAME = "Data";
    public static final String BATCHES_TABLE_NAME = "Batches";
    public static final String QC_FLAGS_TABLE_NAME = "QCFlags";

    /** Legacy location for PropertyDescriptor columns is under a separate node. New location is as a top-level member of the table */
    private static final String RUN_PROPERTIES_COLUMN_NAME = "RunProperties";
    private static final String BATCH_PROPERTIES_COLUMN_NAME = "BatchProperties";

    private static final String DESCR = "Contains data about the %s assay definition and its associated batches and runs.";

    private final ExpProtocol _protocol;
    private final AssayProvider _provider;

    @Deprecated
    public AssayProtocolSchema(User user, Container container, @NotNull ExpProtocol protocol, @Nullable Container targetStudy)
    {
        this(user, container, AssayService.get().getProvider(protocol), protocol, targetStudy);
    }

    public AssayProtocolSchema(User user, Container container, @NotNull AssayProvider provider, @NotNull ExpProtocol protocol, @Nullable Container targetStudy)
    {
        super(SchemaKey.fromParts(AssaySchema.NAME, provider.getResourceName(), protocol.getName()), descr(protocol), user, container, ExperimentService.get().getSchema(), targetStudy);

        if (protocol == null)
            throw new NotFoundException("Assay protocol not found");
        _protocol = protocol;

        if (provider == null)
            throw new NotFoundException("Assay provider for assay protocol '" + protocol.getName() + "' not found");
        _provider = provider;
    }

    private static String descr(ExpProtocol protocol)
    {
        return String.format(DESCR, protocol.getName());
    }

    @NotNull
    public ExpProtocol getProtocol()
    {
        return _protocol;
    }

    @Override
    @NotNull
    public Map<String, QueryDefinition> getQueryDefs()
    {
        // Get all the custom queries from the standard locations
        Map<String, QueryDefinition> result = super.getQueryDefs();

        // Add in ones that are associated with the assay type
        List<QueryDefinition> providerQueryDefs = getFileBasedAssayProviderScopedQueries();
        for (QueryDefinition providerQueryDef : providerQueryDefs)
        {
            if (!result.containsKey(providerQueryDef.getName()))
            {
                result.put(providerQueryDef.getName(), providerQueryDef);
            }
        }
        return result;
    }

    @Override
    @Nullable
    public QueryDefinition getQueryDef(@NotNull String queryName)
    {
        // Get all the custom queries from the standard locations
        QueryDefinition qdef = super.getQueryDef(queryName);
        if (qdef != null)
            return qdef;

        // Add in ones that are associated with the assay type
        List<QueryDefinition> providerQueryDefs = getFileBasedAssayProviderScopedQueries();
        for (QueryDefinition providerQueryDef : providerQueryDefs)
        {
            if (queryName.equals(providerQueryDef.getName()))
                return providerQueryDef;
        }

        return null;
    }

    /**
     * @return all of the custom query definitions associated with the assay provider/type,
     * which will be exposed for each assay design
     */
    public List<QueryDefinition> getFileBasedAssayProviderScopedQueries()
    {
        if (getContainer().getActiveModules().contains(getProvider().getDeclaringModule()))
        {
            Path providerPath = new Path(AssayService.ASSAY_DIR_NAME, getProvider().getResourceName(), QueryService.MODULE_QUERIES_DIRECTORY);
            return QueryService.get().getFileBasedQueryDefs(getUser(), getContainer(), getSchemaName(), providerPath, getProvider().getDeclaringModule());
        }
        return Collections.emptyList();
    }

    @NotNull
    public AssayProvider getProvider()
    {
        return _provider;
    }

    @Override
    /** NOTE: Subclasses should override to add any additional provider specific tables. */
    public Set<String> getTableNames()
    {
        Set<String> names = new HashSet<>();
        names.add(BATCHES_TABLE_NAME);
        names.add(RUNS_TABLE_NAME);
        names.add(DATA_TABLE_NAME);
        names.add(QC_FLAGS_TABLE_NAME);

        return names;
    }

    @Override
    public TableInfo createTable(String name)
    {
        TableInfo table = createProviderTable(name);
        if (table == null)
        {
            // Issue 16787: SQL parse error when resolving legacy assay table names in new assay provider schema.
            String protocolPrefix = _protocol.getName().toLowerCase() + " ";
            if (name.toLowerCase().startsWith(protocolPrefix))
            {
                name = name.substring(protocolPrefix.length());
                table = createProviderTable(name);
            }
        }

        if (table != null)
            overlayMetadata(table, name);

        return table;
    }

    // NOTE: Subclasses should override to add any additional provider specific tables.
    protected TableInfo createProviderTable(String name)
    {
        if (name.equalsIgnoreCase(BATCHES_TABLE_NAME))
            return createBatchesTable();
        else if (name.equalsIgnoreCase(RUNS_TABLE_NAME))
            return createRunsTable();
        else if (name.equalsIgnoreCase(DATA_TABLE_NAME))
            return createDataTable();
        else if (name.equalsIgnoreCase(QC_FLAGS_TABLE_NAME))
            return createQCFlagTable();

        return null;
    }

    public TableInfo createBatchesTable()
    {
        return createBatchesTable(getProtocol(), getProvider(), null);
    }

    /** @return may return null if no results/data are tracked by this assay type */
    @Nullable
    public ContainerFilterable createDataTable()
    {
        ContainerFilterable table = createDataTable(true);
        if (null != table)
        {
            ColumnInfo columnInfo = table.getColumn("Properties");
            if (null != columnInfo)
                fixupPropertyURLs(columnInfo);
        }
        return table;
    }

    public ExpQCFlagTable createQCFlagTable()
    {
        ExpQCFlagTable table = ExperimentService.get().createQCFlagsTable(QC_FLAGS_TABLE_NAME, this);
        table.populate();
        table.setAssayProtocol(getProtocol());
        return table;
    }

    // NOTE: This should be transitioned to partly happen in the TableInfo.overlayMetadata() for the various tables
    // associated with the assay design. They should call into here with the unprefixed name to be added
    // from the assay provider's metadata files.
    protected void overlayMetadata(TableInfo table, String name)
    {
        fixupRenderers(table);

        // Check for metadata associated with the legacy query/schema names
        ArrayList<QueryException> errors = new ArrayList<>();
        String legacyName = getProtocol().getName() + " " + name;
        Collection<TableType> legacyMetadata = QueryService.get().findMetadataOverride(AssayService.get().createSchema(getUser(), getContainer(), _targetStudy), legacyName, false, false, errors, null);
        if (errors.isEmpty())
        {
            table.overlayMetadata(legacyMetadata, this, errors);
        }

        errors = new ArrayList<>();
        Path dir = new Path(AssayService.ASSAY_DIR_NAME, getProvider().getResourceName(), QueryService.MODULE_QUERIES_DIRECTORY);
        Collection<TableType> metadata = QueryService.get().findMetadataOverride(this, name, false, true, errors, dir);
        if (errors.isEmpty())
            table.overlayMetadata(metadata, this, errors);
    }


    private ExpExperimentTable createBatchesTable(ExpProtocol protocol, AssayProvider provider, final ContainerFilter containerFilter)
    {
        final ExpExperimentTable result = ExperimentService.get().createExperimentTable(BATCHES_TABLE_NAME, this);
        result.populate();
        if (containerFilter != null)
        {
            result.setContainerFilter(containerFilter);
        }
        ActionURL runsURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), protocol, result.getContainerFilter());

        // Unfortunately this seems to be the best way to figure out the name of the URL parameter to filter by batch id
        ActionURL fakeURL = new ActionURL(ShowSelectedRunsAction.class, getContainer());
        fakeURL.addFilter(AssayProtocolSchema.RUNS_TABLE_NAME,
                AbstractAssayProvider.BATCH_ROWID_FROM_RUN, CompareType.EQUAL, "${RowId}");
        String paramName = fakeURL.getParameters().get(0).getKey();

        Map<String, String> urlParams = new HashMap<>();
        urlParams.put(paramName, "RowId");
        result.setDetailsURL(new DetailsURL(runsURL, urlParams));

        runsURL.addParameter(paramName, "${RowId}");
        result.getColumn(ExpExperimentTable.Column.Name).setURL(StringExpressionFactory.createURL(runsURL));
        result.setBatchProtocol(protocol);
        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromParts(ExpExperimentTable.Column.Name));
        defaultCols.add(FieldKey.fromParts(ExpExperimentTable.Column.CreatedBy));
        defaultCols.add(FieldKey.fromParts(ExpExperimentTable.Column.RunCount));
        result.setDefaultVisibleColumns(defaultCols);

        Domain batchDomain = provider.getBatchDomain(protocol);
        if (batchDomain != null)
        {
            addPropertyColumn(result, batchDomain, BATCH_PROPERTIES_COLUMN_NAME);
            result.setDomain(batchDomain);
        }

        for (ColumnInfo col : result.getColumns())
        {
            fixupRenderers(col, col);
        }

        result.setDescription("Contains a row per " + protocol.getName() + " batch (a group of runs that were loaded at the same time)");

        return result;
    }

    @Nullable
    /** Implementations may return null if they don't have any data associated with them */
    public abstract ContainerFilterable createDataTable(boolean includeCopiedToStudyColumns);

    public ExpRunTable createRunsTable()
    {
        final ExpRunTable runTable = ExperimentService.get().createRunTable(RUNS_TABLE_NAME, this);
        if (getProvider().isEditableRuns(getProtocol()))
        {
            runTable.addAllowablePermission(UpdatePermission.class);
        }
        runTable.populate();
        LookupForeignKey assayRunFK = new LookupForeignKey()
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return getTable(RUNS_TABLE_NAME);
            }
        };
        runTable.getColumn(ExpRunTable.Column.ReplacedByRun).setFk(assayRunFK);
        runTable.getColumn(ExpRunTable.Column.ReplacesRun).setFk(assayRunFK);
        runTable.getColumn(ExpRunTable.Column.RowId).setURL(new DetailsURL(new ActionURL(AssayDetailRedirectAction.class, getContainer()), Collections.singletonMap("runId", "rowId")));

        addQCFlagColumn(runTable);

        ColumnInfo dataLinkColumn = runTable.getColumn(ExpRunTable.Column.Name);
        dataLinkColumn.setLabel("Assay Id");
        dataLinkColumn.setDescription("The assay/experiment ID that uniquely identifies this assay run.");
        dataLinkColumn.setURL(new DetailsURL(new ActionURL(AssayDetailRedirectAction.class, getContainer()), Collections.singletonMap("runId", "rowId")));

        runTable.setProtocolPatterns(getProtocol().getLSID());

        Domain runDomain = getProvider().getRunDomain(getProtocol());
        if (runDomain != null)
        {
            addPropertyColumn(runTable, runDomain, RUN_PROPERTIES_COLUMN_NAME);
            runTable.setDomain(runDomain);
        }

        List<FieldKey> visibleColumns = new ArrayList<>(runTable.getDefaultVisibleColumns());
        visibleColumns.remove(FieldKey.fromParts(ExpRunTable.Column.Protocol));
        visibleColumns.remove(FieldKey.fromParts(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME));

        // Add the batch column, but replace the lookup with one to the assay's Batches table.
        ColumnInfo batchColumn = runTable.addColumn(AssayService.BATCH_COLUMN_NAME, ExpRunTable.Column.Batch);
        batchColumn.setFk(new QueryForeignKey(this, getContainer(), AssayProtocolSchema.BATCHES_TABLE_NAME, "RowId", null)
        {
            // Issue 23399: Batch properties not accessible from copy to study Nab assay.
            // Propagate run table's container filter to batch table
            @Override
            public TableInfo getLookupTableInfo()
            {
                TableInfo result = super.getLookupTableInfo();
                if (result instanceof ContainerFilterable)
                {
                    ((ContainerFilterable)result).setContainerFilter(new DelegatingContainerFilter(runTable));
                }
                return result;
            }
        });

        visibleColumns.add(FieldKey.fromParts(batchColumn.getName()));
        FieldKey batchPropsKey = FieldKey.fromParts(batchColumn.getName());
        Domain batchDomain = getProvider().getBatchDomain(getProtocol());
        if (batchDomain != null)
        {
            for (DomainProperty col : batchDomain.getProperties())
            {
                if (!col.isHidden() && !AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equalsIgnoreCase(col.getName()))
                    visibleColumns.add(new FieldKey(batchPropsKey, col.getName()));
            }
        }
        runTable.setDefaultVisibleColumns(visibleColumns);

        runTable.setDescription("Contains a row per " + getProtocol().getName() + " run.");

        return runTable;
    }

    protected void addQCFlagColumn(ExpRunTable runTable)
    {
        runTable.addColumn(new AssayQCFlagColumn(runTable, getSchemaName(), true));
        ColumnInfo qcEnabled = runTable.addColumn(new ExprColumn(runTable, "QCFlagsEnabled", AssayQCFlagColumn.createSQLFragment(runTable.getSqlDialect(), "Enabled"), JdbcType.VARCHAR));
        qcEnabled.setLabel("QC Flags Enabled State");
        qcEnabled.setHidden(true);
    }

    private void addPropertyColumn(ExpTable table, Domain domain, String columnName)
    {
        ColumnInfo propsCol = table.addColumns(domain, columnName);
        if (propsCol != null)
        {
            // Will be null if the domain doesn't have any properties
            propsCol.setFk(new AssayPropertyForeignKey(domain));
            propsCol.setUserEditable(false);
            propsCol.setShownInInsertView(false);
            propsCol.setShownInUpdateView(false);
        }
    }

    @Nullable
    @Override
    public String getDomainURI(String queryName)
    {
        String domainURI = null;
        if (null != queryName)
        {
            if (queryName.equalsIgnoreCase(getLegacyProtocolTableName(getProtocol(), DATA_TABLE_NAME)) || queryName.equalsIgnoreCase(DATA_TABLE_NAME))
            {
                Domain resultsDomain = getProvider().getResultsDomain(getProtocol());
                if (resultsDomain != null)
                {
                    domainURI = resultsDomain.getTypeURI();
                }
            }
            else if (queryName.equalsIgnoreCase(getLegacyProtocolTableName(getProtocol(), RUNS_TABLE_NAME)) || queryName.equalsIgnoreCase(RUNS_TABLE_NAME))
            {
                Domain runDomain = getProvider().getRunDomain(getProtocol());
                if (runDomain != null)
                    domainURI = runDomain.getTypeURI();
            }
            else if (queryName.equalsIgnoreCase(getLegacyProtocolTableName(getProtocol(), BATCHES_TABLE_NAME)) || queryName.equalsIgnoreCase(BATCHES_TABLE_NAME))
            {
                Domain batchDomain = getProvider().getBatchDomain(getProtocol());
                if (batchDomain != null)
                    domainURI = batchDomain.getTypeURI();
            }
        }

        return domainURI;
    }

    @Override
    public QueryView createView(ViewContext context, QuerySettings settings, BindException errors)
    {
        String name = settings.getQueryName();
        QueryView result = null;
        if (name != null)
        {
            if (name.equalsIgnoreCase(getLegacyProtocolTableName(getProtocol(), DATA_TABLE_NAME)) || name.equalsIgnoreCase(DATA_TABLE_NAME))
            {
                settings.setQueryName(DATA_TABLE_NAME);
                result = createDataQueryView(context, settings, errors);
            }
            else if (name.equalsIgnoreCase(getLegacyProtocolTableName(getProtocol(), RUNS_TABLE_NAME)) || name.equalsIgnoreCase(RUNS_TABLE_NAME))
            {
                settings.setQueryName(RUNS_TABLE_NAME);
                result = createRunsQueryView(context, settings, errors);
            }
            else if (name.equalsIgnoreCase(getLegacyProtocolTableName(getProtocol(), BATCHES_TABLE_NAME)) || name.equalsIgnoreCase(BATCHES_TABLE_NAME))
            {
                settings.setQueryName(BATCHES_TABLE_NAME);
                result = new BatchListQueryView(getProtocol(), this, settings);
            }
        }

        if (result == null)
        {
            String prefix = getProtocol().getName().toLowerCase() + " ";
            // Check if we need to reset the name to the new, post refactor name
            if (name != null && name.toLowerCase().startsWith(prefix))
            {
                // Cut off the prefix part that's no longer a part of the table name and is
                // now part of the schema name
                String newName = name.substring(prefix.length());
                // We only need to check this for tables in the schema, not custom queries since they didn't get
                // moved as part of the refactor
                if (new CaseInsensitiveHashSet(getTableNames()).contains(newName))
                {
                    settings.setQueryName(newName);
                }
            }
            result = super.createView(context, settings, errors);
        }
        return result;
    }

    @Override
    protected QuerySettings createQuerySettings(String dataRegionName, String queryName, String viewName)
    {
        QuerySettings result = super.createQuerySettings(dataRegionName, queryName, viewName);
        if (RUNS_TABLE_NAME.equals(queryName))
        {
            result.setBaseSort(new Sort("-RowId"));
        }
        result.setLastFilterScope(getLastFilterScope());
        return result;
    }

    @Nullable
    protected RunListQueryView createRunsQueryView(ViewContext context, QuerySettings settings, BindException errors)
    {
        RunListQueryView queryView = new RunListQueryView(this, settings, new AssayRunType(getProtocol(), getContainer()));

        if (getProvider().hasCustomView(ExpProtocol.AssayDomainTypes.Run, true))
        {
            ActionURL runDetailsURL = new ActionURL(AssayRunDetailsAction.class, context.getContainer());
            runDetailsURL.addParameter("rowId", getProtocol().getRowId());
            Map<String, String> params = new HashMap<>();
            params.put("runId", "RowId");

            AbstractTableInfo ati = (AbstractTableInfo)queryView.getTable();
            ati.setDetailsURL(new DetailsURL(runDetailsURL, params));
            queryView.setShowDetailsColumn(true);
        }

        return queryView;
    }

    @Nullable
    protected ResultsQueryView createDataQueryView(ViewContext context, QuerySettings settings, BindException errors)
    {
        ResultsQueryView queryView = new ResultsQueryView(_protocol, context, settings);

        if (_provider.hasCustomView(ExpProtocol.AssayDomainTypes.Result, true))
        {
            ActionURL resultDetailsURL = new ActionURL(AssayResultDetailsAction.class, context.getContainer());
            resultDetailsURL.addParameter("rowId", _protocol.getRowId());
            Map<String, String> params = new HashMap<>();
            // map ObjectId to url parameter ResultDetailsForm.dataRowId
            params.put("dataRowId", AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME);

            AbstractTableInfo ati = (AbstractTableInfo)queryView.getTable();
            ati.setDetailsURL(new DetailsURL(resultDetailsURL, params));
            queryView.setShowDetailsColumn(true);
        }

        return queryView;
    }

    @Override
    public List<CustomView> getModuleCustomViews(Container container, QueryDefinition qd)
    {
        List<CustomView> result = new ArrayList<>();

        // Look for <MODULE>/assay/<ASSAY_TYPE>/queries/<TABLE_TYPE>/*.qview.xml files
        // where TABLE_TYPE is Runs, Batches, Data, etc
        Path providerPath = new Path(AssayService.ASSAY_DIR_NAME, getProvider().getResourceName(), QueryService.MODULE_QUERIES_DIRECTORY, FileUtil.makeLegalName(qd.getName()));
        result.addAll(QueryService.get().getFileBasedCustomViews(container, qd, providerPath, qd.getName(), getProvider().getDeclaringModule()));

        // Look in the legacy location in file-based modules (assay.<PROTOCOL_NAME> Batches, etc)
        String legacyQueryName = _protocol.getName() + " " + qd.getName();
        String legacySchemaName = AssaySchema.NAME;
        Path legacyPath = new Path(QueryService.MODULE_QUERIES_DIRECTORY, legacySchemaName, FileUtil.makeLegalName(legacyQueryName));
        result.addAll(QueryService.get().getFileBasedCustomViews(container, qd, legacyPath, qd.getName()));

        // Look in the legacy location in file-based modules (assay.<PROTOCOL_NAME> Batches, etc)
        result.addAll(QueryService.get().getCustomViews(getUser(), container, getUser(), legacySchemaName, legacyQueryName, true));

        // Look in the standard location (based on the assay design name) for additional custom views
        result.addAll(super.getModuleCustomViews(container, qd));
        return result;
    }

    /**
     * in order to allow using short name for properties in assay table we need
     * to patch up the keys
     *
     * for instance ${myProp} instead of ${RunProperties/myProp}
     *
     * @param fk properties column (e.g. RunProperties)
     */
    private static void fixupPropertyURL(ColumnInfo fk, ColumnInfo col)
    {
        if (null == fk || !(col.getURL() instanceof StringExpressionFactory.FieldKeyStringExpression))
            return;

        TableInfo table = fk.getParentTable();
        StringExpressionFactory.FieldKeyStringExpression fkse = (StringExpressionFactory.FieldKeyStringExpression)col.getURL();
        // quick check
        Set<FieldKey> keys = fkse.getFieldKeys();
        Map<FieldKey,FieldKey> map = new HashMap<>();
        for (FieldKey key : keys)
        {
            if (null == key.getParent() && null == table.getColumn(key.getName()))
                map.put(key, new FieldKey(fk.getFieldKey(), key.getName()));
        }
        if (map.isEmpty())
            return;
        col.setURL(fkse.remapFieldKeys(null, map));
    }


    /**
     * Adds columns to an assay data table, providing a link to any datasets that have
     * had data copied into them.
     * @return The names of the added columns that should be visible
     */
    public Set<String> addCopiedToStudyColumns(AbstractTableInfo table, boolean setVisibleColumns)
    {
        Set<String> visibleColumnNames = new HashSet<>();
        int datasetIndex = 0;
        Set<String> usedColumnNames = new HashSet<>();
        for (final Dataset assayDataset : StudyService.get().getDatasetsForAssayProtocol(getProtocol()))
        {
            if (!assayDataset.getContainer().hasPermission(getUser(), ReadPermission.class) || !assayDataset.canRead(getUser()))
            {
                continue;
            }

            String datasetIdColumnName = "dataset" + datasetIndex++;
            final StudyDatasetColumn datasetColumn = new StudyDatasetColumn(table, datasetIdColumnName, getProvider(), assayDataset, getUser());
            datasetColumn.setHidden(true);
            datasetColumn.setUserEditable(false);
            datasetColumn.setShownInInsertView(false);
            datasetColumn.setShownInUpdateView(false);
            datasetColumn.setReadOnly(true);
            table.addColumn(datasetColumn);

            String studyCopiedSql = "(SELECT CASE WHEN " + datasetColumn.getDatasetIdAlias() +
                "._key IS NOT NULL THEN 'copied' ELSE NULL END)";

            String studyName = assayDataset.getStudy().getLabel();
            if (studyName == null)
                continue; // No study in that folder
            String studyColumnName = "copied_to_" + PropertiesEditorUtil.sanitizeName(studyName);

            // column names must be unique. Prevent collisions
            while (usedColumnNames.contains(studyColumnName))
                studyColumnName = studyColumnName + datasetIndex;
            usedColumnNames.add(studyColumnName);

            final ExprColumn studyCopiedColumn = new ExprColumn(table,
                studyColumnName,
                new SQLFragment(studyCopiedSql),
                JdbcType.VARCHAR,
                datasetColumn);
            final String copiedToStudyColumnCaption = "Copied to " + studyName;
            studyCopiedColumn.setLabel(copiedToStudyColumnCaption);
            studyCopiedColumn.setUserEditable(false);
            studyCopiedColumn.setReadOnly(true);
            studyCopiedColumn.setShownInInsertView(false);
            studyCopiedColumn.setShownInUpdateView(false);
            studyCopiedColumn.setURL(StringExpressionFactory.createURL(StudyService.get().getDatasetURL(assayDataset.getContainer(), assayDataset.getDatasetId())));

            table.addColumn(studyCopiedColumn);

            visibleColumnNames.add(studyCopiedColumn.getName());
        }
        if (setVisibleColumns)
        {
            List<FieldKey> visibleColumns = new ArrayList<>();
            for (FieldKey key : table.getDefaultVisibleColumns())
            {
                visibleColumns.add(key);
            }
            for (String columnName : visibleColumnNames)
            {
                visibleColumns.add(new FieldKey(null, columnName));
            }
            table.setDefaultVisibleColumns(visibleColumns);
        }

        return visibleColumnNames;
    }


    private static void fixupPropertyURLs(ColumnInfo fk)
    {
        for (ColumnInfo c : fk.getParentTable().getColumns())
            fixupPropertyURL(fk, c);
    }

    public void fixupRenderers(TableInfo table)
    {
        for (ColumnInfo col : table.getColumns())
        {
            fixupRenderers(col, col);
        }
    }

    public void fixupRenderers(final ColumnRenderProperties col, ColumnInfo columnInfo)
    {
        if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equalsIgnoreCase(col.getName()))
        {
            columnInfo.setFk(new LookupForeignKey("Folder", "Label")
            {
                public TableInfo getLookupTableInfo()
                {
                    FilteredTable table = new FilteredTable<>(DbSchema.get("study").getTable("study"), AssayProtocolSchema.this);
                    table.setContainerFilter(new StudyContainerFilter(AssayProtocolSchema.this));
                    ExprColumn col = new ExprColumn(table, "Folder", new SQLFragment("CAST (" + ExprColumn.STR_TABLE_ALIAS + ".Container AS VARCHAR(200))"), JdbcType.VARCHAR);
                    col.setKeyField(true);
                    ContainerForeignKey.initColumn(col, AssayProtocolSchema.this);
                    table.addColumn(col);
                    table.addWrapColumn(table.getRealTable().getColumn("Label"));
                    table.setPublic(false);
                    return table;
                }
            });
        }
        else if (AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equalsIgnoreCase(col.getName()))
            columnInfo.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new ParticipantVisitResolverColumn(colInfo);
                }
            });
    }

    /**
     * Scope the .lastFilter parameter to the assay design, so that we don't end up reusing the same filters
     * when you view a different assay's data in the same container
     */
    public String getLastFilterScope()
    {
        return getLastFilterScope(getProtocol());
    }

    /**
     * Scope the .lastFilter parameter to the assay design, so that we don't end up reusing the same filters
     * when you view a different assay's data in the same container
     */
    public static String getLastFilterScope(ExpProtocol protocol)
    {
        return "Assay" + protocol.getRowId();
    }

    private class AssayPropertyForeignKey extends PropertyForeignKey
    {
        public AssayPropertyForeignKey(Domain domain)
        {
            super(domain, AssayProtocolSchema.this);
        }

        @Override
        protected ColumnInfo constructColumnInfo(ColumnInfo parent, FieldKey name, final PropertyDescriptor pd)
        {
            ColumnInfo result = super.constructColumnInfo(parent, name, pd);
            fixupRenderers(pd, result);
            return result;
        }
    }

    @Override
    public Collection<String> getReportKeys(String queryName)
    {
        // Include the standard report name
        Set<String> result = new HashSet<>(super.getReportKeys(queryName));
        // Include the legacy schema/query name so we don't lose old reports
        result.add(ReportUtil.getReportKey(AssaySchema.NAME, getLegacyProtocolTableName(getProtocol(), queryName)));
        return result;
    }

    private class ParticipantVisitResolverColumn extends DataColumn
    {
        public ParticipantVisitResolverColumn(ColumnInfo colInfo)
        {
            super(colInfo);
        }

        @NotNull
        @Override
        public String getFormattedValue(RenderContext ctx)
        {
            // Value may be a simple string (legacy), or JSON.

            Object val = super.getDisplayValue(ctx);

            try
            {
                Map<String, String> decodedVals = new ObjectMapper().readValue(val.toString(), Map.class);
                StringBuilder sb = new StringBuilder(decodedVals.remove(ParticipantVisitResolverType.Serializer.STRING_VALUE_PROPERTY_NAME));

                // Issue 21126 If lookup was pasted tsv, could still get a default list entry in properties list. Fix the redisplay
                // This addresses the issue for existing runs. New runs avoid the problem with corresponding change in ParticipantResolverType.Serializer.encode
                if (ThawListResolverType.TEXT_NAMESPACE_SUFFIX.equals(decodedVals.get(ThawListResolverType.THAW_LIST_TYPE_INPUT_NAME)))
                {
                    decodedVals.remove(ThawListResolverType.THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME);
                    decodedVals.remove(ThawListResolverType.THAW_LIST_LIST_QUERY_NAME_INPUT_NAME);
                }
                for (Map.Entry<String, String> decodedVal : decodedVals.entrySet())
                {
                    sb.append("<br/>");
                    sb.append(StringUtils.substringAfter(decodedVal.getKey(), ThawListResolverType.NAMESPACE_PREFIX));
                    sb.append(" : ");
                    sb.append(decodedVal.getValue());
                }

                return sb.toString();
            }
            catch (IOException e)
            {
                // Value wasn't JSON, was a legacy simple string. Output it
                return  val.toString();
            }
        }
    }
}
