/*
 * Copyright (c) 2013-2018 LabKey Corporation
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

import org.apache.commons.collections4.MapUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyC;
import org.labkey.test.components.DomainDesignerPage;
import org.labkey.test.components.PropertiesEditor;
import org.labkey.test.components.domain.DomainFormPanel;
import org.labkey.test.pages.EditDatasetDefinitionPage;
import org.labkey.test.pages.ImportDataPage;
import org.labkey.test.pages.study.ManageDatasetQCStatesPage;
import org.labkey.test.pages.study.ManageStudyPage;
import org.labkey.test.pages.study.ManageVisitPage;
import org.labkey.test.pages.study.QCStateTableRow;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.ListHelper;
import org.labkey.test.util.StudyHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

/**
 * This test is designed to test individual parts/properties of the study import/export archive.
 * The @BeforeClass creates a new study manually using the default settings.
 * Each @Test then sets a property in that study, exports the study, and reimports it into a subfolder
 */
@Category({DailyC.class})
@BaseWebDriverTest.ClassTimeout(minutes = 28)
public class StudySimpleExportTest extends StudyBaseTest
{
    private static final String TEST_DATASET_NAME = "TestDataset";
    public static final String NOTIFICATION_EMAIL = "specimen-test@simpleexport.test";
    private final String FOLDER_SCOPE = "folder";
    private final String PROJECT_SCOPE = "project";

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getFolderName()
    {
        return "Manually Created Study";
    }

    @Override @Ignore
    public void testSteps(){}

    @Override
    protected void doVerifySteps(){}

    @Override
    protected void doCreateSteps(){}

    @BeforeClass
    public static void doSetup()
    {
        StudySimpleExportTest initTest = (StudySimpleExportTest)getCurrentTest();

        initTest.initializeFolder();
        initTest.setPipelineRoot(StudyHelper.getPipelinePath());

        initTest.clickFolder(initTest.getFolderName()); // navigate to StudyVerifyProject/Manually Created Study
        // click button to create manual study
        initTest.clickButton("Create Study");
        // use all of the default study settings
        initTest.clickButton("Create Study");
        // populate study with one dataset, one ptid, and one visit
        initTest.createSimpleDataset();

        // quickly verify some expected text on the overview tab to make sure we have a study
        initTest.clickTab("Overview");
        initTest.waitForElement(Locator.linkWithText("1 dataset"));
        initTest.assertTextPresentInThisOrder("Study tracks data in", "over 1 visit. Data is present for 1 Participant");
    }

    @Override
    protected void initializeFolder()
    {
        super.initializeFolder();

        clickProject(getProjectName());
        goToFolderManagement().goToFolderTypeTab();
        checkCheckbox(Locator.radioButtonByNameAndValue("folderType", "Study"));
        clickButton("Update Folder");

        // click button to create manual study
        clickButton("Create Study");
        // use all of the default study settings
        clickButton("Create Study");
        clickFolder(getFolderName());
    }

    private void createSimpleDataset()
    {
        log("Do Setup: create simple dataset with one ptid and one visit");
        clickFolder(getFolderName());
        EditDatasetDefinitionPage editDatasetPage = _studyHelper
                .goToManageDatasets()
                .clickCreateNewDataset()
                .setName(TEST_DATASET_NAME)
                .submit();
        PropertiesEditor fieldsEditor = editDatasetPage.getFieldsEditor();
        fieldsEditor.selectField(0).markForDeletion();
        fieldsEditor.addField(new FieldDefinition("TestInt").setLabel("TestInt").setType(FieldDefinition.ColumnType.Integer)
                .setValidator(new ListHelper.RangeValidator("numberValidator", "numberValidator", "TestInt must equals '999'.", ListHelper.RangeType.Equals, "999"))
                .setRequired(false));
        fieldsEditor.addField(new FieldDefinition("TestString").setLabel("TestRequiredString").setType(FieldDefinition.ColumnType.String)
                .setRequired(true));
        // Format "TestDate" as "Date"
        fieldsEditor.addField(new FieldDefinition("TestDate").setLabel("TestDate").setType(FieldDefinition.ColumnType.DateTime));
        // "TestDateTime" format will default to date-time
        fieldsEditor.addField(new FieldDefinition("TestDateTime").setLabel("TestDateTime").setType(FieldDefinition.ColumnType.DateTime));
        editDatasetPage
                .save()
                .clickViewData()
                .getDataRegion()
                .clickImportBulkData();
        waitForElement(Locator.name("text"));
        setFormElement(Locator.name("text"), "ParticipantId\tSequenceNum\tTestInt\tTestString\tTestDate\tTestDateTime\nPTID123\t1.0\t999\tABC\t2013-10-29\t2013-10-28 01:23");
        clickButton("Submit");
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
        TestFileUtils.deleteDir(new File(StudyHelper.getPipelinePath() + "export"));
    }

