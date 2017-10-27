/*
 * Copyright (c) 2015-2017 LabKey Corporation
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

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.Locators;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.DailyA;
import org.labkey.test.components.ParticipantListWebPart;
import org.labkey.test.components.studydesigner.ManageAssaySchedulePage;
import org.labkey.test.pages.DatasetInsertPage;
import org.labkey.test.pages.study.ManageVisitPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.DatasetDesignerPage;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.Maps;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Category({DailyA.class})
public class SharedStudyTest extends BaseWebDriverTest
{
    private static final String NON_DATASPACE_PROJECT = "Non Dataspace Project";
    private static final String STUDY1 = "Study001";
    private static final String STUDY2 = "Study002";
    private static final String SHARED_DEMOGRAPHICS = "P_One_Shared";
    private static final String SHARED_DEMOGRAPHICS_ID = "5001";
    private static final String STUDY2_DATASET = "Extra Dataset";
    private static final String STUDY2_DATASET_ID = "999";
    private static final String[] STUDY1_PTIDS = {"1000", "1001", "1002", "1003"};
    private static final String[] STUDY2_PTIDS = {"9000", "9001"};
    public static final File STUDY_DIR = TestFileUtils.getSampleData("studies/ExtraKeyStudy");
    private static final String user = "study_reader@sharedstudy.test";
    public static final String PARTICIPANT_NOUN_PLURAL = "Pandas";
    public static final String PARTICIPANT_NOUN_SINGULAR = "Panda";

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
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
        deleteUsersIfPresent(user);
    }

    @BeforeClass
    public static void setupProject()
    {
        SharedStudyTest initTest = (SharedStudyTest)getCurrentTest();

        initTest.doSetup();
    }

    private void doSetup()
    {
        createDataspaceProject(getProjectName(), PARTICIPANT_NOUN_SINGULAR, PARTICIPANT_NOUN_PLURAL, "PandaId","VISIT", true, true);

        DatasetDesignerPage datasetDomainEditor = _studyHelper.defineDataset(SHARED_DEMOGRAPHICS, getProjectName());
        datasetDomainEditor.checkDemographicData();
        datasetDomainEditor.shareDemographics(DatasetDesignerPage.ShareDemographicsBy.PTID);
        datasetDomainEditor.inferFieldsFromFile(new File(STUDY_DIR, "study/datasets/dataset5001.tsv"));
        datasetDomainEditor.save();

        setPipelineRoot(STUDY_DIR.getAbsolutePath());
        _containerHelper.createSubfolder(getProjectName(), STUDY1, "Study");
        importFolderFromPipeline("folder.xml", 1, false);

        _containerHelper.createSubfolder(getProjectName(), STUDY2, "Study");
        clickButton("Create Study");
        setFormElement(Locator.name("subjectNounSingular"), PARTICIPANT_NOUN_SINGULAR);
        setFormElement(Locator.name("subjectNounPlural"), PARTICIPANT_NOUN_PLURAL);
        setFormElement(Locator.name("subjectColumnName"), "PandaId");
        clickButton("Create Study");
        beginAt(WebTestHelper.buildURL("study", getProjectName() + "/" + STUDY2, "dataset", Maps.of("datasetId", SHARED_DEMOGRAPHICS_ID)));
        _listHelper.importDataFromFile(new File(STUDY_DIR, "study/datasets/extra_demographics.txt"));
        _studyHelper.importDataset(STUDY2_DATASET, getProjectName() + "/" + STUDY2, STUDY2_DATASET_ID, new File(STUDY_DIR, "study/datasets/extra_dataset.txt"));
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
        _customizeViewsHelper.addColumn("Folder");

        String viewName = "withfolder";
        _customizeViewsHelper.saveCustomView(viewName, false, true);

        log("Verify visit folder is project");
        DataRegionTable table = new DataRegionTable("query", this);
        Assert.assertEquals(getProjectName(), table.getDataAsText(0, "Folder"));

        log("Verify visit folder is project");
        beginAt("/query/" + getProjectName() + "/" + STUDY1 + "/executeQuery.view?schemaName=study&query.queryName=Visit&query.viewName=" + viewName);
        table = new DataRegionTable("query", this);
        Assert.assertEquals(getProjectName(), table.getDataAsText(0, "Folder"));
    }

    @Test
    public void testVisitLookup()
    {
        log("Create custom view with 'folder' column");
        beginAt("/query/" + getProjectName() + "/" + STUDY1 + "/executeQuery.view?schemaName=study&query.queryName=PVString_Two");
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.showHiddenItems();
        _customizeViewsHelper.addColumn(new String[]{"PandaVisit", "Visit", "Folder"});
        _customizeViewsHelper.saveCustomView("withfolder");

        log("Verify visit folder is project");
        DataRegionTable table = new DataRegionTable("query", this);
        Assert.assertEquals(getProjectName(), table.getDataAsText(0, "Folder"));
    }

    @Test
    public void testStudyOverview()
    {
        log("Verify sub-folder study PV_One dataset has 2 participants at 'Visit 1'");
        beginAt("/study/" + getProjectName() + "/" + STUDY1 + "/overview.view?");
        Assert.assertEquals("PV_One?", getTableCellText(Locator.id("studyOverview"), 6, 0));
        String visitLabel = getTableCellText(Locator.id("studyOverview"), 0, 4);
        Assert.assertTrue("Expected 'Visit 1', got: " + visitLabel, visitLabel.contains("Visit 1"));
        Assert.assertEquals("2", getTableCellText(Locator.id("studyOverview"), 6, 4));
    }

    @Test
    public void testManageVisitsRedirect()
    {
        log("Verify 'manage visits' redirect");
        clickFolder(STUDY1);
        goToManageStudy();
        click(Locator.linkWithText("manage shared visits"));
        String url = getCurrentRelativeURL();
        Assert.assertFalse("Expected redirect to project manage visits page, got: " + url, url.contains(STUDY1));

        String title = getDriver().getTitle();
        Assert.assertTrue("Expected title to start with 'Manage Shared Timepoints', got:" + title, title.startsWith("Manage Shared Visits"));
    }

    @Test
    public void testDataspacePublishButtonVisibility()
    {
        log("Verify dataspace publish button");
        Assert.assertTrue("Dataspace of timepoint style visit with shared datasets and shared visits should be shown", isDataspacePublishButtonShown(getProjectName()));

        String dataSpaceDateVisit = "Dataspace Date Visit";
        createDataspaceProject(dataSpaceDateVisit, PARTICIPANT_NOUN_SINGULAR, PARTICIPANT_NOUN_PLURAL, "PandaId","DATE", true, true);
        Assert.assertFalse("Dataspace of timepoint style DATE should not be shown", isDataspacePublishButtonShown(dataSpaceDateVisit));
        _containerHelper.deleteProject(dataSpaceDateVisit);

        String dataSpaceDatasetNotShared = "Dataspace Dataset Not Shared";
        createDataspaceProject(dataSpaceDatasetNotShared, PARTICIPANT_NOUN_SINGULAR, PARTICIPANT_NOUN_PLURAL, "PandaId","VISIT", false, true);
        Assert.assertFalse("Dataspace without shared datasets should not be shown", isDataspacePublishButtonShown(dataSpaceDatasetNotShared));
        _containerHelper.deleteProject(dataSpaceDatasetNotShared);

        String dataSpaceVisitNotShared = "Dataspace Visit Not Shared";
        createDataspaceProject(dataSpaceVisitNotShared, PARTICIPANT_NOUN_SINGULAR, PARTICIPANT_NOUN_PLURAL, "PandaId","VISIT", true, false);
        Assert.assertFalse("Dataspace without shared visits should not be shown", isDataspacePublishButtonShown(dataSpaceVisitNotShared));
        _containerHelper.deleteProject(dataSpaceVisitNotShared);
    }

    @Test
    public void testDataspacePublishWizard()
    {
        String nonDataspaceProject = "Non Dataspace Project";
        String studyPublishedFromDataspace = "Study Published From Dataspace";
        List<String> allPtids = new ArrayList<>(Arrays.asList(ArrayUtils.addAll(STUDY1_PTIDS, STUDY2_PTIDS)));

        _containerHelper.createProject(nonDataspaceProject, "None");

        goToProjectHome(getProjectName());
        goToManageStudy();

        clickButton("Publish Study", 0);
        _extHelper.waitForExtDialog("Publish Study");

        // Wizard page 1 : General Setup
        waitForElement(Locator.xpath("//div[@class = 'labkey-nav-page-header'][text() = 'General Setup']"));
        setFormElement(Locator.name("studyName"), studyPublishedFromDataspace);
        clickButton("Change", 0);
        sleep(1000); // sleep while the tree expands
        Locator projectTreeNode = Locator.tagWithClass("a", "x-tree-node-anchor").withDescendant(Locator.tagWithText("span", NON_DATASPACE_PROJECT));
        doubleClick(projectTreeNode);
        clickButton("Next", 0);

        // Wizard page 2 : Mice
        clickButton("Next", 0);

        // Wizard page 3 : Datasets
        waitForElement(Locator.xpath("//div[@class = 'labkey-nav-page-header'][text() = 'Datasets']"));
        waitForElement(Locator.css(".studyWizardDatasetList"));
        click(Locator.css(".studyWizardDatasetList .x-grid3-hd-checker  div"));
        clickButton("Next", 0);

        // Wizard page 4 : Visits
        waitForElement(Locator.xpath("//div[@class = 'labkey-nav-page-header'][text() = 'Visits']"));
        waitForElement(Locator.css(".studyWizardVisitList"));
        clickButton("Next", 0);
        _extHelper.waitForExtDialog("Error");
        assertTextPresent("You must select at least one visit.");
        _extHelper.clickExtButton("Error", "OK", 0);
        click(Locator.css(".studyWizardVisitList .x-grid3-hd-checker  div"));
        clickButton("Next", 0);

        // Wizard page 5 : Specimens
        clickButton("Next", 0);

        // Wizard Page 6 : Study Objects
        waitForElement(Locator.xpath("//div[@class = 'labkey-nav-page-header'][text() = 'Study Objects']"));
        click(Locator.css(".studyObjects .x-grid3-hd-checker  div"));
        clickButton("Next", 0);

        // Wizard page 7 : Lists
        clickButton("Next", 0);

        // Wizard page 8 : Grid Views
        clickButton("Next", 0);

        // Wizard Page 9 : Reports and Charts
        clickButton("Next", 0);

        // Wizard page 10 : Folder Objects
        waitForElement(Locator.xpath("//div[@class = 'labkey-nav-page-header'][text() = 'Folder Objects']"));
        click(Locator.css(".folderObjects .x-grid3-hd-checker  div"));
        clickButton("Next", 0);

        // Wizard page 11 : Publish Options
        clickButton("Finish");
        waitForPipelineJobsToComplete(1, "Publish Study", false);

        //verfiy that published study description has correct number visits, and number participants
        beginAt("/" + nonDataspaceProject + "/" + studyPublishedFromDataspace + "/project-begin.view?");
        assertTextPresent(String.format("over 6 visits. Data is present for 6 Pandas."));

        // verfiy that published study description has correct dataset
        clickAndWait(Locator.linkWithText("1 dataset"));
        clickAndWait(Locator.linkWithText(SHARED_DEMOGRAPHICS));

        // verify dataset has correct participants
        DataRegionTable dataset = new DataRegionTable("Dataset", this);
        List actualPtids = dataset.getColumnDataAsText("PandaId");
        Assert.assertEquals("Published study does not have the expected participants", allPtids, actualPtids);

        _containerHelper.deleteProject(nonDataspaceProject);
    }

    @Test
    public void testEditVisitDescription()
    {
        goToManageStudy();
        clickAndWait(Locator.linkWithText("manage shared visits"));
        ManageVisitPage manageVisitPage = new ManageVisitPage(getDriver());
        manageVisitPage.goToEditVisit("Visit 1");
        setFormElement(Locator.name("description"), "This is the first visit");
        clickButton("Save");

        String description = getText(Locator.xpath("//td[text() = 'Visit 1']/../td[7]"));
        Assert.assertEquals("This is the first visit", description);
    }

    @Test
    public void testCreateVisitViaAssaySchedule()
    {
        clickFolder(STUDY1);
        goToManageStudy();
        clickAndWait(Locator.linkWithText("manage assay schedule"));

        ManageAssaySchedulePage assaySchedulePage = new ManageAssaySchedulePage(this, true);
        assaySchedulePage.addNewVisitColumn("Visit 4", 4.0, 4.99);
        click(Locator.linkWithText("manage visits"));
        String url = getCurrentRelativeURL();
        Assert.assertFalse("Expected redirect to project manage visits page, got: " + url, url.contains(STUDY1));

        ManageVisitPage manageVisitPage = new ManageVisitPage(getDriver());
        manageVisitPage.goToEditVisit("Visit 4");
        clickButton("Delete Visit");
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

        beginAt(WebTestHelper.buildURL("study", getProjectName() + "/" + STUDY2, "dataset", Maps.of("datasetId", SHARED_DEMOGRAPHICS_ID)));
        assertElementNotPresent(Locator.linkWithText(insertedPtid));

        new DataRegionTable("Dataset", getDriver()).clickInsertNewRow();
        new DatasetInsertPage(this.getDriver(), SHARED_DEMOGRAPHICS).insert(Maps.of("PandaId", insertedPtid));

        beginAt(WebTestHelper.buildURL("study", getProjectName() + "/" + STUDY2, "dataset", Maps.of("datasetId", STUDY2_DATASET_ID)));
        assertElementNotPresent(Locator.linkWithText(insertedPtid));
        _listHelper.uploadData(
                "pandaId\tsequenceNum\n" +
                insertedPtid + "\t1");

        // Inserted participant should not appear in adjacent study
        beginAt(WebTestHelper.buildURL("study", getProjectName() + "/" + STUDY1, "dataset", Maps.of("datasetId", SHARED_DEMOGRAPHICS_ID)));
        assertElementNotPresent(Locator.linkWithText(insertedPtid));

        // Inserted participant should appear in parent study
        beginAt(WebTestHelper.buildURL("study", getProjectName(), "dataset", Maps.of("datasetId", SHARED_DEMOGRAPHICS_ID)));
        assertElementPresent(Locator.linkWithText(insertedPtid));
    }

    @Test
    public void testShadowingSharedDataset()
    {
        final String datasetName = "Shadowing Dataset";

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

        assertTextPresent(String.format("A shared dataset is shadowed by this local dataset definition: %s.", SHARED_DEMOGRAPHICS));
        clickAndWait(Locator.linkWithText("Manage Datasets"));
        assertTextPresent("WARNING: One or more datasets in parent study are shadowed by datasets defined in this folder.");
        assertElementNotPresent(Locator.linkContainingText(SHARED_DEMOGRAPHICS));
    }

    @Test
    public void testSharedDatasetOverrides()
    {
        // verify the demographics dataset is visible in the project shared study
        beginAt(WebTestHelper.buildURL("study", getProjectName(), "datasets"));
        assertTextPresent(SHARED_DEMOGRAPHICS);

        // verify the demographics dataset in STUDY2 is also visible
        beginAt(WebTestHelper.buildURL("study", getProjectName() + "/" + STUDY2, "datasets"));
        assertTextPresent(SHARED_DEMOGRAPHICS, STUDY2_DATASET);

        // go to the dataset property editor
        clickAndWait(Locator.linkWithText("Manage Datasets"));
        clickAndWait(Locator.linkWithText("Change Properties"));

        // verify the shared dataset label is readonly while the local dataset is not
        Assert.assertEquals(null, getAttribute(Locator.xpath("//input[@type='text' and @value='" + STUDY2_DATASET + "']"), "readonly"));
        Assert.assertEquals("true", getAttribute(Locator.xpath("//input[@type='text' and @value='" + SHARED_DEMOGRAPHICS + "']"), "readonly"));

        // hide the demographics dataset in STUDY2
        click(Locator.checkboxById("dataset[" + SHARED_DEMOGRAPHICS_ID + "].visible"));
        clickAndWait(Locator.lkButton("Save"));

        // verify the dataset is hidden in the datasets webpart
        beginAt(WebTestHelper.buildURL("study", getProjectName() + "/" + STUDY2, "datasets"));
        assertTextNotPresent(SHARED_DEMOGRAPHICS);
        assertTextPresent(STUDY2_DATASET);

        // verify the dataset is hidden in the study navigator
        beginAt(WebTestHelper.buildURL("study", getProjectName() + "/" + STUDY2, "overview"));
        assertTextNotPresent(SHARED_DEMOGRAPHICS);
        assertTextPresent(STUDY2_DATASET);

        // verify the dataset is visible in the project datasets webpart
        beginAt(WebTestHelper.buildURL("study", getProjectName(), "datasets"));
        assertTextPresent(SHARED_DEMOGRAPHICS);

        // verify the dataset is visible in the project study navigator
        beginAt(WebTestHelper.buildURL("study", getProjectName(), "overview"));
        assertTextPresent(SHARED_DEMOGRAPHICS);

        // reset all dataset overrides
        beginAt(WebTestHelper.buildURL("study", getProjectName() + "/" + STUDY2, "datasetVisibility"));
        click(Locator.lkButton("Reset Overrides"));
        clickAndWait(Ext4Helper.Locators.ext4Button("Yes")); // reloads the current page

        // verify the dataset is visible in the datasets webpart
        beginAt(WebTestHelper.buildURL("study", getProjectName() + "/" + STUDY2, "datasets"));
        assertTextPresent(SHARED_DEMOGRAPHICS, STUDY2_DATASET);
    }

    @Test
    public void testDuplicateParticipantInsertDisallowed()
    {
        final String overlappingParticipant = "1001";

        beginAt(WebTestHelper.buildURL("study", getProjectName() + "/" + STUDY2, "dataset", Maps.of("datasetId", SHARED_DEMOGRAPHICS_ID)));
        assertElementNotPresent(Locator.linkWithText(overlappingParticipant));

        new DataRegionTable("Dataset", getDriver()).clickInsertNewRow();
        new DatasetInsertPage(this.getDriver(), SHARED_DEMOGRAPHICS).insert(Maps.of("PandaId", overlappingParticipant));

        assertElementPresent(Locators.labkeyError.containing("Duplicate: Panda = " + overlappingParticipant));
    }

    @Test @Ignore("Planned feature")
    public void testMultiStudyParticipantGroup()
    {
        final String mixed_group = "Mixed Group";
        final String[] ptids = {STUDY1_PTIDS[0], STUDY1_PTIDS[1], STUDY2_PTIDS[0], STUDY2_PTIDS[1]};

        _studyHelper.createCustomParticipantGroup(getProjectName(), getProjectName(), mixed_group, PARTICIPANT_NOUN_SINGULAR, ptids);

        clickTab("Overview");
        clickAndWait(Locator.linkWithText("1 dataset"));
        clickAndWait(Locator.linkWithText(SHARED_DEMOGRAPHICS));

        DataRegionTable dataset = new DataRegionTable("Dataset", this);
        dataset.clickHeaderMenu(PARTICIPANT_NOUN_SINGULAR + " Groups", mixed_group);
        Set<String> expectedPtids = new HashSet<>(Arrays.asList(ptids));
        Set<String> actualPtids = new HashSet<>(dataset.getColumnDataAsText("PandaId"));

        Assert.assertEquals("Wrong ptids for multi-study participant group", expectedPtids, actualPtids);
    }

    @Test
    public void testSharedDatasetSubfolderSecurity()
    {
        createUserWithPermissions(user, getProjectName(), "Reader");

        impersonate(user);
        {
            goToProjectHome();

            clickTab(PARTICIPANT_NOUN_PLURAL);
            assertElementNotPresent(Locator.css("ul.subjectlist"));
            assertElementNotPresent(Locator.css("li.ptid"));

            clickTab("Overview");
            clickAndWait(Locator.linkWithText("1 dataset"));
            clickAndWait(Locator.linkWithText(SHARED_DEMOGRAPHICS));

            DataRegionTable dataset = new DataRegionTable("Dataset", this);
            Assert.assertEquals("User can see participants from studies without read permission", Collections.emptyList(), dataset.getColumnDataAsText("PandaId"));
        }
        stopImpersonating();

        goToProjectHome();
        clickFolder(STUDY1);
        _permissionsHelper.enterPermissionsUI();
        _permissionsHelper.uncheckInheritedPermissions();
        _permissionsHelper.setUserPermissions(user, "Reader");
        _permissionsHelper.saveAndFinish();
        clickFolder(STUDY2);
        _permissionsHelper.enterPermissionsUI();
        _permissionsHelper.uncheckInheritedPermissions();
        _permissionsHelper.saveAndFinish();

        impersonate(user);
        {
            goToProjectHome();

            clickTab(PARTICIPANT_NOUN_PLURAL);
            ParticipantListWebPart pandaWebPart = new ParticipantListWebPart(this, PARTICIPANT_NOUN_SINGULAR, PARTICIPANT_NOUN_PLURAL);
            List<String> actualPtids = pandaWebPart.getParticipants();
            List<String> expectedPtids = Arrays.asList(STUDY1_PTIDS);
            Assert.assertEquals("Missing ptids in participant webpart with limited permissions", expectedPtids, actualPtids);

            clickTab("Overview");

            clickAndWait(Locator.linkWithText("1 dataset"));
            clickAndWait(Locator.linkWithText(SHARED_DEMOGRAPHICS));

            DataRegionTable dataset = new DataRegionTable("Dataset", this);
            expectedPtids = Arrays.asList(STUDY1_PTIDS);
            actualPtids = dataset.getColumnDataAsText("PandaId");
            Assert.assertEquals("User can see participants from studies without read permission", expectedPtids, actualPtids);
        }
        stopImpersonating();
    }

    @Test
    public void testParticipantWebpart()
    {
        goToProjectHome();
        clickTab(PARTICIPANT_NOUN_PLURAL);

        ParticipantListWebPart pandaWebPart = new ParticipantListWebPart(this, PARTICIPANT_NOUN_SINGULAR, PARTICIPANT_NOUN_PLURAL);
        List<String> ptids = pandaWebPart.getParticipants();
        List<String> missingPtids = new ArrayList<>(Arrays.asList(ArrayUtils.addAll(STUDY1_PTIDS, STUDY2_PTIDS)));
        missingPtids.removeAll(ptids);
        Assert.assertEquals("Missing ptids in project participant webpart", Collections.emptyList(), missingPtids);

        clickFolder(STUDY1);
        clickTab(PARTICIPANT_NOUN_PLURAL);

        pandaWebPart = new ParticipantListWebPart(this, PARTICIPANT_NOUN_SINGULAR, PARTICIPANT_NOUN_PLURAL);
        ptids = pandaWebPart.getParticipants();
        Assert.assertEquals("Wrong ptids in subfolder participant webpart", Arrays.asList(STUDY1_PTIDS), ptids);
    }

    private void createDataspaceProject(String projectName, String subjectNounSingular, String subjectNounPlural, String subjectColumnName,
                                        String timepointStyle, boolean sharedDatasets, boolean sharedVisits)
    {
        // The project of folder type Dataspace gets created with no sharing by default. To create shared datasets and shared visits
        // first create a Study with specified timepointStyle and sharing, then change the folder type from Study to Dataspace.
        _containerHelper.createProject(projectName, "Study");

        // Create a study with shared visits
        clickButton("Create Study");
        setFormElement(Locator.name("subjectNounSingular"), subjectNounSingular);
        setFormElement(Locator.name("subjectNounPlural"), subjectNounPlural);
        setFormElement(Locator.name("subjectColumnName"), subjectColumnName);
        checkRadioButton(Locator.radioButtonByNameAndValue("timepointType", timepointStyle));
        checkRadioButton(Locator.radioButtonByNameAndValue("shareDatasets", Boolean.toString(sharedDatasets)));
        checkRadioButton(Locator.radioButtonByNameAndValue("shareVisits", Boolean.toString(sharedVisits)));
        clickButton("Create Study");

        // Change the folder type from Study to Dataspace
        _containerHelper.setFolderType("Dataspace");
    }

    private boolean isDataspacePublishButtonShown(String projectName)
    {
        clickProject(projectName);
        goToManageStudy();
        return isElementPresent(Locator.lkButton("Publish Study"));
    }
}
