/*
 * Copyright (c) 2016-2026 LabKey Corporation
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

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.api.util.FileUtil;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.assay.AssayListCommand;
import org.labkey.remoteapi.assay.AssayListResponse;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.Assays;
import org.labkey.test.categories.Daily;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.components.DomainDesignerPage;
import org.labkey.test.components.assay.AssayConstants;
import org.labkey.test.components.domain.DomainFieldRow;
import org.labkey.test.components.domain.DomainFormPanel;
import org.labkey.test.pages.ReactAssayDesignerPage;
import org.labkey.test.pages.assay.AssayBeginPage;
import org.labkey.test.pages.assay.AssayImportPage;
import org.labkey.test.pages.assay.AssayRunsPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.params.FieldInfo;
import org.labkey.test.params.assay.GeneralAssayDesign;
import org.labkey.test.params.experiment.SampleTypeDefinition;
import org.labkey.test.tests.AbstractAssayTest;
import org.labkey.test.tests.AuditLogTest;
import org.labkey.test.util.AuditLogHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.DomainUtils;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.SampleTypeHelper;
import org.labkey.test.util.StudyHelper;
import org.labkey.test.util.TestDataGenerator;
import org.labkey.test.util.data.TestArrayDataUtils;
import org.labkey.test.util.data.TestDataUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.labkey.test.util.TestDataGenerator.randomTextChoice;
import static org.labkey.test.util.TestDataGenerator.shuffleSelect;
import static org.labkey.test.util.data.TestArrayDataUtils.formatMultiValueText;

@Category({Daily.class, Assays.class})
public class AssayTest extends AbstractAssayTest
{
    private static final String INVESTIGATOR = "Dr. No";
    private static final String GRANT = "SPECTRE";
    private static final String DESCRIPTION = "World Domination.";
    private static final String ISSUE_53625_ASSAY = TestDataGenerator.randomDomainName("Issue53625", DomainUtils.DomainKind.Assay);
    private static final String ISSUE_53625_PROJECT = "Issue53625Project";
    private static final String ISSUE_53616_ASSAY = "Issue53616Assay";
    private static final String ISSUE_53616_PROJECT = "Issue53616Project" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    private static final String ISSUE_53831_PROJECT = "Issue53831Project" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    private static final String GH_1023_PROJECT = "GH1023Project" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    private static final String SAMPLE_FIELD_TEST_ASSAY = "SampleFieldTestAssay";
    private static final String SAMPLE_FIELD_PROJECT_NAME = "Sample Field Test Project" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    private static final String MVTC_MULTI_FILE_IMPORT_ASSAY = TestDataGenerator.randomDomainName("MVTCMultiFileImportAssay", DomainUtils.DomainKind.Assay);
    private static final String MVTC_MULTI_FILE_IMPORT_PROJECT = "MVTCMultiFileImportAssay" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    private static final String COL_ASSAY_ID_LABEL = "Assay ID";
    private static final List<String> TEXT_MULTI_CHOICE_LIST = randomTextChoice(10);
    private static final FieldInfo COL_MULTITEXTCHOICE = FieldInfo.random("Multi Choice", FieldDefinition.ColumnType.MultiValueTextChoice)
            .customizeFieldDefinition(fd -> fd.setMultiChoiceValues(TEXT_MULTI_CHOICE_LIST));


    @Override
    protected String getProjectName()
    {
        return TEST_ASSAY_PRJ_SECURITY;
    }

    /**
     * Cleanup entry point.
     */
    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        //should also delete the groups
        _containerHelper.deleteProject(getProjectName(), false);
        _containerHelper.deleteProject(SAMPLE_FIELD_PROJECT_NAME, false);
        _containerHelper.deleteProject(ISSUE_53616_PROJECT, false);
        _containerHelper.deleteProject(ISSUE_53625_PROJECT, false);
        _containerHelper.deleteProject(ISSUE_53831_PROJECT, false);
        _containerHelper.deleteProject(GH_1023_PROJECT, false);
        _containerHelper.deleteProject(MVTC_MULTI_FILE_IMPORT_PROJECT, false);

        _userHelper.deleteUsers(false, TEST_ASSAY_USR_PI1, TEST_ASSAY_USR_TECH1);
    }

    // Issue 53831: Assay name max length check
    @Test
    public void testAssayNameMaxLength() throws Exception
    {
        _containerHelper.createProject(ISSUE_53831_PROJECT, "Assay");
        goToProjectHome(ISSUE_53831_PROJECT);
        ReactAssayDesignerPage assayDesignerPage = _assayHelper.createAssayDesign("General", "a" + "0123456789".repeat(15));
        List<String> errors = assayDesignerPage.clickSaveExpectingErrors();
        checker().verifyEquals("Wrong number of errors", 1, errors.size());
        checker().verifyEquals("Wrong error message: " + errors.getFirst(),
                "Value is too long for assay design name, a maximum length of 150 is allowed. The supplied value, 'a01234567890123456789012...78901234567890123456789', was 151 characters long.",
                errors.getFirst());
        assayDesignerPage.clickCancel();
    }

    @Test
    public void testAssayMultiFileImportForMVTC() throws Exception
    {
        _containerHelper.createProject(MVTC_MULTI_FILE_IMPORT_PROJECT, "Assay");
        new GeneralAssayDesign(MVTC_MULTI_FILE_IMPORT_ASSAY)
                .setRunFields(List.of(new FieldDefinition("runText", FieldDefinition.ColumnType.String)), true)
                .setDataFields(List.of(COL_MULTITEXTCHOICE.getFieldDefinition()), false)
                .createAssay(MVTC_MULTI_FILE_IMPORT_PROJECT, createDefaultConnection());

        String firstFileName = "MVTCAssayImport.tsv";
        String secondFileName = "MVTCAssayImportSecond.tsv";
        List<List<String>> fileDataFirstImport = Stream.generate(() -> shuffleSelect(TEXT_MULTI_CHOICE_LIST))
                .limit(5)
                .toList();
        List<List<String>> fileDataSecondImport = Stream.generate(() -> shuffleSelect(TEXT_MULTI_CHOICE_LIST))
                .limit(5)
                .toList();

        log("Import first and second runs with MVTC data from files");
        AssayImportPage assayImportPage = goToManageAssays()
                .clickAssay(MVTC_MULTI_FILE_IMPORT_ASSAY)
                .clickImportData();
        assayImportPage.clickNext();
        assayImportPage.setDataFile(writeMultiValueFileForAssayRun(firstFileName, fileDataFirstImport));

        assayImportPage = assayImportPage.clickSaveAndImportAnother();
        assayImportPage.setDataFile(writeMultiValueFileForAssayRun(secondFileName, fileDataSecondImport));
        assayImportPage.clickSaveAndFinish();

        AssayRunsPage assayRunsPage = new AssayRunsPage(getDriver());
        checker().wrapAssertion(() -> Assertions.assertThat(assayRunsPage.getTable().getColumnDataAsText(COL_ASSAY_ID_LABEL))
                .as("expect both runs to appear in the runs list")
                .containsExactlyInAnyOrder(firstFileName, secondFileName));

        List<String> expectedValues = Stream.concat(fileDataFirstImport.stream(), fileDataSecondImport.stream())
                .map(values -> TestArrayDataUtils.sortAndJoin(values, " "))
                .toList();
        checker().wrapAssertion(() -> Assertions.assertThat(assayRunsPage.clickViewResults().getDataTable().getColumnDataAsText(COL_MULTITEXTCHOICE))
                .as("expect MVTC values to match imported data")
                .containsExactlyInAnyOrderElementsOf(expectedValues));
    }

    private File writeMultiValueFileForAssayRun(String fileName, List<List<String>> fileData) throws IOException
    {
        List<List<String>> rows = Stream.concat(
                Stream.of(List.of(COL_MULTITEXTCHOICE.getName())),
                fileData.stream().map(row -> List.of(formatMultiValueText(row)))
        ).toList();
        return TestDataUtils.writeRowsToFile(fileName, rows);
    }

    // Issue 53616: Assay creation attempt after an error results in "Assay protocol already exists for this name."
    @Test
    public void testFailedCreation() throws Exception
    {
        _containerHelper.createProject(ISSUE_53616_PROJECT, "Assay");
        goToProjectHome(ISSUE_53616_PROJECT);

        log("Create test assay");
        ReactAssayDesignerPage assayDesignerPage = _assayHelper.createAssayDesign("General", ISSUE_53616_ASSAY)
                .setDescription(TEST_ASSAY_DESC);

        DomainFormPanel resultsPanel = assayDesignerPage.goToBatchFields().removeAllFields(false); //remove preset result fields
        resultsPanel.addField("TooLongFieldName".repeat(20));

        log("Save initial assay design with sample field set to 'All Samples'");
        List<String> errors = assayDesignerPage.clickSaveExpectingErrors();
        assertEquals("Wrong number of errors", 1, errors.size());
        assertTrue("Wrong error message: " + errors.getFirst(), errors.getFirst().startsWith("Name cannot exceed 200 characters, but was"));

        resultsPanel.removeAllFields(false);
        resultsPanel.addField("ShortAndSweet");
        assayDesignerPage.clickFinish();

        AssayListCommand command = new AssayListCommand();
        AssayListResponse response = command.execute(createDefaultConnection(), ISSUE_53616_PROJECT);
        assertNotNull("Didn't find expected assay design", response.getDefinition(ISSUE_53616_ASSAY));
    }

    /**
     *  Performs the Assay security test
     *  This test creates a project with a folder hierarchy with multiple groups and users;
     *  defines an Assay at the project level; uploads run data as a labtech; publishes
     *  as a PI, and tests to make sure that security is properly enforced
     */
    @Test
    public void testAssaySecurity() throws Exception
    {
        log("Starting Assay security scenario tests");
        setupEnvironment();
        setupPipeline(getProjectName());
        SpecimenImporter importer = new SpecimenImporter(TestFileUtils.getTestTempDir(), StudyHelper.SPECIMEN_ARCHIVE_A, FileUtil.appendName(TestFileUtils.getTestTempDir(), "specimensSubDir"), TEST_ASSAY_FLDR_STUDY2, 1);
        importer.importAndWaitForComplete();
        defineAssay();
        uploadRuns(TEST_ASSAY_FLDR_LAB1, TEST_ASSAY_USR_TECH1);
        editResults();
        publishData();
        publishDataToDateBasedStudy();
        publishDataToVisitBasedStudy();
        editAssay();
        viewCrossFolderData();
        verifyStudyList();
        verifyRunDeletionRecallsDatasetRows();
        verifyWebdavTree();
    }

    @Test
    public void testSampleFieldUpdate()
    {
        log("Starting sample field update test");
        _containerHelper.createProject(SAMPLE_FIELD_PROJECT_NAME, "Assay");

        log("Create test assay");
        ReactAssayDesignerPage assayDesignerPage = _assayHelper.createAssayDesign("General", SAMPLE_FIELD_TEST_ASSAY)
                .setDescription(TEST_ASSAY_DESC);

        assayDesignerPage.goToBatchFields().removeAllFields(false); //remove preset batch fields

        DomainFormPanel resultsPanel = assayDesignerPage.goToResultsFields().removeAllFields(false); //remove preset result fields

        String sampleFieldName = "SampleField";
        resultsPanel.manuallyDefineFields(sampleFieldName)
                .setType(FieldDefinition.ColumnType.Sample)
                .setSampleType(DomainFieldRow.ALL_SAMPLES_OPTION_TEXT);

        log("Save initial assay design with sample field set to 'All Samples'");
        assayDesignerPage.clickFinish();

        log("Verify save successful");
        assertEquals("Error saving initial assay", 0, checker().errorsSinceMark());
        AssayBeginPage assayPage = goToManageAssays();
        assertElementPresent(Locator.LinkLocator.linkWithText(SAMPLE_FIELD_TEST_ASSAY));

        log("Create new Sample Types to verify against");
        String targetTypeName = "Target Sample Type";
        SampleTypeDefinition targetDefinition = new SampleTypeDefinition(targetTypeName).setFields(new ArrayList<>());
        SampleTypeHelper ssHelper = SampleTypeHelper.beginAtSampleTypesList(this, getCurrentContainerPath());
        ssHelper.createSampleType(targetDefinition, "Name\nS_1\nS_2\nS_3");

        String otherTypeName = "Other Sample Type";
        SampleTypeDefinition otherDefinition = new SampleTypeDefinition(otherTypeName).setFields(new ArrayList<>());
        ssHelper = SampleTypeHelper.beginAtSampleTypesList(this, getCurrentContainerPath());
        ssHelper.createSampleType(otherDefinition, "Name\nOS_1\nOS_2");

        importAssayData(SAMPLE_FIELD_TEST_ASSAY, TEST_RUN1, "SampleField\nOS_1");
        goToManageAssays().clickAndWait(Locator.linkWithText(SAMPLE_FIELD_TEST_ASSAY));
        clickAndWait(Locator.linkWithText("view results"));
        assertElementPresent("Sample lookup failed for: OS_1", new Locator.LinkLocator("OS_1"), 1);

        log("Edit assay design and change Sample field to point to created Sample Type");
        goToManageAssays();
        clickAndWait(Locator.LinkLocator.linkWithText(SAMPLE_FIELD_TEST_ASSAY));
        ReactAssayDesignerPage designerPage = _assayHelper.clickEditAssayDesign();
        designerPage.expandFieldsPanel("Results")
                .getField(sampleFieldName)
                .setSampleType(targetTypeName);
        designerPage.clickFinish();

        log("Verify updates saved successfully");
        assertEquals("Error saving initial assay", 0, checker().errorsSinceMark());
        importAssayData(SAMPLE_FIELD_TEST_ASSAY, TEST_RUN2, "SampleField\nS_1");
        goToManageAssays().clickAndWait(Locator.linkWithText(SAMPLE_FIELD_TEST_ASSAY));
        clickAndWait(Locator.linkWithText("view results"));
        DataRegionTable table = new DataRegionTable("Data", getDriver());
        List<String> sampleFieldValues = table.getColumnDataAsText("SampleField");
        assertTrue("First sample should not resolve to sample type", sampleFieldValues.get(0).startsWith("<"));
        assertEquals("Second sample should resolve to sample type", "S_1", sampleFieldValues.get(1));
        assertElementPresent("Sample lookup failed for: S_1", new Locator.LinkLocator("S_1"), 1);

        log("GitHub Issue #688: verify sample lookup to createdBy");
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.addColumn("SampleField/CreatedBy");
        _customizeViewsHelper.applyCustomView();
        table = new DataRegionTable("Data", getDriver());
        List<String> createdByValues = table.getColumnDataAsText("SampleField/CreatedBy");
        assertEquals("First sample should not have a createdBy since it doesn't resolve", " ", createdByValues.get(0));
        assertEquals("Second sample should have a createdBy since it resolves to a sample type", getCurrentUserName(), createdByValues.get(1));

        log("Edit assay design and change Sample field to point back to 'All Samples'");
        goToManageAssays();
        clickAndWait(Locator.LinkLocator.linkWithText(SAMPLE_FIELD_TEST_ASSAY));
        designerPage = _assayHelper.clickEditAssayDesign();
        designerPage.expandFieldsPanel("Results")
                .getField(sampleFieldName)
                .setSampleType(DomainFieldRow.ALL_SAMPLES_OPTION_TEXT);
        designerPage.clickFinish();
        assertEquals("Error saving updated sample field", 0, checker().errorsSinceMark());

        log("Verify updates saved successfully");
        importAssayData(SAMPLE_FIELD_TEST_ASSAY, TEST_RUN3, "SampleField\nS_2\nOS_2");
        assertEquals("Error importing data after assay sample field update", 0, checker().errorsSinceMark());

        goToManageAssays().clickAndWait(Locator.linkWithText(SAMPLE_FIELD_TEST_ASSAY));
        clickAndWait(Locator.linkWithText("view results"));
        assertElementPresent("Sample lookup failed for: OS_1", new Locator.LinkLocator("OS_1"), 1);
        assertElementPresent("Sample lookup failed for: S_1", new Locator.LinkLocator("S_1"), 1);
        assertElementPresent("Sample lookup failed for: S_2", new Locator.LinkLocator("S_2"), 1);
        assertElementPresent("Sample lookup failed for: OS_2", new Locator.LinkLocator("OS_2"), 1);

        log("GitHub Issue #688: verify sample lookup to createdBy");
        table = new DataRegionTable("Data", getDriver());
        for (int i = 0; i < table.getDataRowCount(); i++)
            assertEquals("Row " + i + " should have current user as createdBy since they all resolve to samples", getCurrentUserName(), table.getDataAsText(i, "SampleField/CreatedBy"));
    }

    private void importAssayData(String assayName, String runName, String runDataStr)
    {
        goToManageAssays();
        clickAndWait(Locator.linkWithText(assayName));
        clickButton("Import Data", "Run Data");
        setFormElement(AssayConstants.ASSAY_NAME_FIELD_LOCATOR, runName);
        click(AssayConstants.TEXT_AREA_DATA_PROVIDER_LOCATOR);
        setFormElement(AssayConstants.TEXT_AREA_DATA_COLLECTOR_LOCATOR,  runDataStr);
        clickButton("Save and Finish");

    }

    @LogMethod
    private void verifyRunDeletionRecallsDatasetRows()
    {
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        DataRegionTable assayRuns = new DataRegionTable("Runs", this);
        assayRuns.checkCheckbox(0);
        assayRuns.clickHeaderButtonAndWait("Delete");
        // Make sure that it shows that the data is part of study datasets
        assertTextPresent(TEST_RUN3, "2 dataset(s)", TEST_ASSAY);
        assertTextNotPresent("FirstRun");
        // Do the delete
        clickButton("Confirm Delete");

        // Be sure that we have a special audit record
        clickAndWait(Locator.linkWithText("view link to study history"));
        assertTextPresent("3 row(s) were recalled from a study to the assay: ");

        // Verify that the deleted run data is gone from the dataset
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_STUDY2);
        clickAndWait(Locator.linkWithText("1 dataset"));
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        assertTextPresent("AAA07XMC-04", TEST_RUN1);
        assertTextNotPresent("BAQ00051-09", TEST_RUN3);
    }

    //Issue 12203: Incorrect files are visible from pipeline directory
    private void verifyWebdavTree()
    {
        beginAt("_webdav");
        _fileBrowserHelper.selectFileBrowserItem(getProjectName() + "/Studies/Study 1/");
        Locator.XPathLocator l = Locator.xpath("//span[text()='@pipeline']");
        assertElementPresent(l,  1);
    }

    @LogMethod
    private void editResults() throws IOException, CommandException
    {
        // Verify that the results aren't editable by default
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        waitAndClickAndWait(Locator.linkWithText("view results"));
        DataRegionTable table = new DataRegionTable("Data", getDriver());
        assertEquals("No rows should be editable", 0, DataRegionTable.updateLinkLocator().findElements(table.getComponentElement()).size());
        assertElementNotPresent(Locator.button("Delete"));

        // Edit the design to make them editable
        ReactAssayDesignerPage assayDesignerPage = _assayHelper.clickEditAssayDesign(true);
        assayDesignerPage.setEditableResults(true);
        assayDesignerPage.clickFinish();

        // Try an edit
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        clickAndWait(Locator.linkWithText("view results"));
        DataRegionTable dataTable = new DataRegionTable("Data", getDriver());
        assertEquals("Incorrect number of results shown.", 10, table.getDataRowCount());
        doAndWaitForPageToLoad(() -> dataTable.updateLink(dataTable.getRowIndex("Specimen ID", "AAA07XK5-05")).click());
        setFormElement(Locator.name("quf_SpecimenID"), "EditedSpecimenID");
        setFormElement(Locator.name("quf_VisitID"), "601.5");
        setFormElement(Locator.name("quf_testAssayDataProp5"), "notAnumber");
        clickButton("Submit");
        assertTextPresent("Could not convert value: " + "notAnumber");
        setFormElement(Locator.name("quf_testAssayDataProp5"), "514801");
        clickButton("Submit");
        assertTextPresent("EditedSpecimenID", "601.5", "514801");

        // Try a delete
        dataTable.checkCheckbox(table.getRowIndex("Specimen ID", "EditedSpecimenID"));
        doAndWaitForPageToLoad(() ->
        {
            dataTable.clickHeaderButton("Delete");
            assertAlert("Are you sure you want to delete the selected row?");
        });

        // Verify that the edit was audited
        AuditLogHelper auditLogHelper = new AuditLogHelper(this, () -> WebTestHelper.getRemoteApiConnection(false));
        auditLogHelper.checkAuditEventDiffCount(getProjectName(), AuditLogHelper.AuditEvent.QUERY_UPDATE_AUDIT_EVENT, List.of(0/*delete*/, 3/*edit*/));

        goToSchemaBrowser();
        viewQueryData("auditLog", "ExperimentAuditEvent");
        assertTextPresent("1 data row has been edited in " + TEST_ASSAY + ".");

    }

    /**
     * Generates the text that appears in the target study drop-down for a given study name
     * @param studyName name of the target study
     * @return formatted string of what appears in the target study drop-down
     */
    private String getTargetStudyOptionText(String studyName)
    {
        //the format used in the drop down is:
        // /<project>/<studies>/<study1> (<study> Study)
        return "/" + getProjectName() + "/" + TEST_ASSAY_FLDR_STUDIES + "/" +
                    studyName + " (" + studyName + " Study)";
    }

    /**
     * Uploads run data for the centrally defined Assay while impersonating a labtech-style user
     * @param folder    name of the folder into which we should upload
     * @param asUser    the user to impersonate before uploading
     */
    @LogMethod
    private void uploadRuns(String folder, String asUser)
    {
        log("Uploading runs into folder " + folder + " as user " + asUser);
        navigateToFolder(getProjectName(), folder);
        impersonate(asUser);

        clickAndWait(Locator.linkWithText("Assay List"));
        clickAndWait(Locator.linkWithText(TEST_ASSAY));

        //nav trail check
        assertNavTrail("Assay List", TEST_ASSAY + " Batches");

        clickButton("Import Data");
        assertTextPresent(TEST_ASSAY_SET_PROP_NAME + "3");

        log("Batch properties");
        clickButton("Next");
        assertTextPresent(TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 1) + " is required and must be of type Number (Double).");
        setFormElement(Locator.name(TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 1)), "Bad Test");
        setFormElement(Locator.name(TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 2)), "Bad Test");
        setFormElement(Locator.name(TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 3)), "Bad Test");
        clickButton("Next");
        assertTextPresent(
                "Could not convert value 'Bad Test' (String) for Double field '" + TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 1) + "'.",
                "Could not convert value 'Bad Test' (String) for Integer field '" + TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 2) + "'.",
                "'Bad Test' is not a valid Date for '" + TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 3) + "' using U.S. date parsing (MDY).");
        setFormElement(Locator.name(TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 1)), TEST_ASSAY_SET_PROPERTIES[1]);
        setFormElement(Locator.name(TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 2)), TEST_ASSAY_SET_PROPERTIES[2]);
        setFormElement(Locator.name(TEST_ASSAY_SET_PROP_NAME + (TEST_ASSAY_SET_PREDEFINED_PROP_COUNT + 3)), TEST_ASSAY_SET_PROPERTIES[3]);

        //ensure that the target study drop down contains Study 1 and Study 2 only and not Study 3
        //(labtech1 does not have read perms to Study 3)
        waitForElement(Locator.xpath("//option").withText(getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY1)));
        assertElementPresent(Locator.xpath("//option").withText(getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY2)));
        assertElementNotPresent(Locator.xpath("//option").withText(getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY3)));

        //select Study2 as the target study (note that PI is not an Editor in this study so we can test for override case)
        selectOptionByText(AssayConstants.TARGET_STUDY_FIELD_LOCATOR, getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY2));

        clickButton("Next");

        log("Check properties set.");
        assertTextPresent(
                TEST_ASSAY_SET_PROPERTIES[1],
                TEST_ASSAY_SET_PROPERTIES[2],
                TEST_ASSAY_SET_PROPERTIES[3],
                TEST_ASSAY_SET_PROPERTIES[0]);

        log("Run properties and data");
        setFormElement(Locator.name(TEST_ASSAY_RUN_PROP_NAME + "5"), PROTOCOL_DOC);
        clickButton("Save and Finish");

        assertTextPresent(TEST_ASSAY_RUN_PROP_NAME + "0 is required and must be of type Text (String).");
        assertTextPresent(PROTOCOL_DOC.getName());
        waitAndClick(Locator.linkWithText("remove"));
        setFormElement(AssayConstants.ASSAY_NAME_FIELD_LOCATOR, TEST_RUN1);
        setFormElement(AssayConstants.COMMENTS_FIELD_LOCATOR, TEST_RUN1_COMMENTS);
        setFormElement(Locator.name(TEST_ASSAY_RUN_PROP_NAME + "0"), TEST_ASSAY_RUN_PROP1);
        clickButton("Save and Finish");

        Locator loc4 = Locator.name(TEST_ASSAY_RUN_PROP_NAME + "5");
        assertEquals("", getFormElement(loc4));
        assertTextPresent("Data file contained zero data rows");
        click(AssayConstants.TEXT_AREA_DATA_PROVIDER_LOCATOR);
        setFormElement(AssayConstants.TEXT_AREA_DATA_COLLECTOR_LOCATOR, TEST_RUN1_DATA1);
        clickButton("Save and Import Another Run");

        setFormElement(AssayConstants.ASSAY_NAME_FIELD_LOCATOR, TEST_RUN2);
        setFormElement(AssayConstants.COMMENTS_FIELD_LOCATOR, TEST_RUN2_COMMENTS);
        setFormElement(Locator.name(TEST_ASSAY_RUN_PROP_NAME + "0"), TEST_ASSAY_RUN_PROP1);
        setFormElement(Locator.name(TEST_ASSAY_RUN_PROP_NAME + "5"), PROTOCOL_DOC2);
        setFormElement(AssayConstants.TEXT_AREA_DATA_COLLECTOR_LOCATOR, TEST_RUN2_DATA1);
        clickButton("Save and Finish");

        assertTextPresent(PROTOCOL_DOC2.getName());
        click(AssayConstants.TEXT_AREA_DATA_PROVIDER_LOCATOR);
        setFormElement(AssayConstants.TEXT_AREA_DATA_COLLECTOR_LOCATOR, TEST_RUN2_DATA2);
        clickButton("Save and Finish");

        assertTextPresent("Could not convert value 'g' (String) for Double field 'VisitID'");
        assertTextPresent(PROTOCOL_DOC2.getName());
        assertEquals(TEST_RUN2, getFormElement(AssayConstants.ASSAY_NAME_FIELD_LOCATOR));
        assertEquals(TEST_RUN2_COMMENTS, getFormElement(AssayConstants.COMMENTS_FIELD_LOCATOR));
        click(AssayConstants.TEXT_AREA_DATA_PROVIDER_LOCATOR);
        setFormElement(AssayConstants.TEXT_AREA_DATA_COLLECTOR_LOCATOR, TEST_RUN2_DATA3);
        clickButton("Save and Import Another Run");

        assertTextPresent("Missing value for required property: " + TEST_ASSAY_DATA_PROP_NAME + "6");
        click(AssayConstants.TEXT_AREA_DATA_PROVIDER_LOCATOR);
        setFormElement(AssayConstants.TEXT_AREA_DATA_COLLECTOR_LOCATOR, TEST_RUN2_DATA4);
        clickButton("Save and Import Another Run");

        assertEquals("", getFormElement(AssayConstants.ASSAY_NAME_FIELD_LOCATOR));
        assertEquals("", getFormElement(AssayConstants.COMMENTS_FIELD_LOCATOR));
        setFormElement(AssayConstants.ASSAY_NAME_FIELD_LOCATOR, TEST_RUN3);
        setFormElement(AssayConstants.COMMENTS_FIELD_LOCATOR, TEST_RUN3_COMMENTS);
        setFormElement(Locator.name(TEST_ASSAY_RUN_PROP_NAME + "5"), PROTOCOL_DOC2);
        clickButton("Save and Finish");

        assertTextPresent(PROTOCOL_DOC2.getName().substring(0, PROTOCOL_DOC2.getName().lastIndexOf(".")) + "-1");
        setFormElement(AssayConstants.TEXT_AREA_DATA_COLLECTOR_LOCATOR, TEST_RUN3_DATA1);
        clickButton("Save and Finish");

        // Verify the first run did not have a file, the second run had the attached file and the third run had a file
        // with a unique name.
        assertTextNotPresent(PROTOCOL_DOC.getName());
        assertTextPresent(PROTOCOL_DOC2.getName());
        assertTextPresent(PROTOCOL_DOC2.getName().substring(0, PROTOCOL_DOC2.getName().lastIndexOf(".")) + "-1");

        log("Check out the data for one of the runs");
        assertNoLabKeyErrors();
        assertTextPresent(
                TEST_ASSAY + " Runs",
                TEST_ASSAY_RUN_PROP1,
                TEST_ASSAY_SET_PROPERTIES[0],
                TEST_ASSAY_SET_PROPERTIES[3]);
        clickAndWait(Locator.linkWithText(TEST_RUN1));
        assertElementNotPresent(Locator.tagWithText("td", "7.0"));
        // Make sure that our specimen IDs resolved correctly
        assertTextPresent(
                "AAA07XSF-02",
                "999320885",
                "301",
                "AAA07XK5-05",
                "999320812",
                "601",
                TEST_ASSAY_DATA_PROP_NAME + "4",
                TEST_ASSAY_DATA_PROP_NAME + "5",
                TEST_ASSAY_DATA_PROP_NAME + "6",
                "2000-06-06",
                "0.0",
                "f",
                ALIASED_DATA);

        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.addColumn("SpecimenID/GlobalUniqueId");
        _customizeViewsHelper.addColumn("SpecimenID/Specimen/PrimaryType");
        _customizeViewsHelper.addColumn("SpecimenID/AssayMatch");
        _customizeViewsHelper.removeColumn("Run/testAssayRunProp1");
        _customizeViewsHelper.removeColumn("Run/Batch/testAssaySetProp2");
        _customizeViewsHelper.removeColumn("testAssayDataProp4");
        _customizeViewsHelper.applyCustomView();

        assertTextPresent("Blood (Whole)", 4);

        Locator.XPathLocator trueLocator = Locator.xpath("//table[contains(@class, 'labkey-data-region')]//td[text() = 'true']");
        int totalTrues = getElementCount(trueLocator);
        assertEquals(4, totalTrues);

        DataRegionTable region = new DataRegionTable("Data", this);
        region.setFilter("SpecimenID", "Starts With", "AssayTestControl");

        // verify that there are no trues showing for the assay match column that were filtered out
        totalTrues = getElementCount(trueLocator);
        assertEquals(0, totalTrues);

        log("Check out the data for all of the runs");
        clickAndWait(Locator.linkWithText("view results"));
        region.clearAllFilters("SpecimenID");
        assertElementPresent(Locator.tagWithText("td", "7.0"));
        assertElementPresent(Locator.tagWithText("td", "18"));

        assertTextPresent("Blood (Whole)", 7);

        Locator.XPathLocator falseLocator = Locator.xpath("//table[contains(@class, 'labkey-data-region')]//td[text() = 'false']");
        int totalFalses = getElementCount(falseLocator);
        assertEquals(3, totalFalses);

        region.setFilter("SpecimenID", "Does Not Start With", "BAQ");

        // verify the falses have been filtered out
        totalFalses = getElementCount(falseLocator);
        assertEquals(0, totalFalses);

        stopImpersonating();
    }

    /**
     * Impersonates the PI user and publishes the data previous uploaded.
     * This will also verify that the PI cannot publish to studies for which
     * the PI does not have Editor permissions.
     */
    @LogMethod
    private void publishData()
    {
        log("Prepare visit map to check PTID counts in study navigator.");
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_STUDY1);
        _studyHelper.goToManageVisits().goToImportVisitMap();
        setFormElement(Locator.name("content"),
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<visitMap xmlns=\"http://labkey.org/study/xml\">\n" +
            "  <visit label=\"Test Visit\" typeCode=\"X\" sequenceNum=\"301.0\" maxSequenceNum=\"302.0\"/>\n" +
            "</visitMap>");
        clickButton("Import");

        log("Publishing the data as the PI");

        //impersonate the PI
        impersonate(TEST_ASSAY_USR_PI1);
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        clickAndWait(Locator.linkWithText("view results"));

        //select all the data rows and click publish
        DataRegionTable table = new DataRegionTable("Data", this);
        table.checkAllOnPage();
        table.clickHeaderButtonAndWait("Link to Study");

        //the target study selected before was Study2, but the PI is not an editor there
        //so ensure that system has correctly caught this fact and now asks the PI to
        //select a different study, and lists only those studies in which the PI is
        //an editor

        //ensure warning
        assertTextPresent("WARNING: You do not have permissions to link to one or more of the selected run's associated studies.");

        //ensure that Study2 and Study 3 are not available in the target study drop down
        assertElementNotPresent(Locator.xpath("//select[@name='TargetStudy']/option[.='" +
                getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY2) + "']"));
        assertElementNotPresent(Locator.xpath("//select[@name='TargetStudy']/option[.='" +
                getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY3) + "']"));

        //Study1 is the only one left, so it should be there and already be selected
        assertElementPresent(Locator.xpath("//select[@name='TargetStudy']/option[.='" +
                getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY1) + "']"));

        // Make sure the selected study is Study1
        selectOptionByText(AssayConstants.TARGET_STUDY_FIELD_LOCATOR, getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY1));

        clickButton("Next");
        assertTextPresent("Link to " + TEST_ASSAY_FLDR_STUDY1 + " Study: Verify Results");

        setFormElement(Locator.name("visitId"), "301.5");
        clickButton("Link to Study");

        log("Verifying that the data was published");
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.addColumn("QCState");
        _customizeViewsHelper.applyCustomView();
        assertTextPresent(
                "Pending Review",
                TEST_RUN1_COMMENTS,
                "2000-01-01");
        clickTab("Overview");
        clickAndWait(Locator.linkWithText("Study Navigator"));

        log("Test participant counts and row counts in study overview");
        String[] row2 = new String[]{TEST_ASSAY, "8", "1", "1", "1", "1", "1", "1", "2"};
        assertTableRowsEqual("studyOverview", 1, new String[][]{row2});
        // Manually click the checkbox -- normal checkCheckbox() method doesn't seem to work for checkbox that reloads using onchange event
        clickAndWait(Locator.checkboxByNameAndValue("visitStatistic", "RowCount"));
        row2 = new String[]{TEST_ASSAY, "8 / 9", "1 / 1", "1 / 1", "1 / 1", "1 / 1", "1 / 1", "1 / 1", "2 / 3"};
        assertTableRowsEqual("studyOverview", 1, new String[][]{row2});
        doAndWaitForPageToLoad(() -> uncheckCheckbox(Locator.checkboxByNameAndValue("visitStatistic", "ParticipantCount")));
        row2 = new String[]{TEST_ASSAY, "9", "1", "1", "1", "1", "1", "1", "3"};
        assertTableRowsEqual("studyOverview", 1, new String[][]{row2});

        clickAndWait(Locator.linkWithText("9"));

        assertElementPresent(Locator.linkWithText("999320885"), 1);
        assertElementPresent(Locator.linkWithText("999320885"), 1);
        assertTextPresent(
                "301.0",
                "9.0",
                "8.0",
                TEST_RUN1_COMMENTS,
                TEST_RUN2_COMMENTS,
                TEST_RUN1,
                TEST_RUN2,
                "2000-06-06",
                TEST_ASSAY_RUN_PROP1,
                "18");

        // test recall
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        waitAndClickAndWait(Locator.linkWithText("view link to study history"));

        // Set a filter so that we know we're recalling SecondRun
        DataRegionTable region = new DataRegionTable("query", this);
        region.setFilter("Comment", "Starts With", "3 row(s) were linked to a study from the assay");
        doAndWaitForPageToLoad(() -> region.detailsLink(region.getRowIndex("Assay/Protocol", TEST_ASSAY)).click());

        DataRegionTable linkStudy = new DataRegionTable("Dataset", this);
        linkStudy.checkAll();
        doAndWaitForPageToLoad(() ->
        {
            linkStudy.clickHeaderButton("Recall Rows");
            acceptAlert();
        });
        assertTextPresent("row(s) were recalled from a study to the assay: " + TEST_ASSAY);

        // Set a filter so that we know we're looking at the link event for SecondRun again
        region.setFilter("Comment", "Starts With", "3 row(s) were linked to a study from the assay");

        // verify audit entry was adjusted
        doAndWaitForPageToLoad(() -> region.detailsLink(region.getRowIndex("Assay/Protocol", TEST_ASSAY)).click());
        assertTextPresent("All rows that were previously linked in this event have been recalled");

        stopImpersonating();
    }

    /**
     * Designed to test automatic timepoint generation when linking to a date based study.
     * Most tests of timepoint matching are covered by separate junit tests; however,
     * this will create 1 pre-existing timepoint, and when linking data this timepoint should be
     * chosen for appropriate records.
     */
    @LogMethod
    private void publishDataToDateBasedStudy()
    {
        log("Prepare visit map to check PTID counts in study navigator.");

        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_STUDY3);

        clickAndWait(Locator.linkWithText("Manage"));
        clickAndWait(Locator.linkWithText("Manage Timepoints"));
        clickAndWait(Locator.linkWithText("Create New Timepoint"));
        setFormElement(Locator.name("label"), "Preexisting Timepoint");
        setFormElement(Locator.name("sequenceNumMin"), "50");
        setFormElement(Locator.name("sequenceNumMax"), "89");
        selectOptionByText(Locator.name("typeCode"), "Screening");

        clickButton("Save");
        assertElementPresent(Locator.tagWithAttribute("a", "data-original-title", "edit"), 1);

        //select the Lab1 folder and view all the data for the test assay
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        clickAndWait(Locator.linkWithText("view results"));

        //select all the data rows and click publish
        DataRegionTable table = new DataRegionTable("Data", getDriver());
        table.checkAll();
        table.clickHeaderButtonAndWait("Link to Study");

        checkCheckbox(Locator.xpath("//input[@id='chooseStudy']"));

        // Make sure the selected study is Study3
        selectOptionByText(AssayConstants.TARGET_STUDY_FIELD_LOCATOR, getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY3));

        clickButton("Next");
        assertTextPresent("Link to " + TEST_ASSAY_FLDR_STUDY3 + " Study: Verify Results");

        //populate initial set of values and verify the timepoint preview column
        String[] dates = new String[]{"2000-02-02", "2000-03-03", "2000-04-04", "2000-05-05", "2000-06-06", "2001-01-01", "2000-01-01", "2000-02-02", "2000-03-03"};
        int idx = 1;
        for (String d : dates)
        {
            setFormElement(Locator.xpath("(//input[@name='date'])[" + idx + "]"), d);
            idx++;
        }

        setFormElement(Locator.xpath("(//input[@name='participantId'])[1]"), "new1");
        setFormElement(Locator.xpath("(//input[@name='participantId'])[2]"), "new2");
        setFormElement(Locator.xpath("(//input[@name='participantId'])[3]"), "new3");
        setFormElement(Locator.xpath("(//input[@name='participantId'])[4]"), "new4");

        DataRegionTable linkStudy = new DataRegionTable("Data", getDriver());
        linkStudy.clickHeaderButtonAndWait("Re-Validate");

        //validate timepoints:
        assertElementPresent(Locator.xpath("//td[text()='Day 32 - 39' and following-sibling::td/a[text()='AAA07XMC-02'] and following-sibling::td[text()='301.0']]"));
        assertElementPresent(Locator.xpath("//td[text()='Preexisting Timepoint' and following-sibling::td/a[text()='AAA07XMC-04'] and following-sibling::td[not(text())]]"));
        assertElementPresent(Locator.xpath("//td[text()='Day 90 - 95' and following-sibling::td/a[text()='AAA07XSF-02'] and following-sibling::td[not(text())]]"));

        assertElementPresent(Locator.xpath("//td[text()='Day 120 - 127' and following-sibling::td/a[text()='AssayTestControl1'] and following-sibling::td[text()='5.0']]"));
        assertElementPresent(Locator.xpath("//td[text()='Day 152 - 159' and following-sibling::td/a[text()='AssayTestControl2'] and following-sibling::td[text()='6.0']]"));
        assertElementPresent(Locator.xpath("//td[text()='Day 0 - 7' and following-sibling::td/a[text()='BAQ00051-09'] and following-sibling::td[text()='7.0']]"));
        assertElementPresent(Locator.xpath("//td[text()='Day 32 - 39' and following-sibling::td/a[text()='BAQ00051-08'] and following-sibling::td[text()='8.0']]"));
        assertElementPresent(Locator.xpath("//td[text()='Preexisting Timepoint' and following-sibling::td/a[text()='BAQ00051-11'] and following-sibling::td[text()='9.0']]"));

        linkStudy.clickHeaderButtonAndWait("Link to Study");

        log("Verifying that the data was published");
        assertTextPresent(
                TEST_RUN1_COMMENTS,
                "2000-01-01");
        clickTab("Overview");
        clickAndWait(Locator.linkWithText("Study Navigator"));

        log("Test participant counts and row counts in study overview");
        String[] row2 = new String[]{TEST_ASSAY, "9", "1", "2", "2", "1", "1", "1"};
        assertTableRowsEqual("studyOverview", 1, new String[][]{row2});
        // Manually click the checkbox -- normal checkCheckbox() method doesn't seem to work for checkbox that reloads using onchange event
        clickAndWait(Locator.checkboxByNameAndValue("visitStatistic", "RowCount"));
        row2 = new String[]{TEST_ASSAY, "9 / 9", "1 / 1", "2 / 2", "2 / 2", "1 / 1", "1 / 1", "1 / 1"};
        assertTableRowsEqual("studyOverview", 1, new String[][]{row2});

        log("Test that correct timepoints were created");

        clickTab("Overview");
        clickAndWait(Locator.linkWithText("Manage Study"));
        clickAndWait(Locator.linkWithText("Manage Timepoints"));
        assertTextPresent(
                "Day 0 - 7",
                "Day 32 - 39",
                "Day 90 - 95",
                "Day 120 - 127",
                "Day 152 - 159");
    }


    /**
     * Designed to test automatic timepoint generation when linking to a date based study.
     * Most tests of timepoint matching are covered by separate junit tests; however,
     * this will create 1 pre-existing timepoint, and when linking data this timepoint should be
     * chosen for appropriate records.
     */
    @LogMethod
    private void publishDataToVisitBasedStudy()
    {
        log("Prepare visit map to check PTID counts in study navigator.");

        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_STUDY2);

        _studyHelper.goToManageVisits().goToImportVisitMap();
        setFormElement(Locator.name("content"),
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<visitMap xmlns=\"http://labkey.org/study/xml\">\n" +
            "  <visit label=\"Test Visit1\" typeCode=\"X\" sequenceNum=\"6.0\" maxSequenceNum=\"13.0\"/>\n" +
            "  <visit label=\"Test Visit2\" typeCode=\"X\" sequenceNum=\"50.0\" maxSequenceNum=\"70.0\"/>\n" +
            "  <visit label=\"Test Visit3\" typeCode=\"X\" sequenceNum=\"302.0\" maxSequenceNum=\"303.0\"/>\n" +
            "</visitMap>"
        );
        clickButton("Import");

        //select the Lab1 folder and view all the data for the test assay
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        clickAndWait(Locator.linkWithText("view results"));

        //select all the data rows and click publish
        DataRegionTable table = new DataRegionTable("Data", getDriver());
        table.checkAll();
        table.clickHeaderButtonAndWait("Link to Study");

        checkCheckbox(Locator.xpath("//input[@id='chooseStudy']"));

        // Make sure the selected study is Study2
        selectOptionByText(AssayConstants.TARGET_STUDY_FIELD_LOCATOR, getTargetStudyOptionText(TEST_ASSAY_FLDR_STUDY2));

        clickButton("Next");
        assertTextPresent("Link to " + TEST_ASSAY_FLDR_STUDY2 + " Study: Verify Results");

        //populate initial set of values and verify the timepoint preview column
        String[] visits = new String[]{"302", "33", "4", "70"};
        int idx = 1;
        for (String v : visits)
        {
            setFormElement(Locator.xpath("(//input[@name='visitId'])[" + idx + "]"), v);
            idx++;
        }

        setFormElement(Locator.xpath("(//input[@name='participantId'])[1]"), "new1");
        setFormElement(Locator.xpath("(//input[@name='participantId'])[2]"), "new2");
        setFormElement(Locator.xpath("(//input[@name='participantId'])[3]"), "new3");
        setFormElement(Locator.xpath("(//input[@name='participantId'])[4]"), "new4");

        DataRegionTable linkStudy = new DataRegionTable("Data", getDriver());
        linkStudy.clickHeaderButtonAndWait("Re-Validate");

        //validate timepoints:
        assertElementPresent(Locator.xpath("//td[text()='Test Visit3' and following-sibling::td/a[text()='AAA07XMC-02']]"));
        assertElementPresent(Locator.xpath("//td[text()='33.0' and following-sibling::td/a[text()='AAA07XMC-04']]"));
        assertElementPresent(Locator.xpath("//td[text()='4.0' and following-sibling::td/a[text()='AAA07XSF-02']]"));

        assertElementPresent(Locator.xpath("//td[text()='Test Visit2' and following-sibling::td/a[text()='AssayTestControl1']]"));
        assertElementPresent(Locator.xpath("//td[text()='Test Visit1' and following-sibling::td/a[text()='AssayTestControl2']]"));
        assertElementPresent(Locator.xpath("//td[text()='Test Visit1' and following-sibling::td/a[text()='BAQ00051-09']]"));
        assertElementPresent(Locator.xpath("//td[text()='Test Visit1' and following-sibling::td/a[text()='BAQ00051-08']]"));
        assertElementPresent(Locator.xpath("//td[text()='Test Visit1' and following-sibling::td/a[text()='BAQ00051-11']]"));

        linkStudy.clickHeaderButtonAndWait("Link to Study");

        log("Verifying that the data was published");
        assertTextPresent(
                TEST_RUN1_COMMENTS,
                "2000-01-01");
        clickTab("Overview");
        clickAndWait(Locator.linkWithText("Study Navigator"));

        log("Test participant counts and row counts in study overview");
        String[] row2 = new String[]{TEST_ASSAY, "9", " ", " ", " ", "1", " ", " ", "1", " ", " ", "4", " ", " ", " ", " ", "1", "1", " ", " ", " ", "1", " ", " ", " ", " ", " "};
        assertTableRowsEqual("studyOverview", 1, new String[][]{row2});
        // Manually click the checkbox -- normal checkCheckbox() method doesn't seem to work for checkbox that reloads using onchange event
        clickAndWait(Locator.checkboxByNameAndValue("visitStatistic", "RowCount"));
        row2 = new String[]{TEST_ASSAY, "9 / 9", " ", " ", " ", "1 / 1", " ", " ", "1 / 1", " ", " ", "4 / 4", " ", " ", " ", " ", "1 / 1", "1 / 1", " ", " ", " ", "1 / 1", " ", " ", " ", " ", " "};
        assertTableRowsEqual("studyOverview", 1, new String[][]{row2});

        log("Test that correct timepoints were created");

        clickTab("Overview");
        _studyHelper.goToManageVisits();
        assertTextPresent(
                "Test Visit1",
                "6.0 - 13.0",
                "Test Visit2",
                "50.0 - 70.0",
                "Test Visit3",
                "302.0 - 303.0");
    }

    /**
     * Tests editing of an existing assay definition
     */
    @LogMethod
    private void editAssay()
    {
        log("Testing edit and delete and assay definition");
        clickProject(getProjectName());
        waitAndClickAndWait(Locator.linkWithText(TEST_ASSAY));

        // change a field name and label and remove a field
        ReactAssayDesignerPage designerPage = _assayHelper.clickEditAssayDesign();
        DomainFormPanel domainFormPanel = designerPage.expandFieldsPanel("Results");
        domainFormPanel.getField(5).setName(TEST_ASSAY_DATA_PROP_NAME + "edit");
        domainFormPanel.getField(5).setLabel(TEST_ASSAY_DATA_PROP_NAME + "edit");
        domainFormPanel.removeField(domainFormPanel.getField(4).getName(), true);
        designerPage.clickFinish();

        //ensure that label has changed in run data in Lab 1 folder
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_LAB1);
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        clickAndWait(Locator.linkWithText(TEST_RUN1));
        assertTextPresent(TEST_ASSAY_DATA_PROP_NAME + "edit");
        assertTextNotPresent(TEST_ASSAY_DATA_PROP_NAME + 4);

        AuditLogTest.verifyAuditEvent(this, AuditLogTest.ASSAY_AUDIT_EVENT, AuditLogTest.COMMENT_COLUMN, "were linked to a study from the assay: " + TEST_ASSAY, 5);
    }

    @LogMethod
    private void viewCrossFolderData()
    {
        log("Testing cross-folder data");

        clickProject(getProjectName());

        portalHelper.addWebPart("Assay Runs");
        selectOptionByText(Locator.name("viewProtocolId"), "General: " + TEST_ASSAY);
        // assay runs has a details page that needs to be submitted
        clickButton("Submit", defaultWaitForPage);

        // Set the container filter to include subfolders
        DataRegionTable assayRuns = DataRegionTable.findDataRegionWithinWebpart(this, TEST_ASSAY + " Runs");
        assayRuns.setContainerFilter(DataRegionTable.ContainerFilterType.CURRENT_AND_SUBFOLDERS);

        assertTextPresent(TEST_RUN1, TEST_RUN2);

        log("Save the customized view to include subfolders");
        assayRuns = DataRegionTable.findDataRegionWithinWebpart(this, TEST_ASSAY + " Runs");
        CustomizeView customizeViewsHelper = assayRuns.getCustomizeView();
        customizeViewsHelper.openCustomizeViewPanel();
        customizeViewsHelper.saveCustomView("");

        assertTextPresent(TEST_RUN1, TEST_RUN2);

        log("Testing select all data and view");
        assayRuns = DataRegionTable.findDataRegionWithinWebpart(this, TEST_ASSAY + " Runs");
        assayRuns.checkAllOnPage();
        clickButton("Show Results", defaultWaitForPage);
        verifySpecimensPresent(3, 2, 3);

        log("Testing clicking on a run");
        clickProject(getProjectName());
        clickAndWait(Locator.linkWithText(TEST_RUN1));
        verifySpecimensPresent(3, 2, 0);

        clickAndWait(Locator.linkWithText("view results"));
        DataRegionTable region = new DataRegionTable("Data", this);
        region.clearAllFilters("SpecimenID");
        verifySpecimensPresent(3, 2, 3);

        log("Testing assay-study linkage");
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_STUDY1);
        portalHelper.addWebPart("Datasets");
        clickAndWait(Locator.linkWithText(TEST_ASSAY));
        clickButton("View Source Assay", defaultWaitForPage);

        assertTextPresent(TEST_RUN1, TEST_RUN2);

        clickAndWait(Locator.linkWithText(TEST_RUN1));
        verifySpecimensPresent(3, 2, 0);

        clickAndWait(Locator.linkWithText("view results"));
        region = new DataRegionTable("Data", this);
        region.clearAllFilters("SpecimenID");
        verifySpecimensPresent(3, 2, 3);

        // Verify that the correct linked to study column is present
        assertTextPresent("Linked to Study 1 Study");

        log("Testing link to study availability");
        clickProject(getProjectName());
        clickAndWait(Locator.linkWithText(TEST_RUN3));

        region = new DataRegionTable("Data", this);
        region.checkAll();
        region.clickHeaderButtonAndWait("Link to Study");
        clickButton("Next");

        verifySpecimensPresent(0, 0, 3);

        clickButton("Cancel");
    }

    @LogMethod
    private void verifyStudyList()
    {
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_STUDIES);
        portalHelper.addWebPart("Study List");
        assertElementPresent(Locator.linkWithText(TEST_ASSAY_FLDR_STUDY1 + " Study"));
        assertElementPresent(Locator.linkWithText(TEST_ASSAY_FLDR_STUDY2 + " Study"));
        assertElementPresent(Locator.linkWithText(TEST_ASSAY_FLDR_STUDY3 + " Study"));
        portalHelper.clickWebpartMenuItem("Studies", "Customize");

        //verify grid view
        selectOptionByText(Locator.name("displayType"), "Grid");
        clickButton("Submit");
        assertElementNotPresent(Locator.linkWithText("edit"));

        //edit study properties
        clickAndWait(Locator.linkWithText(TEST_ASSAY_FLDR_STUDY1 + " Study"));
        click(Locator.tagWithAttribute("span", "title", "Edit"));
        waitForElement(Locator.name("Investigator"), WAIT_FOR_JAVASCRIPT);
        setFormElement(Locator.name("Investigator"), INVESTIGATOR);
        setFormElement(Locator.name("Grant"), GRANT);
        setFormElement(Locator.name("Description"), DESCRIPTION);
        clickButton("Submit");

        //verify study properties (grid view)
        navigateToFolder(getProjectName(), TEST_ASSAY_FLDR_STUDIES);
        DataRegionTable table = new DataRegionTable("qwpStudies", this);
        assertEquals("Studies not sorted correctly.", TEST_ASSAY_FLDR_STUDY1 + " Study", table.getDataAsText(0, "Label"));
        assertEquals("Failed to set study investigator.", INVESTIGATOR, table.getDataAsText(0, "Investigator"));
        assertEquals("Failed to set study grant.", GRANT, table.getDataAsText(0, "Grant"));
        assertEquals("Failed to set study description.", DESCRIPTION, table.getDataAsText(0, "Description"));

        //verify study properties (details view)
        portalHelper.clickWebpartMenuItem("Studies", "Customize");
        selectOptionByText(Locator.name("displayType"), "Details");
        clickButton("Submit");
        assertTextPresent(INVESTIGATOR, DESCRIPTION);
        assertTextNotPresent(GRANT, TEST_ASSAY_FLDR_STUDY1 + " Study tracks data"); //Old description
    }

    private void verifySpecimensPresent(int aaa07Count, int controlCount, int baq00051Count)
    {
        // need to double the count, once for the label and once for the param in the link url
        assertTextPresent("AAA07", aaa07Count * 2);
        assertTextPresent("AssayTestControl", controlCount * 2);
        assertTextPresent("BAQ00051", baq00051Count * 2);
    }

    @Test // Issue 53625
    public void testAssayLookupValidatorConversion()
    {
        _containerHelper.createProject(ISSUE_53625_PROJECT, "Assay");
        goToProjectHome(ISSUE_53625_PROJECT);

        log("Create a list with an int key and a string value");
        String lookToListName = TestDataGenerator.randomDomainName("lookToList", DomainUtils.DomainKind.IntList);
        String keyName = TestDataGenerator.randomFieldName("key", null, DomainUtils.DomainKind.IntList);
        FieldInfo valueField = FieldInfo.random("value", FieldDefinition.ColumnType.String, DomainUtils.DomainKind.IntList);
        _listHelper.createList(ISSUE_53625_PROJECT, lookToListName, keyName, valueField.getFieldDefinition());
        _listHelper.bulkImportData(TestDataUtils.tsvStringFromRowMaps(List.of(
                Map.of(valueField.getName(), "One"),
                Map.of(valueField.getName(), "Two"),
                Map.of(valueField.getName(), "123"),
                // GitHub Issue #443: value is the primary key for another row
                Map.of(valueField.getName(), "5"), // pk = 4
                Map.of(valueField.getName(), "6") // pk = 5
        ), List.of(valueField.getName()), true));

        log("Create an assay with a results lookup field to the list, with lookup validator set");
        goToProjectHome(ISSUE_53625_PROJECT);
        FieldInfo lookupField = new FieldInfo("lookup", new FieldDefinition.IntLookup(null, "lists", lookToListName));
        ReactAssayDesignerPage designerPage = _assayHelper.createAssayDesign("General", ISSUE_53625_ASSAY);
        designerPage.goToBatchFields()
                .removeAllFields(false);
        designerPage.goToResultsFields()
                .removeAllFields(false)
                .manuallyDefineFields(lookupField.getFieldDefinition().setLookupValidatorEnabled(true));
        designerPage.clickFinish();

        log("Verify importing an assay run with valid and invalid values for the lookup field");
        verifyAssayImportForLookupValidator(ISSUE_53625_ASSAY, lookupField, "RunWithLookupValidator", true);

        log("Turn off lookup field validator and test the imports again");
        designerPage = _assayHelper.clickEditAssayDesign();
        designerPage.goToResultsFields()
                .getField(lookupField.getName())
                .setLookupValidatorEnabled(false);
        designerPage.clickFinish();
        verifyAssayImportForLookupValidator(ISSUE_53625_ASSAY, lookupField, "RunWithoutLookupValidator", false);

        log("GitHub Issue #443: Verify that importing a value that is also a primary key maps to the titleColumn value");
        verifyAssayImportForPKValueThatIsTitleColumn(ISSUE_53625_ASSAY, lookupField, "RunWithPKandTitleColumn");
    }

    private void verifyAssayImportForPKValueThatIsTitleColumn(String assayName, FieldInfo lookupField, String runName)
    {
        String runDataStr = TestDataUtils.tsvStringFromRowMaps(List.of(
                        Map.of(lookupField.getName(), "4"), // pk 4, value 5
                        Map.of(lookupField.getName(), "5"), // pk 4, value 5
                        Map.of(lookupField.getName(), "6")), // pk 5, value 6
                List.of(lookupField.getName()), true
        );
        importAssayData(assayName, runName, runDataStr);
        clickAndWait(Locator.linkWithText(runName));
        DataRegionTable dataTable = new DataRegionTable("Data", getDriver());
        checker().verifyEquals("Incorrect number of results shown.", 3, dataTable.getDataRowCount());
        checker().fatal().verifyEquals("Lookup values not as expected.", List.of("5", "5", "6"), dataTable.getColumnDataAsText(lookupField.getLabel()));
    }

    private void verifyAssayImportForLookupValidator(String assayName, FieldInfo lookupField, String runName, boolean validatorOn)
    {
        String runDataStr = TestDataUtils.tsvStringFromRowMaps(List.of(
                        Map.of(lookupField.getName(), "One"), // valid
                        Map.of(lookupField.getName(), "99")), // invalid
                List.of(lookupField.getName()), true
        );
        importAssayData(assayName, runName, runDataStr);
        assertTextPresent("Could not translate value: 99");
        if (validatorOn) assertTextPresent("Value '99' was not present in lookup target");
        else assertTextNotPresent("Value '99' was not present in lookup target");
        clickButton("Cancel");

        runDataStr = TestDataUtils.tsvStringFromRowMaps(List.of(
                        Map.of(lookupField.getName(), "Three"), // invalid
                        Map.of(lookupField.getName(), "2")), // valid
                List.of(lookupField.getName()), true
        );
        importAssayData(assayName, runName, runDataStr);
        assertTextPresent("Failed to convert");
        assertTextPresent("Could not translate value: Three");
        clickButton("Cancel");

        runDataStr = TestDataUtils.tsvStringFromRowMaps(List.of(
                        Map.of(lookupField.getName(), "One"), // valid
                        Map.of(lookupField.getName(), "2"), // valid
                        Map.of(lookupField.getName(), "123")), // valid
                List.of(lookupField.getName()), true
        );
        importAssayData(assayName, runName, runDataStr);
        clickAndWait(Locator.linkWithText(runName));
        DataRegionTable dataTable = new DataRegionTable("Data", getDriver());
        checker().verifyEquals("Incorrect number of results shown.", 3, dataTable.getDataRowCount());
        checker().verifyEquals("Lookup values not as expected.", List.of("One", "Two", "123"), dataTable.getColumnDataAsText(lookupField.getLabel()));

        // test with just the numeric value since that was causing issues during manual testing
        runName = runName + "NumericOnly";
        runDataStr = TestDataUtils.tsvStringFromRowMaps(List.of(
                        Map.of(lookupField.getName(), "123")), // valid
                List.of(lookupField.getName()), true
        );
        importAssayData(assayName, runName, runDataStr);
        clickAndWait(Locator.linkWithText(runName));
        dataTable = new DataRegionTable("Data", getDriver());
        checker().verifyEquals("Incorrect number of results shown.", 1, dataTable.getDataRowCount());
        checker().verifyEquals("Lookup values not as expected.", List.of("123"), dataTable.getColumnDataAsText(lookupField.getLabel()));
    }

    @Test // GitHub Issue #1023
    public void testNoExternalReturnUrlRedirect() throws Exception
    {
        _containerHelper.createProject(GH_1023_PROJECT, "Assay");
        goToProjectHome(GH_1023_PROJECT);

        // Navigate to domain designer with an external returnUrl. The safeRedirect action
        // should prevent external redirects, falling back to the local home page instead.
        String domainDesignerUrl = WebTestHelper.buildURL("assay", GH_1023_PROJECT, "designer",
                Map.of("providerName", "General", "returnUrl", "https://labkey.com"));
        beginAt(domainDesignerUrl);
        DomainDesignerPage domainDesignerPage = new DomainDesignerPage(getDriver());
        domainDesignerPage.fieldsPanel();
        domainDesignerPage.clickCancel();
        String postCancelUrl = getDriver().getCurrentUrl();
        assertFalse("Cancel with an external returnUrl should not navigate to an external site",
                postCancelUrl.contains("labkey.com"));
        assertTrue("Cancel with an external returnUrl should redirect to a local LabKey page instead of: " + postCancelUrl,
                WebTestHelper.isTestServerUrl(postCancelUrl));
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }
}
