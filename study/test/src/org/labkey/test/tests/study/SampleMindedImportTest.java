/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.Specimen;
import org.labkey.test.util.DataRegionTable;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Imports a SampleMinded data export (.xlsx) into the specimen repository.
 */
@Category({DailyB.class, Specimen.class})
public class SampleMindedImportTest extends BaseWebDriverTest
{
    private static final String PROJECT_NAME = "SampleMindedImportTest";

    private static final String FILE = "SampleMindedExport.xlsx";

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }

    @Override
    protected String getProjectName()
    {
        return PROJECT_NAME;
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        File specimenDir = new File(TestFileUtils.getLabKeyRoot() + "/sampledata/study/specimens");
        File specimenArchive = new File(specimenDir, "SampleMindedExport.specimens");
        specimenArchive.delete();

        for (File file : specimenDir.listFiles())
        {
            if (file.getName().startsWith(FILE) && file.getName().endsWith(".log"))
            {
                file.delete();
            }
        }

        // Now delete the project
        super.doCleanup(afterTest);
    }

    @Test
    public void testSteps()
    {
        _containerHelper.createProject(PROJECT_NAME, "Study");
        clickButton("Create Study");
        setFormElement(Locator.name("startDate"), "2011-01-01");
        click(Locator.radioButtonByNameAndValue("simpleRepository", "true"));
        clickButton("Create Study");

        clickAndWait(Locator.linkWithText("manage visits"));
        clickAndWait(Locator.linkWithText("create new visit"));
        setFormElement(Locator.name("label"), "Visit SE");
        setFormElement(Locator.name("sequenceNumMin"), "999.0000");
        setFormElement(Locator.name("sequenceNumMax"), "999.9999");
        selectOptionByValue(Locator.name("sequenceNumHandling"), "logUniqueByDate");
        clickAndWait(Locator.lkButton("Save"));

        // "overview" is a dumb place for this link
        clickAndWait(Locator.linkWithText("Overview"));
        clickAndWait(Locator.linkWithText("manage files"));
        setPipelineRoot(TestFileUtils.getLabKeyRoot() + "/sampledata/study");
        clickFolder(PROJECT_NAME);
        clickTab("Overview");
        clickAndWait(Locator.linkWithText("Manage Files"));

        clickButton("Process and Import Data");
        _fileBrowserHelper.importFile("specimens/" + FILE, "Import Specimen Data");
        clickButton("Start Import");
        waitForPipelineJobsToComplete(1, "Import specimens: SampleMindedExport.xlsx", false);
        clickTab("Specimen Data");
        waitForElement(Locator.linkWithText("BAL"));
        assertElementPresent(Locator.linkWithText("BAL"));
        assertElementPresent(Locator.linkWithText("Blood"));
        clickAndWait(Locator.linkWithText("By Individual Vial"));
        assertElementPresent(Locator.linkWithText("P1000001"), 6);
        assertElementPresent(Locator.linkWithText("P2000001"), 3);
        assertElementPresent(Locator.linkWithText("P20043001"), 5);
        assertTextPresent("20045467", "45627879", "1000001-21");

        clickTab("Specimen Data");
        waitForElement(Locator.linkWithText("NewSpecimenType"));
        clickAndWait(Locator.linkWithText("NewSpecimenType"));
        assertTextPresent("EARL (003)", "REF-A Cytoplasm Beaker");

        clickTab("Specimen Data");
        waitForElement(Locator.linkWithText("BAL"));
        clickAndWait(Locator.linkWithText("BAL"));
        assertTextPresent("BAL Supernatant", "FREE (007)");
        DataRegionTable specimenTable = new DataRegionTable("SpecimenDetail", getDriver());
        assertEquals("Incorrect number of vials.", "Count (non-blank): 5", specimenTable.getSummaryStatFooterText("Global Unique Id"));

        clickAndWait(Locator.linkWithText("Group vials"));
        assertElementPresent(Locator.linkWithText("P20043001"), 2);
        assertTextPresent("Visit SE");

        // add column sequencenum
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.showHiddenItems();
        _customizeViewsHelper.addColumn("SequenceNum");
        _customizeViewsHelper.applyCustomView();
        assertTextPresent("999.0138");
    }
}
