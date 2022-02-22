/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
package org.labkey.api.assay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.assay.actions.AssayDetailRedirectAction;
import org.labkey.api.assay.actions.AssayResultDetailsAction;
import org.labkey.api.assay.actions.AssayRunDetailsAction;
import org.labkey.api.assay.query.BatchListQueryView;
import org.labkey.api.assay.query.ResultsQueryView;
import org.labkey.api.assay.query.RunListQueryView;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
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
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DetailsURL;
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
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AssayReadPermission;
import org.labkey.api.security.permissions.QCAnalystPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.study.assay.StudyContainerFilter;
import org.labkey.api.study.assay.ThawListResolverType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.data.xml.TableType;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.sql.SQLException;
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
public abstract class AssayProtocolSchema extends AssaySchema implements UserSchema.HasContextualRoles
{
    public static final String RUNS_TABLE_NAME = "Runs";
    public static final String DATA_TABLE_NAME = "Data";
    public static final String BATCHES_TABLE_NAME = "Batches";
    public static final String QC_FLAGS_TABLE_NAME = "QCFlags";

    public static final String EXCLUSION_REPORT_TABLE_NAME = "ExclusionReport";

    /** Legacy location for PropertyDescriptor columns is under a separate node. New location is as a top-level member of the table */
    private static final String RUN_PROPERTIES_COLUMN_NAME = "RunProperties";
    private static final String BATCH_PROPERTIES_COLUMN_NAME = "BatchProperties";

    private static final String DESCR = "Contains data about the %s assay definition and its associated batches and runs.";

    private final ExpProtocol _protocol;
    private final AssayProvider _provider;

    private Set<Role> _contextualRoles = new HashSet<>();

    public static SchemaKey schemaName(@NotNull AssayProvider provider, @NotNull ExpProtocol protocol)
    {
        return SchemaKey.fromParts(AssaySchema.NAME, provider.getResourceName(), protocol.getName());
    }

    public AssayProtocolSchema(User user, Container container, @NotNull AssayProvider provider, @NotNull ExpProtocol protocol, @Nullable Container targetStudy)
    {
        super(schemaName(provider, protocol), descr(protocol), user, container, ExperimentService.get().getSchema(), targetStudy);

        if (protocol == null)
            throw new NotFoundException("Assay protocol not found");
        _protocol = protocol;

        if (provider == null)
            throw new NotFoundException("Assay provider for assay protocol '" + protocol.getName() + "' not found");
        _provider = provider;
    }

    /** NOTE: this method should not be used for schemas created via QuerySchema.getSchema() (e.g. DefaultSchema.getSchema())
     * as those might be cached.
     * This should only be used for locally created Schema.
     */
    public void addContextualRole(@NotNull Role role)
    {
        assert role == RoleManager.getRole(role.getClass());
        _contextualRoles.add(role);
    }

    @Override
    public @NotNull Set<Role> getContextualRoles()
    {
        return _contextualRoles;
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
            if (queryName.equalsIgnoreCase(providerQueryDef.getName()))
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

//    @Override
//    public TableInfo createTable(String name)
//    {
//        throw new IllegalStateException();
//    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        TableInfo table = createProviderTable(name, cf);
        if (table == null)
        {
            // Issue 16787: SQL parse error when resolving legacy assay table names in new assay provider schema.
            String protocolPrefix = _protocol.getName().toLowerCase() + " ";
            if (name.toLowerCase().startsWith(protocolPrefix))
            {
                name = name.substring(protocolPrefix.length());
                table = createProviderTable(name, cf);
            }
        }

        if (table != null)
            overlayMetadata(table, name);

        return table;
    }

    // NOTE: Subclasses should override to add any additional provider specific tables.
    protected TableInfo createProviderTable(String name, ContainerFilter cf)
    {
        if (name.equalsIgnoreCase(BATCHES_TABLE_NAME))
            return createBatchesTable(cf);
        else if (name.equalsIgnoreCase(RUNS_TABLE_NAME))
            return createRunsTable(cf);
        else if (name.equalsIgnoreCase(DATA_TABLE_NAME))
            return createDataTable(cf);
        else if (name.equalsIgnoreCase(QC_FLAGS_TABLE_NAME))
            return createQCFlagTable(cf);

        return null;
    }