    @Test
    public void verifyDatasetQCStates()
    {
        log("QC States: go to Manage Dataset QC States page");
        goToProjectHome();
        clickFolder(getFolderName());
        ManageDatasetQCStatesPage qcStatesPage =goToManageStudy().manageDatasetQCStates();

        log("QC States: set [none] state to be public data, i.e. opposite of default");
        qcStatesPage.getStateRow("[none]").setPublicData(true);

        log("QC States: create 3 new QC states (one for each default state type)");

        qcStatesPage.addStateRow("First QC State", "The first qc state description", false)
                .addStateRow("Second QC State", "The second qc state description", false)
                .addStateRow("Third QC State", "The third qc state description", false)
                .clickSave();

        log("QC States: set the default states for dataset data and visibility state");
        new ManageStudyPage(getDriver())
                .manageDatasetQCStates()
                .setDefaultPipelineQCState("First QC State")
                .setDefaultAssayQCState("Second QC State")
                .setDefaultDirectEntryQCState("Third QC State")
                .setDefaultVisibility("Public data")
                .clickSave();

        log("QC States: export study folder to the pipeline as individual files");
        exportFolderAsIndividualFiles(getFolderName(), false, false, false);

        log("QC States: verify xml file was created in export");
        _fileBrowserHelper.selectFileBrowserItem("export/quality_control_states.xml");

        log("QC States: import study into subfolder");
        createSubfolderAndImportStudyFromPipeline("QC States");

        log("QC States: verify imported settings");
        clickFolder("QC States");
        goToManageStudy();
        waitAndClickAndWait(Locator.linkWithText("Manage Dataset QC States"));
        ManageDatasetQCStatesPage statesPage = new ManageDatasetQCStatesPage(getDriver());
        List<QCStateTableRow> states = statesPage.getStateRows();

        QCStateTableRow noneRow = statesPage.getStateRow("[none]");
        assertEquals(true, noneRow.getPublicData());

        QCStateTableRow firstRow = statesPage.getStateRow("First QC State");
        assertEquals("The first qc state description", firstRow.getDescription());
        assertFalse("expect first qc state not to be public", firstRow.getPublicData());

        QCStateTableRow secondRow = statesPage.getStateRow("Second QC State");
        assertEquals("The second qc state description", secondRow.getDescription());
        assertFalse("expect second qc state not to be public", secondRow.getPublicData());

        QCStateTableRow thirdRow = statesPage.getStateRow("Third QC State");
        assertEquals("The third qc state description", thirdRow.getDescription());
        assertFalse("don't expect 3rd row to be public", thirdRow.getPublicData());

        assertEquals("First QC State", statesPage.getDefaultPipelineQCState());
        assertEquals("Second QC State", statesPage.getDefaultAssayQCState());
        assertEquals("Third QC State", statesPage.getDefaultDirectEntryQCState());
        assertEquals("Public data", statesPage.getDefaultVisibility());

        log("QC States: reset default visibility state");
        clickFolder(getFolderName());
        goToManageStudy();
        waitAndClickAndWait(Locator.linkWithText("Manage Dataset QC States"));
        new ManageDatasetQCStatesPage(getDriver())
                .setDefaultVisibility("All data")
                .clickSave();
    }

    @Test
    public void verifyDatasetFieldValidators()
    {
        log("Field Validators: export study folder to the pipeline as individual files");
        exportFolderAsIndividualFiles(getFolderName(), false, false, false);

        log("Field Validators: import study into subfolder");
        createSubfolderAndImportStudyFromPipeline("Field Validators");

        goToProjectHome();
        clickFolder("Field Validators");

        clickTab("Clinical and Assay Data");
        waitAndClickAndWait(Locator.linkWithText(TEST_DATASET_NAME));
        DataRegionTable.findDataRegion(this).clickImportBulkData();
        waitForElement(Locator.name("text"));

        log("Verify required field for imported study");
        ImportDataPage importDataPage = new ImportDataPage(getDriver());
        importDataPage.setText("ParticipantId\tSequenceNum\nPTID123\t999");
        importDataPage.submitExpectingError("Data does not contain required field: TestString");

        log("Verify field validator for imported study");
        importDataPage.setText( "ParticipantId\tSequenceNum\tTestString\tTestInt\nPTID123\t999\tZZZ\t333");
        importDataPage.submitExpectingError("Value '333' for field 'TestInt' is invalid. TestInt must equals '999'.");
    }

    @Test
    public void verifyDefaultDatasetFormats()
    {
        log("Default Formats: set default formats for study");
        goToProjectHome();
        clickFolder(getFolderName());

        // Default date & number formats are now on the folder management "Formats" tab
        goToFolderManagement();
        clickAndWait(Locator.linkWithText("Formats"));
        setFormElement(Locator.name("defaultDateFormat"), "MMM dd, yyyy");
        setFormElement(Locator.name("defaultDateTimeFormat"), "MMM dd, yyyy HH:mm");
        setFormElement(Locator.name("defaultNumberFormat"), "#.000");
        checkCheckbox(Locator.name("restrictedColumnsEnabled"));
        clickButton("Save");

        log("Default Formats: export study folder to the pipeline as individual files");
        exportFolderAsIndividualFiles(getFolderName(), false, false, false);

        log("Default Formats: verify xml file was created in export");
        _fileBrowserHelper.selectFileBrowserItem("export/study/datasets/datasets_manifest.xml");

        log("Default Formats: import study into subfolder");
        createSubfolderAndImportStudyFromPipeline("Default Dataset Formats");

        log("Default Formats: verify imported settings");
        clickFolder("Default Dataset Formats");
        goToFolderManagement();
        clickAndWait(Locator.linkWithText("Formats"));
        assertEquals("MMM dd, yyyy", getFormElement(Locator.name("defaultDateFormat")));
        assertEquals("MMM dd, yyyy HH:mm", getFormElement(Locator.name("defaultDateTimeFormat")));
        assertEquals("#.000", getFormElement(Locator.name("defaultNumberFormat")));
        assertChecked(Locator.name("restrictedColumnsEnabled"));

        clickTab("Clinical and Assay Data");
        waitAndClickAndWait(Locator.linkWithText(TEST_DATASET_NAME));
        assertTextPresentInThisOrder("999.000", "Oct 29, 2013");
    }

    @Test
    public void verifySuppressQueryValidation()
    {
        log("Query Validation: import study folder zip without query validation enabled");
        goToProjectHome();
        _containerHelper.createSubfolder(getProjectName(), getProjectName(), "Query Validation", "Collaboration", null, true);
        importFolderFromZip(TestFileUtils.getSampleData("studies/LabkeyDemoStudyWithCharts.folder.zip"), false, 1);
        goToModule("FileContent");
        Locator.XPathLocator fileLoc = Locator.tag("div").startsWith("folder_load_");
        waitForElement(fileLoc);
        doubleClick(fileLoc);
        switchToWindow(1);
        assertTextPresentInThisOrder("Loading folder properties (folder type, settings and active modules)", " queries imported", "Skipping query validation.");
        getDriver().close();
        switchToMainWindow();
    }

