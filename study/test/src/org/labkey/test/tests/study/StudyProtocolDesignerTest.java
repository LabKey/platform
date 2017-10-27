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

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.query.Filter;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyB;
import org.labkey.test.components.studydesigner.AssayScheduleWebpart;
import org.labkey.test.components.studydesigner.BaseManageVaccineDesignVisitPage;
import org.labkey.test.components.studydesigner.ImmunizationScheduleWebpart;
import org.labkey.test.components.studydesigner.ManageAssaySchedulePage;
import org.labkey.test.components.studydesigner.ManageStudyProductsPage;
import org.labkey.test.components.studydesigner.ManageTreatmentsPage;
import org.labkey.test.components.studydesigner.ManageTreatmentsSingleTablePage;
import org.labkey.test.components.studydesigner.TreatmentDialog;
import org.labkey.test.components.studydesigner.VaccineDesignWebpart;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PortalHelper;
import org.openqa.selenium.WebElement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category({DailyB.class})
public class StudyProtocolDesignerTest extends BaseWebDriverTest
{
    private static final File STUDY_ARCHIVE = TestFileUtils.getSampleData("studies/CohortStudy.zip");
    // Cohorts: defined in study archive
    private static final String[] COHORTS = {"Positive", "Negative"};

    private static final File FOLDER_ARCHIVE = TestFileUtils.getSampleData("FolderExport/ProtocolLookup.folder.zip");
    // lookups: defined in folder archive
    private static final String[] IMMUNOGEN_TYPES = {"Canarypox", "Fowlpox", "Subunit Protein"};
    private static final String[] GENES = {"Env", "Gag"};
    private static final String[] SUBTYPES = {"Clade B", "Clade C"};
    private static final String[] ROUTES = {"Intramuscular (IM)"};
    private static final String[] LABS = {"Lab 1", "McElrath", "Montefiori", "Schmitz"};
    private static final String[] SAMPLE_TYPES = {"Platelets", "Plasma"};
    private static final String[] CHALLENGE_TYPES = {"ChallengeType01", "ChallengeType02", "ChallengeType03"};

    // Study design elements created by this test
    private static final String[] IMMUNOGENS = {"gp100", "Cp1", "Immunogen1"};
    private static final String[] GENBANKIDS = {"GenBank Id 1", "GenBank Id 2"};
    private static final String[] SEQUENCES = {"Sequence A", "Sequence B"};
    private static final String[] ADJUVANTS = {"Adjuvant1", "Freund's incomplete"};
    private static final String[] DOSE_AND_UNITS = {"35ug", "1.6e8 Ad vg", "100ml"};
    private static final String[] TREATMENTS = {"Treatment1", "Treatment2"};
    private static final String[] NEW_ASSAYS = {"Elispot", "Neutralizing Antibodies", "ICS"};
    private static final String[] NEW_COHORTS = {"TestCohort", "OtherTestCohort"};
    private static final String[] CHALLENGES = {"Challenge1", "Challenge2", "Challenge3"};

    private static List<BaseManageVaccineDesignVisitPage.Visit> VISITS = Arrays.asList(
        new BaseManageVaccineDesignVisitPage.Visit("Enrollment", 0.0, 0.0),
        new BaseManageVaccineDesignVisitPage.Visit("Visit 1", 1.0, 1.0),
        new BaseManageVaccineDesignVisitPage.Visit("Visit 2", 2.0, 2.0),
        new BaseManageVaccineDesignVisitPage.Visit("Visit 3", 3.0, 3.0),
        new BaseManageVaccineDesignVisitPage.Visit("Visit 4", 4.0, 4.0)
    );
    private static List<BaseManageVaccineDesignVisitPage.Visit> NEW_VISITS = Arrays.asList(
        new BaseManageVaccineDesignVisitPage.Visit("NewVisit1", 6.0, 7.0),
        new BaseManageVaccineDesignVisitPage.Visit("NewVisit2", 8.0, 8.0)
    );


    private PortalHelper _portalHelper;

    @BeforeClass
    public static void doSetup() throws Exception
    {
        StudyProtocolDesignerTest initTest = (StudyProtocolDesignerTest)getCurrentTest();

        initTest._containerHelper.createProject(initTest.getProjectName(), null);
        initTest.importFolderFromZip(FOLDER_ARCHIVE);

        initTest._containerHelper.createSubfolder(initTest.getProjectName(), initTest.getFolderName(), "Study");
        initTest.importStudyFromZip(STUDY_ARCHIVE);
    }

    @Before
    public void preTest()
    {
        _portalHelper = new PortalHelper(getDriver());
        populateVisitRowIds(getProjectName() + "/" + getFolderName(), false);
        clickProject(getProjectName());
    }

