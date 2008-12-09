/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.study;

import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.study.model.Cohort;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import java.sql.SQLException;
import java.util.List;

/**
 * User: jgarms
 * Date: Jul 21, 2008
 * Time: 5:07:08 PM
 */
public class StudyUpgrader
{
    private static final String UPGRADE_REQUIRED = "UPGRADE_REQUIRED";

    /**
     * Update extensible tables during product upgrade of 8.3
     */
    public static void upgradeExtensibleTables_83(User user) throws SQLException
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        DbScope scope = schema.getScope();
        boolean transactionOwner = !scope.isTransactionActive();

        // Retrieve studies, requesting only columns that exist at this point in the upgrade process 
        TableInfo studyTableInfo = StudySchema.getInstance().getTableInfoStudy();
        List<ColumnInfo> columns = studyTableInfo.getColumns("Label, Container, EntityId, DateBased, StartDate, ParticipantCohortDataSetId, ParticipantCohortProperty, SecurityType, LSID");
        Study[] studies = Table.select(studyTableInfo, columns, null, null, Study.class);

        for (Study study : studies)
        {
            try
            {
                if (transactionOwner)
                    scope.beginTransaction();
                addLsids(user, study);
                if (transactionOwner)
                    scope.commitTransaction();
            }
            finally
            {
                if (transactionOwner)
                    scope.closeConnection();
            }
        }
    }

    private static void addLsids(User user, Study study) throws SQLException
    {
        StudyManager manager = StudyManager.getInstance();
        if (UPGRADE_REQUIRED.equals(study.getLsid()))
        {
            study.setLsid(manager.createLsid(study, study.getContainer().getRowId()));
            manager.updateStudy(user, study);
        }

        for (Cohort cohort : study.getCohorts(user))
        {
            if (UPGRADE_REQUIRED.equals(cohort.getLsid()))
            {
                cohort = cohort.createMutable();
                cohort.setLsid(manager.createLsid(cohort, cohort.getRowId()));
                manager.updateCohort(user, cohort);
            }
        }

    }

}
