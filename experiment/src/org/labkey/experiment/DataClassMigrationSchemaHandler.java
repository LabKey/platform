package org.labkey.experiment;

import org.apache.logging.log4j.Logger;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.DatabaseMigrationConfiguration;
import org.labkey.api.data.DatabaseMigrationService;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.InClause;
import org.labkey.api.data.SimpleFilter.OrClause;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.Formats;
import org.labkey.api.util.GUID;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.experiment.api.DataClassDomainKind;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class DataClassMigrationSchemaHandler extends DatabaseMigrationService.DefaultMigrationSchemaHandler
{
    private static final Logger LOG = LogHelper.getLogger(DataClassMigrationSchemaHandler.class, "Data class migration status");

    public DataClassMigrationSchemaHandler()
    {
        super(DataClassDomainKind.getSchema());
    }

    @Override
    public void addDomainDataFilter(OrClause orClause, DatabaseMigrationService.DomainFilter filter, TableInfo sourceTable, FieldKey fKey, Set<String> selectColumnNames)
    {
        // Data classes have a built-in Flag field
        addDomainDataFlagFilter(orClause, filter, sourceTable, fKey, selectColumnNames);
    }

    private static final Set<String> SEQUENCE_TABLES = Sets.newCaseInsensitiveHashSet("protsequence", "nucsequence", "molecule");
    private static final Set<Long> SEQUENCE_IDS = new HashSet<>();

    @Override
    public void afterTable(TableInfo sourceTable, TableInfo targetTable, SimpleFilter notCopiedFilter)
    {
        Collection<String> notCopiedLsids = new TableSelector(sourceTable, Collections.singleton("LSID"), notCopiedFilter, null).getCollection(String.class);
        if (!notCopiedLsids.isEmpty())
            LOG.info("   {} rows not copied", Formats.commaf0.format(notCopiedLsids.size()));

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
            LOG.info("   {} unique SequenceIds were added to the SequenceIdentity set", SEQUENCE_IDS.size() - startSize);
        }
    }

    @Override
    public void afterSchema(DatabaseMigrationConfiguration configuration, DbSchema sourceSchema, DbSchema targetSchema, Map<String, Map<String, Sequence>> sequenceMap)
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

            DatabaseMigrationService.get().copySourceTableToTargetTable(configuration, sourceTable, targetTable, DbSchemaType.Module, sequenceMap.get("biologics"), new DatabaseMigrationService.DefaultMigrationSchemaHandler(biologicsTargetSchema)
            {
                @Override
                public FilterClause getContainerClause(TableInfo sourceTable, FieldKey containerFieldKey, Set<GUID> containers)
                {
                    return new InClause(FieldKey.fromParts("SequenceId"), SEQUENCE_IDS);
                }
            });
        }
    }
}
