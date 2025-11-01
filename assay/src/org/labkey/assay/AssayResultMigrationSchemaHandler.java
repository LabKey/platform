package org.labkey.assay;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AbstractTsvAssayProvider;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.DatabaseMigrationService;
import org.labkey.api.data.DatabaseMigrationService.DataFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.InClause;
import org.labkey.api.data.SimpleFilter.OrClause;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.Formats;
import org.labkey.api.util.GUID;
import org.labkey.api.util.logging.LogHelper;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

class AssayResultMigrationSchemaHandler extends DatabaseMigrationService.DefaultMigrationSchemaHandler
{
    private static final Logger LOG = LogHelper.getLogger(AssayResultMigrationSchemaHandler.class, "Assay result migration status");

    public AssayResultMigrationSchemaHandler()
    {
        super(DbSchema.get(AbstractTsvAssayProvider.ASSAY_SCHEMA_NAME, DbSchemaType.Provisioned));
    }

    @Override
    public @Nullable FieldKey getContainerFieldKey(TableInfo table)
    {
        return DUMMY_FIELD_KEY;
    }

    // Provisioned assay result tables occasionally have no DataId column; hopefully they have an LSID column.
    private boolean hasDataIdColumn(TableInfo sourceTable)
    {
        return sourceTable.getColumn("DataId") != null;
    }

    @Override
    public FilterClause getContainerClause(TableInfo sourceTable, FieldKey containerFieldKey, Set<GUID> containers)
    {
        SQLFragment sqlFragment = new SQLFragment();

        if (hasDataIdColumn(sourceTable))
            sqlFragment.append("DataId IN (SELECT RowId");
        else
            sqlFragment.append("LSID IN (SELECT LSID");

        return new SQLClause(
            sqlFragment.append(" FROM exp.Data WHERE Container")
                .appendInClause(containers, sourceTable.getSqlDialect())
                .append(")")
        );
    }

    @Override
    public void addDomainDataFilterClause(OrClause orClause, DataFilter filter, TableInfo sourceTable, FieldKey fKey, Set<String> selectColumnNames)
    {
        // We want no rows from containers with a domain data filter, so don't add any clauses
    }

    @Override
    public void afterTable(TableInfo sourceTable, TableInfo targetTable, SimpleFilter notCopiedFilter)
    {
        final Selector selector;

        if (hasDataIdColumn(sourceTable))
        {
            selector = new TableSelector(sourceTable, Collections.singleton("DataId"), notCopiedFilter, null);
        }
        else
        {
            // Forced to use two separate queries here since notCopiedFilter must be applied to sourceTable. Attempting
            // a combined query that joins to exp.Data results in ambiguous LSID references.
            Collection<String> lsids = new TableSelector(sourceTable, Collections.singleton("LSID"), notCopiedFilter, null).getCollection(String.class);
            SimpleFilter filter = new SimpleFilter(new InClause(FieldKey.fromParts("LSID"), lsids));
            selector = new TableSelector(ExperimentService.get().getTinfoData(), new CsvSet("RowId, LSID"), filter, null);
        }

        Collection<Integer> notCopiedDataIds = selector.getCollection(Integer.class);

        LOG.info("   {} rows not copied -- deleting associated rows from exp.Data, exp.Object, etc.", Formats.commaf0.format(notCopiedDataIds.size()));

        // TODO: Delete exp.Data, etc. rows associated with the rows that weren't copied
    }
}