    @Test
    public void testStudyProtocolDesigner()
    {
        testVaccineDesign();
        testTreatmentSchedule();
        testManageTreatmentsSingleTable();
        verifyTreatmentSchedule();
        testAssaySchedule();
        testExportImport();
    }

    @LogMethod
    public void testVaccineDesign()
    {
        clickFolder(getFolderName());
        _portalHelper.addWebPart("Vaccine Design");

        VaccineDesignWebpart vaccineDesignWebpart = new VaccineDesignWebpart(getDriver());
        assertTrue("Unexpected rows in the immunogens or adjuvant table", vaccineDesignWebpart.isEmpty());
        vaccineDesignWebpart.manage();

        // add the first immunogen and define the dose/route values for it
        ManageStudyProductsPage manageStudyProductsPage = new ManageStudyProductsPage(this, true);
        manageStudyProductsPage.addNewImmunogenRow(IMMUNOGENS[0], IMMUNOGEN_TYPES[0], 0);
        manageStudyProductsPage.addNewImmunogenDoseAndRoute(DOSE_AND_UNITS[0], ROUTES[0], 0, 0);

        // add the second immunogen, with HIV antigen records, and define the dose/route values for it
        manageStudyProductsPage.addNewImmunogenRow(IMMUNOGENS[1], IMMUNOGEN_TYPES[1], 1);
        manageStudyProductsPage.addNewImmunogenDoseAndRoute(DOSE_AND_UNITS[1], ROUTES[0], 1, 0);
        manageStudyProductsPage.addNewImmunogenAntigen(GENES[0], SUBTYPES[0], GENBANKIDS[0], SEQUENCES[0], 1, 0);
        manageStudyProductsPage.addNewImmunogenAntigen(GENES[1], SUBTYPES[1], GENBANKIDS[1], SEQUENCES[1], 1, 1);

        // add the third immunogen and define the dose/route values for it
        manageStudyProductsPage.addNewImmunogenRow(IMMUNOGENS[2], IMMUNOGEN_TYPES[2], 2);
        manageStudyProductsPage.addNewImmunogenDoseAndRoute(DOSE_AND_UNITS[1], null, 2, 0);

        // add the first adjuvant and define the dose/route values for it
        manageStudyProductsPage.addNewAdjuvantRow(ADJUVANTS[0], 0);
        manageStudyProductsPage.addNewAdjuvantDoseAndRoute(DOSE_AND_UNITS[2], ROUTES[0], 0, 0);
        manageStudyProductsPage.addNewAdjuvantDoseAndRoute(DOSE_AND_UNITS[2], null, 0, 1);
        manageStudyProductsPage.addNewAdjuvantDoseAndRoute(null, ROUTES[0], 0, 2);

        // add the second adjuvant with no dose/route values
        manageStudyProductsPage.addNewAdjuvantRow(ADJUVANTS[1], 1);

        // add a couple of challenges.
        manageStudyProductsPage.addNewChallengeRow(CHALLENGES[0], CHALLENGE_TYPES[0], 0);
        manageStudyProductsPage.addNewChallengesDoseAndRoute(DOSE_AND_UNITS[0], ROUTES[0], 0, 0);
        manageStudyProductsPage.addNewChallengesDoseAndRoute(DOSE_AND_UNITS[1], ROUTES[0], 0, 1);
        manageStudyProductsPage.addNewChallengeRow(CHALLENGES[1], CHALLENGE_TYPES[1], 1);
        manageStudyProductsPage.addNewChallengesDoseAndRoute(DOSE_AND_UNITS[2], null, 1, 0);
        manageStudyProductsPage.addNewChallengeRow(CHALLENGES[2], CHALLENGE_TYPES[2], 2);
        manageStudyProductsPage.addNewChallengesDoseAndRoute(DOSE_AND_UNITS[1], ROUTES[0], 2, 0);

        manageStudyProductsPage.save();

        verifyImmunogenTable();
        verifyAdjuvantTable();
        verifyChallengesTable();
    }