    @Test
    public void verifyChartViewForParticipant()
    {
        log("Chart view Migration Test - Start");
        String subfolder = "Chart View Migration";
        goToProjectHome();

        log("Creating the subfolder");
        _containerHelper.createSubfolder(getProjectName(), getProjectName(), subfolder, "Collaboration", null, true);
        importFolderFromZip(TestFileUtils.getSampleData("studies/LabkeyDemoStudyWithCharts.folder.zip"), false, 1);
        navigateToFolder(getProjectName(), subfolder);

        log("Checking the charts for a participant");
        clickTab("Participants");
        clickAndWait(Locator.linkWithText("249318596"));
        click(Locator.linkContainingText("Physical Exam"));

        log("Adding the chart to the dataset");
        click(Locator.linkContainingText("Add Chart"));
        selectOptionByTextContaining(Locator.tagWithId("select", "addChartSelect-5004").findElement(getDriver()), "Example Box Plot");
        clickButton("Submit");

        log("Asserting if map is present");
        waitForText("Example Box Plot");

        // Get index of the svg plot we are interested in.
        int svgCount = Locator.css("div:not(.thumbnail) > svg").findElements(getDriver()).size();
        int index = 0;
        for(; index < svgCount; index++)
        {
            if(getSVGText(index).contains("Example Box Plot"))
                break;
        }

        assertNotEquals("Did not find a plot with the expected title.", index, svgCount);

        assertTextPresent("Systolic Blood Pressure xxx/", "Diastolic Blood Pressure /xxx");
        String BOX_PLOT_SVG = "121\n122\n123\n129\n133\n135\n142\n"
                + "76\n78\n80\n82\n84\n86\n88\n90\n"
                + "Example Box Plot\nSystolic Blood Pressure xxx/\nDiastolic Blood Pressure /xxx\n"
                + "121: Min: 78 Max: 78 Q1: 78 Q2: 78 Q3: 78\n"
                + "122: Min: 76 Max: 80 Q1: 77 Q2: 78 Q3: 79\n"
                + "123: Min: 76 Max: 76 Q1: 76 Q2: 76 Q3: 76\n"
                + "129: Min: 76 Max: 87 Q1: 78.75 Q2: 81.5 Q3: 84.25\n"
                + "133: Min: 79 Max: 85 Q1: 80.5 Q2: 82 Q3: 83.5\n"
                + "135: Min: 85 Max: 85 Q1: 85 Q2: 85 Q3: 85\n"
                + "142: Min: 90 Max: 90 Q1: 90 Q2: 90 Q3: 90";
        assertSVG(BOX_PLOT_SVG, index);
    }

    @Test
    public void verifyCustomParticipantView()
    {
        log("Custom Ptid View: create custom ptid view");
        goToProjectHome();
        clickFolder(getFolderName());
        clickTab("Participants");
        clickAndWait(Locator.linkWithText("PTID123"));
        clickAndWait(Locator.linkWithText("Customize View"));
        checkRadioButton(Locator.radioButtonByNameAndValue("useCustomView", "true"));
        setFormElement(Locator.name("customScript"), "This is my custom participant view");
        clickButton("Save and Finish");
        waitForElement(Locator.tagWithText("div", "This is my custom participant view"));

        log("Custom Ptid View: export study folder to the pipeline as individual files");
        exportFolderAsIndividualFiles(getFolderName(), false, false, false);

        log("Custom Ptid View: verify xml file was created in export");
        _fileBrowserHelper.selectFileBrowserItem("export/study/views/settings.xml");
        _fileBrowserHelper.selectFileBrowserItem("export/study/views/participant.html");

        log("Custom Ptid View: import study into subfolder");
        createSubfolderAndImportStudyFromPipeline("Custom Participant View");

        log("Custom Ptid View: verify imported settings");
        clickFolder("Custom Participant View");
        clickTab("Participants");
        clickAndWait(Locator.linkWithText("PTID123"));
        waitForElement(Locator.tagWithText("div", "This is my custom participant view"));
        clickAndWait(Locator.linkWithText("Customize View"));
        assertRadioButtonSelected(Locator.radioButtonByNameAndValue("useCustomView", "true"));
    }

    @Test
    public void verifyVisitProperties()
    {
        String visitLabel = "My visit label";
        String visitSeqNumMin = "999.0";
        String visitSeqNumMax = "999.999";
        String visitProtocolDay = "999.001";
        String visitDescription = "My visit description - " + TRICKY_CHARACTERS_FOR_PROJECT_NAMES + INJECT_CHARS_1 + INJECT_CHARS_2;

        log("Visit Properties: create visit with description");
        goToProjectHome();
        clickFolder(getFolderName());
        _studyHelper.goToManageVisits().goToCreateNewVisit();
        waitForElement(Locator.name("description"));
        setFormElement(Locator.name("label"), visitLabel);
        setFormElement(Locator.name("sequenceNumMin"), visitSeqNumMin);
        setFormElement(Locator.name("sequenceNumMax"), visitSeqNumMax);
        setFormElement(Locator.name("description"), visitDescription);
        clickButton("Save");

        log("Visit Properties: edit visit description and set sequence num target");
        ManageVisitPage manageVisitPage = new ManageVisitPage(getDriver());
        manageVisitPage.goToEditVisit(visitLabel);
        waitForElement(Locator.name("description"));
        assertEquals(visitLabel, getFormElement(Locator.name("label")));
        assertEquals(visitDescription, getFormElement(Locator.name("description")));
        visitDescription += " <b>testing</b>";
        setFormElement(Locator.name("description"), visitDescription);
        setFormElement(Locator.name("protocolDay"), visitProtocolDay);
        clickButton("Save");

        log("Visit Properties: add dataset record using new visit");
        clickTab("Clinical and Assay Data");
        waitAndClickAndWait(Locator.linkWithText(TEST_DATASET_NAME));
        DataRegionTable.DataRegion(getDriver()).find().clickImportBulkData();
        waitForElement(Locator.name("text"));
        setFormElement(Locator.name("text"), "ParticipantId\tSequenceNum\tTestString\nPTID123\t" + visitSeqNumMin + "\tBCD");
        clickButton("Submit");

        log("Visit Properties: export study folder to the pipeline as individual files");
        exportFolderAsIndividualFiles(getFolderName(), false, false, false);

        log("Visit Properties: verify xml file was created in export");
        _fileBrowserHelper.selectFileBrowserItem("export/study/visit_map.xml");

        log("Visit Properties: import study into subfolder");
        createSubfolderAndImportStudyFromPipeline("Visit Properties");

        log("Visit Properties: verify imported settings");
        clickFolder("Visit Properties");
        _studyHelper.goToManageVisits().goToEditVisit(visitLabel);
        waitForElement(Locator.name("description"));
        assertEquals(visitLabel, getFormElement(Locator.name("label")));
        assertEquals(visitDescription, getFormElement(Locator.name("description")));
        assertEquals(visitSeqNumMin, getFormElement(Locator.name("sequenceNumMin")));
        assertEquals(visitSeqNumMax, getFormElement(Locator.name("sequenceNumMax")));
        assertEquals(visitProtocolDay, getFormElement(Locator.name("protocolDay")));

        log("Visit Properties: verify visit description in study navigator hover");
        clickTab("Overview");
        waitAndClickAndWait(Locator.linkWithText("Study Navigator"));
        waitForText(visitLabel);

        click(Locator.css(".labkey-help-pop-up"));
        waitForElement(Locator.xpath("id('helpDivBody')").containing(visitDescription));

        log("Visit Properties: verify visit description in dataset visit column hover");
        clickTab("Clinical and Assay Data");
        waitAndClickAndWait(Locator.linkWithText(TEST_DATASET_NAME));
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.addColumn(new String[]{"ParticipantVisit", "Visit"});
        _customizeViewsHelper.saveDefaultView();
        mouseOver(Locator.tagWithText("td", visitLabel));
        waitForElement(Locator.xpath("id('helpDivBody')").containing(visitDescription));

        log("Visit Properties: remove visit");
        clickFolder(getFolderName());
        _studyHelper.goToManageVisits().goToEditVisit(visitLabel);
        clickButton("Delete Visit");
        waitForText("Do you want to delete Visit");
        clickButton("Delete");
        waitForText("Manage Visits");
    }

