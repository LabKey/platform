package org.labkey.experiment;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DatabaseMigrationService.DefaultMigrationSchemaHandler;
import org.labkey.api.data.DatabaseMigrationService.DomainFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.OrClause;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.SampleTypeDomainKind;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.Formats;
import org.labkey.api.util.GUID;
import org.labkey.api.util.logging.LogHelper;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

class SampleTypeMigrationSchemaHandler extends DefaultMigrationSchemaHandler
{
    private static final Logger LOG = LogHelper.getLogger(SampleTypeMigrationSchemaHandler.class, "Sample type migration status");

    public SampleTypeMigrationSchemaHandler()
    {
        super(SampleTypeDomainKind.getSchema());
    }

    @Override
    public @Nullable FieldKey getContainerFieldKey(TableInfo table)
    {
        return DUMMY_FIELD_KEY; // Unused dummy value -- see override below
    }

    @Override
    public FilterClause getContainerClause(TableInfo sourceTable, FieldKey containerFieldKey, Set<GUID> containers)
    {
        String joinColumnName = getJoinColumnName(sourceTable);

        return new SQLClause(new SQLFragment()
            .appendIdentifier(joinColumnName)
            .append(" IN (SELECT ")
            .appendIdentifier(joinColumnName)
            .append(" FROM exp.Material WHERE Container")
            .appendInClause(containers, sourceTable.getSqlDialect())
            .append(")")
        );
    }

    @Override
    public void addDomainDataFilter(OrClause orClause, DomainFilter filter, TableInfo sourceTable, FieldKey fKey, Set<String> selectColumnNames)
    {
        // Sample-type-specific optimization - joining to exp.Material instead of exp.Object is much faster
        if (filter.column().equalsIgnoreCase("Flag"))
        {
            String joinColumnName = getJoinColumnName(sourceTable);

            // Select all rows where the built-in flag column equals the filter value
            orClause.addClause(
                new SQLClause(new SQLFragment()
                    .appendIdentifier(joinColumnName)
                    .append(" IN (SELECT ")
                    .appendIdentifier(joinColumnName)
                    .append(" FROM exp.Material WHERE Container")
                    .appendInClause(filter.containers(), sourceTable.getSqlDialect())
                    .append(" AND ObjectId IN (SELECT ObjectId FROM exp.ObjectProperty WHERE StringValue = ? AND PropertyId = ?))")
                    .add(filter.condition().getParamVals()[0])
                    .add(getCommentPropertyId(sourceTable))
                )
            );
        }
        else
        {
            addDomainDataStandardFilter(orClause, filter, sourceTable, fKey, selectColumnNames);
        }
    }

    private String getJoinColumnName(TableInfo sourceTable)
    {
        // Provisioned sample type tables occasionally have no RowId column; hopefully they have an LSID column.
        return sourceTable.getColumn("RowId") != null ? "RowId" : "LSID";
    }

    @Override
    public void afterTable(TableInfo sourceTable, TableInfo targetTable, SimpleFilter notCopiedFilter)
    {
        SqlDialect dialect = sourceTable.getSqlDialect();
        final Selector selector;

        if (getJoinColumnName(sourceTable).equals("LSID"))
        {
            Collection<String> lsids = new TableSelector(sourceTable, Collections.singleton("LSID"), notCopiedFilter, null).getCollection(String.class);
            selector = new SqlSelector(getSchema(), new SQLFragment("SELECT RowId FROM exp.Material m WHERE lsid").appendInClause(lsids, dialect));
        }
        else
        {
            selector = new TableSelector(sourceTable, Collections.singleton("RowId"), notCopiedFilter, null);
        }

        Collection<Integer> notCopiedRows = selector.getCollection(Integer.class);

        if (!notCopiedRows.isEmpty())
        {
            LOG.info("   {} rows not copied -- deleting associated rows from exp.Material, exp.Object, etc.", Formats.commaf0.format(notCopiedRows.size()));

            SqlExecutor executor = new SqlExecutor(OntologyManager.getExpSchema());

            // An IN clause of exp.Material.RowIds are also the associated ObjectIds
            SQLFragment objectIdClause = new SQLFragment()
                .appendInClause(notCopiedRows, dialect);

            // Delete from exp.Material (and associated tables)
            LOG.info("   exp.MaterialInput");
            executor.execute(
                new SQLFragment("DELETE FROM exp.MaterialInput WHERE MaterialId")
                    .append(objectIdClause)
            );
            LOG.info("   exp.MaterialAliasMap");
            executor.execute(
                new SQLFragment("DELETE FROM exp.MaterialAliasMap WHERE LSID IN (SELECT LSID FROM exp.Material WHERE RowId")
                    .append(objectIdClause)
                    .append(")")
            );
            LOG.info("   exp.Material");
            executor.execute(
                new SQLFragment("DELETE FROM exp.Material WHERE RowId")
                    .append(objectIdClause)
            );

            ExperimentMigrationSchemaHandler.deleteObjectIds(objectIdClause);
        }
    }
}
