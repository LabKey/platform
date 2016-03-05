/*
 * Copyright (c) 2010-2015 LabKey Corporation
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
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyB;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.LogMethod;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@Category({DailyB.class})
public class CohortTest extends BaseWebDriverTest
{
    private static final String PROJECT_NAME = "Cohort Test Project";
    private static final File COHORT_STUDY_ZIP = TestFileUtils.getSampleData("studies/CohortStudy.zip");
    private static final String XPATH_SPECIMEN_REPORT_TABLE_NEGATIVE = "//td[@id='bodypanel']/div[2]/div[1]/table";
    private static final String XPATH_SPECIMEN_REPORT_TABLE_POSITIVE = "//td[@id='bodypanel']/div[2]/div[2]/table";
    private static final String XPATH_SPECIMEN_REPORT_TABLE_UNASSIGNED = "//td[@id='bodypanel']/div[2]/div[3]/table";
    private static final String TABLE_NEGATIVE = "tableNegative";
    private static final String TABLE_POSITIVE = "tablePositive";
    private static final String TABLE_UNASSIGNED = "tableUnassigned";
    private static final String INFECTED_1 = "Infected1";
    private static final String INFECTED_2 = "Infected2";
    private static final String INFECTED_3 = "Infected3";
    private static final String INFECTED_4 = "Infected4";
    private static final String UNASSIGNED_1 = "Unassigned1";
    private static final String XPATH_COHORT_ASSIGNMENT_TABLE = "//table[@id='participant-cohort-assignments']";
    private static final String COHORT_TABLE = "Cohort Table";
    private static final String COHORT_NEGATIVE = "Negative";
    private static final String COHORT_POSITIVE = "Positive";
    private static final String COHORT_NOCOHORT = "Not in any cohort";
    private static final String[] PTIDS_ALL = {INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4, UNASSIGNED_1};
    private static final String[] PTIDS_POSITIVE = {INFECTED_1, INFECTED_2, INFECTED_3};
    private static final String[] PTIDS_NEGATIVE = {INFECTED_4};
    private static final String[] PTIDS_NOCOHORT = {UNASSIGNED_1};
    private static final String[] PTIDS_POSITIVE_NEGATIVE = {INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4};
    private static final String[] PTIDS_POSITIVE_NOCOHORT = {INFECTED_1, INFECTED_2, INFECTED_3, UNASSIGNED_1};

    @Test
    public void testSteps()
    {
        doSetup();
        cohortTest();
        enrolledCohortTest();
        savedCohortFilterTest();
    }

    @LogMethod
    private void doSetup()
    {
        log("Check advanced cohort features.");
        _containerHelper.createProject(PROJECT_NAME, "Study");
        importStudyFromZip(COHORT_STUDY_ZIP);
        clickProject(PROJECT_NAME);
        addWebPart("Specimens");
        // Check all cohorts after initial import.
    }

    @LogMethod
    private void cohortTest()
    {
        waitAndClick(WAIT_FOR_JAVASCRIPT, Locator.linkWithText("Blood"), WAIT_FOR_PAGE);

        DataRegionTable specimenTable = new DataRegionTable("SpecimenDetail", this, true, true);
        assertEquals("Incorrect number of vials.", "Count:  25", specimenTable.getTotal("Global Unique Id")); // 5 participants x 5 visits
        List<String> cohortValues = specimenTable.getColumnDataAsText("Collection Cohort");
        assertEquals(10, Collections.frequency(cohortValues, "Positive"));
        assertEquals(10, Collections.frequency(cohortValues, "Negative"));

        setCohortFilter("Negative", AdvancedCohortType.INITIAL);
        verifyVialCount(specimenTable, 20); // One participant has no cohorts.
        setCohortFilter("Positive", AdvancedCohortType.INITIAL);
        verifyVialCount(specimenTable, 0); // All participants initially negative
        setCohortFilter("Negative", AdvancedCohortType.CURRENT);
        verifyVialCount(specimenTable, 0); // All participants are positive by the last visit
        setCohortFilter("Positive", AdvancedCohortType.CURRENT);
        verifyVialCount(specimenTable, 20); // All participants are positive by the last visit
        setCohortFilter("Negative", AdvancedCohortType.DATA_COLLECTION);
        verifyVialCount(specimenTable, 10);
        setCohortFilter("Positive", AdvancedCohortType.DATA_COLLECTION);
        verifyVialCount(specimenTable, 10);

        clickAndWait(Locator.linkWithText("Reports"));
        clickButtonByIndex("View", 2); // Specimen Report: Type by Cohort
        assertTextPresent("Specimen Report: Type by Cohort");
        checkCheckbox(Locator.checkboxByName("viewPtidList"));
        clickButton("Refresh");
        assignId(Locator.xpath(XPATH_SPECIMEN_REPORT_TABLE_NEGATIVE), TABLE_NEGATIVE);
        assignId(Locator.xpath(XPATH_SPECIMEN_REPORT_TABLE_POSITIVE), TABLE_POSITIVE);
        assignId(Locator.xpath(XPATH_SPECIMEN_REPORT_TABLE_UNASSIGNED), TABLE_UNASSIGNED);
        assertTableCellContains(TABLE_NEGATIVE, 2, 2, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellContains(TABLE_NEGATIVE, 2, 3, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellContains(TABLE_NEGATIVE, 2, 4, INFECTED_3, INFECTED_4);
        assertTableCellContains(TABLE_NEGATIVE, 2, 5, INFECTED_4);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 2, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 3, INFECTED_1, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 4, INFECTED_1, INFECTED_2, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 5, INFECTED_1, INFECTED_2, INFECTED_3, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 6, INFECTED_1, INFECTED_2, INFECTED_3, UNASSIGNED_1, INFECTED_4);
        assertTableCellContains(TABLE_POSITIVE, 2, 3, INFECTED_1);
        assertTableCellContains(TABLE_POSITIVE, 2, 4, INFECTED_1, INFECTED_2);
        assertTableCellContains(TABLE_POSITIVE, 2, 5, INFECTED_1, INFECTED_2, INFECTED_3);
        assertTableCellContains(TABLE_POSITIVE, 2, 6, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 2, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 3, INFECTED_2, INFECTED_3, INFECTED_4, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 4, INFECTED_3, INFECTED_4, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 5, INFECTED_4, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 6, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 2, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 3, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 4, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 5, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 6, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 2, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 3, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 4, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 5, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 6, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);

        selectOptionByText(Locator.name("cohortFilterType"), AdvancedCohortType.INITIAL.toString());
        clickButton("Refresh");
        assignId(Locator.xpath(XPATH_SPECIMEN_REPORT_TABLE_NEGATIVE), TABLE_NEGATIVE);
        assignId(Locator.xpath(XPATH_SPECIMEN_REPORT_TABLE_POSITIVE), TABLE_POSITIVE);
        assignId(Locator.xpath(XPATH_SPECIMEN_REPORT_TABLE_UNASSIGNED), TABLE_UNASSIGNED);
        assertTableCellContains(TABLE_NEGATIVE, 2, 2, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellContains(TABLE_NEGATIVE, 2, 3, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellContains(TABLE_NEGATIVE, 2, 4, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellContains(TABLE_NEGATIVE, 2, 5, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellContains(TABLE_NEGATIVE, 2, 6, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 2, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 3, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 4, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 5, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 6, UNASSIGNED_1);
        assertTableCellContains(TABLE_POSITIVE, 2, 0, "No data to show.");
        assertTableCellContains(TABLE_UNASSIGNED, 2, 2, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 3, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 4, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 5, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 6, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 2, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 3, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 4, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 5, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 6, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);

        selectOptionByText(Locator.name("cohortFilterType"), AdvancedCohortType.CURRENT.toString());
        clickButton("Refresh");
        assignId(Locator.xpath(XPATH_SPECIMEN_REPORT_TABLE_NEGATIVE), TABLE_NEGATIVE);
        assignId(Locator.xpath(XPATH_SPECIMEN_REPORT_TABLE_POSITIVE), TABLE_POSITIVE);
        assignId(Locator.xpath(XPATH_SPECIMEN_REPORT_TABLE_UNASSIGNED), TABLE_UNASSIGNED);
        assertTableCellContains(TABLE_NEGATIVE, 2, 0, "No data to show.");
        assertTableCellContains(TABLE_POSITIVE, 2, 2, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellContains(TABLE_POSITIVE, 2, 3, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellContains(TABLE_POSITIVE, 2, 4, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellContains(TABLE_POSITIVE, 2, 5, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellContains(TABLE_POSITIVE, 2, 6, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 2, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 3, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 4, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 5, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 6, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 2, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 3, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 4, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 5, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 6, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 2, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 3, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 4, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 5, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 6, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);

        // Check that cohort filters persist through participant view

        // Check that switching visit order changes cohort.
        clickProject(PROJECT_NAME);
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Visits"));
        clickAndWait(Locator.linkWithText("Change Visit Order"));
        checkCheckbox(Locator.checkboxByName("explicitChronologicalOrder"));
        checkCheckbox(Locator.checkboxByName("explicitDisplayOrder"));
        selectOptionByText(Locator.name("displayOrderItems"), "Visit 3");
        clickButtonByIndex("Move Up", 0, 0);
        clickButtonByIndex("Move Up", 0, 0);
        selectOptionByText(Locator.name("chronologicalOrderItems"), "Visit 3");
        clickButtonByIndex("Move Up", 1, 0);
        clickButtonByIndex("Move Up", 1, 0);
        clickButton("Save");
        clickProject(PROJECT_NAME);
        click(Locator.tagContainingText("span", "Specimen Reports")); // expand
        clickAndWait(Locator.linkWithText("View Available Reports"));
        clickButtonByIndex("View", 2);
        checkCheckbox(Locator.checkboxByName("viewPtidList"));
        clickButton("Refresh");
        assignId(Locator.xpath(XPATH_SPECIMEN_REPORT_TABLE_NEGATIVE), TABLE_NEGATIVE);
        assignId(Locator.xpath(XPATH_SPECIMEN_REPORT_TABLE_POSITIVE), TABLE_POSITIVE);
        assertTableCellContains(TABLE_NEGATIVE, 2, 3, INFECTED_1, INFECTED_2, INFECTED_4);
        assertTableCellContains(TABLE_NEGATIVE, 2, 5, INFECTED_4);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 3, INFECTED_3, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 5, INFECTED_1, INFECTED_2, INFECTED_3, UNASSIGNED_1);
        assertTableCellContains(TABLE_POSITIVE, 2, 3, INFECTED_3);
        assertTableCellContains(TABLE_POSITIVE, 2, 5, INFECTED_1, INFECTED_2, INFECTED_3);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 3, INFECTED_1, INFECTED_2, INFECTED_4, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 5, INFECTED_4, UNASSIGNED_1);

        // Check that deleting a vistit changes the cohort.
        clickProject(PROJECT_NAME);
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Visits"));
        clickAndWait(Locator.linkWithText("edit").index(4)); // Visit 4
        clickButton("Delete visit");
        clickButton("Delete");
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Cohorts"));
        assignId(Locator.xpath(XPATH_COHORT_ASSIGNMENT_TABLE), COHORT_TABLE);
        assertTableCellTextEquals(COHORT_TABLE, 4, 1, "Negative"); // Infected4

        // Check all cohorts after manipulation.
        clickProject(PROJECT_NAME);
        waitAndClick(WAIT_FOR_JAVASCRIPT, Locator.linkWithText("Blood"), WAIT_FOR_PAGE);

        specimenTable = new DataRegionTable("SpecimenDetail", this, true, true);
        verifyVialCount(specimenTable, 20); // 5 participants x 4 visits (was five visits, but one was just deleted)

        setCohortFilter("Negative", AdvancedCohortType.INITIAL);
        verifyVialCount(specimenTable, 16); // One participant has no cohorts.
        setCohortFilter("Positive", AdvancedCohortType.INITIAL);
        verifyVialCount(specimenTable, 0); // All participants initially negative
        setCohortFilter("Negative", AdvancedCohortType.CURRENT);
        verifyVialCount(specimenTable, 4); // Final visit (where Infected4 joins Positive cohort) has been deleted.
        setCohortFilter("Positive", AdvancedCohortType.CURRENT);
        verifyVialCount(specimenTable, 12);
        setCohortFilter("Negative", AdvancedCohortType.DATA_COLLECTION);
        verifyVialCount(specimenTable, 10);
        setCohortFilter("Positive", AdvancedCohortType.DATA_COLLECTION);
        verifyVialCount(specimenTable, 6); // Visit4 samples no longer have a cohort, and are thus not shown.

        // Check that participant view respects filter.
        clickProject(PROJECT_NAME);
        clickAndWait(Locator.linkWithText("2 datasets"));
        clickAndWait(Locator.linkWithText("Test Results"));
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.addCustomizeViewSort("ParticipantId", "Ascending");
        _customizeViewsHelper.applyCustomView();

        setCohortFilter("Positive", AdvancedCohortType.DATA_COLLECTION);
        clickAndWait(Locator.linkWithText("Infected1"));
        assertElementNotPresent(Locator.linkWithText("Previous Participant"));
        clickAndWait(Locator.linkWithText("Next Participant"));
        assertTextPresent("Infected2");
        assertElementPresent(Locator.linkWithText("Previous Participant"));
        clickAndWait(Locator.linkWithText("Next Participant"));
        assertTextPresent("Infected3");
        assertElementPresent(Locator.linkWithText("Previous Participant"));
        assertElementNotPresent(Locator.linkWithText("Next Participant")); // Participant 4 should be filtered out

        // Check basic cohorts
        log("Check basic cohort features.");
        clickProject(PROJECT_NAME);
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Cohorts"));
        click(Locator.radioButtonById("simpleCohorts"));
        assertAlertContains("Update cohort assignments now?");

        clickProject(PROJECT_NAME);
        waitAndClick(Locator.linkWithText("Blood"));

        waitForText("Positive", 12, WAIT_FOR_JAVASCRIPT);
        assertTextPresent("Negative", 4);
        clickAndWait(Locator.linkWithText("Reports"));

        clickButtonByIndex("View", 2); // Specimen Report: Type by Cohort
        assertTextPresent("Specimen Report: Type by Cohort");
        checkCheckbox(Locator.checkboxByName("viewPtidList"));
        clickButton("Refresh");

        // Basic cohorts should be determined only by the most recent cohort assignment.
        assignId(Locator.xpath(XPATH_SPECIMEN_REPORT_TABLE_NEGATIVE), TABLE_NEGATIVE);
        assignId(Locator.xpath(XPATH_SPECIMEN_REPORT_TABLE_POSITIVE), TABLE_POSITIVE);
        assignId(Locator.xpath(XPATH_SPECIMEN_REPORT_TABLE_UNASSIGNED), TABLE_UNASSIGNED);
        assertTableCellContains(TABLE_NEGATIVE, 2, 2, INFECTED_4);
        assertTableCellContains(TABLE_NEGATIVE, 2, 3, INFECTED_4);
        assertTableCellContains(TABLE_NEGATIVE, 2, 4, INFECTED_4);
        assertTableCellContains(TABLE_NEGATIVE, 2, 5, INFECTED_4);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 3, INFECTED_1, INFECTED_2, INFECTED_3, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 2, INFECTED_1, INFECTED_2, INFECTED_3, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 4, INFECTED_1, INFECTED_2, INFECTED_3, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 5, INFECTED_1, INFECTED_2, INFECTED_3, UNASSIGNED_1);
        assertTableCellContains(TABLE_POSITIVE, 2, 2, INFECTED_1, INFECTED_2, INFECTED_3);
        assertTableCellContains(TABLE_POSITIVE, 2, 3, INFECTED_1, INFECTED_2, INFECTED_3);
        assertTableCellContains(TABLE_POSITIVE, 2, 4, INFECTED_1, INFECTED_2, INFECTED_3);
        assertTableCellContains(TABLE_POSITIVE, 2, 5, INFECTED_1, INFECTED_2, INFECTED_3);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 2, UNASSIGNED_1, INFECTED_4);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 3, UNASSIGNED_1, INFECTED_4);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 4, UNASSIGNED_1, INFECTED_4);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 5, UNASSIGNED_1, INFECTED_4);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 2, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 3, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 4, UNASSIGNED_1);
        assertTableCellContains(TABLE_UNASSIGNED, 2, 5, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 2, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 3, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 4, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
        assertTableCellNotContains(TABLE_UNASSIGNED, 2, 5, INFECTED_1, INFECTED_2, INFECTED_3, INFECTED_4);
    }

    private void assignId(Locator loc, String id)
    {
        executeScript("arguments[0].id = arguments[1];", loc.findElement(getDriver()), id);
    }
    
    /**
     * Test enrolled and unenrolled cohort functionality for cohorts
     *  The enrolledCohortTest assumes the following state:
     *  Negative cohort {Infected4}
     *  Positive cohort {Infected1, Infected2, Infected3}
     *  Not in any cohort {Unassigned1}
     */
    @LogMethod
    private void enrolledCohortTest()
    {
        log("Check enrolled/unenrolled cohort features.");
        clickProject(PROJECT_NAME);
        DataRegionTable table = getCohortDataRegionTable();

        // verify that we have an 'enrolled' column and both cohorts are
        // true by default
        log("Check that cohorts are enrolled by default.");
        verifyCohortStatus(table, "positive", true);
        verifyCohortStatus(table, "negative", true);

        // issue 15948: verify that a new cohort has the enrolled bit set
        log("Verify a new cohort has enrolled checked.");
        verifyNewCohort();
        table = getCohortDataRegionTable();

        // verify we can roundtrip enrolled status
        // unenroll the "postiive" cohort and check
        log("Check that enrolled bit is roundtripped successfully.");
        changeCohortStatus(table, "positive", false);
        verifyCohortStatus(table, "positive", false);

        // start with everyone enrolled again
        changeCohortStatus(table, "positive", true);
        refreshParticipantList();

        // the rules for when we display the "enrolled" keyword are as follows:
        // 1.  if there are no unenrolled cohorts, then never show enrolled text
        // 2.  if unenrolled cohorts exist and all selected cohorts are enrolled then use the enrolled text to describe them
        // 3.  if unenrolled cohorts exist and all selected cohorts are unenrolled, do not use enrolled text
        // 4.  if unenrolled cohorts exist and selected cohorts include both enrolled and unenrolled cohorts, then do not show the enrolled text
        // note:  participants that don't belong to any cohort are unenrolled (changed with r18435)

        log("verify enrolled text: all cohorts are enrolled");
        // rule #1:  no unenrolled cohorts exist so do not expect the enrolled text.
        verifyParticipantList(PTIDS_POSITIVE_NEGATIVE, true);

        // All cohorts are enrolled... should not see "Enrolled" filter item
        verifyDatasetEnrolledCohortFilter("Test Results", false, 16, 0);
        verifySpecimenEnrolledCohortFilter("By Individual Vial", false, 20, 0);

        // unenroll all cohorts
        table = getCohortDataRegionTable();
        changeCohortStatus(table, "positive", false);
        changeCohortStatus(table, "negative", false);
        refreshParticipantList();

        log("verify enrolled text: all cohorts are unenrolled");
        // rule #3:  all selected cohorts are unenrolled (including "Not in any cohort")
        verifyParticipantList(PTIDS_ALL, false);
        verifyCohortSelection(false, null, null, PTIDS_ALL, false, "Found 5 participants of 5.");

        // rule #3: Negative cohort is not enrolled, so don't show enrolled text
        verifyCohortSelection(true, null, COHORT_NEGATIVE, PTIDS_NEGATIVE, false, "Found 1 participant of 5.");

        // rule #3: Positive cohort is not enrolled, so don't show enrolled text
        verifyCohortSelection(false, COHORT_NEGATIVE, COHORT_POSITIVE, PTIDS_POSITIVE, false, "Found 3 participants of 5.");

        // rule #3: "not in any cohort" is unenrolled, so don't show enrolled text
        verifyCohortSelection(false, COHORT_POSITIVE, COHORT_NOCOHORT, PTIDS_NOCOHORT, false, "Found 1 participant of 5.");

        // All cohorts are unenrolled... should not see "Enrolled" filter item
        verifyDatasetEnrolledCohortFilter("Test Results", false, 16, 0);
        verifySpecimenEnrolledCohortFilter("By Individual Vial", false, 20, 0);

        // test both enrolled and unenrolled cohorts
        table = getCohortDataRegionTable();
        changeCohortStatus(table, "positive", true);
        changeCohortStatus(table, "negative", false);
        refreshParticipantList();

        log("verify enrolled text: Positive cohort enrolled; negative cohort unenrolled");
        // rule #2, showing enrolled cohorts (positive cohort)
        verifyParticipantList(PTIDS_POSITIVE, true);

        // rule #4, don't show enrolled text since we have a mix of enrolled and unenrolled
        verifyCohortSelection(true, null, null, PTIDS_ALL, false, "Found 5 participants of 5.");

        // rule #3, don't show enrolled text since we only have unenrolled cohorots
        verifyCohortSelection(true, null, COHORT_NEGATIVE, PTIDS_NEGATIVE, false, "Found 1 participant of 5.");

        // rule #2, only showing enrolled cohorts
        verifyCohortSelection(false, COHORT_NEGATIVE, COHORT_POSITIVE, PTIDS_POSITIVE, true, "Found 3 enrolled participants of 5.");

        // rule #3, only showing unenrolled cohorts
        verifyCohortSelection(false, COHORT_POSITIVE, COHORT_NOCOHORT, PTIDS_NOCOHORT, false, "Found 1 participant of 5.");

        verifyDatasetEnrolledCohortFilter("Test Results", true, 16, 12);
        verifySpecimenEnrolledCohortFilter("By Individual Vial", true, 20, 16);

        // Verify "Enrolled" filtering with advanced cohorts
        log("Check enrolled filtering with advanced cohorts");
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Cohorts"));
        prepForPageLoad();
        click(Locator.radioButtonById("advancedCohorts"));
        assertAlert("Changing between simple and advanced modes requires updating cohort assignments for all participants.  Update cohort assignments now?");
        waitForPageToLoad();

        verifyDatasetEnrolledCohortFilterAdvanced("Test Results", 16, 0, 12, 6);
        verifySpecimenEnrolledCohortFilterAdvanced("By Individual Vial", 20, 4, 16, 10);
    }

    @LogMethod
    private void savedCohortFilterTest()
    {
        log("Create cohort filtered views");
        clickProject(PROJECT_NAME);
        clickAndWait(Locator.linkWithText("2 datasets"));
        clickAndWait(Locator.linkWithText("Test Results"));

        setCohortFilter("Negative", AdvancedCohortType.CURRENT); // 4 rows
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.clipFilter(AdvancedCohortType.CURRENT.fieldKey());
        _customizeViewsHelper.saveCustomView("CurrentNegative", true);

        _extHelper.clickMenuButton("Views", "default");
        setCohortFilter("Negative", AdvancedCohortType.INITIAL); // 16 rows
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.clipFilter(AdvancedCohortType.INITIAL.fieldKey());
        _customizeViewsHelper.saveCustomView("InitialPositive", true);

        _extHelper.clickMenuButton("Views", "default");
        setCohortFilter("Positive", AdvancedCohortType.DATA_COLLECTION); // 6 rows
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.clipFilter(AdvancedCohortType.DATA_COLLECTION.fieldKey());
        _customizeViewsHelper.saveCustomView("DataCollectionPositive", true);


        log("Verify saved cohort filtered views");
        _extHelper.clickMenuButton("Views", "CurrentNegative");
        DataRegionTable dataset = new DataRegionTable("Dataset", this);
        assertEquals("Unexpected row count", 4, dataset.getDataRowCount());

        _extHelper.clickMenuButton("Views", "InitialPositive");
        assertEquals("Unexpected row count", 16, dataset.getDataRowCount());

        _extHelper.clickMenuButton("Views", "DataCollectionPositive");
        assertEquals("Unexpected row count", 6, dataset.getDataRowCount());
    }

    @LogMethod
    private void testCohortFilterExport()
    {
        clickProject(PROJECT_NAME);
        clickAndWait(Locator.linkWithText("2 datasets"));
        clickAndWait(Locator.linkWithText("Test Results"));

        setCohortFilter("Positive", AdvancedCohortType.CURRENT);
        addUrlParameter("exportAsWebPage=true");
        clickExportToText();
        assertTextNotPresent("Infected4");
        getDriver().navigate().back();
    }

    @LogMethod
    private void testCohortFilteredChart()
    {
        clickProject(PROJECT_NAME);
        clickAndWait(Locator.linkWithText("2 datasets"));
        clickAndWait(Locator.linkWithText("Test Results"));

        setCohortFilter("Positive", AdvancedCohortType.CURRENT);
        DataRegionTable dataset = new DataRegionTable("Dataset", this);
        dataset.createQuickChart("SequenceNum");
        clickButton("View Data", 0);
        waitForElement(Locator.xpath("//*[starts-with(@id, 'aqwp')]"));
        String drtId = getAttribute(Locator.xpath("//*[starts-with(@id, 'aqwp')]"), "id");
        DataRegionTable chartData = new DataRegionTable(drtId, this);
        assertEquals("Wrong amount of data rows in quick chart", 12, chartData.getDataRowCount());
        List<String> participants = chartData.getColumnDataAsText("ParticipantID");
        assertTrue("Expected participant was not present in chart data", participants.contains("Infected1"));
        assertFalse("Filtered out participant was present in chart data", participants.contains("Infected4"));
    }

    private void verifyDatasetEnrolledCohortFilter(String datasetName, boolean enrolledMenu, int allRowCount, int enrolledRowCount)
    {
        DataRegionTable table = verifyUnfilteredDataset(datasetName, allRowCount);

        if (enrolledMenu)
        {
            _extHelper.clickMenuButton("Participant Groups", "Enrolled");
            assertTextPresent("Current cohort is enrolled or unassigned");
            assertEquals(enrolledRowCount, table.getDataRowCount());
        }
        else
        {
            assertFalse("Enrolled menu should not be present", _extHelper.isExtMenuPresent("Participant Groups", "Enrolled"));
        }
    }

    private void verifyDatasetEnrolledCohortFilterAdvanced(String datasetName, int allRowCount, int initialRowCount, int currentRowCount, int dataCollectionRowCount)
    {
        DataRegionTable table = verifyUnfilteredDataset(datasetName, allRowCount);

        _extHelper.clickMenuButton("Participant Groups", "Enrolled", AdvancedCohortType.INITIAL.toString());
        assertTextPresent("Initial cohort is enrolled or unassigned");
        assertEquals(initialRowCount, table.getDataRowCount());

        _extHelper.clickMenuButton("Participant Groups", "Enrolled", AdvancedCohortType.CURRENT.toString());
        assertTextPresent("Current cohort is enrolled or unassigned");
        assertEquals(currentRowCount, table.getDataRowCount());

        _extHelper.clickMenuButton("Participant Groups", "Enrolled", AdvancedCohortType.DATA_COLLECTION.toString());
        assertTextPresent("Cohort as of data collection is enrolled or unassigned");
        assertEquals(dataCollectionRowCount, table.getDataRowCount());
    }

    private DataRegionTable verifyUnfilteredDataset(String datasetName, int allRowCount)
    {
        clickTab("Overview");
        clickAndWait(Locator.linkWithText("2 datasets"));
        clickAndWait(Locator.linkWithText(datasetName));

        assertTextNotPresent("Current cohort is enrolled or unassigned");

        DataRegionTable table = new DataRegionTable("Dataset", this);
        assertEquals(allRowCount, table.getDataRowCount());

        return table;
    }

    private void verifySpecimenEnrolledCohortFilter(String specimenLink, boolean enrolledMenu, int allRowCount, int enrolledRowCount)
    {
        verifyUnfilteredSpecimens(specimenLink, allRowCount);

        if (enrolledMenu)
        {
            DataRegionTable specimenTable = new DataRegionTable("SpecimenDetail", this, true, true);
            _extHelper.clickMenuButton("Participant Groups", "Enrolled");
            verifyVialCount(specimenTable, enrolledRowCount);
        }
        else
        {
            assertFalse("Enrolled menu should not be present", _extHelper.isExtMenuPresent("Participant Groups", "Enrolled"));
        }
    }

    private void verifySpecimenEnrolledCohortFilterAdvanced(String specimenLink, int allRowCount, int initialRowCount, int currentRowCount, int dataCollectionRowCount)
    {
        verifyUnfilteredSpecimens(specimenLink, allRowCount);
        DataRegionTable specimenTable = new DataRegionTable("SpecimenDetail", this, true, true);

        _extHelper.clickMenuButton("Participant Groups", "Enrolled", AdvancedCohortType.INITIAL.toString());
        verifyVialCount(specimenTable, initialRowCount);

        _extHelper.clickMenuButton("Participant Groups", "Enrolled", AdvancedCohortType.CURRENT.toString());
        verifyVialCount(specimenTable, currentRowCount);

        _extHelper.clickMenuButton("Participant Groups", "Enrolled", AdvancedCohortType.DATA_COLLECTION.toString());
        verifyVialCount(specimenTable, dataCollectionRowCount);
    }

    private void verifyUnfilteredSpecimens(String specimenLink, int allRowCount)
    {
        clickTab("Specimen Data");
        waitAndClickAndWait(Locator.linkWithText(specimenLink));

        DataRegionTable specimenTable = new DataRegionTable("SpecimenDetail", this, true, true);
        verifyVialCount(specimenTable, allRowCount);
    }

    private void verifyVialCount(DataRegionTable table, int expectedCount)
    {
        assertEquals("Incorrect number of vials", "Count:  " + expectedCount, table.getTotal("Global Unique Id"));
    }

    private void verifyNewCohort()
    {
        clickButton("Insert New");
        assertChecked(Locator.checkboxByName("quf_enrolled"));
    }

    private void verifyCohortSelection(boolean toggleAll, @Nullable String previousCohort, @Nullable String nextCohort, String[] expectedParticipants, boolean expectEnrolledText, String waitText)
    {
        if (toggleAll)
        {
            Locator all = DataRegionTable.Locators.faceRowCheckbox("All");
            waitAndClick(all);
        }

        if (previousCohort != null)
            _ext4Helper.uncheckGridRowCheckbox(previousCohort);

        if (nextCohort != null)
            _ext4Helper.checkGridRowCheckbox(nextCohort);

        waitForText(waitText);
        verifyParticipantList(expectedParticipants, expectEnrolledText);
    }

    private boolean isPartipantInGroup(String ptid, String[] ptidGroup)
    {
        for (String id : ptidGroup)
        {
            if ( 0 == ptid.compareToIgnoreCase(id))
            {
                return true;
            }
        }

        return false;
    }

    private void refreshParticipantList()
    {
        clickTab("Participants");
        waitForTextToDisappear("Loading..."); // Wait for status to appear.
    }

    private void verifyParticipantList(String[] ptids, boolean expectEnrolledText)
    {
        String statusText = getStatusText();

        // we should not see the "enrolled" text in the participant list status message if no participants are unenrolled
        if (!expectEnrolledText)
        {
            assertTrue("Should not see text: enrolled", !statusText.contains("enrolled"));
        }
        else
        {
            assertTrue("Did not find expected text: enrolled", statusText.contains("enrolled"));
        }

        // make sure everyone in the group is there
        for (String ptid : ptids)
        {
            assertPtid(ptid, true);
        }

        // make sure everyone not in the group is not there
        for (String ptid : PTIDS_ALL)
        {
            if (!isPartipantInGroup(ptid, ptids))
            {
                assertPtid(ptid, false);
            }
        }
    }

    // TODO: Move some place more generally useful?  ParticipantListHelper?
    private void assertPtid(String ptid, boolean present)
    {
        Locator loc = Locator.xpath("//li[contains(@class, 'ptid')]/a[string() = '" + ptid + "']");
        assertEquals(ptid + " should " + (present ? "" : "not ") + "be present", present, isElementPresent(loc));
    }

    // TODO: Same here
    private String getStatusText()
    {
        Locator loc = Locator.id("participantsDiv1.status");
        return getText(loc);
    }

    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }

    @Override
    protected String getProjectName()
    {
        return PROJECT_NAME;
    }

    private static enum AdvancedCohortType
    {
        INITIAL("Initial cohort", "ParticipantId/InitialCohort/Label"),
        CURRENT("Current cohort", "ParticipantId/Cohort/Label"),
        DATA_COLLECTION("Cohort as of data collection", "ParticipantVisit/Cohort/Label");

        private String _type;
        private String _fieldKey;

        private AdvancedCohortType(String type, String fieldKey)
        {
            _type = type;
            _fieldKey = fieldKey;
        }

        public String toString()
        {
            return _type;
        }

        public String fieldKey()
        {
            return _fieldKey;
        }
    }

    private void setCohortFilter(String cohort, AdvancedCohortType type)
    {
        _extHelper.clickMenuButton("Participant Groups", "Cohorts", cohort, type.toString());
    }
}