    @Test
    public void verifyStudyProperties()
    {
        Map<String, String> origProps = new HashMap<>();
        Map<String, String> newProps = new HashMap<>();
        newProps.put("Investigator", "Investigator");
        newProps.put("Grant", "Grant");
        newProps.put("Species", "Species");
        newProps.put("Description", "Description");
        newProps.put("StartDate", "2013-01-01");
        newProps.put("EndDate", "2013-12-31");
        newProps.put("SubjectNounSingular", "Subject");
        newProps.put("SubjectNounPlural", "Subjects");
        newProps.put("SubjectColumnName", "SubjectId");

        // add tricky chars and injection script, for non-dates
        for (String key : newProps.keySet())
        {
            // subject noun fields have a length constraint, and leave the dates alone
            if (key.equals("SubjectColumnName"))
            {
                // no op, this field gets truncated by the server using ColumnInfo.legalNameFromName
            }
            else if (key.startsWith("Subject"))
                newProps.put(key, newProps.get(key) + TRICKY_CHARACTERS_FOR_PROJECT_NAMES);
            else if (!key.contains("Date"))
                newProps.put(key, newProps.get(key) + TRICKY_CHARACTERS_FOR_PROJECT_NAMES + INJECT_CHARS_1 + INJECT_CHARS_2);
        }

        log("Study Properties: set study properties of interest");
        goToProjectHome();
        clickFolder(getFolderName());
        goToManageStudy();
        waitAndClickAndWait(Locator.linkWithText("Change Study Properties"));
        waitForElement(Locator.name("Investigator"));
        for (String key : newProps.keySet())
        {
            origProps.put(key, getFormElement(Locator.name(key)));
            setFormElement(Locator.name(key), newProps.get(key));
        }
        clickButton("Submit");

        log("Study Properties: export study folder to the pipeline as indiviual files");
        exportFolderAsIndividualFiles(getFolderName(), false, false, false);

        log("Study Properties: verify xml file was created in export");
        _fileBrowserHelper.selectFileBrowserItem("export/study/study.xml");

        log("Study Properties: import study into subfolder");
        createSubfolderAndImportStudyFromPipeline("Study Properties");

        log("Study Properties: verify imported settings");
        clickFolder("Study Properties");
        goToManageStudy();
        waitAndClickAndWait(Locator.linkWithText("Change Study Properties"));
        waitForElement(Locator.name("Investigator"));
        for (String key : newProps.keySet())
        {
            assertEquals(newProps.get(key), getFormElement(Locator.name(key)));
        }

        log("Study Properties: verify display of some properties in overview webpart");
        waitAndClickAndWait(Locator.linkWithText("Overview"));
        waitForText(newProps.get("Investigator"));
        assertTextPresent(newProps.get("Grant"), newProps.get("Description"));
        assertElementPresent(Locator.linkWithText(newProps.get("SubjectNounPlural")), 1);

        log("Study Properties: clean up study properties");
        clickFolder(getFolderName());
        goToManageStudy();
        waitAndClickAndWait(Locator.linkWithText("Change Study Properties"));
        waitForElement(Locator.name("Investigator"));
        for (String key : origProps.keySet())
        {
            setFormElement(Locator.name(key), origProps.get(key));
        }
        clickButton("Submit");
    }

    @Test
    public void verifyCohortProperties()
    {
        String cohort1label = "Cohort1";
        String cohort1count = "10";
        String cohort1description = "First Description" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES + INJECT_CHARS_1 + INJECT_CHARS_2;
        String cohort2label = "Cohort2";
        String cohort2count = "55";
        String cohort2description = "Second Description" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES + INJECT_CHARS_1 + INJECT_CHARS_2;

        log("Cohort Properties: create new cohorts");
        goToProjectHome();
        clickFolder(getFolderName());
        goToManageStudy();
        waitAndClickAndWait(Locator.linkWithText("Manage Cohorts"));
        new DataRegionTable("Cohort", this).clickInsertNewRow();
        waitForElement(Locator.name("quf_label"));
        setFormElement(Locator.name("quf_label"), cohort1label);
        setFormElement(Locator.name("quf_subjectCount"), cohort1count);
        setFormElement(Locator.name("quf_description"), cohort1description);
        clickButton("Submit");
        new DataRegionTable("Cohort", this).clickInsertNewRow();
        waitForElement(Locator.name("quf_label"));
        setFormElement(Locator.name("quf_label"), cohort2label);
        setFormElement(Locator.name("quf_subjectCount"), cohort2count);
        setFormElement(Locator.name("quf_description"), cohort2description);
        clickButton("Submit");

        log("Cohort Properties: export study folder to the pipeline as individual files");
        exportFolderAsIndividualFiles(getFolderName(), false, false, false);

        log("Cohort Properties: verify xml file was created in export");
        _fileBrowserHelper.selectFileBrowserItem("export/study/cohorts.xml");

        log("Cohort Properties: import study into subfolder");
        createSubfolderAndImportStudyFromPipeline("Cohort Properties");

        log("Cohort Properties: verify imported settings");
        clickFolder("Cohort Properties");
        goToManageStudy();
        waitAndClickAndWait(Locator.linkWithText("Manage Cohorts"));
        waitForText(cohort1label);
        DataRegionTable dt = new DataRegionTable("Cohort", getDriver());
        dt.clickEditRow(0);
        waitForText("Update Cohort: " + cohort1label);
        assertEquals(cohort1count, getFormElement(Locator.name("quf_subjectCount")));
        assertEquals(cohort1description, getFormElement(Locator.name("quf_description")));
        clickButton("Cancel");
        waitForText(cohort2label);
        dt.clickEditRow(1);
        waitForText("Update Cohort: " + cohort2label);
        assertEquals(cohort2count, getFormElement(Locator.name("quf_subjectCount")));
        assertEquals(cohort2description, getFormElement(Locator.name("quf_description")));
        clickButton("Cancel");

        log("Cohort Properties: verify display of cohorts in subjects webpart");
        waitAndClickAndWait(Locator.linkWithText("Participants"));
        waitForElement(Locator.tagWithClass("div", "lk-filter-panel-label").withText(cohort1label));
        assertElementPresent(Locator.tagWithClass("div", "lk-filter-panel-label").withText(cohort2label));

        log("Cohort Properties: clean up cohorts");
        clickFolder(getFolderName());
        goToManageStudy();
        waitAndClickAndWait(Locator.linkWithText("Manage Cohorts"));
        clickAndWait(Locator.linkWithText("delete")); // first cohort
        clickAndWait(Locator.linkWithText("delete")); // second cohort
        waitForText("No data to show.");
    }