    @LogMethod
    public void testTreatmentSchedule()
    {
        clickTab("Overview");
        _portalHelper.addWebPart("Immunization Schedule");

        //TODO switch to Immunization Schedule webpart after test is updated for single table UI
        clickAndWait(Locator.linkWithText("Manage Study"));
        clickAndWait(Locator.linkWithText("MANAGE TREATMENTS"));


//        ImmunizationScheduleWebpart immunizationScheduleWebpart = new ImmunizationScheduleWebpart(getDriver());
//        assertFalse("Unexpected rows in the immunization schedule table", immunizationScheduleWebpart.isEmpty());
//        assertEquals("Unexpected number of cohort rows", COHORTS.length, immunizationScheduleWebpart.getCohortRowCount());
//        immunizationScheduleWebpart.manage();

        // add the first treatment and define the study products for it
        ManageTreatmentsPage treatmentsPage = new ManageTreatmentsPage(this, true);
        treatmentsPage.addNewTreatmentRow(TREATMENTS[0], TREATMENTS[0] + " Description", 0);
        treatmentsPage.addNewTreatmentImmunogenRow(IMMUNOGENS[0], DOSE_AND_UNITS[0], ROUTES[0], 0, 0);
        treatmentsPage.addNewTreatmentImmunogenRow(IMMUNOGENS[1], DOSE_AND_UNITS[1], ROUTES[0], 0, 1);
        treatmentsPage.addNewTreatmentAdjuvantRow(ADJUVANTS[0], DOSE_AND_UNITS[2], null, 0, 0);
        treatmentsPage.addNewChallengesRow(CHALLENGES[0], DOSE_AND_UNITS[0], ROUTES[0], 0, 0);

        // add the second treatment and define the study products for it
        treatmentsPage.addNewTreatmentRow(TREATMENTS[1], TREATMENTS[1] + " Description", 1);
        treatmentsPage.addNewTreatmentImmunogenRow(IMMUNOGENS[2], DOSE_AND_UNITS[1], null, 1, 0);
        treatmentsPage.addNewTreatmentAdjuvantRow(ADJUVANTS[1], null, null, 1, 0);
        treatmentsPage.addNewChallengesRow(CHALLENGES[1], DOSE_AND_UNITS[2], null, 1, 0);
        treatmentsPage.addNewChallengesRow(CHALLENGES[2], DOSE_AND_UNITS[1], ROUTES[0], 1, 1);
        // add all existing visits as columns to the cohort grid
        treatmentsPage.addAllExistingVisitColumns();

        // create two new visits to add as columns to the cohort grid
        for (BaseManageVaccineDesignVisitPage.Visit visit : NEW_VISITS)
            treatmentsPage.addNewVisitColumn(visit.getLabel(), visit.getRangeMin(), visit.getRangeMax());
        populateVisitRowIds(getProjectName() + "/" + getFolderName(), false);

        // add visit/treatment mappings for the Positive cohort
        treatmentsPage.addCohortTreatmentMapping(VISITS.get(0), TREATMENTS[0], 1);
        treatmentsPage.addCohortTreatmentMapping(VISITS.get(1), TREATMENTS[0], 1);
        treatmentsPage.addCohortTreatmentMapping(NEW_VISITS.get(0), TREATMENTS[1], 1);

        // add the first new cohort and define the treatment/visit mappings for it
        treatmentsPage.addNewCohortRow(NEW_COHORTS[0], 2, 2);
        treatmentsPage.addCohortTreatmentMapping(VISITS.get(0), TREATMENTS[0], 2);
        treatmentsPage.addCohortTreatmentMapping(VISITS.get(2), TREATMENTS[1], 2);

        // add the second new cohort and define the treatment/visit mappings for it
        treatmentsPage.addNewCohortRow(NEW_COHORTS[1], 5, 3);
        treatmentsPage.addCohortTreatmentMapping(VISITS.get(0), TREATMENTS[0], 3);
        treatmentsPage.addCohortTreatmentMapping(NEW_VISITS.get(1), TREATMENTS[1], 3);

        treatmentsPage.save();

    }

    @LogMethod
    public void testAssaySchedule()
    {
        clickTab("Overview");
        _portalHelper.addWebPart("Assay Schedule");

        AssayScheduleWebpart assayScheduleWebpart = new AssayScheduleWebpart(getDriver());
        assertTrue("Unexpected rows in the assay schedule table", assayScheduleWebpart.isEmpty());
        assayScheduleWebpart.manage();

        // show all of the existing visit columns
        ManageAssaySchedulePage assaySchedulePage = new ManageAssaySchedulePage(this, true);
        assaySchedulePage.addAllExistingVisitColumns();

        // add the first assay and define the properties for it
        assaySchedulePage.addNewAssayRow(NEW_ASSAYS[0] + " Label", null, 0);
        assaySchedulePage.setBaseProperties(LABS[0] + " Label", null, null, null, null, 0);
        assaySchedulePage.selectVisits(Arrays.asList(VISITS.get(0), NEW_VISITS.get(0)), 0);

        // add the second assay and define the properties for it
        assaySchedulePage.addNewAssayRow(NEW_ASSAYS[1] + " Label", null, 1);
        assaySchedulePage.setBaseProperties(LABS[1] + " Label", null, null, null, null, 1);
        assaySchedulePage.selectVisits(Arrays.asList(VISITS.get(1), NEW_VISITS.get(1)), 1);


        // add the third assay and define the properties for it
        assaySchedulePage.addNewAssayRow(NEW_ASSAYS[2] + " Label", null, 2);
        assaySchedulePage.setBaseProperties(LABS[2] + " Label", null, null, null, null, 2);
        assaySchedulePage.selectVisits(Arrays.asList(VISITS.get(2)), 2);

        // add the third assay, again, and define it with different properties
        assaySchedulePage.addNewAssayRow(NEW_ASSAYS[2] + " Label", null, 3);
        assaySchedulePage.setBaseProperties(LABS[3] + " Label", null, null, null, null, 3);

        // set the assay plan value
        assaySchedulePage.setAssayPlan("Do some exciting science!");

        assaySchedulePage.save();

        verifyAssaySchedule();
    }

