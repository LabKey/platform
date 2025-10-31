package org.labkey.experiment;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.DatabaseMigrationConfiguration;
import org.labkey.api.data.DatabaseMigrationService;
import org.labkey.api.data.DatabaseMigrationService.DefaultMigrationSchemaHandler;
import org.labkey.api.data.DatabaseMigrationService.DataFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.InClause;
import org.labkey.api.data.SimpleFilter.OrClause;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.Formats;
import org.labkey.api.util.GUID;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.experiment.api.DataClassDomainKind;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class DataClassMigrationSchemaHandler extends DefaultMigrationSchemaHandler
{
    private static final Logger LOG = LogHelper.getLogger(DataClassMigrationSchemaHandler.class, "Data class migration status");

    public DataClassMigrationSchemaHandler()
    {
        super(DataClassDomainKind.getSchema());
    }

    @Override
    public @Nullable FieldKey getContainerFieldKey(TableInfo table)
    {
        FieldKey fieldKey = super.getContainerFieldKey(table);
        return fieldKey != null ? fieldKey : DUMMY_FIELD_KEY; // "DUMMY" case is a data class that lacks an FK
    }

    @Override
    public FilterClause getContainerClause(TableInfo sourceTable, FieldKey containerFieldKey, Set<GUID> containers)
    {
        final FilterClause clause;

        if (containerFieldKey == DUMMY_FIELD_KEY)
        {
            // There are a couple bad data class provisioned tables that lack an FK to exp.Data. In that case, craft the
            // container FilterClause explicitly.
            clause = new SQLClause(
                new SQLFragment("LSID IN (SELECT LSID FROM exp.Data WHERE Container")
                .appendInClause(containers, sourceTable.getSqlDialect())
                .append(")")
            );
        }
        else
        {
            clause = super.getContainerClause(sourceTable, containerFieldKey, containers);
        }

        return clause;
    }

    @Override
    public void addDomainDataFilter(OrClause orClause, DataFilter filter, TableInfo sourceTable, FieldKey fKey, Set<String> selectColumnNames)
    {
        // Data classes have a built-in Flag field
        addDomainDataFlagFilter(orClause, filter, sourceTable, fKey, selectColumnNames);
    }

    private static final Set<String> SEQUENCE_TABLES = Sets.newCaseInsensitiveHashSet("protsequence", "nucsequence", "molecule");

    private final Set<Long> SEQUENCE_IDS = new HashSet<>();

    @Override
    public void afterTable(TableInfo sourceTable, TableInfo targetTable, SimpleFilter notCopiedFilter)
    {
        // exp.Data has an index on ObjectId, so use ObjectIds to delete from exp.Data tables as well as exp.Object.
        // Our notCopiedFilter works on the data class provisioned table, so we need to select LSIDs from that table
        // and then select from exp.Data to map those LSIDs to ObjectIds.
        Collection<String> notCopiedLsids = new TableSelector(sourceTable, Collections.singleton("LSID"), notCopiedFilter, null).getCollection(String.class);

        if (!notCopiedLsids.isEmpty())
        {
            SimpleFilter dataFilter = new SimpleFilter(new InClause(FieldKey.fromParts("LSID"), notCopiedLsids));
            Collection<Integer> notCopiedObjectIds = new TableSelector(ExperimentService.get().getTinfoData(), Collections.singleton("ObjectId"), dataFilter, null).getCollection(Integer.class);

            LOG.info("   {} rows not copied -- deleting associated rows from exp.Data, exp.Object, etc.", Formats.commaf0.format(notCopiedObjectIds.size()));
            SqlExecutor executor = new SqlExecutor(ExperimentService.get().getSchema());
            SqlDialect dialect = ExperimentService.get().getSchema().getSqlDialect();
            SQLFragment objectIdClause = new SQLFragment()
                .appendInClause(notCopiedObjectIds, dialect);

            // Delete from exp.Data (and associated tables)
            LOG.info("   exp.DataInput");
            executor.execute(
                new SQLFragment("DELETE FROM exp.DataInput WHERE DataId IN (SELECT RowId FROM exp.Data WHERE ObjectId")
                    .append(objectIdClause)
                    .append(")")
            );
            LOG.info("   exp.DataAliasMap");
            executor.execute(
                new SQLFragment("DELETE FROM exp.DataAliasMap WHERE LSID")
                    .appendInClause(notCopiedLsids, dialect)
            );
            LOG.info("   exp.Data");
            executor.execute(
                new SQLFragment("DELETE FROM exp.Data WHERE ObjectId")
                    .append(objectIdClause)
            );

            ExperimentMigrationSchemaHandler.deleteObjectIds(objectIdClause);
        }

        String name = sourceTable.getName();
        int idx = name.indexOf('_');
        name = name.substring(idx + 1);

        if (SEQUENCE_TABLES.contains(name))
        {
            int startSize = SEQUENCE_IDS.size();
            new TableSelector(targetTable, Collections.singleton("Ident")).stream(String.class)
                .map(ident -> {
                    int i = ident.indexOf(':');
                    try
                    {
                        return Long.parseLong(ident.substring(i + 1));
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException("Exception trying to split ident on ':' (" + ident + ")", e);
                    }
                })
                .forEach(SEQUENCE_IDS::add);
            LOG.info("   {} unique SequenceIds were added to the SequenceIdentity set", Formats.commaf0.format(SEQUENCE_IDS.size() - startSize));
        }
    }

    @Override
    public void afterSchema(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema)
    {
        // Experiment shouldn't mess with Biologics tables, but it gets the job done

        DbScope sourceScope = configuration.getSourceScope();
        DbScope targetScope = configuration.getTargetScope();
        DbSchema biologicsSourceSchema = sourceScope.getSchema("biologics", DbSchemaType.Migration);
        DbSchema biologicsTargetSchema = targetScope.getSchema("biologics", DbSchemaType.Module);

        if (biologicsSourceSchema.existsInDatabase() && biologicsTargetSchema.existsInDatabase())
        {
            TableInfo sourceTable = biologicsSourceSchema.getTable("SequenceIdentity");
            TableInfo targetTable = biologicsTargetSchema.getTable("SequenceIdentity");

            DatabaseMigrationService.get().copySourceTableToTargetTable(configuration, sourceTable, targetTable, DbSchemaType.Module, new DefaultMigrationSchemaHandler(biologicsTargetSchema)
            {
                @Override
                public FilterClause getTableFilter(TableInfo sourceTable, FieldKey containerFieldKey, Set<GUID> containers)
                {
                    // This is a global table, so no container clause. Just query and copy the sequence IDs referenced by data class rows we copied.
                    return new InClause(FieldKey.fromParts("SequenceId"), SEQUENCE_IDS);
                }
            });
        }
    }
}
