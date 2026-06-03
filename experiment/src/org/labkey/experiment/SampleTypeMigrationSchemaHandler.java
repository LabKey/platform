/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.experiment;

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.OrClause;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.SampleTypeDomainKind;
import org.labkey.api.migration.DatabaseMigrationService.DataFilter;
import org.labkey.api.migration.DefaultMigrationSchemaHandler;
import org.labkey.api.migration.FilePathWriter;
import org.labkey.api.util.GUID;
import org.labkey.api.util.logging.LogHelper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

class SampleTypeMigrationSchemaHandler extends DefaultMigrationSchemaHandler
{
    private static final Logger LOG = LogHelper.getLogger(SampleTypeMigrationSchemaHandler.class, "Sample type migration status");

    public SampleTypeMigrationSchemaHandler()
    {
        super(SampleTypeDomainKind.getSchema());
    }

    @Override
    public FilterClause getContainerClause(TableInfo sourceTable, Set<GUID> containers)
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
    public void addDomainDataFilterClause(OrClause orClause, DataFilter filter, TableInfo sourceTable, Set<String> selectColumnNames)
    {
        // Sample-type-specific optimization - joining to exp.Material instead of exp.Object is much faster
        if (filter.column().equalsIgnoreCase("Flag"))
        {
            String joinColumnName = getJoinColumnName(sourceTable);

            // Select all rows where the implicit flag column equals the filter value
            orClause.addClause(
                new SQLClause(new SQLFragment()
                    .appendIdentifier(joinColumnName)
                    .append(" IN (SELECT ")
                    .appendIdentifier(joinColumnName)
                    .append(" FROM exp.Material WHERE Container")
                    .appendInClause(filter.containers(), sourceTable.getSqlDialect())
                    .append(" AND ObjectId IN (SELECT ObjectId FROM exp.ObjectProperty WHERE StringValue = ? AND PropertyId = ?))")
                    .add(filter.condition().getParamVals()[0])
                    .add(getCommentPropertyId(sourceTable.getSchema().getScope()))
                )
            );
        }
        else
        {
            addDataFilterClause(orClause, filter, sourceTable, selectColumnNames);
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

        // Select all MaterialIds and ObjectIds associated with the not-copied rows from the source database. Our
        // notCopiedFilter works on the sample type provisioned table, so we need to use a sub-select (as opposed
        // to a join) to avoid ambiguous column references.
        String joinColumnName = getJoinColumnName(sourceTable);

        SQLFragment rowIdAndObjectIdSql = new SQLFragment("SELECT RowId, ObjectId FROM exp.Material WHERE ")
            .appendIdentifier(joinColumnName)
            .append(" IN (SELECT ")
            .appendIdentifier(joinColumnName)
            .append(" FROM ")
            .appendIdentifier(sourceTable.getSelectName())
            .append(" ")
            .append(notCopiedFilter.getSQLFragment(dialect))
            .append(")");

        Collection<Integer> notCopiedMaterialIds = new ArrayList<>();
        Collection<Long> notCopiedObjectIds = new ArrayList<>();

        try (Stream<ResultSet> stream = new SqlSelector(sourceTable.getSchema(), rowIdAndObjectIdSql).uncachedResultSetStream())
        {
            stream.forEach(rs -> {
                try
                {
                    notCopiedMaterialIds.add(rs.getInt(1));
                    notCopiedObjectIds.add(rs.getLong(2));
                }
                catch (SQLException e)
                {
                    throw new RuntimeException(e);
                }
            });
        }

        if (notCopiedMaterialIds.isEmpty())
        {
            LOG.info(rowsNotCopied(0));
        }
        else
        {
            LOG.info("{} -- deleting associated rows from exp.Material, exp.Object, etc.", rowsNotCopied(notCopiedMaterialIds.size()));

            SqlExecutor executor = new SqlExecutor(OntologyManager.getExpSchema());

            // Create an IN clause of exp.Material.RowIds
            SQLFragment materialIdClause = new SQLFragment()
                .appendInClause(notCopiedMaterialIds, dialect);

            // Delete from exp.Material (and associated tables)
            LOG.info("   exp.MaterialInput");
            executor.execute(
                new SQLFragment("DELETE FROM exp.MaterialInput WHERE MaterialId")
                    .append(materialIdClause)
            );
            LOG.info("   exp.MaterialAliasMap");
            executor.execute(
                new SQLFragment("DELETE FROM exp.MaterialAliasMap WHERE LSID IN (SELECT LSID FROM exp.Material WHERE RowId")
                    .append(materialIdClause)
                    .append(")")
            );
            LOG.info("   assay.Well");
            executor.execute(
                new SQLFragment("UPDATE assay.Well SET SampleId = null WHERE SampleId")
                    .append(materialIdClause)
            );
            LOG.info("   inventory.Item");
            executor.execute(
                new SQLFragment("DELETE FROM inventory.Item WHERE MaterialId")
                    .append(materialIdClause)
            );
            LOG.info("   exp.Material");
            executor.execute(
                new SQLFragment("DELETE FROM exp.Material WHERE RowId")
                    .append(materialIdClause)
            );

            SQLFragment objectIdClause = new SQLFragment()
                .appendInClause(notCopiedObjectIds, dialect);

            ExperimentMigrationSchemaHandler.deleteObjectIds(objectIdClause);
        }
    }

    @Override
    public void writeFilePaths(FilePathWriter writer, Set<GUID> guids)
    {
        // TODO: Enumerate FileLink fields in sample types in the filtered containers (guids) and write out those file
        // paths. Current client has none.
    }
}