    public TableInfo createBatchesTable(ContainerFilter cf)
    {
        return createBatchesTable(getProtocol(), getProvider(), cf);
    }

    /**
     * @return may return null if no results/data are tracked by this assay type
     */
    @Nullable
    public TableInfo createDataTable(ContainerFilter cf)
    {
        TableInfo table = createDataTable(cf, true);
        if (null != table)
        {
            var columnInfo = ((AbstractTableInfo)table).getMutableColumn("Properties");
            if (null != columnInfo)
                fixupPropertyURLs(columnInfo);
        }
        return table;
    }

    public ExpQCFlagTable createQCFlagTable(ContainerFilter cf)
    {
        ExpQCFlagTable table = ExperimentService.get().createQCFlagsTable(QC_FLAGS_TABLE_NAME, this, cf);
        table.populate();
        table.setAssayProtocol(getProvider(), getProtocol());
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
        Collection<TableType> metadata = QueryService.get().findMetadataOverride(this, name, false, false, errors, dir);
        if (errors.isEmpty())
            table.overlayMetadata(metadata, this, errors);
    }


    private ExpExperimentTable createBatchesTable(ExpProtocol protocol, AssayProvider provider, ContainerFilter containerFilter)
    {
        final ExpExperimentTable result = ExperimentService.get().createExperimentTable(BATCHES_TABLE_NAME, this, containerFilter);
        result.populate();
        ActionURL runsURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), protocol, result.getContainerFilter());

        Map<String, String> urlParams = new HashMap<>();
        String paramName = PageFlowUtil.urlProvider(AssayUrls.class).getBatchIdFilterParam();
        urlParams.put(paramName, "RowId");
        result.setDetailsURL(new DetailsURL(runsURL, urlParams));

        runsURL.addParameter(paramName, "${RowId}");
        result.getMutableColumn(ExpExperimentTable.Column.Name).setURL(StringExpressionFactory.createURL(runsURL));
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
            fixupRenderers(col, (MutableColumnInfo)col);
        }

        result.setDescription("Contains a row per " + protocol.getName() + " batch (a group of runs that were loaded at the same time)");

        return result;
    }

    /** Implementations may return null if they don't have any data associated with them */
    @Nullable
    public abstract TableInfo createDataTable(ContainerFilter cf, boolean includeLinkedToStudyColumns);

    public ExpRunTable createRunsTable(ContainerFilter cf)
    {
        final ExpRunTable runTable = ExperimentService.get().createRunTable(RUNS_TABLE_NAME, this, cf);
        if (getProvider().isEditableRuns(getProtocol()))
        {
            runTable.addAllowablePermission(UpdatePermission.class);
        }
        runTable.populate();
        LookupForeignKey assayRunFK = new LookupForeignKey(cf, null, null)
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return getTable(RUNS_TABLE_NAME, getLookupContainerFilter());
            }
        };
        runTable.getMutableColumn(ExpRunTable.Column.ReplacedByRun).setFk(assayRunFK);
        runTable.getMutableColumn(ExpRunTable.Column.ReplacesRun).setFk(assayRunFK);
        runTable.getMutableColumn(ExpRunTable.Column.RowId).setURL(new DetailsURL(new ActionURL(AssayDetailRedirectAction.class, getContainer()), Collections.singletonMap("runId", "rowId")));

        addQCFlagColumn(runTable);

        var dataLinkColumn = runTable.getMutableColumn(ExpRunTable.Column.Name);
        dataLinkColumn.setLabel("Assay ID");
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
        var batchColumn = runTable.addColumn(AssayService.BATCH_COLUMN_NAME, ExpRunTable.Column.Batch);
        // Issue 23399: Batch properties not accessible from copy (link) to study Nab assay.
        // Propagate run table's container filter to batch table
        batchColumn.setFk(
                QueryForeignKey
                        .from(this, runTable.getContainerFilter())
                        .to(AssayProtocolSchema.BATCHES_TABLE_NAME, "RowId", null)

        );

        // add any QC filter conditions if applicable
        AssayQCService svc = AssayQCService.getProvider();
        SQLFragment qcFragment = svc.getRunsTableCondition(getProtocol(), getContainer(), getUser());
        runTable.addCondition(qcFragment);

        // include QCFlags if the assay is qc enabled
        if (getProvider().isQCEnabled(getProtocol()))
        {
            visibleColumns.add(FieldKey.fromParts(AssayQCFlagColumn.NAME));
        }

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
        AssayFlagHandler handler = AssayFlagHandler.getHandler(getProvider());
        if (handler != null)
        {
            var flagCol = handler.createFlagColumn(getProtocol(), runTable, getSchemaName(), true);
            var enabledCol = handler.createQCEnabledColumn(getProtocol(), runTable, getSchemaName());

            if (flagCol != null)
                runTable.addColumn(flagCol);
            if (enabledCol != null)
                runTable.addColumn(enabledCol);
        }
    }

    private void addPropertyColumn(ExpTable table, Domain domain, String columnName)
    {
        var propsCol = table.addColumns(domain, columnName);
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

            queryView.setDetailsURL(new DetailsURL(runDetailsURL, params));
            queryView.setShowDetailsColumn(true);
        }

        // add an indicator for any missing rows
        DataRegionDecorator decorator = new DataRegionDecorator()
        {
            @Override
            public QueryView createAllResultsQueryView(ViewContext context, QuerySettings settings)
            {
                // need to create a new protocol schema with the contextual role added to the user
                AssayProtocolSchema schema = AssayService.get().getProvider(getProtocol()).createProtocolSchema(context.getUser(), getContainer(), getProtocol(), getTargetStudy());
                return new RunListQueryView(schema, settings, new AssayRunType(getProtocol(), getContainer()));
            }
        };
        decorator.addQCWarningIndicator(queryView, context, settings, errors);

        return queryView;
    }

    @Nullable
    protected ResultsQueryView createDataQueryView(ViewContext context, QuerySettings settings, BindException errors)
    {
        AssayWellExclusionService svc = AssayWellExclusionService.getProvider(_protocol);
        ResultsQueryView queryView = new ResultsQueryView(_protocol, context, settings);
        if (svc != null)
        {
            ResultsQueryView.ResultsDataRegion dr = svc.createResultsDataRegion(_protocol);
            if (dr != null)
            {
                queryView = new ResultsQueryView(_protocol, context, settings)
                {
                    @Override
                    protected DataRegion createDataRegion()
                    {
                        initializeDataRegion(dr);
                        return dr;
                    }
                };
            }
        }

        // add an indicator for any missing rows
        DataRegionDecorator decorator = new DataRegionDecorator()
        {
            @Override
            public QueryView createAllResultsQueryView(ViewContext context, QuerySettings settings)
            {
                return new ResultsQueryView(_protocol, context, settings);
            }
        };
        decorator.addQCWarningIndicator(queryView, context, settings, errors);

        if (_provider.hasCustomView(ExpProtocol.AssayDomainTypes.Result, true))
        {
            ActionURL resultDetailsURL = new ActionURL(AssayResultDetailsAction.class, context.getContainer());
            resultDetailsURL.addParameter("rowId", _protocol.getRowId());
            Map<String, String> params = new HashMap<>();
            // map ObjectId to url parameter ResultDetailsForm.dataRowId
            params.put("dataRowId", AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME);

            queryView.setDetailsURL(new DetailsURL(resultDetailsURL, params));
            queryView.setShowDetailsColumn(true);
        }

        return queryView;
    }

    abstract class DataRegionDecorator
    {
        abstract QueryView createAllResultsQueryView(ViewContext context, QuerySettings settings);

        /**
         * Helper to display an indicator in the dataregion message area if there are rows not being shown because
         * they have not been QC'ed and the user is not a QC Analyst.
         */
        public void addQCWarningIndicator(QueryView baseQueryView, ViewContext context, QuerySettings settings, BindException errors)
        {
            if (AssayQCService.getProvider().isQCEnabled(getProtocol()))
            {
                User user = context.getUser();

                // Don't bother checking for elided rows if the user doesn't at least have ReadPermission
                if (!context.getContainer().hasPermission(user, AssayReadPermission.class))
                    return;

                // if the user does not have the QCAnalyst permission, they may not be seeing unapproved data
                if (!context.getContainer().hasPermission(user, QCAnalystPermission.class))
                {
                    Set<Role> contextualRoles = new HashSet<>(user.getStandardContextualRoles());
                    Role qcRole = RoleManager.getRole("org.labkey.api.security.roles.QCAnalystRole");
                    Role readerRole = RoleManager.getRole("org.labkey.api.security.roles.ReaderRole");
                    if (qcRole != null && readerRole != null)
                    {
                        try
                        {
                            contextualRoles.add(RoleManager.getRole(qcRole.getClass()));
                            contextualRoles.add(RoleManager.getRole(readerRole.getClass()));
                            User elevatedUser = new LimitedUser(user, user.getGroups(), contextualRoles, true);

                            ViewContext viewContext = new ViewContext(context);
                            viewContext.setUser(elevatedUser);
                            QuerySettings qs = getSettings(viewContext, settings.getDataRegionName(), settings.getQueryName());

                            // we want all the rows
                            qs.setMaxRows(Table.ALL_ROWS);
                            QueryView allResultsQueryView = createAllResultsQueryView(viewContext, qs);

                            DataView dataView = allResultsQueryView.createDataView();

                            RenderContext renderContext = dataView.getRenderContext();
                            try (Results r = dataView.getDataRegion().getResults(renderContext))
                            {
                                final int rowCount = r.countAll();

                                baseQueryView.setMessageSupplier(dataRegion -> {
                                    try
                                    {
                                        // Get a fresh set of aggregates from the render context. We're applying
                                        // a different set of filters based on QC state than the main DataRegion
                                        Aggregate countAgg = Aggregate.createCountStar();
                                        Map<String, List<Aggregate.Result>> allAggResults = renderContext.getAggregates(dataRegion.getDisplayColumns(), dataRegion.getTable(), dataRegion.getSettings(), dataRegion.getName(), Collections.singletonList(countAgg), dataRegion.getQueryParameters(), dataRegion.isAllowAsync());
                                        List<Aggregate.Result> aggResults = allAggResults.get(countAgg.getFieldKey().toString());
                                        assert aggResults.size() == 1 : "Expected a single aggregate result but got " + aggResults.size();
                                        int totalRows = ((Number)aggResults.get(0).getValue()).intValue();

                                        if (totalRows < rowCount)
                                        {
                                            long count = rowCount - totalRows;
                                            String msg = count > 1 ? "There are " + count + " rows not shown due to unapproved QC state."
                                                    : "There is one row not shown due to unapproved QC state.";
                                            DataRegion.Message drm = new DataRegion.Message(msg, DataRegion.MessageType.WARNING, DataRegion.MessagePart.view);
                                            return Collections.singletonList(drm);
                                        }
                                        return Collections.emptyList();
                                    }
                                    catch(IOException e)
                                    {
                                        throw new RuntimeException(e);
                                    }
                                });
                            }
                        }
                        catch (SQLException | IOException e)
                        {
                            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<CustomView> getModuleCustomViews(Container container, QueryDefinition qd)
    {
        // Look for <MODULE>/assay/<ASSAY_TYPE>/queries/<TABLE_TYPE>/*.qview.xml files
        // where TABLE_TYPE is Runs, Batches, Data, etc
        Path providerPath = new Path(AssayService.ASSAY_DIR_NAME, getProvider().getResourceName(), QueryService.MODULE_QUERIES_DIRECTORY, FileUtil.makeLegalName(qd.getName()));
        List<CustomView> result = new ArrayList<>(QueryService.get().getFileBasedCustomViews(container, qd, providerPath, qd.getName(), getProvider().getDeclaringModule()));

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
    private static void fixupPropertyURL(ColumnInfo fk, MutableColumnInfo col)
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

    private static void fixupPropertyURLs(MutableColumnInfo fk)
    {
        for (ColumnInfo c : fk.getParentTable().getColumns())
            fixupPropertyURL(fk, (MutableColumnInfo)c);
    }

    public void fixupRenderers(TableInfo table)
    {
        for (ColumnInfo col : table.getColumns())
        {
            fixupRenderers(col, (MutableColumnInfo)col);
        }
    }

    public void fixupRenderers(final ColumnRenderProperties col, MutableColumnInfo columnInfo)
    {
        StudyService svc = StudyService.get();

        if (null != svc && AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equalsIgnoreCase(col.getName()))
        {
            columnInfo.setFk(new LookupForeignKey("Folder", "Label")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    FilteredTable table = new FilteredTable<>(DbSchema.get("study", DbSchemaType.Module).getTable("study"), AssayProtocolSchema.this, getLookupContainerFilter());
                    AliasedColumn col = new AliasedColumn(table, "Folder", table.getRealTable().getColumn("Container"));
                    col.setKeyField(true);
                    table.addColumn(col);
                    table.addWrapColumn(table.getRealTable().getColumn("Label"));
                    table.setPublic(false);
                    return table;
                }

                @Override
                protected ContainerFilter getLookupContainerFilter()
                {
                    return new StudyContainerFilter(AssayProtocolSchema.this);
                }
            });
        }
        else if (AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equalsIgnoreCase(col.getName()))
        {
            columnInfo.setDisplayColumnFactory(ParticipantVisitResolverColumn::new);
        }
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
            super(AssayProtocolSchema.this, null, domain);
        }

        @Override
        protected BaseColumnInfo constructColumnInfo(ColumnInfo parent, FieldKey name, final PropertyDescriptor pd)
        {
            var result = super.constructColumnInfo(parent, name, pd);
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
        public HtmlString getFormattedHtml(RenderContext ctx)
        {
            // Value may be a simple string (legacy), or JSON.

            Object val = super.getDisplayValue(ctx);

            try
            {
                Map<String, String> decodedVals = new ObjectMapper().readValue(val.toString(), Map.class);
                HtmlStringBuilder sb = HtmlStringBuilder.of(decodedVals.remove(ParticipantVisitResolverType.Serializer.STRING_VALUE_PROPERTY_NAME));

                // Issue 21126 If lookup was pasted tsv, could still get a default list entry in properties list. Fix the redisplay
                // This addresses the issue for existing runs. New runs avoid the problem with corresponding change in ParticipantResolverType.Serializer.encode
                if (ThawListResolverType.TEXT_NAMESPACE_SUFFIX.equals(decodedVals.get(ThawListResolverType.THAW_LIST_TYPE_INPUT_NAME)))
                {
                    decodedVals.remove(ThawListResolverType.THAW_LIST_LIST_SCHEMA_NAME_INPUT_NAME);
                    decodedVals.remove(ThawListResolverType.THAW_LIST_LIST_QUERY_NAME_INPUT_NAME);
                }
                for (Map.Entry<String, String> decodedVal : decodedVals.entrySet())
                {
                    sb.append(HtmlString.unsafe("<br/>"));
                    sb.append(StringUtils.substringAfter(decodedVal.getKey(), ThawListResolverType.NAMESPACE_PREFIX));
                    sb.append(" : ");
                    sb.append(decodedVal.getValue());
                }

                return sb.getHtmlString();
            }
            catch (IOException e)
            {
                // Value wasn't JSON, was a legacy simple string. Output it
                return HtmlString.of(val.toString());
            }
        }
    }
}
