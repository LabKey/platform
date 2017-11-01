/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyC;
import org.labkey.test.pages.DatasetPropertiesPage;
import org.labkey.test.pages.EditDatasetDefinitionPage;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.LogMethod;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category({DailyC.class})
public class StudyDatasetReloadTest extends StudyBaseTest
{
    private static final File STUDY_WITH_BIT = TestFileUtils.getSampleData("studies/StudyWithDemoBit.folder.zip");
    private static final File STUDY_WITHOUT_BIT = TestFileUtils.getSampleData("studies/StudyWithoutDemoBit.folder.zip");

    protected String getProjectName()
    {
        return "StudyDatasetReloadProject";
    }

    protected String getFolderName()
    {
        return "Study Dataset Reload";
    }

    @Override
    @LogMethod
    protected void doCreateSteps()
    {
        initializeFolder();
        initializePipeline(null);
        clickFolder(getFolderName());

        log("Import study with Demographics bit set on dataset");
        importFolderFromZip(STUDY_WITH_BIT);
    }

    @Override
    @LogMethod
    protected void doVerifySteps()
    {
        reloadStudyFromZip(STUDY_WITHOUT_BIT);

        // Check changes
        clickFolder(getFolderName());
        clickAndWait(Locator.linkContainingText("DEM: Demographics"));
        _extHelper.clickMenuButton(true, "Manage");
        waitForText("StaffCode", "DoubleNum");
        assertElementPresent(Locator.tagWithText("td", "Staff Code").append("/../td/input[last()][@checked]"));     // MV
        assertElementPresent(Locator.tagWithText("td", "VisitDay").append("/../td/input[last()][not(@checked)]"));  // MV

        EditDatasetDefinitionPage editDatasetPage = new DatasetPropertiesPage(getDriver()).clickEditDefinition();
        assertFalse("Study import set demographics bit incorrectly", editDatasetPage.isDemographicsData());

        goToManageStudy();
        clickButton("Reload Study");
        setFormElement(Locator.name("folderZip"), STUDY_WITH_BIT);
        clickButton("Reload Study");
        waitForPipelineJobsToComplete(3, "Study Reload", false);

        // Check changes
        clickFolder(getFolderName());
        clickAndWait(Locator.linkContainingText("DEM-1: Demographics"));
        _extHelper.clickMenuButton(true, "Manage");
        assertTextNotPresent("StaffCode", "DoubleNum");
        assertElementPresent(Locator.tagWithText("td", "VisitDay").append("/../td/input[last()][@checked]"));  // MV

        editDatasetPage = new DatasetPropertiesPage(getDriver()).clickEditDefinition();
        assertTrue("Study import set demographics bit incorrectly", editDatasetPage.isDemographicsData());
    }


}