    @LogMethod
    public void testManageTreatmentsSingleTable()
    {
        ManageTreatmentsSingleTablePage singleManagementTable;
        TreatmentDialog treatmentDialog;
        List<String> EXPECTED_HEADERS = new ArrayList<>(Arrays.asList("Group / Cohort", "Participant Count", "Enrollment", "Visit 1", "Visit 2", "NewVisit1", "NewVisit2"));
        String tempText;

        // These are the expected Immunogen options:
        // Cp1 - 1.6e8 Ad vg : Intramuscular (IM)
        // gp100 - 35ug : Intramuscular (IM)
        // Immunogen1 - 1.6e8 Ad vg :
        List<String> EXPECTED_IMMUNOGEN_VALUES = new ArrayList<>(Arrays.asList(
                IMMUNOGENS[1] + " - " + DOSE_AND_UNITS[1] + " : " + ROUTES[0],
                IMMUNOGENS[0] + " - " + DOSE_AND_UNITS[0] + " : " + ROUTES[0],
                IMMUNOGENS[2] + " - " + DOSE_AND_UNITS[1] + " :"
        ));

        // These are the expected Adjuvant options:
        // Adjuvant1 - 100ml :
        // Adjuvant1 - 100ml : Intramuscular (IM)
        // Adjuvant1 - : Intramuscular (IM)
        // Freund's incomplete
        List<String> EXPECTED_ADJUVANT_VALUES = new ArrayList<>(Arrays.asList(
                ADJUVANTS[0] + " - " + DOSE_AND_UNITS[2] + " :",
                ADJUVANTS[0] + " - " + DOSE_AND_UNITS[2] + " : " + ROUTES[0],
                ADJUVANTS[0] + " - : " + ROUTES[0],
                ADJUVANTS[1]
        ));

        // These are the expected Chhallenge options:
        // Challenge1 - 1.6e8 Ad vg : Intramuscular (IM)
        // Challenge1 - 35ug : Intramuscular (IM)
        // Challenge2 - 100ml :
        // Challenge3 - 1.6e8 Ad vg : Intramuscular (IM)
        List<String> EXPECTED_CHALLENGE_VALUES = new ArrayList<>(Arrays.asList(
                CHALLENGES[0] + " - " + DOSE_AND_UNITS[1] + " : " + ROUTES[0],
                CHALLENGES[0] + " - " + DOSE_AND_UNITS[0] + " : " + ROUTES[0],
                CHALLENGES[1] + " - " + DOSE_AND_UNITS[2] + " :",
                CHALLENGES[2] + " - " + DOSE_AND_UNITS[1] + " : " + ROUTES[0]
        ));


        clickFolder("ProtocolDesigner Study");
        clickTab("Overview");

        log("Validate the Treatment dialog for the single table management.");

        ImmunizationScheduleWebpart immunizationScheduleWebpart = new ImmunizationScheduleWebpart(getDriver());
        sleep(1000);
        immunizationScheduleWebpart.manage();

        log("Validate that the column headers are as expected, and that the number of rows is as expected.");

        singleManagementTable = new ManageTreatmentsSingleTablePage(this);
        List<String> actualHeaders = singleManagementTable.columnHeaders();
        for(String expectedHeader : EXPECTED_HEADERS)
        {
            Assert.assertTrue("Did not find header '" + expectedHeader + "'", actualHeaders.contains(expectedHeader));
        }

        Assert.assertEquals("Number of rows not as expected.", 6, singleManagementTable.numberOfRows());

        log("Validate that the cell we are going to click on has the expected default value.");
        Assert.assertEquals("Value of cell not as expected. ", TREATMENTS[1], singleManagementTable.getCellValue(3,5));

        log("Validate that the options shown in the treatment dialog are as expected.");

        treatmentDialog = singleManagementTable.clickCell(3,5);
        log("Validate the number of labels in the Immunogen section.");
        Assert.assertEquals("Number of labels in the Immunogen section not as expected.", 3, treatmentDialog.sectionOptions(TreatmentDialog.Sections.Immunogen).size());
        for(WebElement we : treatmentDialog.sectionOptions(TreatmentDialog.Sections.Immunogen))
        {
            tempText = we.getText().trim();
            Assert.assertTrue("Found unexpected value '" + tempText + "' in Immunogen section.", EXPECTED_IMMUNOGEN_VALUES.contains(tempText));
        }

        log("Validate the number of labels in the Adjuvant section.");
        Assert.assertEquals("Number of labels in the Adjuvant section not as expected.", 4, treatmentDialog.sectionOptions(TreatmentDialog.Sections.Adjuvant).size());
        for(WebElement we : treatmentDialog.sectionOptions(TreatmentDialog.Sections.Adjuvant))
        {
            tempText = we.getText().trim();
            Assert.assertTrue("Found unexpected value '" + tempText + "' in Adjuvant section.", EXPECTED_ADJUVANT_VALUES.contains(tempText));
        }

        log("Validate the number of labels in the Challenge section.");
        Assert.assertEquals("Number of labels in the Challenge section not as expected.", 4, treatmentDialog.sectionOptions(TreatmentDialog.Sections.Challenge).size());
        for(WebElement we : treatmentDialog.sectionOptions(TreatmentDialog.Sections.Challenge))
        {
            tempText = we.getText().trim();
            Assert.assertTrue("Found unexpected value '" + tempText + "' in Challenge section.", EXPECTED_CHALLENGE_VALUES.contains(tempText));
        }

        log("Validate that the expected values are selected.");
        List<WebElement> selectedValues = treatmentDialog.getSelectedValues();
        Assert.assertEquals("Number of values selected not as expected.", 4, selectedValues.size());
        Assert.assertEquals(EXPECTED_IMMUNOGEN_VALUES.get(2), selectedValues.get(0).getText());
        Assert.assertEquals(EXPECTED_ADJUVANT_VALUES.get(3), selectedValues.get(1).getText());
        Assert.assertEquals(EXPECTED_CHALLENGE_VALUES.get(2), selectedValues.get(2).getText());
        Assert.assertEquals(EXPECTED_CHALLENGE_VALUES.get(3), selectedValues.get(3).getText());

        log("Change the setting by selecting a new challenge");
        treatmentDialog.selectOption(TreatmentDialog.Sections.Challenge, EXPECTED_CHALLENGE_VALUES.get(1));

        treatmentDialog.clickOk();
        sleep(500);

        log("Validate that the text in the grid is updated. ");
        tempText = IMMUNOGENS[2] + "|" + ADJUVANTS[1] + "|" + CHALLENGES[0] + "|" + CHALLENGES[1] + "|" + CHALLENGES[2];
        Assert.assertEquals("Cell value not as expected after update.", tempText, singleManagementTable.getCellValue(3,5));

        log("Reopen the dialog and validate the updates are shown.");
        treatmentDialog = singleManagementTable.clickCell(3,5);
        selectedValues = treatmentDialog.getSelectedValues();
        Assert.assertEquals("Number of values selected not as expected.", 5, selectedValues.size());
        Assert.assertEquals(EXPECTED_IMMUNOGEN_VALUES.get(2), selectedValues.get(0).getText());
        Assert.assertEquals(EXPECTED_ADJUVANT_VALUES.get(3), selectedValues.get(1).getText());
        Assert.assertEquals(EXPECTED_CHALLENGE_VALUES.get(1), selectedValues.get(2).getText());
        Assert.assertEquals(EXPECTED_CHALLENGE_VALUES.get(2), selectedValues.get(3).getText());
        Assert.assertEquals(EXPECTED_CHALLENGE_VALUES.get(3), selectedValues.get(4).getText());

        treatmentDialog.clickCancel();
        sleep(500);

        log("Now add a treatment.");
        treatmentDialog = singleManagementTable.clickCell(1,7);
        treatmentDialog.selectOption(TreatmentDialog.Sections.Immunogen, EXPECTED_IMMUNOGEN_VALUES.get(1))
                .selectOption(TreatmentDialog.Sections.Adjuvant, EXPECTED_ADJUVANT_VALUES.get(2))
                .selectOption(TreatmentDialog.Sections.Challenge, EXPECTED_CHALLENGE_VALUES.get(1));
        treatmentDialog.clickOk();
        sleep(500);
        log("Validate that the text in the grid is as expected. ");
        tempText = IMMUNOGENS[0] + "|" + ADJUVANTS[0] + "|" + CHALLENGES[0];
        log(tempText);
        log(singleManagementTable.getCellValue(1,7));
        Assert.assertEquals("Cell value not as expected after update.", tempText, singleManagementTable.getCellValue(1,7));

        log("Now add another treatment that should map to a named treatment.");
        treatmentDialog = singleManagementTable.clickCell(1,5);
        treatmentDialog.selectOption(TreatmentDialog.Sections.Immunogen, EXPECTED_IMMUNOGEN_VALUES.get(0))
                .selectOption(TreatmentDialog.Sections.Immunogen, EXPECTED_IMMUNOGEN_VALUES.get(1))
                .selectOption(TreatmentDialog.Sections.Adjuvant, EXPECTED_ADJUVANT_VALUES.get(0))
                .selectOption(TreatmentDialog.Sections.Challenge, EXPECTED_CHALLENGE_VALUES.get(1));
        treatmentDialog.clickOk();
        sleep(500);
        log("Validate that the text in the grid is as expected. ");
        tempText = IMMUNOGENS[0] + "|" + IMMUNOGENS[1] + "|" + ADJUVANTS[0] + "|" + CHALLENGES[0];
        Assert.assertEquals("Cell value not as expected after update.", tempText, singleManagementTable.getCellValue(1,5));

        sleep(1000);
        clickButton("Save");
        sleep(1000);

        log("Now revisit the single table and make sure the updates were saved.");
        immunizationScheduleWebpart = new ImmunizationScheduleWebpart(getDriver());
        immunizationScheduleWebpart.manage();
        sleep(1000);
        singleManagementTable = new ManageTreatmentsSingleTablePage(this);

        log("Validate that the cell has the text version of the update.");
        tempText = IMMUNOGENS[0] + "|" + ADJUVANTS[0] + "|" + CHALLENGES[0];
        Assert.assertEquals("Cell value not as expected after update.", tempText, singleManagementTable.getCellValue(1,7));
        log("Validate the dialog reflects the changes.");
        treatmentDialog = singleManagementTable.clickCell(1,7);
        Assert.assertEquals("Number of values selected not as expected.", 3, treatmentDialog.getSelectedValues().size());
        selectedValues = treatmentDialog.getSelectedValues();
        Assert.assertEquals(EXPECTED_IMMUNOGEN_VALUES.get(1), selectedValues.get(0).getText());
        Assert.assertEquals(EXPECTED_ADJUVANT_VALUES.get(2), selectedValues.get(1).getText());
        Assert.assertEquals(EXPECTED_CHALLENGE_VALUES.get(1), selectedValues.get(2).getText());
        treatmentDialog.clickCancel();
        sleep(500);

        tempText = TREATMENTS[0];
        log("Validate that this cell now says: " + tempText);
        Assert.assertEquals("Cell value not as expected after update.", tempText, singleManagementTable.getCellValue(1,5));

        log("We are done, go to Overview tab.");
        clickTab("Overview");

    }

