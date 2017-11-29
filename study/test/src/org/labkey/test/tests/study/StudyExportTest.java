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
import org.junit.rules.Timeout;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyC;
import org.labkey.test.categories.FileBrowser;
import org.labkey.test.components.PropertiesEditor;
import org.labkey.test.components.html.BootstrapMenu;
import org.labkey.test.components.html.Checkbox;
import org.labkey.test.components.html.Table;
import org.labkey.test.pages.EditDatasetDefinitionPage;
import org.labkey.test.pages.study.ManageVisitPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.StudyHelper;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@Category({DailyC.class, FileBrowser.class})
public class StudyExportTest extends StudyManualTest
{
    private static final String SPECIMEN_ARCHIVE_B = "/sampledata/study/specimens/sample_b.specimens";
    private static final String DEMOGRAPHICS_DATASET = "DEM-1: Demographics";
    private static final String TEST_ADD_ENTRY = "999000000";

    private final String DATASET_DATA_FILE = TestFileUtils.getLabKeyRoot() + "/sampledata/dataLoading/excel/dataset_data.xls";
    private static final String HIDDEN_DATASET = "URS-1: Screening Urinalysis";
    private static final String MODIFIED_DATASET = "Quality Control Report"; // Empty dataset.
    private static final String REORDERED_DATASET1 = "LLS-1: Screening Local Lab Results (Page 1)";
    private static final String REORDERED_DATASET2 = "LLS-2: Screening Local Lab Results (Page 2)";
    private static final String CATEGORY = "Test Category";
    private static final String DATE_FORMAT = "dd/mm hh:mma";
    private static final String NUMBER_FORMAT = "00.00";
    private static final String MODIFIED_PARTICIPANT = "999321033";
    protected static final String GROUP_2 = "Group 2"; // protected so that CohortStudyExportTest can use it
    private static final String COLUMN_DESC = "Test Column Description";
    private static final String MODIFIED_VISIT = "Cycle 2";

    @Override
    public Timeout testTimeout()
    {
        return new Timeout(40, TimeUnit.MINUTES);
    }

    @Override
    protected void doCreateSteps()
    {
        // manually create a study and load a specimen archive
        log("Creating study manually");
        createStudyManually();

        // import the specimens and wait for both datasets & specimens to load
        SpecimenImporter specimenImporter = new SpecimenImporter(new File(StudyHelper.getPipelinePath()), new File(TestFileUtils.getLabKeyRoot(), SPECIMEN_ARCHIVE_A), new File(TestFileUtils.getLabKeyRoot(), ARCHIVE_TEMP_DIR), getFolderName(), 2);
        specimenImporter.importAndWaitForComplete();

        // TODO: Call afterManualCreate()?
        setDemographicsDescription();
        createCustomAssays();
        setFormatStrings();
        doCohortCreateSteps();
        modifyVisits();
        changeDatasetOrder("16");
        setDatasetCategory(MODIFIED_DATASET, CATEGORY);
        hideDataset(HIDDEN_DATASET);
        modifyDatasetColumn(MODIFIED_DATASET);
        setDemographicsBit();

        _listHelper.importListArchive(getFolderName(), new File(TestFileUtils.getLabKeyRoot(), "remoteapi/r/test/listArchive.zip"));

        // export new study to zip file using "xml" formats
        exportStudy(true);

        // delete the study
        clickFolder(getFolderName());
        deleteStudy();

        log("Importing exported study (xml formats)");
        clickButton("Import Study");
        clickButton("Use Pipeline");
        _fileBrowserHelper.selectFileBrowserItem("export/");
        // select the first exported zip archive file by row
        Locator.XPathLocator gridRow = Locator.tag("tr").withClass("x4-grid-data-row").withAttributeContaining("data-recordid", "My Study_");
        waitForElement(gridRow);
        click(gridRow);
        _fileBrowserHelper.selectImportDataAction("Import Study");
        waitForText("Import Study from Pipeline");
        clickButton("Start Import");

        // wait for study & specimen load
        waitForPipelineJobsToComplete(3, "study and specimen archive import", false);
    }

