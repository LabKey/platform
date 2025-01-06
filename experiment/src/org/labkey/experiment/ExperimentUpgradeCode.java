/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleContext;
import org.labkey.experiment.api.ClosureQueryHelper;
import org.labkey.experiment.samples.SampleTimelineAuditProvider;

import java.util.List;

public class ExperimentUpgradeCode implements UpgradeCode
{
    private static final Logger LOG = LogManager.getLogger(ExperimentUpgradeCode.class);

    // called from exp-24.003-24.004.sql
    public static void addMissingSampleTypeIdsForSampleTimelineAudit(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        DbScope scope = ExperimentService.get().getSchema().getScope();
        List<String> tableNames = new SqlSelector(scope, "SELECT StorageTableName FROM exp.domainDescriptor WHERE StorageSchemaName='audit' AND name='" + SampleTimelineAuditProvider.SampleTimelineAuditDomainKind.NAME + "'").getArrayList(String.class);
        if (tableNames.size() > 1)
            LOG.warn("Found " + tableNames.size() + " tables for " + SampleTimelineAuditProvider.SampleTimelineAuditDomainKind.NAME);

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            for (String table : tableNames)
            {
                SQLFragment countSql = new SQLFragment("SELECT COUNT(*) FROM audit.").append(table).append(" WHERE sampleTypeId = 0");
                SqlSelector countSelector = new SqlSelector(scope, countSql);

                long toUpdate = countSelector.getObject(Long.class);
                LOG.info("There are " + toUpdate + " audit log entries to be updated in audit." + table + ".");
                // first update the type id by finding other audit entries that reference the same sample id.
                if (toUpdate > 0)
                {
                    LOG.info("Updating table audit." + table + " via self-join.");
                    SQLFragment updateSql = new SQLFragment("UPDATE audit.").append(table)
                            .append(" SET sampleTypeId = a3.sampleTypeId\n")
                            .append(" FROM\n")
                            .append("   (SELECT sampleId, rowId as rowIdToUpdate FROM audit.").append(table).append(" WHERE sampleTypeId = 0").append(") a2 ")
                            .append("   LEFT JOIN\n")
                            .append("   (SELECT MAX(sampleTypeId) as sampleTypeId, sampleId FROM audit.").append(table).append(" GROUP BY sampleId) a3")
                            .append("   ON a2.sampleId = a3.sampleId")
                            .append(" WHERE rowId = a2.rowIdToUpdate");
                    long start = System.currentTimeMillis();
                    SqlExecutor executor = new SqlExecutor(scope);
                    int numRows = executor.execute(updateSql);
                    long elapsed = System.currentTimeMillis() - start;
                    LOG.info("Updated " + numRows + " rows via self-join for table " + table + " in " + (elapsed / 1000) + " sec");
                }

                toUpdate = countSelector.getObject(Long.class);
                if (toUpdate > 0)
                {
                    // It may have happened that there's only one audit entry for a sample and that entry has a 0 for the type id, in which case we may be able
                    // to find the type id from the exp.materials table. Since samples may have been deleted, it isn't sufficient to do only this update
                    LOG.info("Updating table audit." + table + " via exp.materials.");
                    SQLFragment updateSql = new SQLFragment("UPDATE audit.").append(table)
                            .append(" SET sampleTypeId = m.materialSourceId\n")
                            .append(" FROM\n")
                            .append("   (SELECT sampleId, rowId as rowIdToUpdate FROM audit.").append(table).append(" WHERE sampleTypeId = 0").append(") a2 ")
                            .append("   LEFT JOIN\n")
                            .append("   (SELECT materialSourceId, rowId AS sampleRowId FROM exp.material) m")
                            .append("   ON a2.sampleId = m.sampleRowId")
                            .append(" WHERE rowId = a2.rowIdToUpdate");
                    long start = System.currentTimeMillis();
                    SqlExecutor executor = new SqlExecutor(scope);
                    int numRows = executor.execute(updateSql);
                    long elapsed = System.currentTimeMillis() - start;
                    LOG.info("Updated " + numRows + " rows from exp.material table join in " + (elapsed / 1000) + " sec");
                }
                long remaining = countSelector.getObject(Long.class);
                LOG.info("There are " + remaining + " rows in audit." + table + " that could not be updated with a proper sample type id.");
            }
            transaction.commit();
        }
    }

    // called from exp-24.005-24.006.sql
    public static void repopulateAncestors(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        ClosureQueryHelper.truncateAndRecreate(LOG);
    }
}