    @LogMethod
    public void testExportImport()
    {
        final String importedFolder = "Imported " + getFolderName();
        File downloadedFolder = exportFolderToBrowserAsZip();

        _containerHelper.createSubfolder(getProjectName(), importedFolder);
        importFolderFromZip(downloadedFolder);

        verifyImportedProtocol(importedFolder);
    }

    private void populateVisitRowIds(String folderPath, boolean forceOverride)
    {
        for (BaseManageVaccineDesignVisitPage.Visit visit : VISITS)
        {
            if (forceOverride || visit.getRowId() == null)
                visit.setRowId(queryVisitRowId(folderPath, visit));
        }

        for (BaseManageVaccineDesignVisitPage.Visit visit : NEW_VISITS)
        {
            if (forceOverride || visit.getRowId() == null)
                visit.setRowId(queryVisitRowId(folderPath, visit));
        }
    }

    private Integer queryVisitRowId(String folderPath, BaseManageVaccineDesignVisitPage.Visit visit)
    {
        SelectRowsCommand command = new SelectRowsCommand("study", "Visit");
        command.setFilters(Arrays.asList(new Filter("Label", visit.getLabel())));
        SelectRowsResponse response;
        try
        {
            response = command.execute(createDefaultConnection(true), folderPath);
        }
        catch (IOException | CommandException e)
        {
            throw new RuntimeException(e);
        }

        List<Map<String, Object>> rows = response.getRows();
        if (rows.size() == 1)
            return Integer.parseInt(rows.get(0).get("RowId").toString());

        return null;
    }