    protected int getVisitCount()
    {
        return 67;
    }

    protected void doCohortCreateSteps()
    {
        setManualCohorts();
    }

    @Override
    protected void waitForSpecimenImport()
    {
        // specimen import is already complete
    }

    @Override
    protected void verifyStudyAndDatasets()
    {
        super.verifyStudyAndDatasets();

        clickFolder(getFolderName());

        // verify format strings
        goToFolderManagement();
        clickAndWait(Locator.linkWithText("Formats"));
        assertFormElementEquals(Locator.name("defaultDateFormat"), DATE_FORMAT);
        assertFormElementEquals(Locator.name("defaultNumberFormat"), NUMBER_FORMAT);

        clickFolder(getFolderName());

        // verify reordered, categorized, & hidden datasets.
        clickAndWait(Locator.linkWithText("47 datasets"));
        assertTextBefore(REORDERED_DATASET2, REORDERED_DATASET1);
        assertElementNotPresent(Locator.linkWithText(HIDDEN_DATASET));
        assertTextBefore(CATEGORY, MODIFIED_DATASET);

        // verify dataset category on dataset management page
        clickAndWait(Locator.linkWithText("Manage Datasets"));
        assertTextPresent(CATEGORY, 1);
        assertElementContains(Locator.xpath("//tr[./td/a[text() = '" + MODIFIED_DATASET + "']]/td[4]"), CATEGORY);

        // verify dataset columns
        clickAndWait(Locator.linkWithText(MODIFIED_DATASET));
        Table baseColumns = new Table(getDriver(), Locator.tagWithText("h4", "Base Columns")
                .followingSibling("table").waitForElement(getDriver(), WAIT_FOR_JAVASCRIPT));
        Checkbox mouseIdRequiredBox = new Checkbox(Locator.checkbox().findElement(
            baseColumns.getDataAsElement(baseColumns.getRowIndex("Name", "MouseId"), baseColumns.getColumnIndex("Required"))));
        assertTrue(mouseIdRequiredBox.isChecked());
        Checkbox seqNumRequiredBox = new Checkbox(Locator.checkbox().findElement(
                baseColumns.getDataAsElement(7, baseColumns.getColumnIndex("Required"))));
        assertTrue(seqNumRequiredBox.isChecked());

        Table userDefinedColumns = new Table(getDriver(), Locator.tagWithText("h4", "User Defined Columns")
                .followingSibling("table").waitForElement(getDriver(), WAIT_FOR_JAVASCRIPT));
        String desc = userDefinedColumns.getDataAsText(3, 7); //QCREP_ID, Description
        assertEquals("Description mismatch", COLUMN_DESC, desc);
        assertTextPresent(CATEGORY);

        // TODO: verify lookup

        // verify manual cohorts
        clickFolder(getFolderName());
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Cohorts"));
        assertRadioButtonSelected(Locator.radioButtonByNameAndValue("manualCohortAssignment", "true"));
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText("47 datasets"));
        clickAndWait(Locator.linkWithText(DEMOGRAPHICS_DATASET));
        BootstrapMenu.find(getDriver(),"Groups").clickSubMenu(true, "Cohorts", GROUP_2);
        BootstrapMenu.find(getDriver(),"QC State").clickSubMenu(true, "All data");
        assertTextPresent(MODIFIED_PARTICIPANT);

        // verify visit display order
        clickFolder(getFolderName());
        ManageVisitPage manageVisitPage = _studyHelper.goToManageVisits();
        assertTextBefore("Cycle 3", MODIFIED_VISIT);