    /**
     * Study design tables are exported from either the assay schedule or treatment data export categories, they
     * are non-extensible but can have data at both the folder and project level.
     */
    @Test
    public void verifyStudyDesignTables()
    {
        final String FOLDER_NAME = "Study Design Data";
        final String FOLDER_NAME_2 = "Study Design Data2";

        log("StudyDesign Tables");
        goToProjectHome();
        clickFolder(getFolderName());

        Map<String, List<Map>> tableData = new HashMap<>();

        List<Map> assayData = new ArrayList<>();
        assayData.add(toMap(new Object[][]{
                {"scope", PROJECT_SCOPE},
                {"Name", "proj-elispot assay"},
                {"Label", "proj-elispot assay label"},
                {"Type", "proj-Antibody"},
                {"Platform", "proj-Elispot"},
                {"Category", "proj-Cell Engineering"},
                {"TargetFunction", "proj-Neutralization"},
                {"LeadContributor", "Dr. Toop"},
                {"Contact", "dtoop@elispot.org"},
                {"Summary", "Cell Engineering"},
                {"Keywords", "elispot, cell, toop"}
        }));
        assayData.add(toMap(new Object[][]{
                {"scope", FOLDER_SCOPE},
                {"Name", "elisa assay"},
                {"Label", "elisa assay label"},
                {"Type", "Humoral"},
                {"Platform", "Elisa"},
                {"Category", "Epidemiology"},
                {"TargetFunction", "Neutralization"},
                {"Summary", "in construction"},
                {"Keywords", "elisa, epidemiology"}
        }));
        tableData.put("StudyDesignAssays", assayData);

        List<Map> geneData = new ArrayList<>();
        geneData.add(toMap(new Object[][]{
                {"scope", PROJECT_SCOPE},
                {"Name", "env"},
                {"Label", "env label"}
        }));
        geneData.add(toMap(new Object[][]{
                {"scope", FOLDER_SCOPE},
                {"Name", "gag"},
                {"Label", "gag label"}
        }));
        tableData.put("StudyDesignGenes", geneData);

        List<Map> immunogenData = new ArrayList<>();
        immunogenData.add(toMap(new Object[][]{
                {"scope", PROJECT_SCOPE},
                {"Name", "Cp1"},
                {"Label", "Cp1 label"}
        }));
        immunogenData.add(toMap(new Object[][]{
                {"scope", FOLDER_SCOPE},
                {"Name", "Immunogen2"},
                {"Label", "Immunogen2 label"}
        }));
        tableData.put("StudyDesignImmunogenTypes", immunogenData);

        List<Map> labData = new ArrayList<>();
        labData.add(toMap(new Object[][]{
                {"scope", PROJECT_SCOPE},
                {"Name", "Anderson Lab"},
                {"Label", "blank"},
                {"PI", "Dr. Anderson"},
                {"Description", "proj-the PI"},
                {"Summary", "blah blah blah, science"},
                {"Institution", "Medowville"},
        }));
        labData.add(toMap(new Object[][]{
                {"scope", FOLDER_SCOPE},
                {"Name", "Lab2"},
                {"Label", "Lab2 label"},
                {"PI", "Dr. Miller"},
                {"Description", "the PI"},
        }));
        tableData.put("StudyDesignLabs", labData);

        List<Map> routesData = new ArrayList<>();
        routesData.add(toMap(new Object[][]{
                {"scope", PROJECT_SCOPE},
                {"Name", "Intramuscular"},
                {"Label", "Intramuscular label"}
        }));
        routesData.add(toMap(new Object[][]{
                {"scope", FOLDER_SCOPE},
                {"Name", "Epidural"},
                {"Label", "Epidural label"}
        }));
        tableData.put("StudyDesignRoutes", routesData);

        List<Map> sampleTypes = new ArrayList<>();
        sampleTypes.add(toMap(new Object[][]{
                {"scope", PROJECT_SCOPE},
                {"Name", "proj-Type2"},
                {"PrimaryType", "proj-Diabetes"},
                {"ShortSampleCode", "T2"}
        }));
        sampleTypes.add(toMap(new Object[][]{
                {"scope", FOLDER_SCOPE},
                {"Name", "Epidural"},
                {"Name", "Type2"},
                {"PrimaryType", "Diabetes"},
                {"ShortSampleCode", "DB"}
        }));
        tableData.put("StudyDesignSampleTypes", sampleTypes);

        List<Map> subTypeData = new ArrayList<>();
        subTypeData.add(toMap(new Object[][]{
                {"scope", PROJECT_SCOPE},
                {"Name", "ST1"},
                {"Label", "ST1 label"}
        }));
        subTypeData.add(toMap(new Object[][]{
                {"scope", FOLDER_SCOPE},
                {"Name", "ST2"},
                {"Label", "ST2 label"}
        }));
        tableData.put("StudyDesignSubTypes", subTypeData);

        List<Map> unitsData = new ArrayList<>();
        unitsData.add(toMap(new Object[][]{
                {"scope", PROJECT_SCOPE},
                {"Name", "IN"},
                {"Label", "Inches label"}
        }));
        unitsData.add(toMap(new Object[][]{
                {"scope", FOLDER_SCOPE},
                {"Name", "LTR"},
                {"Label", "liters label"}
        }));
        tableData.put("StudyDesignUnits", unitsData);

        log("StudyDesign Tables: populate project level table data");
        populateTableData(tableData, null, PROJECT_SCOPE);

        log("StudyDesign Tables: populate folder level table data");
        populateTableData(tableData, getFolderName(), FOLDER_SCOPE);

        log("StudyDesign Tables: export study folder to the pipeline as individual files");
        exportFolderAsIndividualFiles(getFolderName(), false, false, false);

        log("StudyDesign Tables: import study into subfolder");
        createSubfolderAndImportStudyFromPipeline(FOLDER_NAME);

        log("StudyDesign Tables: verify imported settings preserve existing project level data");
        verifyTableData(tableData, FOLDER_NAME, FOLDER_SCOPE);

        clickFolder(getFolderName());
        exportFolderAsIndividualFiles(getFolderName(), false, false, false);

        // delete all of the project level data, and import into a new folder, both project and
        // folder data should appear
        clickProject(getProjectName());

        for (Map.Entry<String, List<Map>> entry : tableData.entrySet())
        {
            beginAt("/query/" + getProjectName() + "/executeQuery.view?schemaName=study&query.queryName=" + entry.getKey());
            DataRegionTable dt = new DataRegionTable("query", getDriver());
            dt.checkCheckbox(0);
            dt.deleteSelectedRows();
        }

        log("StudyDesign Tables: import study into subfolder");
        createSubfolderAndImportStudyFromPipeline(FOLDER_NAME_2);

        log("StudyDesign Tables: verify imported settings insert both project and folder level data");
        verifyTableData(tableData, FOLDER_NAME_2, null);
    }