    @LogMethod
    private void verifyImportedProtocol(String folderName)
    {
        clickFolder(folderName);

        verifyImmunogenTable();
        verifyAdjuvantTable();
        verifyTreatmentSchedule();
        verifyChallengesTable();

        // the imported folder will have different visit RowIds, so re-populate
        populateVisitRowIds(getProjectName() + "/" + folderName, true);

        verifyAssaySchedule();
    }

    @LogMethod(quiet = true)
    private void verifyImmunogenTable()
    {
        VaccineDesignWebpart vaccineDesignWebpart = new VaccineDesignWebpart(getDriver());
        assertFalse(vaccineDesignWebpart.isEmpty());

        assertEquals("Unexpected number of immunogen rows", 3, vaccineDesignWebpart.getImmunogenRowCount());
        for (int i = 0; i < IMMUNOGENS.length; i++)
        {
            assertEquals("Unexpected immunogen label at row " + i, IMMUNOGENS[i], vaccineDesignWebpart.getImmunogenCellDisplayValue("Label", i));
            assertEquals("Unexpected immunogen type at row " + i, IMMUNOGEN_TYPES[i] + " Label", vaccineDesignWebpart.getImmunogenCellDisplayValue("Type", i));

            int antigenSubgridRowCount = vaccineDesignWebpart.getImmunogenAntigenRowCount(i);
            if (antigenSubgridRowCount > 0)
            {
                for (int j = 0; j < antigenSubgridRowCount; j++)
                {
                    assertEquals("", GENES[j] + " Label", vaccineDesignWebpart.getImmunogenAntigenRowCellDisplayValue("Gene", i, j));
                    assertEquals("", SUBTYPES[j] + " Label", vaccineDesignWebpart.getImmunogenAntigenRowCellDisplayValue("SubType", i, j));
                    assertEquals("", GENBANKIDS[j], vaccineDesignWebpart.getImmunogenAntigenRowCellDisplayValue("GenBankId", i, j));
                    assertEquals("", SEQUENCES[j], vaccineDesignWebpart.getImmunogenAntigenRowCellDisplayValue("Sequence", i, j));
                }
            }
        }
    }

