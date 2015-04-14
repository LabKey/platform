/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.test.tests;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyA;
import org.labkey.test.categories.Study;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.DatasetDomainEditor;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * User: kevink
 * Date: 2/23/15
 */
@Category({DailyA.class, Study.class})
public class SharedStudyTest extends BaseWebDriverTest
{
    private static final String STUDY_ONE = "Study001";
    private static final String STUDY_TWO = "Study002";
    private static final String SHARED_DEMOGRAPHICS = "P_One_Shared";
    public static final File STUDY_DIR = TestFileUtils.getSampleData("studies/ExtraKeyStudy");

    @BeforeClass
    public static void setupProject()
    {
        SharedStudyTest initTest = (SharedStudyTest)getCurrentTest();

        initTest.doSetup();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName(), "Study");

        // Create a study with shared visits
        clickButton("Create Study");
        setFormElement(Locator.name("subjectNounSingular"), "Panda");
        setFormElement(Locator.name("subjectNounPlural"), "Pandas");
        setFormElement(Locator.name("subjectColumnName"), "PandaId");
        checkRadioButton(Locator.radioButtonByNameAndValue("shareDatasets", "true"));
        checkRadioButton(Locator.radioButtonByNameAndValue("shareVisits", "true"));
        clickButton("Create Study");
        _containerHelper.setFolderType("Dataspace");

        DatasetDomainEditor datasetDomainEditor = _studyHelper.defineDataset(SHARED_DEMOGRAPHICS, getProjectName());
        datasetDomainEditor.checkDemographicData();
        datasetDomainEditor.shareDemographics(DatasetDomainEditor.ShareDemographicsBy.PTID);
        datasetDomainEditor.inferFieldsFromFile(new File(STUDY_DIR, "study/datasets/dataset5001.tsv"));
        datasetDomainEditor.save();

        setPipelineRoot(STUDY_DIR.getAbsolutePath());
        _containerHelper.createSubfolder(getProjectName(), STUDY_ONE, "Study");
        importFolderFromPipeline("folder.xml", 1, false);

        _containerHelper.createSubfolder(getProjectName(), STUDY_TWO, "Study");
        createDefaultStudy();
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
    }

    @Nullable
    @Override
    protected String getProjectName()
    {
        return getClass().getSimpleName() + " Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @Test
    public void testVisitsContainerFilter()
    {
        log("Create shared custom view in project with 'folder' column");
        beginAt("/query/" + getProjectName() + "/executeQuery.view?schemaName=study&query.queryName=Visit");
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.showHiddenItems();
        _customizeViewsHelper.addCustomizeViewColumn("Folder");

        String viewName = "withfolder";
        _customizeViewsHelper.saveCustomView(viewName, false, true);

        log("Verify visit folder is project");
        DataRegionTable table = new DataRegionTable("query", this, false);
        Assert.assertEquals(getProjectName(), table.getDataAsText(0, "Folder"));

        log("Verify visit folder is project");
        beginAt("/query/" + getProjectName() + "/" + STUDY_ONE + "/executeQuery.view?schemaName=study&query.queryName=Visit&query.viewName=" + viewName);
        table = new DataRegionTable("query", this, false);
        Assert.assertEquals(getProjectName(), table.getDataAsText(0, "Folder"));
    }

    @Test
    public void testVisitLookup()
    {
        log("Create custom view with 'folder' column");
        beginAt("/query/" + getProjectName() + "/" + STUDY_ONE + "/executeQuery.view?schemaName=study&query.queryName=PVString_Two");
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.showHiddenItems();
        _customizeViewsHelper.addCustomizeViewColumn(new String[] { "PandaVisit", "Visit", "Folder" });
        _customizeViewsHelper.saveCustomView("withfolder");

        log("Verify visit folder is project");
        DataRegionTable table = new DataRegionTable("query", this, false);
        Assert.assertEquals(getProjectName(), table.getDataAsText(0, "Folder"));
    }

    @Test
    public void testStudyOverview()
    {
        log("Verify sub-folder study PV_One dataset has 2 participants at 'Visit 1'");
        beginAt("/study/" + getProjectName() + "/" + STUDY_ONE + "/overview.view?");
        Assert.assertEquals("PV_One?", getTableCellText(Locator.id("studyOverview"), 6, 0));
        String visitLabel = getTableCellText(Locator.id("studyOverview"), 0, 4);
        Assert.assertTrue("Expected 'Visit 1', got: " + visitLabel, visitLabel.contains("Visit 1"));
        Assert.assertEquals("2", getTableCellText(Locator.id("studyOverview"), 6, 4));
    }

    @Test
    public void testManageVisitsRedirect()
    {
        log("Verify 'manage visits' redirect");
        clickFolder(STUDY_ONE);
        goToManageStudy();
        click(Locator.linkWithText("manage shared visits"));
        String url = getCurrentRelativeURL();
        Assert.assertFalse("Expected redirect to project manage visits page, got: " + url, url.contains(STUDY_ONE));

        String title = getDriver().getTitle();
        Assert.assertTrue("Expected title to start with 'Manage Shared Timepoints', got:" + title, title.startsWith("Manage Shared Visits"));
    }

    @Test
    public void testEditVisitDescription()
    {
        goToManageStudy();
        click(Locator.linkWithText("manage shared visits"));
        click(Locator.xpath("//th[text() = 'Visit 1']/../td/a[text() = 'edit']"));
        setFormElement(Locator.name("description"), "This is the first visit");
        clickButton("Save");

        String description = getText(Locator.xpath("//th[text() = 'Visit 1']/../td[6]"));
        Assert.assertEquals("This is the first visit", description);
    }

    @Test
    public void testCreateVisitViaAssaySchedule()
    {
        clickFolder(STUDY_ONE);
        goToManageStudy();
        clickAndWait(Locator.linkWithText("manage assay schedule"));

        click(Locator.linkWithText("create new visit"));
        setFormElement(Locator.input("newVisitLabel"), "Visit 4");
        setFormElement(Locator.input("newVisitRangeMin"), "4.00");
        setFormElement(Locator.input("newVisitRangeMax"), "4.99");
        clickButton("Submit");

        click(Locator.linkWithText("manage visits"));
        String url = getCurrentRelativeURL();
        Assert.assertFalse("Expected redirect to project manage visits page, got: " + url, url.contains(STUDY_ONE));

        click(Locator.xpath("//th[text() = 'Visit 4']/../td/a[text() = 'edit']"));
        clickButton("Delete visit");
        clickButton("Delete");
    }

    @Test
    public void testNoSharingInSubFolders()
    {
        String folderName = "No Sharing";
        _containerHelper.createSubfolder(getProjectName(), folderName, "Study");

        clickButton("Create Study");

        assertElementNotPresent(Locator.name("shareDatasets"));
        assertElementNotPresent(Locator.name("shareVisits"));
    }
}
