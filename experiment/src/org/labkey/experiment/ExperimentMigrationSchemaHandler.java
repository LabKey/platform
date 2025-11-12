package org.labkey.experiment;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.CompareType.CompareClause;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter.AndClause;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.InClause;
import org.labkey.api.data.SimpleFilter.OrClause;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpProtocolAttachmentType;
import org.labkey.api.exp.api.ExpRunAttachmentType;
import org.labkey.api.migration.DatabaseMigrationConfiguration;
import org.labkey.api.migration.DefaultMigrationSchemaHandler;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.GUID;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.Collection;
import java.util.List;
import java.util.Set;

class ExperimentMigrationSchemaHandler extends DefaultMigrationSchemaHandler
{
    private static final Logger LOG = LogHelper.getLogger(ExperimentMigrationSchemaHandler.class, "Progress of exp deletes during database migration");

    public ExperimentMigrationSchemaHandler()
    {
        super(OntologyManager.getExpSchema());
    }

    @Override
    public void beforeSchema()
    {
        // Work around foreign key cycle between ExperimentRun <-> ProtocolApplication by temporarily dropping FK_Run_WorfklowTask.
        // Yes, the FK name is misspelled
        new SqlExecutor(getSchema()).execute("ALTER TABLE exp.ExperimentRun DROP CONSTRAINT FK_Run_WorfklowTask");
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
        // No need to populate the MaterialIndexed or DataIndexed tables -- new server should be completely re-indexed after migration
        List<TableInfo> tables = super.getTablesToCopy();
        tables.remove(ExperimentServiceImpl.get().getTinfoMaterialIndexed());
        tables.remove(ExperimentServiceImpl.get().getTinfoDataIndexed());
        return tables;
    }

    @Override
    public FilterClause getContainerClause(TableInfo sourceTable, Set<GUID> containers)
    {
//        Set<GUID> assayFilteredContainers = assayFilteredContainers(containers);
        return switch (sourceTable.getName())
        {
//            case "ExperimentRun", "ProtocolApplication" -> super.getContainerClause(sourceTable, assayFilteredContainers);
//            case "Data" -> new AndClause(
//                new InClause(FieldKey.fromParts("Container"), containers),
//                new OrClause(
//                    new CompareClause(FieldKey.fromParts("RunId"), CompareType.ISBLANK, null),
//                    new InClause(FieldKey.fromParts("RunId", "Container"), assayFilteredContainers)
//                )
//            );
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
        new SqlExecutor(getSchema()).execute("ALTER TABLE exp.ExperimentRun ADD CONSTRAINT FK_Run_WorfklowTask FOREIGN KEY (WorkflowTask) REFERENCES exp.ProtocolApplication (RowId) MATCH SIMPLE ON DELETE SET NULL");
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
    public @NotNull Collection<AttachmentType> getAttachmentTypes()
    {
        return List.of(
            ExpProtocolAttachmentType.get(),
            ExpRunAttachmentType.get()
        );
    }
}
