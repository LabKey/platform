package org.labkey.assay;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AbstractTsvAssayProvider;
import org.labkey.api.data.DatabaseMigrationService.DataFilter;
import org.labkey.api.data.DatabaseMigrationService.DefaultMigrationSchemaHandler;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.OrClause;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.GUID;
import org.labkey.api.util.logging.LogHelper;

import java.util.Collection;
import java.util.Set;

class AssayResultMigrationSchemaHandler extends DefaultMigrationSchemaHandler
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
        return new SQLClause(
            new SQLFragment(hasDataIdColumn(sourceTable) ? "DataId IN (SELECT RowId" : "LSID IN (SELECT LSID")
                .append(" FROM exp.Data WHERE Container")
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
        SQLFragment objectIdSql = new SQLFragment("SELECT ObjectId FROM exp.Data WHERE ")
            .append(hasDataIdColumn(sourceTable) ? "RowId IN (SELECT DataId" : "LSID IN (SELECT LSID")
            .append(" FROM ")
            .appendIdentifier(sourceTable.getSelectName())
            .append(" ")
            .append(notCopiedFilter.getSQLFragment(sourceTable.getSqlDialect()))
            .append(")");

        Collection<Long> notCopiedDataIds = new SqlSelector(sourceTable.getSchema(), objectIdSql).getCollection(Long.class);

        if (notCopiedDataIds.isEmpty())
        {
            LOG.info(rowsNotCopied(0));
        }
        else
        {
            LOG.info("{} -- deleting associated rows from exp.Data, exp.Object, etc.", rowsNotCopied(notCopiedDataIds.size()));

            // TODO: Delete exp.Data, etc. rows associated with the rows that weren't copied
        }
    }
}