    private Map toMap(final Object[][] data)
    {
        ResourceBundle bundle = new ListResourceBundle()
        {
            @Override
            protected Object[][] getContents()
            {
                return data;
            }
        };
        return MapUtils.toMap(bundle);
    }

    private void populateTableData(Map<String, List<Map>> tableData, @Nullable String folderName, String level)
    {
        for (Map.Entry<String, List<Map>> entry : tableData.entrySet())
        {
            if (folderName != null)
                beginAt("/query/" + getProjectName() + "/" + folderName + "/executeQuery.view?schemaName=study&query.queryName=" + entry.getKey());
            else
                beginAt("/query/" + getProjectName() + "/executeQuery.view?schemaName=study&query.queryName=" + entry.getKey());

            for (Map row : entry.getValue())
            {
                Object levelProp = row.get("scope");
                if (levelProp != null && !level.equalsIgnoreCase(levelProp.toString()))
                    continue;
                
                waitForText("Insert");
                DataRegionTable.DataRegion(getDriver()).find().clickInsertNewRow();
                populateFormData(row, "quf_");
                clickButton("Submit");
            }
        }
    }

    private void populateFormData(Map formData, @Nullable String formFieldPrefix)
    {
        for (Object key : formData.keySet())
        {
            String name = (formFieldPrefix != null ? formFieldPrefix : "") + key.toString();
            Locator option = Locator.tagWithName("select", name);
            Locator field = Locator.name(name);
            log("setting form element: " + name);
            if (isElementPresent(option))
            {
                selectOptionByText(option, (String) formData.get(key));
            }
            else if (isElementPresent(field))
            {
                setFormElement(field, (String)formData.get(key));
            }
        }
    }

    private void verifyTableData(Map<String, List<Map>> tableData, @Nullable String folderName, @Nullable String level)
    {
        for (Map.Entry<String, List<Map>> entry : tableData.entrySet())
        {
            if (folderName != null)
                beginAt("/query/" + getProjectName() + "/" + folderName + "/executeQuery.view?schemaName=study&query.queryName=" + entry.getKey());
            else
                beginAt("/query/" + getProjectName() + "/executeQuery.view?schemaName=study&query.queryName=" + entry.getKey());

            for (Map row : entry.getValue())
            {
                Object levelProp = row.get("scope");
                boolean checkPresent = true;
                if (level != null && levelProp != null && !level.equalsIgnoreCase(levelProp.toString()))
                    checkPresent = false;

                for (Map.Entry fieldEntry : ((Set<Map.Entry>)row.entrySet()))
                {
                    String columnName = fieldEntry.getKey().toString();

                    if ("scope".equalsIgnoreCase(columnName))
                        continue;

                    Locator cell = Locator.tagWithClass("table", "labkey-data-region").append(Locator.tag("td")).withText(fieldEntry.getValue().toString());
                    if (checkPresent)
                        assertElementPresent(cell);
                    else
                        assertElementNotPresent(cell);
                }
            }
        }
    }

    /**
     * Verify the study design extensible tables can round trip their custom properties (and data)
     */
    @Test
    public void verifyStudyDesignExtensibleTables()
    {
        final String FOLDER_NAME = "Study Design ExData";

        log("StudyDesign Extensible Tables");
        goToProjectHome();
        clickFolder(getFolderName());

        log("StudyDesign Extensible Tables: adding custom fields");
        // add custom fields to all the extensible tables
        addCustomField("Treatment", "cust_treatment", FieldDefinition.ColumnType.String);
        addCustomField("TreatmentProductMap", "cust_map", FieldDefinition.ColumnType.Integer);
        addCustomField("Product", "cust_product", FieldDefinition.ColumnType.DateAndTime);
        addCustomField("ProductAntigen", "cust_antigen", FieldDefinition.ColumnType.Decimal);
        addCustomField("Personnel", "cust_personnel", FieldDefinition.ColumnType.MultiLine);

        // add data and export
        Map<String, List<Map>> tableData = new LinkedHashMap<>();

        List<Map> treatmentData = new ArrayList<>();
        treatmentData.add(toMap(new Object[][]{
                {"scope", FOLDER_SCOPE},
                {"Label", "Treatment label"},
                {"Description", "Treatment description"},
                {"DescriptionRendererType", "HTML"},
                {"cust_treatment", "custom treatment field"}
        }));
        tableData.put("Treatment", treatmentData);

        List<Map> productData = new ArrayList<>();
        productData.add(toMap(new Object[][]{
                {"scope", FOLDER_SCOPE},
                {"Label", "Product label"},
                {"Role", "Product role"},
                {"cust_product", "2014-03-26 00:00"}
        }));
        tableData.put("Product", productData);

        List<Map> treatmentMap = new ArrayList<>();
        treatmentMap.add(toMap(new Object[][]{
                {"scope", FOLDER_SCOPE},
                {"TreatmentId", "Treatment label"},
                {"ProductId", "Product label"},
                {"cust_map", "100"}
        }));
        tableData.put("TreatmentProductMap", treatmentMap);

        List<Map> productAntigen = new ArrayList<>();
        productAntigen.add(toMap(new Object[][]{
                {"scope", FOLDER_SCOPE},
                {"ProductId", "Product label"},
                {"cust_antigen", "100.0"}
        }));
        tableData.put("ProductAntigen", productAntigen);

        List<Map> personnel = new ArrayList<>();
        personnel.add(toMap(new Object[][]{
                {"scope", FOLDER_SCOPE},
                {"Label", "Personnel label"},
                {"Role", "personnel role"},
                {"cust_personnel", "custom personnel"}
        }));
        tableData.put("Personnel", personnel);

        log("StudyDesign Extensible Tables: populate folder level table data");
        populateTableData(tableData, getFolderName(), FOLDER_SCOPE);

        log("StudyDesign Extensible Tables: export study folder to the pipeline as individual files");
        exportFolderAsIndividualFiles(getFolderName(), false, false, false);

        log("StudyDesign Extensible Tables: import study into subfolder");
        createSubfolderAndImportStudyFromPipeline(FOLDER_NAME);

        log("StudyDesign Extensible Tables: verify imported settings");
        verifyTableData(tableData, FOLDER_NAME, null);
    }

