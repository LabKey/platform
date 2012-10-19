package org.labkey.api.study.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpExperimentTable;
import org.labkey.api.exp.query.ExpQCFlagTable;
import org.labkey.api.exp.query.ExpRunTable;
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
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.actions.AssayDetailRedirectAction;
import org.labkey.api.study.actions.ShowSelectedRunsAction;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.data.xml.TableType;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 9/15/12
 */
public abstract class AssayProtocolSchema extends AssaySchema
{
    /** Legacy location for PropertyDescriptor columns is under a separate node. New location is as a top-level member of the table */
    private static final String RUN_PROPERTIES_COLUMN_NAME = "RunProperties";
    private static final String BATCH_PROPERTIES_COLUMN_NAME = "BatchProperties";

    private static final String DESCR = "Contains data about the %s assay definition and its associated batches and runs.";

    private final ExpProtocol _protocol;
    private final AssayProvider _provider;

    // XXX: Not sure if prefixedTableNames should be a property of the schema or an argument to .getTableNames() and .createTable()
    public AssayProtocolSchema(User user, Container container, @NotNull ExpProtocol protocol, @Nullable Container targetStudy)
    {
        super(SchemaKey.fromParts(AssaySchema.NAME, AssayService.get().getProvider(protocol).getResourceName(), protocol.getName()), descr(protocol), user, container, ExperimentService.get().getSchema(), targetStudy);
        _protocol = protocol;
        _provider = AssayService.get().getProvider(protocol);
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
    public Map<String, QueryDefinition> getQueryDefs()
    {
        Map<String, QueryDefinition> result = super.getQueryDefs();
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

    /**
     * @return all of the custom query definitions associated with the assay provider/type,
     * which will be exposed for each assay design
     */
    public List<QueryDefinition> getFileBasedAssayProviderScopedQueries()
    {
        Path providerPath = new Path(AssayService.ASSAY_DIR_NAME, getProvider().getResourceName(), QueryService.MODULE_QUERIES_DIRECTORY);
        return QueryService.get().getFileBasedQueryDefs(getUser(), getContainer(), getSchemaName(), providerPath);
    }

    @NotNull
    public AssayProvider getProvider()
    {
        return _provider;
    }

    @Override
    // NOTE: Subclasses should override to add any additional provider specific tables.
    public Set<String> getTableNames()
    {
        Set<String> names = new HashSet<String>();
        names.add(getBatchesTableName(_protocol, false));
        names.add(getRunsTableName(_protocol, false));
        names.add(getResultsTableName(_protocol, false));
        names.add(getQCFlagTableName(_protocol, false));

        return names;
    }

    @Override
    public TableInfo createTable(String name)
    {
        TableInfo table = createProviderTable(name);
        if (table != null)
            overlayMetadata(table, name);

        return table;
    }

    // NOTE: Subclasses should override to add any additional provider specific tables.
    protected TableInfo createProviderTable(String name)
    {
        if (name.equalsIgnoreCase(getBatchesTableName(getProtocol(), false)))
            return createBatchesTable();
        else if (name.equalsIgnoreCase(getRunsTableName(getProtocol(), false)))
            return createRunsTable();
        else if (name.equalsIgnoreCase(getResultsTableName(getProtocol(), false)))
            return createDataTable();
        else if (name.equalsIgnoreCase(getQCFlagTableName(getProtocol(), false)))
            return createQCFlagTable();

        return null;
    }

    public TableInfo createBatchesTable()
    {
        return createBatchesTable(getProtocol(), getProvider(), null, false);
    }

    /** @return may return null if no results/data are tracked by this assay type */
    @Nullable
    public final FilteredTable createDataTable()
    {
        FilteredTable table = createDataTable(true);
        if (table != null && table.getColumn("Properties") != null)
            fixupPropertyURLs(table.getColumn("Properties"));
        return table;
    }

    public ExpQCFlagTable createQCFlagTable()
    {
        ExpQCFlagTable table = ExperimentService.get().createQCFlagsTable(AssaySchema.getQCFlagTableName(getProtocol(), false), this);
        table.populate();
        table.setAssayProtocol(getProtocol());
        return table;
    }

    public String getBatchesTableName(boolean protocolPrefixed)
    {
        return getBatchesTableName(getProtocol(), protocolPrefixed);
    }

    public String getRunsTableName(boolean protocolPrefixed)
    {
        return getRunsTableName(getProtocol(), protocolPrefixed);
    }

    public String getResultsTableName(boolean protocolPrefixed)
    {
        return getResultsTableName(getProtocol(), protocolPrefixed);
    }

    public String getQCFlagTableName(boolean protocolPrefixed)
    {
        return getQCFlagTableName(getProtocol(), protocolPrefixed);
    }

    // NOTE: This should be transitioned to partly happen in the TableInfo.overlayMetadata() for the various tables
    // associated with the assay design. They should call into here with the unprefixed name to be added
    // from the assay provider's metadata files.
    protected void overlayMetadata(TableInfo table, String name)
    {
        fixupRenderers(table);

        ArrayList<QueryException> errors = new ArrayList<QueryException>();
        Path dir = new Path(AssayService.ASSAY_DIR_NAME, getProvider().getResourceName(), QueryService.MODULE_QUERIES_DIRECTORY);
        TableType metadata = QueryService.get().findMetadataOverride(this, name, false, true, errors, dir);
        if (errors.isEmpty())
            table.overlayMetadata(metadata, this, errors);
    }


    private ExpExperimentTable createBatchesTable(ExpProtocol protocol, AssayProvider provider, final ContainerFilter containerFilter, boolean protocolPrefixed)
    {
        final ExpExperimentTable result = ExperimentService.get().createExperimentTable(getBatchesTableName(protocolPrefixed), this);
        result.populate();
        if (containerFilter != null)
        {
            result.setContainerFilter(containerFilter);
        }
        ActionURL runsURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), protocol, result.getContainerFilter());

        // Unfortunately this seems to be the best way to figure out the name of the URL parameter to filter by batch id
        ActionURL fakeURL = new ActionURL(ShowSelectedRunsAction.class, getContainer());
        fakeURL.addFilter(AssayService.get().getRunsTableName(protocol),
                AbstractAssayProvider.BATCH_ROWID_FROM_RUN, CompareType.EQUAL, "${RowId}");
        String paramName = fakeURL.getParameters()[0].getKey();

        Map<String, String> urlParams = new HashMap<String, String>();
        urlParams.put(paramName, "RowId");
        result.setDetailsURL(new DetailsURL(runsURL, urlParams));

        runsURL.addParameter(paramName, "${RowId}");
        result.getColumn(ExpExperimentTable.Column.Name).setURL(StringExpressionFactory.createURL(runsURL));
        result.setBatchProtocol(protocol);
        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts(ExpExperimentTable.Column.Name));
        defaultCols.add(FieldKey.fromParts(ExpExperimentTable.Column.CreatedBy));
        defaultCols.add(FieldKey.fromParts(ExpExperimentTable.Column.RunCount));
        result.setDefaultVisibleColumns(defaultCols);