        // verify visit modifications
        manageVisitPage.goToEditVisit(MODIFIED_VISIT);
        assertFormElementEquals(Locator.name("datasetStatus"), "OPTIONAL");
        assertOptionEquals(Locator.name("cohortId"), GROUP_2);
    }

    @Override
    protected void verifySpecimens()
    {
        super.verifySpecimens();

        // configure specimen tracking
        clickFolder(getFolderName());
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Request Statuses"));
        setFormElement(Locator.name("newLabel"), "New Request");
        clickButton("Save");
        setFormElement(Locator.name("newLabel"), "Pending Approval");
        clickButton("Save");
        setFormElement(Locator.name("newLabel"), "Complete");
        clickButton("Done");
        clickAndWait(Locator.linkWithText("Manage Actors and Groups"));
        setFormElement(Locator.name("newLabel"), "Institutional Review Board");
        selectOptionByText(Locator.name("newPerSite"), "Multiple Per Study (Location Affiliated)");
        clickButton("Save");
        setFormElement(Locator.name("newLabel"), "Scientific Leadership Group");
        selectOptionByText(Locator.name("newPerSite"), "One Per Study");
        clickButton("Save");
        clickAndWait(Locator.linkWithText("Update Members"));
        clickAndWait(Locator.linkWithText("FHCRC - Seattle (Endpoint Lab, Site Affiliated Lab, Clinic)"));
        assertTextPresent("Institutional Review Board, FHCRC - Seattle", "This group currently has no members.");
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Default Requirements"));
        selectOptionByText(Locator.name("providerActor"), "Institutional Review Board");
        setFormElement(Locator.id("providerDescription"), "To be deleted");
        clickButtonByIndex("Add Requirement", 1);
        assertTextPresent("To be deleted");
        clickAndWait(Locator.linkWithText("Delete"));
        assertTextNotPresent("To be deleted");
        selectOptionByText(Locator.name("providerActor"), "Institutional Review Board");
        setFormElement(Locator.id("providerDescription"), "Providing lab approval");
        clickButtonByIndex("Add Requirement", 1);
        selectOptionByText(Locator.name("receiverActor"), "Institutional Review Board");
        setFormElement(Locator.id("receiverDescription"), "Receiving lab approval");
        clickButtonByIndex("Add Requirement", 2);
        selectOptionByText(Locator.name("generalActor"), "Scientific Leadership Group");
        setFormElement(Locator.id("generalDescription"), "SLG Request Approval");
        clickButtonByIndex("Add Requirement", 3);
        clickTab("Manage");

        // create specimen request
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText("Study Navigator"));

        assertElementNotPresent(Locator.linkWithText("24"));
        selectOptionByText(Locator.name("QCState"), "All data");

        waitAndClickAndWait(Locator.linkWithText("24"));
        checkCheckbox(Locator.checkboxByName(".toggle"));
        clickButton("View Specimens");
        assertElementPresent(Locator.linkWithText("999320016"));
        assertElementPresent(Locator.linkWithText("999320518"));
        clickAndWait(Locator.linkWithText("Show individual vials"));
        assertElementPresent(Locator.linkWithText("999320016"));
        checkCheckbox(Locator.checkboxByName(".toggle"));
        BootstrapMenu.find(getDriver(), "Request Options").clickSubMenu(true, "Create New Request");
        assertTextPresent("HAQ0003Y-09", "BAQ00051-09");
        assertTextNotPresent("KAQ0003Q-01");
        selectOptionByText(Locator.name("destinationLocation"), "Duke University (Repository, Site Affiliated Lab, Clinic)");
        setFormElements("textarea", "inputs", new String[]{"An Assay Plan", "Duke University, NC", "My comments"});
        clickButton("Create and View Details");

        assertTextPresent("This request has not been submitted");
        assertButtonPresent("Cancel Request");
        assertButtonPresent("Submit Request");
        clickAndWait(Locator.linkWithText("Specimen Requests"));

        assertButtonPresent("Submit");
        assertButtonPresent("Cancel");
        assertButtonPresent("Details");
        assertTextPresent("Not Yet Submitted");
        doAndWaitForPageToLoad(() -> {
                    clickButton("Submit", 0);
                    acceptAlert(); // TODO: add check for expected alert text
                },
                WAIT_FOR_PAGE);
        waitAndClickAndWait(Locator.linkWithText("Specimen Requests"));
        assertButtonNotPresent("Submit");
        assertButtonPresent("Details");
        assertTextPresent("New Request");

        // test auto-fill:
        clickButton("Create New Request");
        assertNotEquals(getFormElement(Locator.id("input1")), "Duke University, NC");
        selectOptionByText(Locator.name("destinationLocation"), "Duke University (Repository, Site Affiliated Lab, Clinic)");
        assertEquals(getFormElement(Locator.id("input1")), "Duke University, NC");
        clickButton("Cancel");

        // manage new request
        clickButton("Details");
        assertTextNotPresent("Complete", "WARNING: Missing Specimens");
        assertTextPresent("New Request");
        assertTextNotPresent("Pending Approval");
        clickAndWait(Locator.linkWithText("Update Request"));
        selectOptionByText(Locator.name("status"), "Pending Approval");
        setFormElement(Locator.name("comments"), "Request is now pending.");
        clickButton("Save Changes and Send Notifications");
        assertTextNotPresent("New Request");
        assertTextPresent("Pending Approval");
        clickAndWait(Locator.linkWithText("Details"));
        assertTextPresent("Duke University", "Providing lab approval");
        checkCheckbox(Locator.checkboxByName("complete"));
        setFormElement(Locator.name("comment"), "Approval granted.");
        setFormElement(Locator.name("formFiles[0]"), VISIT_MAP);
        log("File upload skipped.");
        clickButton("Save Changes and Send Notifications");
        assertTextPresent("Complete");

        clickAndWait(Locator.linkWithText("Details").index(1));
        clickButton("Delete Requirement");
        assertTextNotPresent("Receiving lab approval");

        clickAndWait(Locator.linkWithText("Originating Location Specimen Lists"));
        assertTextPresent("WARNING: The requirements for this request are incomplete",
                "KCMC, Moshi, Tanzania");
        clickButton("Cancel");

        clickAndWait(Locator.linkWithText("View History"));
        assertTextPresent("Request is now pending.",
                "Approval granted.",
                "Institutional Review Board (Duke University), Receiving lab approval",
                VISIT_MAP.getName());

        navigateToFolder(getProjectName(), getFolderName());
        enterStudySecurity();

        // enable advanced study security
        selectOptionByValue(Locator.name("securityString"), "ADVANCED_READ");
        clickAndWait(Locator.lkButton("Update Type"));

        waitForElements(Locator.tagWithName("div", "webpart"), 3);

        click(Locator.xpath("//td[.='Users']/..//input[@value='READ']"));
        clickAndWait(Locator.id("groupUpdateButton"));

        // set the QC state 
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText("47 datasets"));
        clickAndWait(Locator.linkWithText(DEMOGRAPHICS_DATASET));
        BootstrapMenu.find(getDriver(), "QC State").clickSubMenu(true, "All data");
        new DataRegionTable("Dataset", this).checkAll();
        BootstrapMenu.find(getDriver(), "QC State").clickSubMenu(true, "Update state of selected rows");
        selectOptionByText(Locator.name("newState"), "clean");
        setFormElement(Locator.name("comments"), "This data is clean.");
        clickButton("Update Status");
        BootstrapMenu.find(getDriver(), "QC State").clickSubMenu(true, "clean");

        // test specimen comments
        clickFolder(getFolderName());
        waitAndClick(Locator.linkWithText("Vials by Derivative Type"));
        waitForText("Plasma, Unknown Processing");
        clickAndWait(Locator.linkWithText("Plasma, Unknown Processing"));
        clickButton("Enable Comments/QC");
        new DataRegionTable("SpecimenDetail", this).checkAll();
        BootstrapMenu.find(getDriver(), "Comments and QC").clickSubMenu(true, "Set Vial Comment or QC State for Selected");
        setFormElement(Locator.name("comments"), "These vials are very important.");
        clickButton("Save Changes");
        assertTextPresent("These vials are very important.", 25);
        DataRegionTable specimenDetail = new DataRegionTable("SpecimenDetail", this);
        specimenDetail.setFilter("MouseId", "Equals", "999320824");
        specimenDetail.checkAll();

        doAndWaitForPageToLoad(() -> {
                    BootstrapMenu.find(getDriver(), "Comments and QC").clickSubMenu(false, "Clear Vial Comments for Selected");
                    acceptAlert(); // TODO: add check for expected alert text
                },
                WAIT_FOR_PAGE);

        assertTextNotPresent("These vials are very important.");
        new DataRegionTable("SpecimenDetail", this).clearFilter("MouseId");
        assertTextPresent("These vials are very important.", 23);
        BootstrapMenu.find(getDriver(), "Comments and QC").clickSubMenu(true, "Exit Comments and QC mode");

        // import second archive, verify that that data is merged:
        SpecimenImporter importer = new SpecimenImporter(new File(StudyHelper.getPipelinePath()), new File(TestFileUtils.getLabKeyRoot(), SPECIMEN_ARCHIVE_B), new File(TestFileUtils.getLabKeyRoot(), ARCHIVE_TEMP_DIR), getFolderName(), 4);
        importer.importAndWaitForComplete();

        // verify that comments remain after second specimen load
        clickFolder(getFolderName());
        waitAndClick(Locator.linkWithText("Vials by Derivative Type"));
        waitForText("Plasma, Unknown Processing");
        clickAndWait(Locator.linkWithText("Plasma, Unknown Processing"));
        assertTextPresent("These vials are very important.", 2);

        // check to see that data in the specimen archive was merged correctly:
        clickFolder(getFolderName());
        clickAndWait(Locator.linkContainingText("By Individual Vial"));
        specimenDetail.getPagingWidget().setPageSize(250, true);
        specimenDetail.getPagingWidget().clickNextPage();
        assertTextPresent("DRT000XX-01");
        clickAndWait(Locator.linkWithText("Search"));
        waitForTextToDisappear("Loading");
        waitForText("Additive Type");
        _ext4Helper.selectRadioButton("Search Type:", "Grouped Vials");