    private void addCustomField(String tableName, String fieldName, FieldDefinition.ColumnType type)
    {
        goToSchemaBrowser();
        selectQuery("study", tableName);
        waitAndClickAndWait(Locator.linkWithText("Edit Definition"));
        DomainDesignerPage domainDesignerPage = new DomainDesignerPage(getDriver());
        domainDesignerPage.fieldsPanel().addField(new FieldDefinition(fieldName, type));
        domainDesignerPage.clickFinish();

        // update the default view to contain the custom column
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.addColumn(fieldName);
        _customizeViewsHelper.saveCustomView("", true);
    }

    private void createSubfolderAndImportStudyFromPipeline(String subfolderName)
    {
        _containerHelper.createSubfolder(getProjectName(), getProjectName(), subfolderName, "Collaboration", null, true);
        clickFolder(subfolderName);
        setPipelineRoot(StudyHelper.getPipelinePath());
        importFolderFromPipeline("/export/folder.xml");
    }

    /**
     * Test for simple roundtripping of some of the specimen categories (14.2 sprint 1) :
     *      - manage display and behavior
     *      - request forms
     *      - notifications
     *      - requestability rules
     */
    @Test
    public void verifySpecimenSettings()
    {
        final String FOLDER_NAME = "Specimen Settings";

        log("Export specimen request settings");
        goToProjectHome();
        clickFolder(getFolderName());
        goToManageStudy();

        log("Configure advanced specimen settings and requests");
        waitAndClickAndWait(Locator.linkWithText("Change Repository Type"));
        click(Locator.radioButtonByNameAndValue("simple", "false"));
        click(Locator.radioButtonByNameAndValue("enableRequests", "true"));
        clickButton("Submit");

        waitAndClickAndWait(Locator.linkWithText("Manage Display and Behavior"));
        log("Export Display and Behavior");
        selectOptionByText(Locator.tagWithName("select", "defaultToCommentsMode"), "Comments Mode");
        selectOptionByText(Locator.tagWithName("select", "enableManualQCFlagging"), "Disabled");
        clickButton("Save");

        waitAndClickAndWait(Locator.linkWithText("Manage New Request Form"));
        log("Export Request forms");
        setFormElement(Locator.tagWithAttribute("input", "value", "Assay Plan"), "Assay Plan-1");
        click(Locator.tagWithClass("i", "fa fa-arrow-down").index(2));
        clickButton("Add New Input", 0);

        Locator input1 = Locator.xpath("(//input[@type='text' and @name='title'])[4]");
        waitForElement(input1);
        setFormElement(input1, "Request #4");
        setFormElement(Locator.xpath("(//input[@type='text' and @name='helpText'])[4]"), "Request help text #4");
        checkCheckbox(Locator.checkboxByNameAndValue("required", "3"));
        clickButton("Add New Input", 0);
        Locator input2 = Locator.xpath("(//input[@type='text' and @name='title'])[5]");
        waitForElement(input2);
        setFormElement(input2, "Request #5");
        setFormElement(Locator.xpath("(//input[@type='text' and @name='helpText'])[5]"), "Request help text #5");
        checkCheckbox(Locator.checkboxByNameAndValue("required", "4"));

        clickButton("Save");

        waitAndClickAndWait(Locator.linkWithText("Manage Notifications"));
        log("Export Notifications");
        click(Locator.radioButtonByNameAndValue("replyToCurrentUser", "false"));
        setFormElement(Locator.tagWithName("input", "replyTo"), NOTIFICATION_EMAIL);

        click(Locator.checkboxByName("ccCheckbox"));
        waitForElement(Locator.xpath("//textarea[@id='cc']").notHidden());
        setFormElement(Locator.xpath("//textarea[@id='cc']"), NOTIFICATION_EMAIL);
        click(Locator.radioButtonByNameAndValue("defaultEmailNotify", "None"));
        click(Locator.radioButtonByNameAndValue("specimensAttachment", "ExcelAttachment"));
        clickButton("Save");

        waitAndClickAndWait(Locator.linkWithText("Manage Requestability rules"));
        log("Export Requestability Rules");
        _extHelper.clickMenuButton(false, "Add Rule", "Custom Query");
        _extHelper.waitForExtDialog("Add Custom Query Rule");
        _extHelper.selectComboBoxItem("Schema:", "auditLog");
        _extHelper.selectComboBoxItem("Query:", "FileSystem");
        _extHelper.selectComboBoxItem("Mark vials:", "Unavailable");
        clickButton("Submit", 0);
        _extHelper.waitForExtDialogToDisappear("Add Custom Query Rule");
        _extHelper.selectExtGridItem("Rule", "At Repository Check", 0, "x-grid-panel", false);
        clickButton("Move Down", 0);
        clickButton("Save");

         log("StudyDesign Extensible Tables: export study folder to the pipeline as individual files");
        exportFolderAsIndividualFiles(getFolderName(), false, false, false);

        log("StudyDesign Extensible Tables: import study into subfolder");
        createSubfolderAndImportStudyFromPipeline(FOLDER_NAME);

        log("Verify specimen request settings");
        goToProjectHome();
        clickFolder(FOLDER_NAME);
        goToManageStudy();

        waitAndClickAndWait(Locator.linkWithText("Manage Display and Behavior"));
        log("Verify Display and Behavior");
        assertEquals("Comments Mode", getSelectedOptionText(Locator.tagWithName("select", "defaultToCommentsMode")));
        assertEquals("Disabled", getSelectedOptionText(Locator.tagWithName("select", "enableManualQCFlagging")));
        clickButton("Cancel");

        waitAndClickAndWait(Locator.linkWithText("Manage New Request Form"));
        log("Verify Request forms");
        assertElementPresent(Locator.xpath("(//input[@type='text' and @name='title'])[1][@value='Assay Plan-1']"));
        assertElementPresent(Locator.xpath("(//input[@type='text' and @name='title'])[2][@value='Shipping Information']"));
        assertElementPresent(Locator.xpath("(//input[@type='text' and @name='title'])[3][@value='Comments']"));
        assertElementPresent(Locator.xpath("(//input[@type='text' and @name='title'])[4][@value='Request #4']"));
        assertElementPresent(Locator.xpath("(//input[@type='text' and @name='title'])[5][@value='Request #5']"));
        clickButton("Cancel");

        waitAndClickAndWait(Locator.linkWithText("Manage Notifications"));
        log("Verify Notifications");
        assertRadioButtonSelected(Locator.radioButtonByNameAndValue("replyToCurrentUser", "false"));
        assertEquals(NOTIFICATION_EMAIL, getFormElement(Locator.tagWithName("input", "replyTo")));

        assertChecked(Locator.checkboxByName("ccCheckbox"));
        waitForElement(Locator.xpath("//textarea[@id='cc']"));
        assertEquals(NOTIFICATION_EMAIL, getFormElement(Locator.xpath("//textarea[@id='cc']")));
        assertRadioButtonSelected(Locator.radioButtonByNameAndValue("defaultEmailNotify", "None"));
        assertRadioButtonSelected(Locator.radioButtonByNameAndValue("specimensAttachment", "ExcelAttachment"));
        clickButton("Cancel");

        waitAndClickAndWait(Locator.linkWithText("Manage Requestability rules"));
        log("Verify Requestability Rules");

        //Locator row = _extHelper.locateExt3GridRow(0, "//div[@id='rulesGrid'");
        assertElementPresent(_extHelper.locateExt3GridRow(1, "//div[@id='rulesGrid']").append(Locator.xpath("//div[contains(text(), 'Administrator Override')]")));
        assertElementPresent(_extHelper.locateExt3GridRow(2, "//div[@id='rulesGrid']").append(Locator.xpath("//div[contains(text(), 'At Repository Check')]")));
        assertElementPresent(_extHelper.locateExt3GridRow(3, "//div[@id='rulesGrid']").append(Locator.xpath("//div[contains(text(), 'Locked In Request Check')]")));
        assertElementPresent(_extHelper.locateExt3GridRow(4, "//div[@id='rulesGrid']").append(Locator.xpath("//div[contains(text(), 'Custom Query: auditLog.FileSystem')]")));

        clickButton("Cancel");
    }

