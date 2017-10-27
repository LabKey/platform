/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
import org.labkey.test.Locators;
import org.labkey.test.SortDirection;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyB;
import org.labkey.test.components.ParticipantListWebPart;
import org.labkey.test.pages.study.ManageVisitPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PortalHelper;
import org.openqa.selenium.WebElement;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category({DailyB.class})
public class CohortTest extends BaseWebDriverTest
{
    private static final String PROJECT_NAME = "Cohort Test Project";
    private static final File COHORT_STUDY_ZIP = TestFileUtils.getSampleData("studies/CohortStudy.zip");
    private static final String TABLE_NEGATIVE = "tableNegative";
    private static final String TABLE_POSITIVE = "tablePositive";
    private static final String TABLE_UNASSIGNED = "tableUnassigned";
    private static final String INFECTED_1 = "Infected1";
    private static final String INFECTED_2 = "Infected2";
    private static final String INFECTED_3 = "Infected3";
    private static final String INFECTED_4 = "Infected4";
    private static final String UNASSIGNED_1 = "Unassigned1";
    private static final String COHORT_ASSIGNMENT_TABLE_ID = "participant-cohort-assignments";
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
        updateCohortAssignmentTest();
    }

    @LogMethod
    private void doSetup()
    {
        log("Check advanced cohort features.");
        _containerHelper.createProject(PROJECT_NAME, "Study");
        importStudyFromZip(COHORT_STUDY_ZIP);
        clickProject(PROJECT_NAME);
        new PortalHelper(this).addWebPart("Specimens");
        // Check all cohorts after initial import.
    }

    @LogMethod
    private void cohortTest()
    {
        Locator.XPathLocator specimenReportTableLoc = Locators.bodyPanel().append(Locator.tagWithClass("table", "labkey-data-region-legacy"));
        
        waitAndClick(WAIT_FOR_JAVASCRIPT, Locator.linkWithText("Blood"), WAIT_FOR_PAGE);

        DataRegionTable specimenTable = new DataRegionTable("SpecimenDetail", getDriver());
        assertEquals("Incorrect number of vials.", "Count (non-blank): 25", specimenTable.getSummaryStatFooterText("Global Unique Id")); // 5 participants x 5 visits
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
        List<WebElement> specimenReportTables = specimenReportTableLoc.findElements(getDriver());
        assignId(specimenReportTables.get(0), TABLE_NEGATIVE);
        assignId(specimenReportTables.get(1), TABLE_POSITIVE);
        assignId(specimenReportTables.get(2), TABLE_UNASSIGNED);
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
        specimenReportTables = specimenReportTableLoc.findElements(getDriver());
        assignId(specimenReportTables.get(0), TABLE_NEGATIVE);
        assignId(specimenReportTables.get(1), TABLE_POSITIVE);
        assignId(specimenReportTables.get(2), TABLE_UNASSIGNED);
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
        specimenReportTables = specimenReportTableLoc.findElements(getDriver());
        assignId(specimenReportTables.get(0), TABLE_NEGATIVE);
        assignId(specimenReportTables.get(1), TABLE_POSITIVE);
        assignId(specimenReportTables.get(2), TABLE_UNASSIGNED);
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
        _studyHelper.goToManageVisits().goToChangeVisitOrder();
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
        specimenReportTables = specimenReportTableLoc.findElements(getDriver());
        assignId(specimenReportTables.get(0), TABLE_NEGATIVE);
        assignId(specimenReportTables.get(1), TABLE_POSITIVE);
        assertTableCellContains(TABLE_NEGATIVE, 2, 3, INFECTED_1, INFECTED_2, INFECTED_4);
        assertTableCellContains(TABLE_NEGATIVE, 2, 5, INFECTED_4);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 3, INFECTED_3, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_NEGATIVE, 2, 5, INFECTED_1, INFECTED_2, INFECTED_3, UNASSIGNED_1);
        assertTableCellContains(TABLE_POSITIVE, 2, 3, INFECTED_3);
        assertTableCellContains(TABLE_POSITIVE, 2, 5, INFECTED_1, INFECTED_2, INFECTED_3);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 3, INFECTED_1, INFECTED_2, INFECTED_4, UNASSIGNED_1);
        assertTableCellNotContains(TABLE_POSITIVE, 2, 5, INFECTED_4, UNASSIGNED_1);

        // Check that deleting a visit changes the cohort.
        clickProject(PROJECT_NAME);
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Visits"));
        ManageVisitPage manageVisitPage = new ManageVisitPage(getDriver());
        manageVisitPage.goToEditVisit("Visit 4");
        clickButton("Delete Visit");
        clickButton("Delete");
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Cohorts"));
        assertTableCellTextEquals(COHORT_ASSIGNMENT_TABLE_ID, 4, 1, "Negative"); // Infected4

        // Check all cohorts after manipulation.
        clickProject(PROJECT_NAME);
        waitAndClick(WAIT_FOR_JAVASCRIPT, Locator.linkWithText("Blood"), WAIT_FOR_PAGE);

        specimenTable = new DataRegionTable("SpecimenDetail", getDriver());
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
        _customizeViewsHelper.addSort("ParticipantId", SortDirection.ASC);
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
        DataRegionTable vials = new DataRegionTable("SpecimenDetail", getDriver());
        vials.setFilter("CollectionCohort", "Equals", "Positive");
        assertEquals("Unexpected number of collection cohort rows", 12, vials.getDataRowCount());
        vials.setFilter("CollectionCohort", "Equals", "Negative");
        assertEquals("Unexpected number of collection cohort rows", 4, vials.getDataRowCount());
        vials.clearFilter("CollectionCohort");

        clickAndWait(Locator.linkWithText("Reports"));
        clickButtonByIndex("View", 2); // Specimen Report: Type by Cohort
        assertTextPresent("Specimen Report: Type by Cohort");
        checkCheckbox(Locator.checkboxByName("viewPtidList"));
        clickButton("Refresh");

        // Basic cohorts should be determined only by the most recent cohort assignment.
        specimenReportTables = specimenReportTableLoc.findElements(getDriver());
        assignId(specimenReportTables.get(0), TABLE_NEGATIVE);
        assignId(specimenReportTables.get(1), TABLE_POSITIVE);
        assignId(specimenReportTables.get(2), TABLE_UNASSIGNED);
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

    private void assignId(WebElement el, String id)
    {
        executeScript("arguments[0].id = arguments[1];", el, id);
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
        doAndWaitForPageToLoad(() ->
        {
            click(Locator.radioButtonById("advancedCohorts"));
            assertAlert("Changing between simple and advanced modes requires updating cohort assignments for all participants.  Update cohort assignments now?");
        });

        verifyDatasetEnrolledCohortFilterAdvanced("Test Results", 16, 0, 12, 6);
        verifySpecimenEnrolledCohortFilterAdvanced("By Individual Vial", 20, 4, 16, 10);
    }

    @LogMethod
    private void updateCohortAssignmentTest()
    {
        // Regression test for Issue: 30616
        clickProject(PROJECT_NAME);
        clickAndWait(Locator.linkWithText("2 datasets"));
        clickAndWait(Locator.linkWithText("Cohort Assignments"));
        DataRegionTable dataset = new DataRegionTable("Dataset", getDriver());
        clickAndWait(dataset.updateLink(0));
        setFormElement(Locator.input("quf_ParticipantId"), "");
        clickButton("Submit");

        // Update should fail, and we should be on same update dataset page.
        // Check that cohort field is drop down and not a text field.
        assertElementVisible(Locator.xpath("//select").withAttribute("name", "quf_Cohort"));
        clickButton("Cancel");
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
        _customizeViewsHelper.saveCustomView("CurrentNegative", true);

        goToCustomView("default");
        setCohortFilter("Negative", AdvancedCohortType.INITIAL); // 16 rows
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.saveCustomView("InitialPositive", true);

        goToCustomView("default");
        setCohortFilter("Positive", AdvancedCohortType.DATA_COLLECTION); // 6 rows
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.saveCustomView("DataCollectionPositive", true);

        log("Verify saved cohort filtered views");
        DataRegionTable dataset = new DataRegionTable("Dataset", getDriver());
        dataset.clearAllFilters("ParticipantId");
        dataset = goToCustomView("CurrentNegative");
        assertEquals("Unexpected row count", 4, dataset.getDataRowCount());
        dataset = goToCustomView("InitialPositive");
        assertEquals("Unexpected row count", 16, dataset.getDataRowCount());
        dataset = goToCustomView("DataCollectionPositive");
        assertEquals("Unexpected row count", 6, dataset.getDataRowCount());
    }

    private DataRegionTable goToCustomView(String viewName)
    {
        DataRegionTable dataset = new DataRegionTable("Dataset", getDriver());
        dataset.goToView(viewName);
        return new DataRegionTable("Dataset", getDriver());
    }

    private void verifyDatasetEnrolledCohortFilter(String datasetName, boolean enrolledMenu, int allRowCount, int enrolledRowCount)
    {
        DataRegionTable table = verifyUnfilteredDataset(datasetName, allRowCount);

        if (enrolledMenu)
        {
            DataRegionTable.findDataRegion(this).clickHeaderMenu("Groups", "Enrolled");
            assertTextPresent("Current cohort is enrolled or unassigned");
            assertEquals(enrolledRowCount, table.getDataRowCount());
        }
        else
        {
            assertFalse("Enrolled menu should not be present", _extHelper.isExtMenuPresent("Groups", "Enrolled"));
        }
    }

    private void verifyDatasetEnrolledCohortFilterAdvanced(String datasetName, int allRowCount, int initialRowCount, int currentRowCount, int dataCollectionRowCount)
    {
        DataRegionTable table = verifyUnfilteredDataset(datasetName, allRowCount);

        DataRegionTable.findDataRegion(this).clickHeaderMenu("Groups", "Enrolled", AdvancedCohortType.INITIAL.toString());
        assertTextPresent("Initial cohort is enrolled or unassigned");
        assertEquals(initialRowCount, table.getDataRowCount());

        DataRegionTable.findDataRegion(this).clickHeaderMenu("Groups", "Enrolled", AdvancedCohortType.CURRENT.toString());
        assertTextPresent("Current cohort is enrolled or unassigned");
        assertEquals(currentRowCount, table.getDataRowCount());

        DataRegionTable.findDataRegion(this).clickHeaderMenu("Groups", "Enrolled", AdvancedCohortType.DATA_COLLECTION.toString());
        assertTextPresent("Cohort as of data collection is enrolled or unassigned");
        assertEquals(dataCollectionRowCount, table.getDataRowCount());
    }

    private DataRegionTable verifyUnfilteredDataset(String datasetName, int allRowCount)
    {
        clickTab("Overview");
        clickAndWait(Locator.linkWithText("2 datasets"));
        clickAndWait(Locator.linkWithText(datasetName));

        assertTextNotPresent("Current cohort is enrolled or unassigned");

        DataRegionTable table = new DataRegionTable("Dataset", getDriver());
        assertEquals(allRowCount, table.getDataRowCount());

        return table;
    }

    private void verifySpecimenEnrolledCohortFilter(String specimenLink, boolean enrolledMenu, int allRowCount, int enrolledRowCount)
    {
        verifyUnfilteredSpecimens(specimenLink, allRowCount);

        if (enrolledMenu)
        {
            DataRegionTable specimenTable = new DataRegionTable("SpecimenDetail", getDriver());
            DataRegionTable.findDataRegion(this).clickHeaderMenu("Groups", "Enrolled");
            verifyVialCount(specimenTable, enrolledRowCount);
        }
        else
        {
            assertFalse("Enrolled menu should not be present", _extHelper.isExtMenuPresent("Groups", "Enrolled"));
        }
    }

    private void verifySpecimenEnrolledCohortFilterAdvanced(String specimenLink, int allRowCount, int initialRowCount, int currentRowCount, int dataCollectionRowCount)
    {
        verifyUnfilteredSpecimens(specimenLink, allRowCount);
        DataRegionTable specimenTable = new DataRegionTable("SpecimenDetail", getDriver());

        DataRegionTable.findDataRegion(this).clickHeaderMenu("Groups", "Enrolled", AdvancedCohortType.INITIAL.toString());
        verifyVialCount(specimenTable, initialRowCount);

        DataRegionTable.findDataRegion(this).clickHeaderMenu("Groups", "Enrolled", AdvancedCohortType.CURRENT.toString());
        verifyVialCount(specimenTable, currentRowCount);

        DataRegionTable.findDataRegion(this).clickHeaderMenu("Groups", "Enrolled", AdvancedCohortType.DATA_COLLECTION.toString());
        verifyVialCount(specimenTable, dataCollectionRowCount);
    }

    private void verifyUnfilteredSpecimens(String specimenLink, int allRowCount)
    {
        clickTab("Specimen Data");
        waitForElement(Locator.css(".specimenSearchLoaded"));
        waitAndClickAndWait(Locator.linkWithText(specimenLink));

        DataRegionTable specimenTable = new DataRegionTable("SpecimenDetail", getDriver());
        verifyVialCount(specimenTable, allRowCount);
    }

    private void verifyVialCount(DataRegionTable table, int expectedCount)
    {
        assertEquals("Incorrect number of vials", "Count (non-blank): " + expectedCount, table.getSummaryStatFooterText("Global Unique Id"));
    }

    private void verifyNewCohort()
    {
        DataRegionTable cohorts = new DataRegionTable("Cohort", getDriver());
        cohorts.clickInsertNewRow();
        assertChecked(Locator.checkboxByName("quf_enrolled"));
    }

    private void verifyCohortSelection(boolean toggleAll, @Nullable String previousCohort, @Nullable String nextCohort, String[] expectedParticipants, boolean expectEnrolledText, String waitText)
    {
        if (toggleAll)
        {
            Locator all = DataRegionTable.Locators.facetRowCheckbox("All");
            waitAndClick(all);
        }

        if (previousCohort != null)
            _ext4Helper.uncheckGridRowCheckbox(previousCohort);

        if (nextCohort != null)
            _ext4Helper.checkGridRowCheckbox(nextCohort);

        waitForText(waitText);
        verifyParticipantList(expectedParticipants, expectEnrolledText);
    }

    private void refreshParticipantList()
    {
        clickTab("Participants");
        waitForTextToDisappear("Loading..."); // Wait for status to appear.
    }

    private void verifyParticipantList(String[] ptids, boolean expectEnrolledText)
    {
        ParticipantListWebPart participantListWebPart = new ParticipantListWebPart(this);
        String statusText = participantListWebPart.getStatusMessage();

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
        List<String> actualPtids = getTexts(Locator.tagWithClass("li", "ptid").findElements(getDriver()));
        assertEquals("Wrong ptids visible", Arrays.asList(ptids), actualPtids);
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

    private enum AdvancedCohortType
    {
        INITIAL("Initial cohort", "ParticipantId/InitialCohort/Label"),
        CURRENT("Current cohort", "ParticipantId/Cohort/Label"),
        DATA_COLLECTION("Cohort as of data collection", "ParticipantVisit/Cohort/Label");

        private String _type;
        private String _fieldKey;

        AdvancedCohortType(String type, String fieldKey)
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
        DataRegionTable table = DataRegionTable.DataRegion(getDriver()).find();
        table.clickHeaderMenu("Groups", "Cohorts", cohort, type.toString());
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }
}