//        WARNING: Using getFormElementNameByTableCaption() is dangerous... if muliple values are returned their
//        order is unpredictable, since they come back in keyset order.  The code below breaks under Java 6.
//
//        String[] globalUniqueIDCompareElems = getFormElementNameByTableCaption("Specimen Number", 0, 1);
//        String[] globalUniqueIDValueElems = getFormElementNameByTableCaption("Specimen Number", 0, 2);
//        String[] participantIDFormElems = getFormElementNameByTableCaption("Participant Id", 0, 1);
//        setFormElement(globalUniqueIDCompareElems[1], "CONTAINS");
//        setFormElement(globalUniqueIDValueElems[0], "1416");
//        setFormElement(participantIDFormElems[2], "999320528");

        _ext4Helper.selectComboBoxItem(Ext4Helper.Locators.formItemWithLabel("Mouse:"), Ext4Helper.TextMatchTechnique.CONTAINS, "999320528");
        _ext4Helper.selectComboBoxItem(Ext4Helper.Locators.formItemWithLabel("Visit:"), Ext4Helper.TextMatchTechnique.CONTAINS, "Enroll/Vacc #1 (201)");

        clickButton("Search");
        assertTextPresent("999320528");
        clickAndWait(Locator.linkWithText("Show individual vials"));
        // if our search worked, we'll only have six vials:
        assertTextPresent("[history]", 6);
        assertElementPresent(Locator.linkWithText("999320528"), 6);
        assertTextNotPresent("DRT000XX-01");
        clickAndWait(Locator.linkWithText("[history]"));
        assertTextPresent("GAA082NH-01",
                "BAD",
                "Added Comments",
                "Johannesburg, South Africa");

        clickFolder(getFolderName());
        waitAndClick(Locator.linkWithText("Specimen Requests"));
        clickAndWait(Locator.linkWithText("View Current Requests"));
        clickButton("Details");
        assertTextPresent("WARNING: Missing Specimens");
        doAndWaitForPageToLoad(() -> {
                    clickButton("Delete missing specimens", 0);
                    acceptAlert(); // TODO: add check for expected alert text
                },
                WAIT_FOR_PAGE);
        assertTextPresent("Duke University",
                "An Assay Plan",
                "Providing lab approval",
                "HAQ0003Y-09",
                "BAQ00051-09",
                "BAQ00051-11");
        assertTextNotPresent("BAQ00051-10",
                "WARNING: Missing Specimens");

        log("Test editing rows in a dataset");
        navigateToFolder(getProjectName(), getFolderName());

        enterStudySecurity();

        doAndWaitForPageToLoad(() -> {
                    selectOptionByValue(Locator.name("securityString"), "BASIC_WRITE");
                    click(Locator.lkButton("Update Type"));
                },
                WAIT_FOR_PAGE);

        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText("47 datasets"));
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));

        DataRegionTable.DataRegion(getDriver()).find().clickEditRow(0);
        setFormElement(Locator.name("quf_DEMbdt"), "2001-11-11");
        clickButton("Submit");
        BootstrapMenu.find(getDriver(), "QC State").clickSubMenu(true, "unknown QC");
        assertTextPresent("2001-11-11");

        log("Test adding a row to a dataset");
        DataRegionTable.findDataRegion(this).clickInsertNewRow();
        clickButton("Submit");
        assertTextPresent("This field is required");
        setFormElement(Locator.name("quf_MouseId"), TEST_ADD_ENTRY);
        setFormElement(Locator.name("quf_SequenceNum"), "123");
        setFormElement(Locator.name("quf_DEMdt"), "1/1/2018");
        setFormElement(Locator.name("quf_DEMbdt"), "1/1/1980");
        setFormElement(Locator.name("quf_DEMsex"), "Male");
        setFormElement(Locator.name("quf_DEMhisp"), "no");
        setFormElement(Locator.name("quf_DEMnatam"), "no");
        setFormElement(Locator.name("quf_DEMasian"), "no");
        setFormElement(Locator.name("quf_DEMblack"), "no");
        setFormElement(Locator.name("quf_DEMhawpi"), "no");
        setFormElement(Locator.name("quf_DEMwhite"), "no");
        setFormElement(Locator.name("quf_DEMraco"), "Martian");
        setFormElement(Locator.name("quf_DEMracox"), "Stranger, in a strange land");
        setFormElement(Locator.name("quf_DEMsexor"), "Yes");
        setFormElement(Locator.name("quf_formlang"), "Galactic");
        setFormElement(Locator.name("quf_sfdt_001"), "JH");
        clickButton("Submit");
        BootstrapMenu.find(getDriver(), "QC State").clickSubMenu(true, "All data");
        assertTextPresent(TEST_ADD_ENTRY);

        // Make sure that we can view its participant page immediately
        pushLocation();
        clickAndWait(Locator.linkWithText(TEST_ADD_ENTRY));
        assertTextPresent("Mouse - " + TEST_ADD_ENTRY,
                "DEM-1: Demographics");
        popLocation();

        log("Test deleting rows in a dataset");
        checkCheckbox(Locator.xpath("//input[contains(@value, '999320529')]"));
        doAndWaitForPageToLoad(() -> {
                    DataRegionTable.DataRegion(getDriver()).find().clickHeaderButton("Delete");
                    acceptAlert(); // TODO: add check for expected alert text
                },
                WAIT_FOR_PAGE);
        assertTextNotPresent("999320529");

        // configure QC state management to show all data by default so the next steps don't have to keep changing the state:
        clickFolder(getFolderName());
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Dataset QC States"));
        selectOptionByText(Locator.name("showPrivateDataByDefault"), "All data");
        clickButton("Save");

        // Test creating and importing a dataset from an excel file
        doTestDatasetImport();
    }

    protected boolean comparePaths(String path1, String path2)
    {
        String[] parseWith = { "/", "\\\\" };
        for (String parser1 : parseWith)
        {
            String[] path1Split = path1.split(parser1);
            for  (String parser2 : parseWith)
            {
                String[] path2Split = path2.split(parser2);
                if (path1Split.length == path2Split.length)
                {
                    int index = 0;
                    while (path1Split[index].compareTo(path2Split[index]) == 0)
                    {
                        index++;
                        if (index > path2Split.length - 1)
                            return true;
                    }
                }
            }
        }
        return false;
    }

    private void changeDatasetOrder(String value)
    {
        clickFolder(getFolderName());
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Datasets"));
        clickAndWait(Locator.linkWithText("Change Display Order"));
        selectOptionByValue(Locator.name("items"), value);
        clickButton("Move Down", 0);
        clickButton("Save");
    }

    protected void hideDataset(String dataset)
    {
        clickFolder(getFolderName());
        setVisibleBit(dataset, false);
    }

    protected void setDatasetCategory(String dataset, String category)
    {
        clickFolder(getFolderName());
        _studyHelper.goToManageDatasets()
                .selectDatasetByLabel(dataset)
                .clickEditDefinition()
                .setCategory(category)
                .save();
    }

    private void modifyVisits()
    {
        hideSceeningVisit();
        _studyHelper.goToManageVisits().goToChangeVisitOrder();
        checkCheckbox(Locator.checkboxByName("explicitDisplayOrder"));
        selectOptionByText(Locator.name("displayOrderItems"), MODIFIED_VISIT);
        clickButton("Move Down", 0);
        clickButton("Save");

        ManageVisitPage manageVisitPage = new ManageVisitPage(getDriver());
        manageVisitPage.goToEditVisit(MODIFIED_VISIT);
        selectOption("datasetStatus", 0, "OPTIONAL");
        selectOptionByText(Locator.name("cohortId"), GROUP_2);
        clickButton("Save");
        
    }

    private void modifyDatasetColumn(String dataset)
    {
        EditDatasetDefinitionPage editDatasetPage = _studyHelper.goToManageDatasets()
                .selectDatasetByLabel(dataset)
                .clickEditDefinition();

        PropertiesEditor.FieldRow fieldRow = editDatasetPage.getFieldsEditor().selectField(0);
        fieldRow.properties().selectDisplayTab().description.set(COLUMN_DESC);
        fieldRow.properties().selectAdvancedTab().mvEnabledCheckbox.check();

        editDatasetPage.save();
        // TODO: add lookups for current & other folders
    }

    private void setFormatStrings()
    {
        clickFolder(getFolderName());
        goToFolderManagement();
        clickAndWait(Locator.linkWithText("Formats"));
        setFormElement(Locator.name("defaultDateFormat"), DATE_FORMAT);
        setFormElement(Locator.name("defaultNumberFormat"), NUMBER_FORMAT);
        clickButton("Save");
    }

    private void setManualCohorts()
    {
        clickFolder(getFolderName());
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Cohorts"));
        click(Locator.radioButtonById("manualCohortAssignmentEnabled"));
        waitForText("Mouse-Cohort Assignments");
        setParticipantCohort(MODIFIED_PARTICIPANT, GROUP_2);
        clickButton("Save");
    }

    private void setParticipantCohort(String ptid, String cohort)
    {
        selectOptionByText(Locator.xpath("//tr[./td = '" + ptid + "']//select"), cohort);
    }

    protected void doTestDatasetImport()
    {
        navigateToFolder(getProjectName(), getFolderName());
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Datasets"));
        clickAndWait(Locator.linkWithText("Create New Dataset"));
        setFormElement(Locator.name("typeName"), "fileImportDataset");
        click(Locator.checkboxByName("fileImport"));
        clickButton("Next");

        waitForElement(Locator.xpath("//input[@name='uploadFormElement']"), WAIT_FOR_JAVASCRIPT);

        File datasetFile = new File(DATASET_DATA_FILE);
        setFormElement(Locator.name("uploadFormElement"), datasetFile);

        waitForElement(Locator.xpath("//span[@id='button_Import']"), WAIT_FOR_JAVASCRIPT);

        Locator.XPathLocator mouseId = Locator.xpath("//label[contains(@class, 'x-form-item-label') and text() ='MouseId:']/../div/div");
        _extHelper.selectGWTComboBoxItem(mouseId, "name");
        Locator.XPathLocator sequenceNum = Locator.xpath("//label[contains(@class, 'x-form-item-label') and text() ='Sequence Num:']/../div/div");
        _extHelper.selectGWTComboBoxItem(sequenceNum, "visit number");

        clickButton("Import", defaultWaitForPage);
        waitForElement(Locator.paginationText(9));
        assertTextPresent("kevin", "chimpanzee");
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);

        TestFileUtils.deleteDir(new File(StudyHelper.getPipelinePath() + "export"));
    }
}
