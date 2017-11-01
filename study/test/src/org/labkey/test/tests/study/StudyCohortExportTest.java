/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
package org.labkey.test.tests.study;

import org.junit.experimental.categories.Category;
import org.labkey.test.categories.DailyC;
import org.labkey.test.util.DataRegionTable;

@Category({DailyC.class})
public class StudyCohortExportTest extends StudyExportTest
{
    @Override
    protected void doCohortCreateSteps()
    {
        unenrollCohort();
    }

    @Override
    protected void doVerifySteps()
    {
        // verify the unenrolled bit is set for group 2
        // Note that the regular StudyExportTest will cover the manual export
        // of cohorts.  The automatic cohort case will cover the manual cohorts
        // codepath since we have to persist out a cohorts.xml file.  Test that
        // automatic cohorts do this.
        verifyUnenrolledCohort();
        verifyCohorts(false);
    }

    @Override
    public void runApiTests()
    {
    }

    private void verifyUnenrolledCohort()
    {
        clickFolder(getFolderName());
        DataRegionTable table = getCohortDataRegionTable();
        verifyCohortStatus(table, GROUP_2, false);
    }

    // unenroll the GROUP 2 cohort
    private void unenrollCohort()
    {
        clickFolder(getFolderName());
        DataRegionTable table =  getCohortDataRegionTable();
        changeCohortStatus(table, GROUP_2, false);
    }
}