    @LogMethod(quiet = true)
    private void verifyChallengesTable()
    {
        VaccineDesignWebpart vaccineDesignWebpart = new VaccineDesignWebpart(getDriver());
        assertFalse(vaccineDesignWebpart.isEmpty());

        assertEquals("Unexpected number of challenges rows", CHALLENGES.length, vaccineDesignWebpart.getChallengesRowCount());

        for (int i = 0; i < CHALLENGES.length; i++)
        {
            assertEquals("Unexpected challenge label at row " + i, CHALLENGES[i], vaccineDesignWebpart.getChallengeCellDisplayValue("Label", i));
            assertEquals("Unexpected challenge type at row " + i, CHALLENGE_TYPES[i] + " Label", vaccineDesignWebpart.getChallengeCellDisplayValue("Type", i));
        }
    }

    @LogMethod(quiet = true)
    private void verifyAdjuvantTable()
    {
        VaccineDesignWebpart vaccineDesignWebpart = new VaccineDesignWebpart(getDriver());
        assertFalse(vaccineDesignWebpart.isEmpty());

        assertEquals("Unexpected number of adjuvant rows", 2, vaccineDesignWebpart.getAdjuvantRowCount());
        for (int i = 0; i < ADJUVANTS.length; i++)
            assertEquals("Unexpected adjuvant label at row " + i, ADJUVANTS[i], vaccineDesignWebpart.getAdjuvantCellDisplayValue("Label", i));
    }

    @LogMethod(quiet = true)
    private void verifyTreatmentSchedule()
    {
        //TODO switch to Immunization Schedule webpart after test is updated for single table UI
        clickTab("Overview");

        ImmunizationScheduleWebpart immunizationScheduleWebpart = new ImmunizationScheduleWebpart(getDriver());
        assertFalse("Expected rows in the immunization schedule table", immunizationScheduleWebpart.isEmpty());
        assertEquals("Unexpected number of cohort rows", COHORTS.length + NEW_COHORTS.length, immunizationScheduleWebpart.getCohortRowCount());

        Map<String, String> visitTreatments;
        List<String> allVisitLabels = Arrays.asList(
                VISITS.get(0).getLabel(), VISITS.get(1).getLabel(), VISITS.get(2).getLabel(),
                NEW_VISITS.get(0).getLabel(), NEW_VISITS.get(1).getLabel()
        );

        visitTreatments = new HashMap<>();
        visitTreatments.put(VISITS.get(2).getLabel(), TREATMENTS[0]);
        visitTreatments.put(NEW_VISITS.get(1).getLabel(), IMMUNOGENS[0] + "|" + ADJUVANTS[0] + "|" + CHALLENGES[0]);
        verifyCohortRow(immunizationScheduleWebpart, 0, COHORTS[1], null, visitTreatments, allVisitLabels);

        visitTreatments = new HashMap<>();
        visitTreatments.put(VISITS.get(0).getLabel(), TREATMENTS[0]);
        visitTreatments.put(NEW_VISITS.get(1).getLabel(), TREATMENTS[1]);
        verifyCohortRow(immunizationScheduleWebpart, 1, NEW_COHORTS[1], 5, visitTreatments, allVisitLabels);

        visitTreatments = new HashMap<>();
        visitTreatments.put(VISITS.get(0).getLabel(), TREATMENTS[0]);
        visitTreatments.put(VISITS.get(1).getLabel(), TREATMENTS[0]);
        visitTreatments.put(NEW_VISITS.get(0).getLabel(), TREATMENTS[1]);
        verifyCohortRow(immunizationScheduleWebpart, 2, COHORTS[0], null, visitTreatments, allVisitLabels);

        visitTreatments = new HashMap<>();
        visitTreatments.put(VISITS.get(0).getLabel(), TREATMENTS[0]);
        visitTreatments.put(VISITS.get(2).getLabel(), IMMUNOGENS[2] + "|" + ADJUVANTS[1] + "|" + CHALLENGES[0] + "|" + CHALLENGES[1] + "|" + CHALLENGES[2]);
        verifyCohortRow(immunizationScheduleWebpart, 3, NEW_COHORTS[0], 2, visitTreatments, allVisitLabels);

//        // Check the 1st and last rows after everything else. If this is after update visit treatments will have changed.
//        if(!afterUpdate)
//        {
//            visitTreatments = new HashMap<>();
//            verifyCohortRow(immunizationScheduleWebpart, 0, COHORTS[1], null, visitTreatments, allVisitLabels);
//
//            visitTreatments = new HashMap<>();
//            visitTreatments.put(VISITS.get(0).getLabel(), TREATMENTS[0]);
//            visitTreatments.put(VISITS.get(2).getLabel(), TREATMENTS[1]);
//            verifyCohortRow(immunizationScheduleWebpart, 3, NEW_COHORTS[0], 2, visitTreatments, allVisitLabels);
//
//        }
//        else
//        {
//
//        }
//
    }

