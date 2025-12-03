package org.labkey.experiment;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.CompareType.CompareClause;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.AndClause;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.InClause;
import org.labkey.api.data.SimpleFilter.NotClause;
import org.labkey.api.data.SimpleFilter.OrClause;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpProtocolAttachmentType;
import org.labkey.api.exp.api.ExpRunAttachmentType;
import org.labkey.api.migration.AssaySkipContainers;
import org.labkey.api.migration.DatabaseMigrationConfiguration;
import org.labkey.api.migration.DefaultMigrationSchemaHandler;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.GUID;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ExperimentMigrationSchemaHandler extends DefaultMigrationSchemaHandler
{
    private static final Logger LOG = LogHelper.getLogger(ExperimentMigrationSchemaHandler.class, "Progress of database migration");

    public ExperimentMigrationSchemaHandler()
    {
        super(OntologyManager.getExpSchema());
    }

    @Override
    public void beforeSchema()
    {
        new SqlExecutor(getSchema()).execute("ALTER TABLE exp.Object DROP CONSTRAINT FK_Object_Object");

        // Need to drop self FK until all rows are populated because replaced runs will be inserted before their replacements
        new SqlExecutor(getSchema()).execute("ALTER TABLE exp.ExperimentRun DROP CONSTRAINT FK_ExperimentRun_ReplacedByRunId");
    }

    @Override
    public @Nullable FieldKey getContainerFieldKey(TableInfo table)
    {
        return switch (table.getName())
        {
            case "DataTypeExclusion" -> FieldKey.fromParts("ExcludedContainer");
            case "PropertyDomain" -> FieldKey.fromParts("DomainId", "Container");
            case "ProtocolApplication" -> FieldKey.fromParts("RunId", "Container");
            default -> super.getContainerFieldKey(table);
        };
    }

    @Override
    public List<TableInfo> getTablesToCopy()
    {
        // No need to populate the MaterialIndexed or DataIndexed tables -- new server will be completely re-indexed after migration
        List<TableInfo> tables = super.getTablesToCopy();
        tables.remove(ExperimentServiceImpl.get().getTinfoMaterialIndexed());
        tables.remove(ExperimentServiceImpl.get().getTinfoDataIndexed());
        return tables;
    }

    @Override
    public FilterClause getTableFilterClause(TableInfo sourceTable, Set<GUID> containers)
    {
        FilterClause containerClause = getContainerClause(sourceTable, containers);
        return switch (sourceTable.getName())
        {
            case "ExperimentRun" -> {
                createIncludedExperimentRunRowIdCollection(sourceTable, containerClause);
                yield getIncludedExperimentRunFilter(sourceTable, containerClause, FieldKey.fromParts("RowId"), false);
            }
            case "ProtocolApplication" -> getIncludedExperimentRunFilter(sourceTable, containerClause, FieldKey.fromParts("RunId"), false);
            case "Data", "Edge" -> getIncludedExperimentRunFilter(sourceTable, containerClause, FieldKey.fromParts("RunId"), true);
            case "DataInput", "MaterialInput" -> getIncludedExperimentRunFilter(sourceTable, containerClause, FieldKey.fromParts("TargetApplicationId", "RunId"), false);
            case "RunList" -> getIncludedExperimentRunFilter(sourceTable, containerClause, FieldKey.fromParts("ExperimentRunId"), false);
            case "DataAncestors" -> getIncludedExperimentRunFilter(sourceTable, containerClause, FieldKey.fromParts("RowId", "RunId"), true);
            default -> containerClause;
        };
    }

    // Combine the full container clause with the assay experiment run inclusion filter, if present
    private FilterClause getIncludedExperimentRunFilter(TableInfo sourceTable, FilterClause containerClause, FieldKey runIdFieldKey, boolean nullable)
    {
        FilterClause includedRowIdClause = getIncludedRowIdClause(sourceTable, runIdFieldKey);

        return includedRowIdClause == null ?
            containerClause :
            new AndClause(
                containerClause,
                nullable ?
                    new OrClause(
                        new CompareClause(runIdFieldKey, CompareType.ISBLANK, null),
                        includedRowIdClause
                    ) :
                    includedRowIdClause
            );
    }

    @Nullable FilterClause getIncludedRowIdClause(TableInfo sourceTable, FieldKey runIdFieldKey)
    {
        Collection<Integer> experimentRunsToInclude = getIncludedExperimentRunRowIds();

        if (null == experimentRunsToInclude)
            return null;

        return new InClause(runIdFieldKey, experimentRunsToInclude, getTempTableInClauseGenerator(sourceTable.getSchema().getScope()))
        {
            @Override
            public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
            {
                // Hackfest: turn temp-table IN clause into the equivalent EXISTS() clause because performance on PostgreSQL is so much better
                SQLFragment fragment = super.toSQLFragment(columnMap, dialect);
                String sql = fragment.getSQL();
                int idx = sql.indexOf("temp.InClause$");
                if (idx > -1)
                {
                    SQLFragment newFragment = new SQLFragment("EXISTS (SELECT 1 FROM ")
                        .append(sql.substring(idx, idx + 46))
                        .append(" WHERE ")
                        .append(sql.substring(1, sql.indexOf(" IN ")))
                        .append(" = Id)");
                    newFragment.addAll(fragment.getParams());
                    newFragment.addTempTokens(fragment);
                    fragment = newFragment;
                }
                return fragment;
            }
        };
    }

    // Collection of all exp.ExperimentRun RowIds in all copy containers that should be copied. If an AssaySkipFilter is
    // provided, this collection excludes assay runs in those containers plus runs that list one of the excluded runs
    // as a replacement, etc. A null value means include all experiment runs in all copy containers.
    private Collection<Integer> _includedExperimentRunRowIds = null;

    private @Nullable Collection<Integer> getIncludedExperimentRunRowIds()
    {
        return _includedExperimentRunRowIds;
    }

    private void createIncludedExperimentRunRowIdCollection(TableInfo sourceExperimentRunsTable, FilterClause containerClause)
    {
        if (AssaySkipContainers.getContainers().isEmpty())
        {
            _includedExperimentRunRowIds = null;
        }
        else
        {
            DbSchema sourceSchema = sourceExperimentRunsTable.getSchema();

            // Selects all assay runs (same filter used by assay.AssayRuns)
            SQLFragment assayRunSql = new SQLFragment(
                "ProtocolLSID IN (SELECT LSID FROM exp.Protocol x WHERE (ApplicationType = 'ExperimentRun') AND " +
                "((SELECT MAX(pd.PropertyId) from exp.Object o, exp.ObjectProperty op, exp.PropertyDescriptor pd WHERE " +
                "pd.PropertyId = op.PropertyId and op.ObjectId = o.ObjectId and o.ObjectURI = LSID AND pd.PropertyURI LIKE '%AssayDomain-Run%') IS NOT NULL))"
            );

            // Selects all assay runs in the configured assay-skip containers
            FilterClause assayRunClause = new AndClause(
                new InClause(FieldKey.fromParts("Container"), AssaySkipContainers.getContainers()),
                new SQLClause(assayRunSql)
            );

            // Selects assay runs (regardless of their container) that were replaced by assay runs that are being
            // excluded
            FilterClause replaceByRunIdClause = new SQLClause(
                new SQLFragment("ReplacedByRunId IS NOT NULL AND ReplacedByRunId IN (SELECT RowId FROM exp.ExperimentRun WHERE ")
                    .append(assayRunClause.toSQLFragment(null, sourceSchema.getSqlDialect()))
                    .append(")")
            );

            // Selects assay runs that were replaced by assay runs that are being excluded because they were replaced
            // by an excluded assay run. Yes, we actually have to do this...
            FilterClause replaceByReplacedRunIdClause = new SQLClause(
                new SQLFragment("ReplacedByRunId IS NOT NULL AND ReplacedByRunId IN (SELECT RowId FROM exp.ExperimentRun WHERE ")
                    .append(replaceByRunIdClause.toSQLFragment(null, sourceSchema.getSqlDialect()))
                    .append(")")
            );

            // Selects all assay runs that need to be included -- all rows in exp.ExperimentRuns in all copy containers,
            // except assay runs in the assay-skip containers or runs replaced by one of those excluded runs.
            SimpleFilter filter = new SimpleFilter(
                containerClause,
                new NotClause(new OrClause(
                    assayRunClause,
                    replaceByRunIdClause,
                    replaceByReplacedRunIdClause
                ))
            );

            // Select the experiment run RowIds to transfer. All tables with FKs to ExperimentRun (or FKs to other
            // tables with FKs to ExperimentRun) must add these run IDs as an include filter to avoid FK violations.
            _includedExperimentRunRowIds = new TableSelector(sourceExperimentRunsTable, new CsvSet("RowId, ProtocolLSID, ReplacedByRunId"), filter, null).getCollection(Integer.class);
            LOG.info("   {} being included due to the configured AssaySkipContainers parameter", StringUtilsLabKey.pluralize(_includedExperimentRunRowIds.size(), "assay experiment run is", "assay experiment runs are"));
        }
    }

    @Override
    public FilterClause getContainerClause(TableInfo sourceTable, Set<GUID> containers)
    {
        return switch (sourceTable.getName())
        {
            case "DataInput" -> new AndClause(
                new InClause(FieldKey.fromParts("DataId", "Container"), containers),
                new InClause(FieldKey.fromParts("TargetApplicationId", "RunId", "Container"), containers)
            );
            case "Material" -> new AndClause(
                new InClause(FieldKey.fromParts("Container"), containers),
                new OrClause(
                    new CompareClause(FieldKey.fromParts("RunId"), CompareType.ISBLANK, null),
                    new InClause(FieldKey.fromParts("RunId", "Container"), containers)
                )
            );
            case "MaterialInput" -> new AndClause(
                new InClause(FieldKey.fromParts("TargetApplicationId", "RunId", "Container"), containers),
                // The below effectively matches the "Material" conditions above, since MaterialInput has an FK to Material
                new InClause(FieldKey.fromParts("MaterialId", "Container"), containers),
                new OrClause(
                    new CompareClause(FieldKey.fromParts("MaterialId", "RunId"), CompareType.ISBLANK, null),
                    new InClause(FieldKey.fromParts("MaterialId", "RunId", "Container"), containers)
                )
            );
            case "MaterialAncestors" -> new AndClause(
                // Effectively matches the "Material" conditions above since MaterialAncestors has an FK to Material
                new InClause(FieldKey.fromParts("RowId", "Container"), containers),
                new OrClause(
                    new CompareClause(FieldKey.fromParts("RowId", "RunId"), CompareType.ISBLANK, null),
                    new InClause(FieldKey.fromParts("RowId", "RunId", "Container"), containers)
                )
            );
            case "Edge" -> new AndClause(
                new InClause(FieldKey.fromParts("FromObjectId", "Container"), containers),
                new InClause(FieldKey.fromParts("ToObjectId", "Container"), containers)
            );
            case "Alias" -> new SQLClause(
                new SQLFragment("RowId IN (SELECT Alias FROM exp.MaterialAliasMap WHERE Container")
                    .appendInClause(containers, sourceTable.getSqlDialect())
                    .append(" UNION SELECT Alias FROM exp.DataAliasMap WHERE Container")
                    .appendInClause(containers, sourceTable.getSqlDialect())
                    .append(")")
            );
            case "ObjectLegacyNames" -> new SQLClause(
                new SQLFragment("ObjectId IN (SELECT ObjectId FROM exp.Object WHERE Container")
                    .appendInClause(containers, sourceTable.getSqlDialect())
                    .append(")")
            );
            default -> super.getContainerClause(sourceTable, containers);
        };
    }

    @Override
    public void afterSchema(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema)
    {
        new SqlExecutor(getSchema()).execute("ALTER TABLE exp.Object ADD CONSTRAINT FK_Object_Object FOREIGN KEY (OwnerObjectId) REFERENCES exp.Object (ObjectId)");
        new SqlExecutor(getSchema()).execute("ALTER TABLE exp.ExperimentRun ADD CONSTRAINT FK_ExperimentRun_ReplacedByRunId FOREIGN KEY (ReplacedByRunId) REFERENCES exp.ExperimentRun (RowId)");
    }

    // Delete from exp.Object and associated tables
    public static void deleteObjectIds(SQLFragment objectIdClause)
    {
        SqlExecutor executor = new SqlExecutor(OntologyManager.getExpSchema());

        LOG.info("   exp.Edge (FromObjectId)");
        executor.execute(
            new SQLFragment("DELETE FROM exp.Edge WHERE FromObjectId")
                .append(objectIdClause)
        );
        LOG.info("   exp.Edge (ToObjectId)");
        executor.execute(
            new SQLFragment("DELETE FROM exp.Edge WHERE ToObjectId")
                .append(objectIdClause)
        );
        LOG.info("   exp.ObjectProperty");
        executor.execute(
            new SQLFragment("DELETE FROM exp.ObjectProperty WHERE ObjectId")
                .append(objectIdClause)
        );
        LOG.info("   exp.Object");
        executor.execute(
            new SQLFragment("DELETE FROM exp.Object WHERE ObjectId")
                .append(objectIdClause)
        );
    }

    @Override
    public @NotNull Collection<AttachmentParentType> getAttachmentTypes()
    {
        return List.of(
            ExpProtocolAttachmentType.get(),
            ExpRunAttachmentType.get()
        );
    }
}
