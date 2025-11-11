package org.labkey.experiment;

import org.apache.logging.log4j.Logger;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.DatabaseMigrationConfiguration;
import org.labkey.api.data.DatabaseMigrationService;
import org.labkey.api.data.DatabaseMigrationService.DataFilter;
import org.labkey.api.data.DatabaseMigrationService.DefaultMigrationSchemaHandler;
import org.labkey.api.data.DatabaseMigrationService.ExperimentDeleteService;
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
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.GUID;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.experiment.api.DataClassDomainKind;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class DataClassMigrationSchemaHandler extends DefaultMigrationSchemaHandler implements ExperimentDeleteService
{
    private static final Logger LOG = LogHelper.getLogger(DataClassMigrationSchemaHandler.class, "Data class migration status");

    public DataClassMigrationSchemaHandler()
    {
        super(DataClassDomainKind.getSchema());
    }

    @Override
    public FilterClause getContainerClause(TableInfo sourceTable, Set<GUID> containers)
    {
        final FilterClause clause;

        if (getContainerFieldKey(sourceTable) == null)
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
            clause = super.getContainerClause(sourceTable, containers);
        }

        return clause;
    }

    @Override
    public void addDomainDataFilterClause(OrClause orClause, DataFilter filter, TableInfo sourceTable, Set<String> selectColumnNames)
    {
        // Data classes have a built-in Flag field
        if (filter.column().equalsIgnoreCase("Flag"))
        {
            addObjectPropertyClause(orClause, filter, sourceTable, getCommentPropertyId(sourceTable.getSchema().getScope()));
        }
        else
        {
            addDataFilterClause(orClause, filter, sourceTable, selectColumnNames);
        }
    }

    private static final Set<String> SEQUENCE_TABLES = Sets.newCaseInsensitiveHashSet("protsequence", "nucsequence", "molecule");

    private final Set<Long> SEQUENCE_IDS = new HashSet<>();

    @Override
    public void afterTable(TableInfo sourceTable, TableInfo targetTable, SimpleFilter notCopiedFilter)
    {
        // Select all ObjectIds associated with the not-copied rows from the source database. Our notCopiedFilter
        // works on the data class provisioned table, so we need to use a sub-select (as opposed to a join) to avoid
        // ambiguous column references.
        SQLFragment objectIdSql = new SQLFragment("SELECT ObjectId FROM exp.Data WHERE LSID IN (SELECT LSID FROM ")
            .appendIdentifier(sourceTable.getSelectName())
            .append(" ")
            .append(notCopiedFilter.getSQLFragment(sourceTable.getSqlDialect()))
            .append(")");
        Collection<Long> notCopiedObjectIds = new SqlSelector(sourceTable.getSchema(), objectIdSql).getCollection(Long.class);

        if (notCopiedObjectIds.isEmpty())
        {
            LOG.info(rowsNotCopied(0));
        }
        else
        {
            LOG.info("{} -- deleting associated rows from exp.Data, exp.Object, etc.", rowsNotCopied(notCopiedObjectIds.size()));
            deleteDataRows(notCopiedObjectIds);
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
            LOG.info("{} added to the SequenceIdentity set",
                StringUtilsLabKey.pluralize(SEQUENCE_IDS.size() - startSize, "unique SequenceId was", "unique SequenceIds were"));
        }
    }

    // exp.Data has an index on ObjectId plus we need ObjectIds to delete from exp.Object, etc. so pass in ObjectIds here
    @Override
    public void deleteDataRows(Collection<Long> objectIds)
    {
        SqlExecutor executor = new SqlExecutor(ExperimentService.get().getSchema());
        SqlDialect dialect = ExperimentService.get().getSchema().getSqlDialect();
        SQLFragment objectIdClause = new SQLFragment()
            .appendInClause(objectIds, dialect);

        // Delete from exp.Data (and associated tables)
        LOG.info("   exp.DataInput");
        executor.execute(
            new SQLFragment("DELETE FROM exp.DataInput WHERE DataId IN (SELECT RowId FROM exp.Data WHERE ObjectId")
                .append(objectIdClause)
                .append(")")
        );
        LOG.info("   exp.DataAliasMap");
        executor.execute(
            new SQLFragment("DELETE FROM exp.DataAliasMap WHERE LSID IN (SELECT LSID FROM exp.Data WHERE ObjectId")
                .appendInClause(objectIds, dialect)
                .append(")")
        );
        LOG.info("   exp.Data");
        executor.execute(
            new SQLFragment("DELETE FROM exp.Data WHERE ObjectId")
                .append(objectIdClause)
        );

        ExperimentMigrationSchemaHandler.deleteObjectIds(objectIdClause);
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
                public FilterClause getTableFilterClause(TableInfo sourceTable, Set<GUID> containers)
                {
                    // This is a global table, so no container clause. Just query and copy the sequence IDs referenced by data class rows we copied.
                    return new InClause(FieldKey.fromParts("SequenceId"), SEQUENCE_IDS);
                }
            });
        }
    }
}
