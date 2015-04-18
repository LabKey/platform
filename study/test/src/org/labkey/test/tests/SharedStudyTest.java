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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.DailyA;
import org.labkey.test.categories.Study;
import org.labkey.test.pages.DatasetInsertPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.DatasetDomainEditor;
import org.labkey.test.util.Maps;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@Category({DailyA.class, Study.class})
public class SharedStudyTest extends BaseWebDriverTest
{
    private static final String IMPORTED_STUDY = "Study001";
    private static final String EMPTY_STUDY = "Study002";
    private static final String SHARED_DEMOGRAPHICS = "P_One_Shared";
    private static final String SHARED_DEMOGRAPHICS_ID = "5001";
    public static final File STUDY_DIR = TestFileUtils.getSampleData("studies/ExtraKeyStudy");

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

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
    }

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
        _containerHelper.createSubfolder(getProjectName(), IMPORTED_STUDY, "Study");
        importFolderFromPipeline("folder.xml", 1, false);

        _containerHelper.createSubfolder(getProjectName(), EMPTY_STUDY, "Study");
        createDefaultStudy();
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
        beginAt("/query/" + getProjectName() + "/" + IMPORTED_STUDY + "/executeQuery.view?schemaName=study&query.queryName=Visit&query.viewName=" + viewName);
        table = new DataRegionTable("query", this, false);
        Assert.assertEquals(getProjectName(), table.getDataAsText(0, "Folder"));
    }

    @Test
    public void testVisitLookup()
    {
        log("Create custom view with 'folder' column");
        beginAt("/query/" + getProjectName() + "/" + IMPORTED_STUDY + "/executeQuery.view?schemaName=study&query.queryName=PVString_Two");
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.showHiddenItems();
        _customizeViewsHelper.addCustomizeViewColumn(new String[]{"PandaVisit", "Visit", "Folder"});
        _customizeViewsHelper.saveCustomView("withfolder");

        log("Verify visit folder is project");
        DataRegionTable table = new DataRegionTable("query", this, false);
        Assert.assertEquals(getProjectName(), table.getDataAsText(0, "Folder"));
    }

    @Test
    public void testStudyOverview()
    {
        log("Verify sub-folder study PV_One dataset has 2 participants at 'Visit 1'");
        beginAt("/study/" + getProjectName() + "/" + IMPORTED_STUDY + "/overview.view?");
        Assert.assertEquals("PV_One?", getTableCellText(Locator.id("studyOverview"), 6, 0));
        String visitLabel = getTableCellText(Locator.id("studyOverview"), 0, 4);
        Assert.assertTrue("Expected 'Visit 1', got: " + visitLabel, visitLabel.contains("Visit 1"));
        Assert.assertEquals("2", getTableCellText(Locator.id("studyOverview"), 6, 4));
    }

    @Test
    public void testManageVisitsRedirect()
    {
        log("Verify 'manage visits' redirect");
        clickFolder(IMPORTED_STUDY);
        goToManageStudy();
        click(Locator.linkWithText("manage shared visits"));
        String url = getCurrentRelativeURL();
        Assert.assertFalse("Expected redirect to project manage visits page, got: " + url, url.contains(IMPORTED_STUDY));

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
        clickFolder(IMPORTED_STUDY);
        goToManageStudy();
        clickAndWait(Locator.linkWithText("manage assay schedule"));

        click(Locator.linkWithText("create new visit"));
        setFormElement(Locator.input("newVisitLabel"), "Visit 4");
        setFormElement(Locator.input("newVisitRangeMin"), "4.00");
        setFormElement(Locator.input("newVisitRangeMax"), "4.99");
        clickButton("Submit");

        click(Locator.linkWithText("manage visits"));
        String url = getCurrentRelativeURL();
        Assert.assertFalse("Expected redirect to project manage visits page, got: " + url, url.contains(IMPORTED_STUDY));

        click(Locator.xpath("//th[text() = 'Visit 4']/../td/a[text() = 'edit']"));
        clickButton("Delete visit");
        clickButton("Delete");
    }

    @Test
    public void testNoSharingFromSubFolders()
    {
        String folderName = "No Sharing";
        _containerHelper.createSubfolder(getProjectName(), folderName, "Study");

        clickButton("Create Study");

        assertElementNotPresent(Locator.name("shareDatasets"));
        assertElementNotPresent(Locator.name("shareVisits"));
    }

    @Test
    public void testNoInsertIntoSharedDemographicsFromProject()
    {
        beginAt(WebTestHelper.buildURL("dataset", getProjectName(), "insert", Maps.of("datasetId", SHARED_DEMOGRAPHICS_ID)));

        Assert.assertEquals("403: Error Page -- User does not have permission to edit this dataset", getDriver().getTitle());
        assertElementNotPresent(Locator.css("table.labkey-data-region"));
    }

    @Test
    public void testInsertIntoSharedDemographicsFromFolder()
    {
        final String insertedPtid = "insertedParticipant";

        beginAt(WebTestHelper.buildURL("study", getProjectName() + "/" + EMPTY_STUDY, "dataset", Maps.of("datasetId", SHARED_DEMOGRAPHICS_ID)));

        DataRegionTable table = new DataRegionTable("Dataset", this);
        int initialRows = table.getDataRowCount();
        table.clickHeaderButtonByText("Insert New");
        DatasetInsertPage insertPage = new DatasetInsertPage(this, SHARED_DEMOGRAPHICS);
        insertPage.insert(Maps.of("ParticipantId", insertedPtid));

        assertElementPresent(Locator.linkWithText(insertedPtid));
        Assert.assertEquals("Wrong number of rows", initialRows + 1, table.getDataRowCount());

        // Inserted participant should not appear in adjacent study
        beginAt(WebTestHelper.buildURL("study", getProjectName() + "/" + IMPORTED_STUDY, "dataset", Maps.of("datasetId", SHARED_DEMOGRAPHICS_ID)));
        assertElementNotPresent(Locator.linkWithText(insertedPtid));
        Assert.assertEquals("Wrong number of rows", 4, table.getDataRowCount());

        // Inserted participant should appear in parent study
        beginAt(WebTestHelper.buildURL("study", getProjectName(), "dataset", Maps.of("datasetId", SHARED_DEMOGRAPHICS_ID)));
        assertElementPresent(Locator.linkWithText(insertedPtid));
        Assert.assertEquals("Wrong number of rows", initialRows + 5, table.getDataRowCount());
    }

    @Test
    public void testHidingSharedDataset()
    {
        final String datasetName = "Hiding Dataset";

        _containerHelper.createSubfolder(getProjectName(), datasetName, "Study");
        createDefaultStudy();

        goToManageStudy();
        clickAndWait(Locator.linkWithText("Manage Datasets"));
        assertElementPresent(Locator.linkContainingText(SHARED_DEMOGRAPHICS));
        clickAndWait(Locator.linkWithText("Create New Dataset"));

        setFormElement(Locator.name("typeName"), datasetName);
        // Default dataset ID will overlap shared demographics (5001)
        clickButton("Next");
        waitAndClick(Locator.id("partdelete_0"));
        clickButton("Save");

        assertTextPresent(String.format("A shared dataset is hidden by this local dataset definition: %s.", SHARED_DEMOGRAPHICS));
        clickAndWait(Locator.linkWithText("Manage Datasets"));
        assertTextPresent("WARNING: One or more datasets in parent study are hidden by datasets defined in this folder.");
        assertElementNotPresent(Locator.linkContainingText(SHARED_DEMOGRAPHICS));
    }

    @Test @Ignore
    public void testOverlappingParticipants()
    {}

    @Test @Ignore
    public void testMultiStudyParticipantGroup()
    {}

    @Test @Ignore
    public void testSharedDatasetSubfolderSecurity()
    {}

    @Test @Ignore
    public void testParticipantWebpart()
    {}
}
