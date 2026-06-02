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
package org.labkey.assay;

import org.apache.commons.lang3.Strings;
import org.apache.logging.log4j.Logger;
import org.labkey.api.assay.AbstractTsvAssayProvider;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.OrClause;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.migration.AssaySkipContainers;
import org.labkey.api.migration.DatabaseMigrationService.DataFilter;
import org.labkey.api.migration.DefaultMigrationSchemaHandler;
import org.labkey.api.migration.ExperimentDeleteService;
import org.labkey.api.util.GUID;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.assay.plate.PlateReplicateStatsDomainKind;

import java.util.Collection;
import java.util.Set;

class AssayResultMigrationSchemaHandler extends DefaultMigrationSchemaHandler
{
    private static final Logger LOG = LogHelper.getLogger(AssayResultMigrationSchemaHandler.class, "Assay result migration status");

    public AssayResultMigrationSchemaHandler()
    {
        super(DbSchema.get(AbstractTsvAssayProvider.ASSAY_SCHEMA_NAME, DbSchemaType.Provisioned));
    }

    private boolean skipTable(TableInfo sourceTable)
    {
        // For now, we're ignoring this table since it's empty in our first migration client's database
        return Strings.CI.endsWith(sourceTable.getName(), PlateReplicateStatsDomainKind.NAME);
    }

    @Override
    public FilterClause getContainerClause(TableInfo sourceTable, Set<GUID> containers)
    {
        final SQLFragment sql;

        if (skipTable(sourceTable))
        {
            sql = new SQLFragment("1 = 0");
        }
        else
        {
            sql = new SQLFragment("DataId IN (SELECT RowId FROM exp.Data WHERE Container")
                .appendInClause(AssaySkipContainers.getFilteredContainers(containers), sourceTable.getSqlDialect())
                .append(")");
        }

        return new SQLClause(sql);
    }

    @Override
    public void addDomainDataFilterClause(OrClause orClause, DataFilter filter, TableInfo sourceTable, Set<String> selectColumnNames)
    {
        // No row-by-row filtering on assay results for now; just add the passed in containers. Note that these will be
        // filtered by container if AssaySkipContainers is configured.
        orClause.addClause(getContainerClause(sourceTable, filter.containers()));
    }

    @Override
    public void afterTable(TableInfo sourceTable, TableInfo targetTable, SimpleFilter notCopiedFilter)
    {
        if (!skipTable(sourceTable))
        {
            SQLFragment objectIdSql = new SQLFragment("SELECT ObjectId FROM exp.Data WHERE RowId IN (SELECT DataId FROM ")
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

                // Delete exp.Data, exp.Object, etc. rows associated with the rows that weren't copied
                ExperimentDeleteService.get().deleteDataRows(notCopiedObjectIds);
            }
        }
    }
}