        Domain batchDomain = provider.getBatchDomain(protocol);
        if (batchDomain != null)
        {
            ColumnInfo propsCol = result.addColumns(batchDomain, BATCH_PROPERTIES_COLUMN_NAME);
            if (propsCol != null)
            {
                // Will be null if the domain doesn't have any properties
                propsCol.setFk(new AssayPropertyForeignKey(batchDomain));
                propsCol.setUserEditable(false);
                propsCol.setShownInInsertView(false);
                propsCol.setShownInUpdateView(false);
            }
            result.setDomain(batchDomain);
        }

        for (ColumnInfo col : result.getColumns())
        {
            fixupRenderers(col, col);
        }

        result.setDescription("Contains a row per " + protocol.getName() + " batch, a group of runs that were loaded at the same time.");

        return result;
    }

    public abstract FilteredTable createDataTable(boolean includeCopiedToStudyColumns);

    public ExpRunTable createRunsTable()
    {
        final String tableName = getRunsTableName(false);
        final ExpRunTable runTable = ExperimentService.get().createRunTable(tableName, this);
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
                return getTable(tableName);
            }
        };
        runTable.getColumn(ExpRunTable.Column.ReplacedByRun).setFk(assayRunFK);
        runTable.getColumn(ExpRunTable.Column.ReplacesRun).setFk(assayRunFK);

        runTable.addColumn(new AssayQCFlagColumn(runTable, getProtocol().getName()));
        ColumnInfo qcEnabled = runTable.addColumn(new ExprColumn(runTable, "QCFlagsEnabled", AssayQCFlagColumn.createSQLFragment(runTable.getSqlDialect(), "Enabled"), JdbcType.VARCHAR));
        qcEnabled.setLabel("QC Flags Enabled State");
        qcEnabled.setHidden(true);

        ColumnInfo dataLinkColumn = runTable.getColumn(ExpRunTable.Column.Name);
        dataLinkColumn.setLabel("Assay Id");
        dataLinkColumn.setDescription("The assay/experiment ID that uniquely identifies this assay run.");
        dataLinkColumn.setURL(new DetailsURL(new ActionURL(AssayDetailRedirectAction.class, getContainer()), Collections.singletonMap("runId", "rowId")));

        runTable.setProtocolPatterns(getProtocol().getLSID());

        Domain runDomain = getProvider().getRunDomain(getProtocol());
        if (runDomain != null)
        {
            ColumnInfo propsCol = runTable.addColumns(runDomain, RUN_PROPERTIES_COLUMN_NAME);
            if (propsCol != null)
            {
                // Will be null if the domain doesn't have any properties
                propsCol.setFk(new AssayPropertyForeignKey(runDomain));
                propsCol.setUserEditable(false);
                propsCol.setShownInInsertView(false);
                propsCol.setShownInUpdateView(false);
            }
            runTable.setDomain(runDomain);
        }

        List<FieldKey> visibleColumns = new ArrayList<FieldKey>(runTable.getDefaultVisibleColumns());
        visibleColumns.remove(FieldKey.fromParts(ExpRunTable.Column.Protocol));
        visibleColumns.remove(FieldKey.fromParts(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME));

        SQLFragment batchSQL = new SQLFragment("(SELECT MIN(ExperimentId) FROM ");
        batchSQL.append(ExperimentService.get().getTinfoRunList(), "rl");
        batchSQL.append(", ");
        batchSQL.append(ExperimentService.get().getTinfoExperiment(), "e");
        batchSQL.append(" WHERE e.RowId = rl.ExperimentId AND rl.ExperimentRunId = ");
        batchSQL.append(ExprColumn.STR_TABLE_ALIAS);
        batchSQL.append(".RowId AND e.BatchProtocolId = ");
        batchSQL.append(getProtocol().getRowId());
        batchSQL.append(")");
        ExprColumn batchColumn = new ExprColumn(runTable, AssayService.BATCH_COLUMN_NAME, batchSQL, JdbcType.INTEGER, runTable.getColumn(ExpRunTable.Column.RowId));
        batchColumn.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                ExpExperimentTable batchesTable = createBatchesTable(getProtocol(), getProvider(), runTable.getContainerFilter(), false);
                fixupRenderers(batchesTable);
                return batchesTable;
            }
        });
        runTable.addColumn(batchColumn);

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

    @Override
    public QueryView createView(ViewContext context, QuerySettings settings, BindException errors)
    {
        String name = settings.getQueryName();
        if (name != null)
        {
            if (name.equals(getResultsTableName(true)) || name.equals(getResultsTableName(false)))
                return new ResultsQueryView(getProtocol(), this, settings);

            if (name.equals(getRunsTableName(true)) || name.equals(getRunsTableName(false)))
                return new RunListQueryView(getProtocol(), this, settings, new AssayRunType(getProtocol(), getContainer()));

            if (name.equals(getBatchesTableName(true)) || name.equals(getBatchesTableName(false)))
                return new BatchListQueryView(getProtocol(), this, settings);
        }

        return super.createView(context, settings, errors);
    }

    @Override
    public List<CustomView> getModuleCustomViews(Container container, QueryDefinition qd)
    {
        List<CustomView> result = new ArrayList<CustomView>();

        // Look for <MODULE>/assay/<ASSAY_TYPE>/queries/<TABLE_TYPE>/*.qview.xml files
        // where TABLE_TYPE is Runs, Batches, Data, etc
        Path providerPath = new Path(AssayService.ASSAY_DIR_NAME, getProvider().getResourceName(), QueryService.MODULE_QUERIES_DIRECTORY, FileUtil.makeLegalName(qd.getName()));
        result.addAll(QueryService.get().getFileBasedCustomViews(container, qd, providerPath));

        // Look in the legacy location in file-based modules (assay.<PROTOCOL_NAME> Batches, etc)
        String legacyQueryName = _protocol.getName() + " " + qd.getName();
        String legacySchemaName = AssaySchema.NAME;
        Path legacyPath = new Path(QueryService.MODULE_QUERIES_DIRECTORY, legacySchemaName, FileUtil.makeLegalName(legacyQueryName));
        result.addAll(QueryService.get().getFileBasedCustomViews(container, qd, legacyPath));

        // Look in the legacy location in file-based modules (assay.<PROTOCOL_NAME> Batches, etc)
        result.addAll(QueryService.get().getCustomViews(getUser(), container, legacySchemaName, legacyQueryName));

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
        Map<FieldKey,FieldKey> map = new HashMap<FieldKey, FieldKey>();
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
        Set<String> visibleColumnNames = new HashSet<String>();
        int datasetIndex = 0;
        Set<String> usedColumnNames = new HashSet<String>();
        for (final DataSet assayDataSet : StudyService.get().getDatasetsForAssayProtocol(getProtocol().getRowId()))
        {
            if (!assayDataSet.getContainer().hasPermission(getUser(), ReadPermission.class) || !assayDataSet.canRead(getUser()))
            {
                continue;
            }

            String datasetIdColumnName = "dataset" + datasetIndex++;
            final StudyDataSetColumn datasetColumn = new StudyDataSetColumn(table, datasetIdColumnName, getProvider(), assayDataSet, getUser());
            datasetColumn.setHidden(true);
            datasetColumn.setUserEditable(false);
            datasetColumn.setShownInInsertView(false);
            datasetColumn.setShownInUpdateView(false);
            datasetColumn.setReadOnly(true);
            table.addColumn(datasetColumn);

            String studyCopiedSql = "(SELECT CASE WHEN " + datasetColumn.getDatasetIdAlias() +
                "._key IS NOT NULL THEN 'copied' ELSE NULL END)";

            String studyName = assayDataSet.getStudy().getLabel();
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
            studyCopiedColumn.setURL(StringExpressionFactory.createURL(StudyService.get().getDatasetURL(assayDataSet.getContainer(), assayDataSet.getDataSetId())));

            table.addColumn(studyCopiedColumn);

            visibleColumnNames.add(studyCopiedColumn.getName());
        }
        if (setVisibleColumns)
        {
            List<FieldKey> visibleColumns = new ArrayList<FieldKey>();
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
        if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(col.getName()))
        {
            columnInfo.setFk(new LookupForeignKey("Folder", "Label")
            {
                public TableInfo getLookupTableInfo()
                {

                    FilteredTable table = new FilteredTable(DbSchema.get("study").getTable("study"));
//                    FilteredTable table = new FilteredTable(AssayProtocolSchema.this.getSchema("Study").getTable("Study"));
                    table.setContainerFilter(new StudyContainerFilter(AssayProtocolSchema.this));
                    ExprColumn col = new ExprColumn(table, "Folder", new SQLFragment("CAST (" + ExprColumn.STR_TABLE_ALIAS + ".Container AS VARCHAR(200))"), JdbcType.VARCHAR);
                    col.setFk(new ContainerForeignKey(AssayProtocolSchema.this));
                    table.addColumn(col);
                    table.addWrapColumn(table.getRealTable().getColumn("Label"));
                    table.setPublic(false);
                    return table;
                }
            });
        }
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
}