    private void verifyCohortRow(ImmunizationScheduleWebpart table, int rowIndex, String label, Integer subjectCount, Map<String, String> visitTreatments, List<String> allVisitLabels)
    {
        assertEquals("Unexpected cohort label at row " + rowIndex, label, table.getCohortCellDisplayValue("Label", rowIndex));
        assertEquals("Unexpected cohort subject count at row " + rowIndex, subjectCount != null ? subjectCount+"" : "", table.getCohortCellDisplayValue("SubjectCount", rowIndex));

        for (String visitLabel : allVisitLabels)
        {
            if (visitTreatments.containsKey(visitLabel))
                assertEquals("Unexpected visit/treatment mapping", visitTreatments.get(visitLabel) + " ?", table.getCohortCellDisplayValue(visitLabel, rowIndex));
            else
                assertEquals("Unexpected visit/treatment mapping", "", table.getCohortCellDisplayValue(visitLabel, rowIndex));
        }
    }

    @LogMethod(quiet = true)
    private void verifyAssaySchedule()
    {
        AssayScheduleWebpart assayScheduleWebpart = new AssayScheduleWebpart(getDriver());
        assertFalse("Expected rows in the immunization schedule table", assayScheduleWebpart.isEmpty());
        assertEquals("Unexpected number of assay rows", NEW_ASSAYS.length + 1, assayScheduleWebpart.getAssayRowCount());

        verifyAssayRow(assayScheduleWebpart, 0, NEW_ASSAYS[0] + " Label", LABS[0] + " Label", Arrays.asList(VISITS.get(0), NEW_VISITS.get(0)));
        verifyAssayRow(assayScheduleWebpart, 1, NEW_ASSAYS[2] + " Label", LABS[2] + " Label", Arrays.asList(VISITS.get(2)));
        verifyAssayRow(assayScheduleWebpart, 2, NEW_ASSAYS[2] + " Label", LABS[3] + " Label", Collections.emptyList());
        verifyAssayRow(assayScheduleWebpart, 3, NEW_ASSAYS[1] + " Label", LABS[1] + " Label", Arrays.asList(VISITS.get(1), NEW_VISITS.get(1)));

        assertEquals("Unexpected assay plan", "Do some exciting science!", assayScheduleWebpart.getAssayPlan());
    }

    private void verifyAssayRow(AssayScheduleWebpart table, int rowIndex, String name, String lab, List<BaseManageVaccineDesignVisitPage.Visit> visits)
    {
        assertEquals("Unexpected assay name at row " + rowIndex, name, table.getAssayCellDisplayValue("AssayName", rowIndex));
        assertEquals("Unexpected assay lab at row " + rowIndex, lab, table.getAssayCellDisplayValue("Lab", rowIndex));

        for (BaseManageVaccineDesignVisitPage.Visit visit : visits)
            assertEquals("Unexpected assay visit mapping", "\u2713", table.getAssayCellDisplayValue("VisitMap", rowIndex, visit.getRowId()+""));
    }

    @Nullable
    @Override
    protected String getProjectName()
    {
        return "StudyProtocolDesignerTest Project";
    }

    protected String getFolderName()
    {
        return "ProtocolDesigner Study";
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }

    public static class Locators
    {
        public static Locator.XPathLocator studyProtocolWebpartGrid(String title)
        {
            return Locator.tagWithClass("table", "labkey-data-region").withPredicate(Locator.tagWithClass("div", "study-vaccine-design-header").withText(title));
        }
    }
}