    /**
     * Verify study pproperties can round trip custom properties (and data)
     */
    @Test
    public void verifyExtensibleStudyProperties()
    {
        final String FOLDER_NAME = "Study Properties ExData";

        log("Study Custom Properties");
        goToProjectHome();
        clickFolder(getFolderName());

        log("Study Properties: adding custom fields");
        DomainDesignerPage domainDesignerPage = goToManageStudy().clickEditAdditionalProperties();
        DomainFormPanel domainFormPanel = domainDesignerPage.fieldsPanel();
        domainFormPanel.addField("cust_string").setType(FieldDefinition.ColumnType.String).setLabel("cust_string");
        domainFormPanel.addField("cust_integer").setType(FieldDefinition.ColumnType.Integer).setLabel("cust_integer");
        domainFormPanel.addField("cust_dateTime").setType(FieldDefinition.ColumnType.DateAndTime).setLabel("cust_dateTime");
        domainFormPanel.addField("cust_double").setType(FieldDefinition.ColumnType.Decimal).setLabel("cust_double");
        domainFormPanel.addField("cust_multiline").setType(FieldDefinition.ColumnType.MultiLine).setLabel("cust_multiline");
        domainDesignerPage.clickFinish();

        // add data and export
        Map studyProperties = toMap(new Object[][]{
                {"Label", "Study Properties"},
                {"Investigator", "Dr. Strangelove"},
                {"Grant", "Grantland"},
                {"Species", "Human"},
                {"Description", "study description"},
                {"cust_string", "custom string"},
                {"cust_integer", "2"},
                {"cust_dateTime", "2014-03-26"},
                {"cust_double", "3.14"},
                {"cust_multiline", "custom multiline\ncustom multiline"}
        });

        goToManageStudy();
        waitForText("Change Study Properties");
        clickAndWait(Locator.linkWithText("Change Study Properties"));
        waitForText("Study Properties");
        waitForElement(Ext4Helper.Locators.ext4Button("Submit"));

        populateFormData(studyProperties, null);
        clickButton("Submit");

        log("Study Properties: export study folder to the pipeline as individual files");
        exportFolderAsIndividualFiles(getFolderName(), false, false, false);

        log("Study Properties: import study into subfolder");
        createSubfolderAndImportStudyFromPipeline(FOLDER_NAME);

        log("Study Properties: verify imported settings");
        goToProjectHome();
        clickFolder(FOLDER_NAME);

        goToManageStudy();
        waitForText("Change Study Properties");
        clickAndWait(Locator.linkWithText("Change Study Properties"));
        waitForText("Study Properties");
        waitForElement(Ext4Helper.Locators.ext4Button("Submit"));

        verifyFormData(studyProperties);
    }

    private void verifyFormData(Map formData)
    {
        for (Object key : formData.keySet())
        {
            String name = key.toString();
            log("verifying form element: " + name);

            if (isElementPresent(Locator.tagWithName("input", name)))
                assertEquals((String)formData.get(key), getFormElement(Locator.tagWithName("input", name)));
            else if (isElementPresent(Locator.tagWithName("textarea", name)))
                assertEquals((String)formData.get(key), getFormElement(Locator.tagWithName("textarea", name)));
            else
                fail("Unable to locate form element: " + name);
        }
    }
}
