/*
 * Copyright (c) 2013-2016 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.ContainerFilter;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.remoteapi.query.Sort;
import org.labkey.test.Locator;
import org.labkey.test.Locators;
import org.labkey.test.SortDirection;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.Specimen;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.ChartHelper;
import org.labkey.test.util.DataRegionExportHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.ListHelper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PasswordUtil;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.SearchHelper;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.labkey.test.util.PasswordUtil.getUsername;

@Category({Specimen.class, DailyB.class})
public class StudyTest extends StudyBaseTest
{
    private String datasetLink = datasetCount + " datasets";
    protected static final String DEMOGRAPHICS_DESCRIPTION = "This is the demographics dataset, dammit. Here are some \u2018special symbols\u2019 - they help test that we're roundtripping in UTF-8.";
    protected static final String DEMOGRAPHICS_TITLE = "DEM-1: Demographics";

    protected String _tsv = "participantid\tsequencenum\tvisitdate\tSampleId\tDateField\tNumberField\tTextField\treplace\taliasedColumn\n" +
        "999321234\t1\t1/1/2006\t1234_A\t2/1/2006\t1.2\ttext\t\taliasedData\n" +
        "999321234\t1\t1/1/2006\t1234_B\t2/1/2006\t1.2\ttext\t\taliasedData\n";

    // specimen comment constants
    private static final String PARTICIPANT_CMT_DATASET = "Mouse Comments";
    private static final String PARTICIPANT_VISIT_CMT_DATASET = "Mouse Visit Comments";
    private static final String COMMENT_FIELD_NAME = "comment";
    private static final String PARTICIPANT_COMMENT_LABEL = "mouse comment";
    private static final String PARTICIPANT_VISIT_COMMENT_LABEL = "mouse visit comment";

    protected static final String VISIT_IMPORT_MAPPING = "Name\tSequenceNum\n" +
        "Cycle 10\t10\n" +
        "Vaccine 1\t201\n" +
        "Vaccination 1\t201\n" +
        "Soc Imp Log #%{S.3.2}\t5500\n" +
        "ConMeds Log #%{S.3.2}\t9002\n" +
        "All Done\t9999";

    public static final String APPEARS_AFTER_PICKER_LOAD = "Add Selected";


    //lists created in participant picker tests must be cleaned up afterwards
    LinkedList<String> persistingLists = new LinkedList<>();
    private String authorUser = "author1_study@study.test";
    private String specimenUrl = null;

    private final PortalHelper portalHelper = new PortalHelper(this);

    protected void setDatasetLink(int datasetCount)
    {
        datasetLink =  datasetCount + " datasets";
    }

    protected boolean isManualTest = false;

    protected void triggerManualTest()
    {
        setDatasetLink(47);
        isManualTest = true;
    }

    protected File[] getTestFiles()
    {
        return new File[]{new File(TestFileUtils.getLabKeyRoot() + "/server/test/data/api/study-api.xml")};
    }

    protected boolean isQuickTest()
    {
        return false;
    }

    protected void doCreateSteps()
    {
        pauseSearchCrawler(); //necessary for the alternate ID testing
        enableEmailRecorder();

        importStudy();
        startSpecimenImport(2);

        waitForPipelineJobsToComplete(2, "study import", false);
    }

    protected void doCleanup(boolean afterTest) throws TestTimeoutException //child class cleanup method throws Exception
    {
        super.doCleanup(afterTest);
        deleteUsers(false, authorUser); // Subclasses may not have created this user
        unpauseSearchCrawler();
    }

    protected void emptyParticipantPickerList()
    {
        goToManageParticipantClassificationPage(PROJECT_NAME, STUDY_NAME, SUBJECT_NOUN);
        while(persistingLists.size()!=0)
        {
            deleteListTest(persistingLists.pop());
        }
    }

    protected void doVerifySteps()
    {
        manageSubjectClassificationTest();
        emptyParticipantPickerList(); // Delete participant lists to avoid interfering with api test.
        verifyStudyAndDatasets();

        if (!isQuickTest())
        {
            waitForSpecimenImport();
            verifySpecimens();
            verifyParticipantComments();
            verifyParticipantReports();
            verifyPermissionsRestrictions();
            verifyDeleteUnusedVisits();
        }
    }

    /*
     * verifyAliasReplacement inserts a new entry into the Quality Control Report dataset
     * and verifies that the inserted Id was changed by the alias to its new value.
     */
    @LogMethod
    private void verifyAliasReplacement()
    {
        goToManageStudy();
        waitAndClickAndWait(Locator.linkWithText("Manage Datasets"));
        waitAndClickAndWait(Locator.linkWithText("Quality Control Report"));
        waitAndClickAndWait(Locator.linkWithText("View Data"));
        // Add a new row to the dataset
        waitAndClickAndWait(Locator.linkWithText("Insert New"));
        setFormElement(Locator.name("quf_MouseId"), "888208905");
        setFormElement(Locator.name("quf_SequenceNum"), "1");
        setFormElement(Locator.name("quf_QCREP_ID"), "42");
        waitAndClickAndWait(Locator.linkWithText("Submit"));
        // verify that the row was inserted and that the alias was converted
        assertTextPresent("999320016");
    }

    @LogMethod
    private void verifyPermissionsRestrictions()
    {
        clickProject(getProjectName());
        createUser(authorUser, null, true);
        _permissionsHelper.setUserPermissions(authorUser, "Author");
        impersonate(authorUser);
        beginAt(specimenUrl);
        clickButton("Request Options", 0);
        assertElementNotPresent(Locator.tagWithText("span", "Create New Request"));
        stopImpersonating();
    }

    @LogMethod
    private void verifyParticipantReports()
    {
        clickFolder(getFolderName());
        portalHelper.addWebPart("Study Data Tools");
        clickAndWait(Locator.linkWithImage("/labkey/study/tools/participant_report.png"));
        clickButton("Choose Measures", 0);
        _extHelper.waitForExtDialog("Add Measure");
        _extHelper.waitForLoadingMaskToDisappear(WAIT_FOR_JAVASCRIPT);

        String textToFilter = "AE-1:(VTN) AE Log";
        Locator measureRow = Locator.tagWithText("div", textToFilter);
        waitForElement(measureRow, WAIT_FOR_JAVASCRIPT * 2);

        assertElementPresent(measureRow, 27);
        assertElementPresent(Locator.tagContainingText("div", "Abbrevi"), 79);
        log("filter participant results down");
        Locator filterSearchText = Locator.xpath("//input[@name='filterSearch']");
        setFormElement(filterSearchText, "a");
        setFormElement(filterSearchText, "abbrev");
        setFormElement(Locator.xpath("//input[@type='text']"), "abbrevi");
        fireEvent(filterSearchText, SeleniumEvent.change);
        sleep(1000);

        assertTextPresent("Abbrevi", 79);
        assertTextNotPresent(textToFilter);

        log("select some records and include them in a report");
        _ext4Helper.selectGridItem(null, null, 4, "measuresGridPanel", true);
        _ext4Helper.selectGridItem(null, null, 40, "measuresGridPanel", true);
        _ext4Helper.selectGridItem(null, null, 20, "measuresGridPanel", true);
        clickButton("Select", 0);
        _extHelper.waitForExt3MaskToDisappear(WAIT_FOR_JAVASCRIPT);

        log("Verify report page looks as expected");
        String reportName = "MouseFooReport";
        String reportDescription = "Desc";
        _extHelper.setExtFormElementByLabel("Report Name", reportName);
        _extHelper.setExtFormElementByLabel("Report Description", reportDescription);
        clickButton("Save", 0);
        waitForText(reportName);
        assertTextPresent(reportName, 1);
        _ext4Helper.waitForComponentNotDirty("participant-report-panel-1");
    }

    @LogMethod
    private void verifyDeleteUnusedVisits()
    {
        clickProject(getProjectName());
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText("Manage Study"));
        clickAndWait(Locator.linkWithText("Manage Visits"));

        assertEquals("Unexpected visit count before delete unused", getVisitCount(), getTableRowCount("visits"));
        clickAndWait(Locator.linkWithText("Delete Unused Visits"));
        assertTextAtPlaceInTable("Are you sure you want to delete the unused visits listed below?", "visitsToDelete", 1, 1);
        assertEquals("Unexpected unused visit count on confirmation page", getUnusedVisitCount(), getTableRowCount("visitsToDelete") - 2);
        clickAndWait(Locator.linkWithText("OK"));
        assertEquals("Unexpected visit count after delete unused", getVisitCount() - getUnusedVisitCount(), getTableRowCount("visits"));
    }

    protected int getVisitCount()
    {
        return 66;
    }

    protected int getUnusedVisitCount()
    {
        return 24;
    }

    protected static final String SUBJECT_NOUN = "Mouse";
    protected static final String SUBJECT_NOUN_PLURAL = "Mice";
    protected static final String PROJECT_NAME = "StudyVerifyProject";
    protected static final String STUDY_NAME = "My Study";
    protected static final String LABEL_FIELD = "groupLabel";
    protected static final String ID_FIELD = "participantIdentifiers";


    /**
     * This is a test of the participant picker/classification creation UI.
     */
    @LogMethod
    protected void manageSubjectClassificationTest()
    {
        if (!isQuickTest())
        {
            //verify/create the right data
            goToManageParticipantClassificationPage(PROJECT_NAME, STUDY_NAME, SUBJECT_NOUN);

            //issue 12487
            assertTextPresent("Manage " + SUBJECT_NOUN + " Groups");

            //nav trail check
            assertTextNotPresent("Manage Study > ");

            String allList = "all list12345";
            String filteredList = "Filtered list";

            cancelCreateClassificationList();

            List<String> pIDs = createListWithAddAll(allList, false);
            persistingLists.add(allList);

            refresh();
            editClassificationList(allList, pIDs);

            //Issue 12485
            createListWithAddAll(filteredList, true);
            persistingLists.add(filteredList);

            String changedList = changeListName(filteredList);
            persistingLists.add(changedList);
            persistingLists.remove(filteredList);
            deleteListTest(allList);
            persistingLists.remove(allList);

            attemptCreateExpectError("1", "does not exist in this study.", "bad List ");
            String id = pIDs.get(0);
            attemptCreateExpectError(id + ", " + id, "Duplicates are not allowed in a group", "Bad List 2");
        }

        // test creating a participant group directly from a data grid
        clickFolder(STUDY_NAME);
        waitAndClickAndWait(Locator.linkWithText(datasetLink));
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));

        // verify warn on no selection
        if(!isQuickTest())
        {
            //nav trail check
            clickAndWait(Locator.linkContainingText("999320016"));
            assertTextPresent("Dataset: DEM-1: Demographics, All Visits");
            clickAndWait(Locator.linkContainingText("Dataset:"));

            _extHelper.clickMenuButton(false, SUBJECT_NOUN + " Groups", "Create " + SUBJECT_NOUN + " Group", "From Selected " + SUBJECT_NOUN_PLURAL);
            _extHelper.waitForExtDialog("Selection Error");
            assertTextPresent("At least one " + SUBJECT_NOUN + " must be selected");
            clickButtonContainingText("OK", 0);
            _extHelper.waitForExt3MaskToDisappear(WAIT_FOR_JAVASCRIPT);

        }

        DataRegionTable table = new DataRegionTable("Dataset", this);
        for (int i=0; i < 5; i++)
            table.checkCheckbox(i);

        // verify the selected list of identifiers is passed to the participant group wizard
        String[] selectedIDs = new String[]{"999320016","999320518","999320529","999320541","999320533"};
        _extHelper.clickMenuButton(false, SUBJECT_NOUN + " Groups", "Create " + SUBJECT_NOUN + " Group", "From Selected " + SUBJECT_NOUN_PLURAL);
        _extHelper.waitForExtDialog("Define " + SUBJECT_NOUN + " Group");
        verifySubjectIDsInWizard(selectedIDs);

        // save the new group and use it
        setFormElement(Locator.name(LABEL_FIELD), "Participant Group from Grid");
        clickButtonContainingText("Save");

        if(!isQuickTest())
        {
            Locator menu = Locator.lkButton(SUBJECT_NOUN + " Groups");
            _extHelper.clickExtMenuButton(menu, "Participant Group from Grid");
            waitForElement(Locator.paginationText(selectedIDs.length));
            assertTextPresent(selectedIDs);
        }
    }

    private void verifySubjectIDsInWizard(String[] ids)
    {
        Locator textArea = Locator.xpath("//table[@id='participantIdentifiers']//textarea");
        waitForElement(textArea, WAIT_FOR_JAVASCRIPT);
        sleep(1000);
        String subjectIDs = getFormElement(textArea);
        Set<String> identifiers = new HashSet<>();

        for (String subjectId : subjectIDs.split(","))
            identifiers.add(subjectId.trim());

        // validate...
        if (!identifiers.containsAll(Arrays.asList(ids)))
            fail("The Participant Group wizard did not contain the subject IDs : [" + StringUtils.join(ids, ", ") + "]");
    }

    /** verify that we can change a list's name
     * pre-conditions: list with name listName exists
     * post-conditions: list now named lCHANGEstName
     * @return new name of list
     */
    @LogMethod
    private String changeListName(String listName)
    {
        String newListName = listName.substring(0, 1) + "CHANGE" + listName.substring(2);
        selectListName(listName);
        clickButtonContainingText("Edit Selected", APPEARS_AFTER_PICKER_LOAD);

        setFormElement(Locator.name(LABEL_FIELD), newListName);

        clickButtonContainingText("Save", 0);

        waitForTextToDisappear(listName, 2*defaultWaitForPage);
        assertTextPresent(newListName);
        return newListName;
    }

    /**
     * verify that we can delete a list and its name no longer appears in classification list
     * pre-conditions:  list listName exists
     * post-conditions:  list listName does not exist
     * @param listName list to delete
     */
    @LogMethod
    private void deleteListTest(String listName)
    {
        sleep(1000);
        selectListName(listName);

        clickButtonContainingText("Delete Selected", 0);

        //make sure we can change our minds

        _extHelper.waitForExtDialog("Delete Group");
        clickButtonContainingText("No", 0);
        _extHelper.waitForExt3MaskToDisappear(WAIT_FOR_JAVASCRIPT);
        assertTextPresent(listName);


        clickButtonContainingText("Delete Selected", 0);
        _extHelper.waitForExtDialog("Delete Group");
        clickButtonContainingText("Yes", 0);
        _extHelper.waitForExt3MaskToDisappear(WAIT_FOR_JAVASCRIPT);
        refresh();
        assertTextNotPresent(listName);

    }

    /** verify that attempting to create a list with the expected name and list of IDs causes
     * the error specified by expectedError
     *
     * @param ids IDs to enter in classification list
     * @param expectedError error message to expect
     * @param listName name to enter in classification label
     */
    @LogMethod
    private void attemptCreateExpectError(String ids, String expectedError, String listName)
    {
        startCreateParticipantGroup();

        setFormElement(Locator.name(LABEL_FIELD), listName);
        setFormElement(Locator.name(ID_FIELD), ids);
        clickButton("Save", 0);
        waitForElement(Ext4Helper.Locators.window("Error"));
        assertTextPresent(expectedError);
        clickButton("OK", 0);
        waitForElementToDisappear(Ext4Helper.Locators.mask().index(1));
        clickButton("Cancel", 0);
        waitForElementToDisappear(Ext4Helper.Locators.mask());
        assertTextNotPresent(listName);
    }

    /**
     * verify that an already created list contains the pIDs we expect it to and can be changed.
     * pre-conditions:  listName exists with the specified IDs
     * post-conditions:  listName exists, with the same IDs, minus the first one
     */
    @LogMethod
    private void editClassificationList(String listName, List<String> pIDs)
    {
        sleep(1000);
        selectListName(listName);

        clickButtonContainingText("Edit Selected", APPEARS_AFTER_PICKER_LOAD);
        List<String> newPids = Arrays.asList(getFormElement(Locator.name(ID_FIELD)).replaceAll(" +", "").split(","));
        assertEquals(new HashSet<>(pIDs), new HashSet<>(newPids));
        log("IDs present after opening list: " + newPids);

        //remove first element
        sleep(1000);
        waitForElement(Locator.xpath("//input[contains(@name, 'infoCombo')]"));
        newPids = pIDs.subList(1, pIDs.size());
        setFormElement(Locator.name(ID_FIELD), StringUtils.join(newPids, ","));
        log("edit list of IDs to: " + newPids);

        //save, close, reopen, verify change
        _extHelper.waitForExtDialog("Define Mouse Group");
        clickButtonContainingText("Save", 0);
        _extHelper.waitForExt3MaskToDisappear(WAIT_FOR_JAVASCRIPT);
        sleep(1000);
        waitForElement(Ext4Helper.Locators.ext4Button("Delete Selected"));
        selectListName(listName);
        clickButtonContainingText("Edit Selected", APPEARS_AFTER_PICKER_LOAD);

        sleep(1000);
        waitForElement(Locator.xpath("//input[contains(@name, 'infoCombo')]"));
        String pidsAfterEdit = getFormElement(Locator.name(ID_FIELD)).replaceAll(" +", "");
        log("pids after edit: " + pidsAfterEdit);

        Collections.sort(newPids);
        List<String> editedPids = Arrays.asList(pidsAfterEdit.split(","));
        Collections.sort(editedPids);
        assertEquals(newPids, editedPids);

        clickButtonContainingText("Cancel", 0);
    }

    // select the list name from the main classification page
    private void selectListName(String listName)
    {
        Locator report = Locator.tagContainingText("div", listName);

        // select the report and click the delete button
        waitAndClick(report);
    }

    /**
     * very basic test of ability to enter and exit clist creation screen
     *
     * pre-condition:  at participant classification main screen
     * post-condition:  no change
     */
    private void cancelCreateClassificationList()
    {
        startCreateParticipantGroup();
        clickButtonContainingText("Cancel", 0);
        _extHelper.waitForExt3MaskToDisappear(WAIT_FOR_JAVASCRIPT);
    }

    /**preconditions: at participant picker main page
     * post-conditions:  at screen for creating new PP list
     */
    private void startCreateParticipantGroup()
    {
        waitAndClick(Ext4Helper.Locators.ext4Button("Create"));
        _extHelper.waitForExtDialog("Define Mouse Group");
        waitForElement(Locator.css(".doneLoadingTestMarker"));
        String dataset = getFormElement(Locator.name("infoCombo"));
        if (dataset.length() > 0)
        {
            waitForElement(Locator.id("demoDataRegion"));
        }
    }


    /** create list using add all
     *
     * @param listName name of list to create
     * @param filtered should list be filtered?  If so, only participants with DEMasian=0 will be included
     * @return ids in new list
     */
    @LogMethod
    private List<String> createListWithAddAll(String listName, boolean filtered)
    {
        startCreateParticipantGroup();
        setFormElement(Locator.name(LABEL_FIELD), listName);
        DataRegionTable table = new DataRegionTable("demoDataRegion", this);

        if(filtered)
        {
            table.setFilter("DEMasian", "Equals", "0", 0);
            waitForElement(Locator.paginationText(21));
        }

        clickButtonContainingText("Add All", 0);

        List<String> idsInColumn = table.getColumnDataAsText("Mouse Id");
        List<String> idsInForm = Arrays.asList(getFormElement(Locator.name(ID_FIELD)).replaceAll(" +", "").split(","));
        assertIDListsMatch(idsInColumn, idsInForm);

        clickButtonContainingText("Save", 0);

        _extHelper.waitForLoadingMaskToDisappear(WAIT_FOR_JAVASCRIPT);
        waitForText(WAIT_FOR_JAVASCRIPT, listName);
        return idsInForm;
    }

    /**
     * Compare list of IDs extracted from a column to those entered in
     * the form.  They should be identical.
     */
    private void assertIDListsMatch(List<String> idsInColumn, List<String> idsInForm)
    {
        //assert same size
        assertEquals("Wrong participants selected", new HashSet<>(idsInColumn), new HashSet<>(idsInForm));
    }

    private void goToManageParticipantClassificationPage(String projectName, String studyName, String subjectNoun)
    {
        //else
        sleep(1000);
        goToManageStudyPage(projectName, studyName);
        clickAndWait(Locator.linkContainingText("Manage " + subjectNoun + " Groups"));
    }

    @LogMethod
    protected void verifyStudyAndDatasets()
    {
        goToProjectHome();
        verifyDemographics();
        verifyVisitMapPage();
        verifyManageDatasetsPage();

        if (isQuickTest())
        {
            verifyParticipantVisitDay();
            verifyAliasReplacement();
            return;
        }

        if (!isManualTest)
            verifyAlternateIDs();

        verifyHiddenVisits();
        verifyVisitImportMapping();
        verifyCohorts();

        // configure QC state management before importing duplicate data
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText("Manage Study"));
        clickAndWait(Locator.linkWithText("Manage Dataset QC States"));
        setFormElement(Locator.name("newLabel"), "unknown QC");
        setFormElement(Locator.name("newDescription"), "Unknown data is neither clean nor dirty.");
        click(Locator.checkboxById("dirty_public"));
        click(Locator.checkboxByName("newPublicData"));
        clickButton("Save");
        selectOptionByText(Locator.name("defaultDirectEntryQCState"), "unknown QC");
        selectOptionByText(Locator.name("showPrivateDataByDefault"), "Public data");
        clickButton("Save");

        // return to dataset import page
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText(datasetLink));
        clickAndWait(Locator.linkWithText("verifyAssay"));
        assertTextPresent("QC State");
        assertTextNotPresent("1234_B");
        _extHelper.clickMenuButton("QC State", "All data");
        clickButton("QC State", 0);
        assertTextPresent("unknown QC", "1234_B");

        // Issue 21234: Dataset import no longer merges rows during import
        clickButton("Import Data");
        _tsv = "mouseid\tsequencenum\tvisitdate\tSampleId\tDateField\tNumberField\tTextField\treplace\n" +
                "999321234\t1\t1/1/2006\t1234_A\t2/1/2006\t5000\tnew text\tTRUE\n" +
                "999321234\t1\t1/1/2006\t1234_B\t2/1/2006\t5000\tnew text\tTRUE\n";
        setFormElement(Locator.id("tsv3"), _tsv);
        _listHelper.submitImportTsv_error("Duplicate dataset row");

        // Update a row and check the QC flag is defaulted to the study default 'unknown QC'
        {
            // Verify current state
            clickAndWait(Locator.linkWithText("verifyAssay"));
            _extHelper.clickMenuButton("QC State", "All data");
            _customizeViewsHelper.openCustomizeViewPanel();
            _customizeViewsHelper.addCustomizeViewColumn("QCState", "QC State");
            _customizeViewsHelper.addCustomizeViewSort("SampleId", "Ascending");
            _customizeViewsHelper.applyCustomView();
            DataRegionTable table = new DataRegionTable("Dataset", this);
            assertEquals(Arrays.asList("1234_A", "1234_B"), table.getColumnDataAsText("SampleId"));
            assertEquals(Arrays.asList(" ", " "), table.getColumnDataAsText("QCState"));
            List<String> numberField = table.getColumnDataAsText("NumberField");
            List<String> textField = table.getColumnDataAsText("TextField");

            // Update the first row
            String newText = "more new text";
            clickAndWait(Locator.linkWithText("edit").index(0));
            setFormElement(Locator.input("quf_TextField"), newText);
            clickButton("Submit");
            List<String> updatedTextField = Arrays.asList(newText, textField.get(0));

            // Verify new state
            table = new DataRegionTable("Dataset", this);
            assertEquals(Arrays.asList("1234_A", "1234_B"), table.getColumnDataAsText("SampleId"));
            assertEquals(Arrays.asList("unknown QC?", " "), table.getColumnDataAsText("QCState"));
            assertEquals(numberField, table.getColumnDataAsText("NumberField"));
            assertEquals(updatedTextField, table.getColumnDataAsText("TextField"));
        }

        // Test Bad Field Names -- #13607
        clickButton("Manage Dataset");
        clickButton("Edit Definition");
        Locator.XPathLocator nameField = Locator.xpath("//input[starts-with(@name, 'ff_name')]");
        waitForElement(nameField);
        int newFieldIndex = getElementCount(nameField);
        _listHelper.addField("Dataset Fields", "Bad Name", "Bad Name", ListHelper.ListColumnType.String);
        clickButton("Save");
        clickButton("View Data");
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.addCustomizeViewColumn("Bad Name", "Bad Name");
        _customizeViewsHelper.applyCustomView();
        _extHelper.clickMenuButton("QC State", "All data");
        clickAndWait(Locator.linkWithText("edit").index(0));
        setFormElement(Locator.input("quf_Bad Name"), "Updatable Value");
        clickButton("Submit");
        assertTextPresent("Updatable Value");
        clickAndWait(Locator.linkWithText("edit").index(0));
        assertFormElementEquals(Locator.input("quf_Bad Name"), "Updatable Value");
        setFormElement(Locator.input("quf_Bad Name"), "Updatable Value11");
        clickButton("Submit");
        assertTextPresent("Updatable Value11");
    }

    private void verifyAlternateIDs()
    {
        clickFolder(STUDY_NAME);
        clickAndWait(Locator.linkWithText("Alt ID mapping"));
        waitForElement(Locator.tagContainingText("div", "Contains up to one row of Alt ID mapping data for each "));
        clickButton("Import Data");
        waitForElement(Locator.tagWithText("div", "This is the Alias Dataset. You do not need to include information for the date column."));

        //the crawler should be paused (this is done in create) to verify
        log("Verify searching for alternate ID returns participant page");
        SearchHelper searchHelper = new SearchHelper(this);
        searchHelper.searchFor("888208905");
        assertTextPresent("Study Study 001 -- Mouse 999320016");
        goBack();
        goBack();

        //TODO: edit an entry, search for that

        Map<String, String> nameAndValue = new HashMap<>();
        nameAndValue.put("Alt ID", "191919");
        (new ChartHelper(this)).editDrtRow(4, nameAndValue);
        searchHelper.searchFor("191919");
        // Issue 17203: Changes to study datasets not auto indexed
//        assertTextPresent("Study Study 001 -- Mouse 999320687");
    }

    @LogMethod
    protected void verifySpecimens()
    {
        clickFolder(getFolderName());
        portalHelper.addWebPart("Specimens");
        waitForText("Blood (Whole)");
        clickAndWait(Locator.linkWithText("Blood (Whole)"));
        specimenUrl = getCurrentRelativeURL();


        log("verify presence of \"create new request\" button");
        clickButton("Request Options", 0);
        assertElementPresent(Locator.tagWithText("span", "Create New Request"));
    }

    @LogMethod
    private void verifyParticipantComments()
    {
        log("creating the participant/visit comment dataset");
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText("Manage Study"));
        clickAndWait(Locator.linkWithText("Manage Datasets"));
        clickAndWait(Locator.linkWithText("Create New Dataset"));

        setFormElement(Locator.name("typeName"), PARTICIPANT_CMT_DATASET);
        clickButton("Next");
        waitForElement(Locator.xpath("//input[@id='DatasetDesignerName']"), WAIT_FOR_JAVASCRIPT);

        // set the demographic data checkbox
        checkCheckbox(Locator.xpath("//input[@name='demographicData']"));

        // add a comment field
        _listHelper.setColumnName(0, COMMENT_FIELD_NAME);
        _listHelper.setColumnLabel(0, PARTICIPANT_COMMENT_LABEL);
        _listHelper.setColumnType(0, ListHelper.ListColumnType.MultiLine);
        clickButton("Save");

        log("creating the participant/visit comment dataset");
        clickAndWait(Locator.linkWithText("Manage Datasets"));
        clickAndWait(Locator.linkWithText("Create New Dataset"));

        setFormElement(Locator.name("typeName"), PARTICIPANT_VISIT_CMT_DATASET);
        clickButton("Next");
        waitForElement(Locator.xpath("//input[@id='DatasetDesignerName']"), WAIT_FOR_JAVASCRIPT);

        // add a comment field
        _listHelper.setColumnName(0, COMMENT_FIELD_NAME);
        _listHelper.setColumnLabel(0, PARTICIPANT_VISIT_COMMENT_LABEL);
        _listHelper.setColumnType(0, ListHelper.ListColumnType.MultiLine);
        clickButton("Save");

        log("configure comments");
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Comments"));
        if (isTextPresent("Comments can only be configured for studies with editable datasets"))
        {
            log("configure editable datasets");
            clickTab("Manage");
            clickAndWait(Locator.linkWithText("Manage Security"));
            prepForPageLoad();
            selectOptionByText(Locator.name("securityString"), "Basic security with editable datasets");
            waitForPageToLoad();

            log("configure comments");
            clickFolder(getFolderName());
            clickTab("Manage");
            clickAndWait(Locator.linkWithText("Manage Comments"));
        }
        prepForPageLoad();
        selectOptionByText(Locator.name("participantCommentDatasetId"), PARTICIPANT_CMT_DATASET);
        waitForPageToLoad();
        selectOptionByText(Locator.name("participantCommentProperty"), PARTICIPANT_COMMENT_LABEL);

        prepForPageLoad();
        selectOptionByText(Locator.name("participantVisitCommentDatasetId"), PARTICIPANT_VISIT_CMT_DATASET);
        waitForPageToLoad();
        selectOptionByText(Locator.name("participantVisitCommentProperty"), PARTICIPANT_VISIT_COMMENT_LABEL);
        clickButton("Save");

        clickFolder(getFolderName());
        waitForText("Blood (Whole)");
        clickAndWait(Locator.linkWithText("Blood (Whole)"));
        clickButton("Enable Comments/QC");
        log("manage participant comments directly");
        _extHelper.clickMenuButton("Comments and QC", "Manage Mouse Comments");

        int datasetAuditEventCount = getDatasetAuditEventCount(); //inserting a new event should increase this by 1;
        clickButton("Insert New");
        setFormElement(Locator.name("quf_MouseId"), "999320812");
        setFormElement(Locator.name("quf_" + COMMENT_FIELD_NAME), "Mouse Comment");
        clickButton("Submit");
        //Issue 14894: Datasets no longer audit row insertion
        verifyAuditEventAdded(datasetAuditEventCount);

        clickFolder(getFolderName());
        waitForText("Blood (Whole)");
        clickAndWait(Locator.linkWithText("Blood (Whole)"));
        DataRegionTable specimenDetail = new DataRegionTable("SpecimenDetail", this);
        specimenDetail.setFilter("MouseId", "Equals", "999320812");

        waitForElement(Locator.tagContainingText("td", "Mouse Comment"));
        specimenDetail.clearAllFilters("MouseId");

        log("verify copying and moving vial comments");
        specimenDetail.setFilter("GlobalUniqueId", "Equals", "AAA07XK5-01");
        checkCheckbox(Locator.name(".toggle"));
        clickButton("Enable Comments/QC");
        _extHelper.clickMenuButton("Comments and QC", "Set Vial Comment or QC State for Selected");
        setFormElement(Locator.name("comments"), "Vial Comment");
        clickButton("Save Changes");

        checkCheckbox(Locator.name(".toggle"));
        _extHelper.clickMenuButton("Comments and QC", "Set Vial Comment or QC State for Selected");
        _extHelper.clickMenuButton("Copy or Move Comment(s)", "Copy", "To Mouse", "999320812");
        setFormElement(Locator.name("quf_" + COMMENT_FIELD_NAME), "Copied PTID Comment");
        clickButton("Submit");
        assertTextPresent("Copied PTID Comment");

        checkCheckbox(Locator.name(".toggle"));
        _extHelper.clickMenuButton("Comments and QC", "Set Vial Comment or QC State for Selected");
        prepForPageLoad();
        _extHelper.clickMenuButton(false, "Copy or Move Comment(s)", "Move", "To Mouse", "999320812");
        acceptAlert();
        waitForPageToLoad();
        setFormElement(Locator.name("quf_" + COMMENT_FIELD_NAME), "Moved PTID Comment");
        clickButton("Submit");
        assertElementPresent(Locator.tagContainingText("td", "Moved PTID Comment"));
        assertElementNotPresent(Locator.tagContainingText("td", "Mouse Comment"));
        assertElementNotPresent(Locator.tagContainingText("td", "Vial Comment"));
    }

    private void verifyAuditEventAdded(int previousCount)
    {
        log("Verify there is exactly one new DatasetAuditEvent, and it refers to the insertion of a new record");
        SelectRowsResponse selectResp = getDatasetAuditLog();
        List<Map<String,Object>> rows = selectResp.getRows();
        assertEquals("Unexpected size of datasetAuditEvent log", previousCount + 1, rows.size());
        log("Dataset audit log contents: " + rows);
        assertEquals("A new dataset record was inserted", rows.get(rows.size() - 1).get("Comment"));
    }

    private SelectRowsResponse getDatasetAuditLog()
    {
        SelectRowsCommand selectCmd = new SelectRowsCommand("auditLog", "DatasetAuditEvent");

        selectCmd.setMaxRows(-1);
        selectCmd.setContainerFilter(ContainerFilter.CurrentAndSubfolders);
        selectCmd.setColumns(Collections.singletonList("*"));
        selectCmd.setSorts(Collections.singletonList(new Sort("Date", Sort.Direction.ASCENDING)));
        Connection cn = new Connection(getBaseURL(), getUsername(), PasswordUtil.getPassword());
        SelectRowsResponse selectResp = null;
        try
        {
            selectResp = selectCmd.execute(cn,  "/" +  getProjectName());
        }
        catch (CommandException | IOException e)
        {
            throw new RuntimeException("Error when attempting to verify audit trail", e);
        }
        return selectResp;
    }

    private int getDatasetAuditEventCount()
    {
        SelectRowsResponse selectResp = getDatasetAuditLog();
        List<Map<String,Object>> rows = selectResp.getRows();
        return rows.size();
    }

    @LogMethod
    private void verifyDemographics()
    {
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText("Study Navigator"));
        clickAndWait(Locator.linkWithText("24"));
        assertTextPresent(DEMOGRAPHICS_DESCRIPTION, "Male", "African American or Black");
        clickAndWait(Locator.linkWithText("999320016"));
        waitAndClick(Locator.linkWithText("125: EVC-1: Enrollment Vaccination"));
        assertTextPresent("right deltoid");

        verifyDemoCustomizeOptions();
        verifyDatasetExport();
    }

    @LogMethod
    private void verifyDatasetExport()
    {
        pushLocation();
        DataRegionTable drt = new DataRegionTable("Dataset", this);
        DataRegionExportHelper exportHelper = new DataRegionExportHelper(drt);
        exportHelper.exportText();
        scrollIntoView(Locators.ADMIN_MENU);
        goToAuditLog();
        prepForPageLoad();
        selectOptionByText(Locator.name("view"), "Query export events");
        waitForPageToLoad();

        DataRegionTable auditTable =  new DataRegionTable("query", this);
        String[][] columnAndValues = new String[][] {{"Created By", getDisplayName()},
                {"Project", PROJECT_NAME}, {"Container", STUDY_NAME}, {"SchemaName", "study"},
                {"QueryName", "DEM-1"}, {"Comment", "Exported to TSV"}};
        for(String[] columnAndValue : columnAndValues)
        {
            log("Checking column: "+ columnAndValue[0]);
            assertEquals(columnAndValue[1], auditTable.getDataAsText(0, columnAndValue[0]));
        }
        clickAndWait(Locator.linkContainingText("details"));

        popLocation();
    }

    private void verifyDemoCustomizeOptions()
    {
        log("verify demographic data set not present");
        clickAndWait(Locator.linkContainingText(DEMOGRAPHICS_TITLE));
        _customizeViewsHelper.openCustomizeViewPanel();
        assertFalse(_customizeViewsHelper.isColumnPresent("MouseVisit/DEM-1"));
    }

    @LogMethod
    protected void verifyVisitMapPage()
    {
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText("Manage Study"));
        clickAndWait(Locator.linkWithText("Manage Visits"));

        // test optional/required/not associated
        clickAndWait(Locator.linkWithText("edit").index(1));
        selectOption("datasetStatus", 0, "NOT_ASSOCIATED");
        selectOption("datasetStatus", 1, "NOT_ASSOCIATED");
        selectOption("datasetStatus", 2, "NOT_ASSOCIATED");
        selectOption("datasetStatus", 3, "OPTIONAL");
        selectOption("datasetStatus", 4, "OPTIONAL");
        selectOption("datasetStatus", 5, "OPTIONAL");
        selectOption("datasetStatus", 6, "REQUIRED");
        selectOption("datasetStatus", 7, "REQUIRED");
        selectOption("datasetStatus", 8, "REQUIRED");
        clickButton("Save");
        clickAndWait(Locator.linkWithText("edit").index(1));
        selectOption("datasetStatus", 0, "NOT_ASSOCIATED");
        selectOption("datasetStatus", 1, "OPTIONAL");
        selectOption("datasetStatus", 2, "REQUIRED");
        selectOption("datasetStatus", 3, "NOT_ASSOCIATED");
        selectOption("datasetStatus", 4, "OPTIONAL");
        selectOption("datasetStatus", 5, "REQUIRED");
        selectOption("datasetStatus", 6, "NOT_ASSOCIATED");
        selectOption("datasetStatus", 7, "OPTIONAL");
        selectOption("datasetStatus", 8, "REQUIRED");
        clickButton("Save");
        clickAndWait(Locator.linkWithText("edit").index(1));
        assertSelectOption("datasetStatus", 0, "NOT_ASSOCIATED");
        assertSelectOption("datasetStatus", 1, "OPTIONAL");
        assertSelectOption("datasetStatus", 2, "REQUIRED");
        assertSelectOption("datasetStatus", 3, "NOT_ASSOCIATED");
        assertSelectOption("datasetStatus", 4, "OPTIONAL");
        assertSelectOption("datasetStatus", 5, "REQUIRED");
        assertSelectOption("datasetStatus", 6, "NOT_ASSOCIATED");
        assertSelectOption("datasetStatus", 7, "OPTIONAL");
        assertSelectOption("datasetStatus", 8, "REQUIRED");
    }

    @LogMethod
    protected void verifyManageDatasetsPage()
    {
        clickFolder(getFolderName());
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Datasets"));

        clickAndWait(Locator.linkWithText("489"));
        assertTextPresent("ESIdt", "Form Completion Date");
        assertTableCellTextEquals("details", 4, 1, "false");     // "Demographics Data" should be false

        // Verify that "Demographics Data" is checked and description is set
        clickAndWait(Locator.linkWithText("Manage Datasets"));
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));
        assertTableCellTextEquals("details", 4, 1, "true");
        assertTableCellTextEquals("details", 4, 3, DEMOGRAPHICS_DESCRIPTION);

        // "Demographics Data" bit needs to be false for the rest of the test
        setDemographicsBit("DEM-1: Demographics", false);

        log("verify ");
        clickButtonContainingText("View Data");
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.showHiddenItems();
        assertTrue("Could not find column \"MouseVisit/DEM-1\"", _customizeViewsHelper.isColumnPresent("MouseVisit/DEM-1"));
    }

    @LogMethod
    private void verifyHiddenVisits()
    {
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText("Study Navigator"));
        assertElementPresent(Locator.tag("td").withClass("labkey-column-header").withText("Pre-exist Cond"));
        assertElementNotPresent(Locator.tag("td").withClass("labkey-column-header").withText("Screening Cycle"));
        assertElementNotPresent(Locator.tag("td").withClass("labkey-column-header").withText("Cycle 1"));
        clickAndWait(Locator.linkWithText("Show All Datasets"));
        assertElementPresent(Locator.tag("td").withClass("labkey-column-header").withText("Screening Cycle"));
        assertElementPresent(Locator.tag("td").withClass("labkey-column-header").withText("Cycle 1"));
        assertElementPresent(Locator.tag("td").withClass("labkey-column-header").withText("Pre-exist Cond"));
    }

    @LogMethod
    private void verifyVisitImportMapping()
    {
        clickAndWait(Locator.linkWithText("Manage Study"));
        clickAndWait(Locator.linkWithText("Manage Visits"));
        clickAndWait(Locator.linkWithText("Visit Import Mapping"));
        assertTableRowsEqual("customMapping", 2, VISIT_IMPORT_MAPPING.replace("SequenceNum", "Sequence Number Mapping"));

        assertEquals("Incorrect number of gray cells", 60, countTableCells(null, true));
        assertEquals("Incorrect number of non-gray \"Int. Vis. %{S.1.1} .%{S.2.1}\" cells", 1, countTableCells("Int. Vis. %{S.1.1} .%{S.2.1}", false));
        assertEquals("Incorrect number of gray \"Int. Vis. %{S.1.1} .%{S.2.1}\" cells", 18, countTableCells("Int. Vis. %{S.1.1} .%{S.2.1}", true));
        assertEquals("Incorrect number of non-gray \"Soc Imp Log #%{S.3.2}\" cells", 1, countTableCells("Soc Imp Log #%{S.3.2}", false));
        assertEquals("Incorrect number of gray \"Soc Imp Log #%{S.3.2}\" cells", 1, countTableCells("Soc Imp Log #%{S.3.2}", true));
        assertEquals("Incorrect number of non-gray \"ConMeds Log #%{S.3.2}\" cells", 1, countTableCells("ConMeds Log #%{S.3.2}", false));
        assertEquals("Incorrect number of gray \"ConMeds Log #%{S.3.2}\" cells", 1, countTableCells("ConMeds Log #%{S.3.2}", true));

        // Replace custom visit mapping and verify
        String replaceMapping = "Name\tSequenceNum\nBarBar\t4839\nFoofoo\t9732";
        clickAndWait(Locator.linkWithText("Replace Custom Mapping"));
        setFormElement(Locator.id("tsv"), replaceMapping);
        clickButton("Submit");
        assertTableRowsEqual("customMapping", 2, replaceMapping.replace("SequenceNum", "Sequence Number Mapping"));
        assertTextNotPresent("Cycle 10", "All Done");

        assertEquals("Incorrect number of gray cells", 54, countTableCells(null, true));
        assertEquals("Incorrect number of non-gray \"Int. Vis. %{S.1.1} .%{S.2.1}\" cells", 1, countTableCells("Int. Vis. %{S.1.1} .%{S.2.1}", false));
        assertEquals("Incorrect number of gray \"Int. Vis. %{S.1.1} .%{S.2.1}\" cells", 18, countTableCells("Int. Vis. %{S.1.1} .%{S.2.1}", true));
        assertEquals("Incorrect number of non-gray \"Soc Imp Log #%{S.3.2}\" cells", 1, countTableCells("Soc Imp Log #%{S.3.2}", false));
        assertEquals("Incorrect number of gray \"Soc Imp Log #%{S.3.2}\" cells", 0, countTableCells("Soc Imp Log #%{S.3.2}", true));
        assertEquals("Incorrect number of non-gray \"ConMeds Log #%{S.3.2}\" cells", 1, countTableCells("ConMeds Log #%{S.3.2}", false));
        assertEquals("Incorrect number of gray \"ConMeds Log #%{S.3.2}\" cells", 0, countTableCells("ConMeds Log #%{S.3.2}", true));

        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText(datasetLink));
        clickAndWait(Locator.linkWithText("Types"));
        log("Verifying sequence numbers and visit names imported correctly");

        DataRegionTable table = new DataRegionTable("Dataset", this);
        List<String> visits = table.getColumnDataAsText("Visit");

        int enrollmentCount = 0;
        int screeningCount = 0;

        for (String visit : visits)
        {
            switch (visit)
            {
                case "Enroll/Vacc #1":
                    enrollmentCount++;
                    break;
                case "Screening":
                    screeningCount++;
                    break;
                default:
                    fail("Unexpected visit: " + visit);
                    break;
            }
        }

        assertEquals("Incorrect count for visit: 'Enroll/Vacc #1'", 24, enrollmentCount);
        assertEquals("Incorrect count for visit: 'Screening'", 24, screeningCount);
    }

    // Either param can be null
    private int countTableCells(String text, Boolean grayed)
    {
        List<String> parts = new LinkedList<>();

        if (null != text)
            parts.add("contains(text(), '" + text + "')");

        if (null != grayed)
        {
            if (grayed)
                parts.add("contains(@class, 'labkey-mv')");
            else
                parts.add("not(contains(@class, 'labkey-mv'))");
        }

        String path = "//td[" + StringUtils.join(parts, " and ") + "]";
        return getElementCount(Locator.xpath(path));
    }

    protected void verifyCohorts()
    {
        verifyCohorts(true);
    }

    @LogMethod
    protected void verifyCohorts(boolean altIDsEnabled)       //todo
    {
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText("Study Navigator"));
        clickAndWait(Locator.linkWithText("24"));

        // verify that cohorts are working
        assertTextPresent("999320016", "999320518");

        _extHelper.clickMenuButton("Mouse Groups", "Cohorts", "Group 1");
        assertTextPresent("999320016");
        assertTextNotPresent("999320518");

        _extHelper.clickMenuButton("Mouse Groups", "Cohorts", "Group 2");
        assertTextNotPresent("999320016");
        assertTextPresent("999320518");

        // verify that the participant view respects the cohort filter:
        DataRegionTable drt = new DataRegionTable("Dataset", this);
        drt.setSort("MouseId", SortDirection.ASC);
        clickAndWait(Locator.linkWithText("999320518"));
        if (!isManualTest)
            assertTextPresent("b: 888209407"); //Alternate ID
        click(Locator.linkWithText("125: EVC-1: Enrollment Vaccination"));
        assertElementNotPresent(Locator.tagContainingText("td", "Group 1"));
        assertElementPresent(Locator.tagContainingText("td", "Group 2"));
        clickAndWait(Locator.linkWithText("Next Mouse"));
        assertElementNotPresent(Locator.tagContainingText("td", "Group 1"));
        assertElementPresent(Locator.tagContainingText("td", "Group 2"));
        clickAndWait(Locator.linkWithText("Next Mouse"));
        assertElementNotPresent(Locator.tagContainingText("td", "Group 1"));
        assertElementPresent(Locator.tagContainingText("td", "Group 2"));
        clickAndWait(Locator.linkWithText("Next Mouse"));
    }

    @LogMethod
    protected void verifyParticipantVisitDay()
    {
        clickFolder(getFolderName());
        goToManageStudy();
        waitAndClickAndWait(Locator.linkWithText("Manage Datasets"));
        waitAndClickAndWait(Locator.linkWithText(DEMOGRAPHICS_TITLE));
        clickButton("Edit Definition");
        waitForElement(Locator.xpath("//input[@id='DatasetDesignerName']"), WAIT_FOR_JAVASCRIPT);
        _listHelper.addField("Dataset Fields", "VisitDay", "VisitDay", ListHelper.ListColumnType.Integer);
        Locator element3 = Locator.gwtListBoxByLabel("Visit Date Column");
        selectOptionByValue(element3, "DEMdt");
        clickButton("Save");
        clickButton("View Data");

        // Edit 1 item changing sequence from 101; then edit again and change back and set VisitDay to something
        clickAndWait(Locator.linkWithText("edit").index(0));
        setFormElement(Locator.input("quf_SequenceNum"), "100");
        clickButton("Submit");
        clickAndWait(Locator.linkWithText("edit").index(0));
        setFormElement(Locator.input("quf_SequenceNum"), "101");
        setFormElement(Locator.input("quf_VisitDay"), "102");
        clickButton("Submit");

        // Then check that item in MouseVisit table
        goToSchemaBrowser();
        viewQueryData("study", "MouseVisit");
        DataRegionTable table = new DataRegionTable("query", this);
        table.setFilter("Day", "Equals", "102");
        assertTextPresent("999320016", "Screening", "101.0", "2005-01-01", "102", "Group 1");
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }
}
