/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyC;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.StudyHelper;
import org.labkey.test.util.WikiHelper;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.labkey.test.util.DataRegionTable.DataRegion;

@Category({DailyC.class})
public class AncillaryStudyTest extends StudyBaseTest
{
    private static final String PROJECT_NAME = "AncillaryStudyTest Project";
    private static final String STUDY_NAME = "Special Emphasis Study";
    private static final String STUDY_DESCRIPTION = "Ancillary study created by AncillaryStudyTest.";
    private static final String[] DATASETS = {"APX-1: Abbreviated Physical Exam","AE-1:(VTN) AE Log","BRA-1: Behavioral Risk Assessment (Page 1)","BRA-2: Behavioral Risk Assessment (Page 2)","CM-1:(Ph I/II) Concomitant Medications Log","CPF-1: Follow-up Chemistry Panel","CPS-1: Screening Chemistry Panel","DEM-1: Demographics","DOV-1: Discontinuation of Vaccination","ECI-1: Eligibility Criteria"};
    private static final String[] DEPENDENT_DATASETS = {"EVC-1: Enrollment Vaccination", "FPX-1: Final Complete Physical Exam", "IV-1:Interim Visit", "TM-1: Termination"};
    private static final String PARTICIPANT_GROUP = "Ancillary Group";
    private static final String[] PTIDS = {"999320016", "999320518", "999320529", "999320533", "999320541", "999320557", "999320565", "999320576", "999320582", "999320590"};
    private static final String PARTICIPANT_GROUP_BAD = "Bad Ancillary Group";
    private static final String[] PTIDS_BAD = {"999320004", "999320007", "999320010", "999320016", "999320018", "999320021", "999320029", "999320033", "999320036","999320038"};
    private static final String SEQ_NUMBER = "1001"; //These should alphabetically precede all existing sequence numbers.
    private static final String SEQ_NUMBER2 = "1002";
    private static final String UPDATED_DATASET_VAL = "Esperanto";
    private static final String EXTRA_DATASET_ROWS = "mouseId\tsequenceNum\n" + // Rows for APX-1: Abbreviated Physical Exam
                                                     PTIDS[0] + "\t"+SEQ_NUMBER+"\n" +
                                                     PTIDS_BAD[0] + "\t" + SEQ_NUMBER;
    private final File PROTOCOL_DOC = TestFileUtils.getSampleData("study/Protocol.txt");
    private final File PROTOCOL_DOC2 = TestFileUtils.getSampleData("study/Protocol2.txt");

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

    @Override
    public void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _containerHelper.deleteProject(getProjectName(), afterTest);
        TestFileUtils.deleteDir(new File(StudyHelper.getPipelinePath(), "export"));
    }

    @Override
    public void doCreateSteps()
    {
        importStudy();
        startSpecimenImport(2);
        waitForPipelineJobsToComplete(2, "study import", false);
        _studyHelper.createCustomParticipantGroup(PROJECT_NAME, getFolderName(), PARTICIPANT_GROUP, "Mouse", true, PTIDS);
        _studyHelper.createCustomParticipantGroup(PROJECT_NAME, getFolderName(), PARTICIPANT_GROUP_BAD, "Mouse", true, PTIDS_BAD);
        createAncillaryStudy();
    }

    private void createAncillaryStudy()
    {
        navigateToFolder(PROJECT_NAME, getFolderName());
        clickTab("Manage");

        log("Create Special Emphasis Study.");
        clickButton("Create Ancillary Study", 0);

        //Wizard page 1 - location
        _extHelper.waitForExtDialog("Create Ancillary Study");
        click(Locator.xpath("//label/span[text()='Protocol']"));
        waitForElement(Locator.xpath("//div[" + Locator.NOT_HIDDEN + " and @class='g-tip-header']//span[text()='Protocol Document']"), WAIT_FOR_JAVASCRIPT);
        setFormElement(Locator.name("studyName"), getFolderName());
        setFormElement(Locator.name("studyDescription"), STUDY_DESCRIPTION);
        assertTrue(PROTOCOL_DOC.exists());
        setFormElement(Locator.name("protocolDoc"), PROTOCOL_DOC);
        clickButton("Change", 0);
        Locator projectTreeNode = Locator.tagWithClass("a", "x-tree-node-anchor").withDescendant(Locator.tagWithText("span", PROJECT_NAME));
        sleep(1000); // sleep while the tree expands
        doubleClick(projectTreeNode);
        clickButton("Next", 0);

        //Wizard page 2 - participant group
        Locator groupLocator = Locator.xpath("//span[contains(text(),  '" + PARTICIPANT_GROUP + "')]");
        waitForElement(groupLocator, WAIT_FOR_JAVASCRIPT);
        assertWizardError("Next", "You must select at least one Mouse group.");
        waitAndClick(groupLocator);

        log("Check participant group.");
        Locator.XPathLocator ptidLocator = Locator.xpath("//div[not(contains(@style, 'display: none;'))]/span[contains(@class, 'testParticipantGroups')]");
        waitForElement(ptidLocator, WAIT_FOR_JAVASCRIPT);
        assertEquals("Did not find expected number of participants", PTIDS.length, getElementCount(ptidLocator));
        for (String ptid : PTIDS)
        {
            assertElementPresent(Locator.xpath("//div[not(contains(@style, 'display: none;'))]/span[@class='testParticipantGroups' and text() = '" + ptid + "']"));
        }

        _extHelper.selectExtGridItem(null, null, 0, "studyWizardParticipantList", false);

        clickButton("Next", 0);

        //Wizard page 3 - select datasets
        waitForElement(Locator.xpath("//div[contains(@class, 'studyWizardDatasetList')]"), WAIT_FOR_JAVASCRIPT);
        click(Locator.xpath("//label/span[text()='Data Refresh:']"));
        waitForElement(Locator.xpath("//div[" + Locator.NOT_HIDDEN + " and @class='g-tip-header']//span[text()='Data Refresh']"), WAIT_FOR_JAVASCRIPT);
        for (String dataset : DATASETS)
        {
            _extHelper.selectExtGridItem("Label", dataset, -1, "studyWizardDatasetList", true);
        }
        assertWizardError("Finish", "A study already exists in the destination folder.");

        clickButton("Previous", 0);
        clickButton("Previous", 0);
        setFormElement(Locator.name("studyName"), STUDY_NAME);
        clickButton("Next", 0);
        clickButton("Next", 0);
        checkCheckbox(Locator.radioButtonByNameAndValue("refreshType", "Manual"));
        clickButton("Finish");

        waitForPipelineJobsToFinish(3);
        clickAndWait(Locator.linkWithText("Create Ancillary Study"));
    }

    @Override
    public void doVerifySteps()
    {
        assertTextPresent("Ancillary study created by AncillaryStudyTest");
        clickTab("Manage");
        waitForText((DATASETS.length + DEPENDENT_DATASETS.length) + " datasets");
        clickAndWait(Locator.linkWithText("Manage Datasets"));
        for( String str : DATASETS )
        {
            assertElementPresent(Locator.linkWithText(str));
        }

        clickAndWait(Locator.linkWithText("Mice"));
        waitForText(PTIDS[0]);
        for( String str : PTIDS )
        {
            assertElementPresent(Locator.linkWithText(str));
        }
        assertTextPresent("10 mice");

        verifySpecimens(5, 44);
        verifyContainerPathFilter();
        verifyModifyParticipantGroup(STUDY_NAME);
        verifyModifyParticipantGroup(getFolderName());
        verifyModifyDataset();
        verifyProtocolDocument();
        verifyDatasets();
        verifySpecimens(4, 38); // Lose one specimen and associated vials due to mouse group modification
        verifyExportImport();
    }

    private void verifyModifyParticipantGroup(String study)
    {
        clickFolder(study);
        log("Modify " + study + " participant group.");
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Mouse Groups"));
        waitForText(PARTICIPANT_GROUP);
        String csp = PTIDS[0];
        for( int i = 1; i < PTIDS.length - 1; i++ )
            csp += ","+PTIDS[i];
        _studyHelper.editCustomParticipantGroup(PARTICIPANT_GROUP, "Mouse", null, null, true, true, true, csp);

        log("Verify that modified participant group has no effect on ancillary study.");
        clickFolder(STUDY_NAME);
        clickAndWait(Locator.linkWithText("Mice"));
        waitForText("Filter"); // Wait for participant list to appear.

        for( String str : PTIDS )
        {
            waitForElement(Locator.linkWithText(str), WAIT_FOR_JAVASCRIPT);
        }
    }

    private void verifyModifyDataset()
    {
        //INSERT
        log("Insert rows into source dataset");
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText(DATASETS[0]));
        DataRegion(getDriver()).find().clickImportBulkData();
        setFormElement(Locator.name("text"), EXTRA_DATASET_ROWS);
        clickButton("Submit");

        log("Verify changes in Ancillary Study. (insert)");
        clickFolder(STUDY_NAME);
        DataRegionTable table = _studyHelper.goToManageDatasets()
                .selectDatasetByLabel(DATASETS[0])
                .clickViewData()
                .getDataRegion();

        table.goToView("Edit Snapshot");
        clickButton("Update Snapshot", 0);
        assertAlert("Updating will replace all existing data with a new set of data. Continue?");

        table.ensureColumnPresent("SequenceNum");
        assertEquals("Dataset does not reflect changes in source study.", 21, table.getDataRowCount());
        assertTextPresent(SEQ_NUMBER + ".0");
        table.getColumnDataAsText("Sequence Num");

        //UPDATE
        log("Modify row in source dataset");
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText(DATASETS[0]));
        Map<String, String> nameAndValue = new HashMap<>();
        nameAndValue.put("SequenceNum", SEQ_NUMBER2);
        table.updateRow(1, nameAndValue);

        log("Verify changes in Ancillary Study. (modify)");
        clickFolder(STUDY_NAME);
        _studyHelper.goToManageDatasets()
                .selectDatasetByLabel(DATASETS[0])
                .clickViewData();
        table.goToView("Edit Snapshot");
        clickButton("Update Snapshot", 0);
        assertAlert("Updating will replace all existing data with a new set of data. Continue?");
        waitForElement(Locator.tagWithClass("table", "labkey-data-region"));
        table = new DataRegionTable("Dataset", getDriver());
        assertEquals("Dataset does not reflect changes in source study.", 21, table.getDataRowCount());
        assertTextPresent(SEQ_NUMBER2 + ".0");
        assertTextNotPresent(SEQ_NUMBER + ".0");

        //DELETE
        log("Delete row from source dataset");
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText(DATASETS[0]));
        table.checkCheckbox(1);
        table.clickHeaderButton("Delete");
        assertAlert("Delete selected row from this dataset?");
        waitForElement(Locator.paginationText(48));

        log("Verify changes in Ancillary Study. (delete)");
        clickFolder(STUDY_NAME);
        _studyHelper.goToManageDatasets()
                .selectDatasetByLabel(DATASETS[0])
                .clickViewData();
        table.goToView("Edit Snapshot");
        clickButton("Update Snapshot", 0);
        assertAlert("Updating will replace all existing data with a new set of data. Continue?");
        waitForElement(Locator.tagWithClass("table", "labkey-data-region"));
        table = new DataRegionTable("Dataset", getDriver());
        assertEquals("Dataset does not reflect changes in source study.", 20, table.getDataRowCount());
        assertTextNotPresent(SEQ_NUMBER + ".0", SEQ_NUMBER2 + ".0");
    }


    private void verifyProtocolDocument()
    {
        clickFolder(STUDY_NAME);
        assertTextPresent(STUDY_DESCRIPTION);
        assertElementPresent(Locator.xpath("//a[contains(@href, 'name=" + PROTOCOL_DOC.getName() + "')]"));
        clickAndWait(Locator.xpath("//a[./span[@title='Edit']]"));

        waitForElement(Locator.name("Label"), WAIT_FOR_JAVASCRIPT);
        setFormElement(Locator.name("Label"), "Extra " + STUDY_NAME);
        setFormElement(Locator.name("Description"), "Extra " + STUDY_DESCRIPTION);
        click(Locator.tag("img").withAttribute("src", "/labkey/_images/paperclip.gif")); //Attach a file
        waitForElement(Locator.xpath("//div[contains(@class, 'protocolPanel')]//input[@type='file']"), WAIT_FOR_JAVASCRIPT);
        setFormElement(Locator.xpath("//div[contains(@class, 'protocolPanel')]//input[@type='file']"), PROTOCOL_DOC2.toString());
        clickButton("Submit");
        assertElementPresent(Locator.linkWithText(PROTOCOL_DOC.getName()));
        assertElementPresent(Locator.linkWithText(PROTOCOL_DOC2.getName()));
        assertTextPresent(
                "Protocol documents:",
                "Extra " + STUDY_NAME,
                "Extra " + STUDY_DESCRIPTION);
    }

    private void verifyDatasets()
    {
        log("Verify Linked Datasets");
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText(DEPENDENT_DATASETS[0]));
        clickAndWait(Locator.tag("a").withAttribute("data-original-title","edit"));
        setFormElement(Locator.name("quf_formlang"), UPDATED_DATASET_VAL);
        clickButton("Submit");

        clickFolder(STUDY_NAME);
        clickAndWait(Locator.linkWithText("Clinical and Assay Data"));
        for(String dataset : DATASETS)
        {
            waitForText(dataset);
        }
        for(String dataset : DEPENDENT_DATASETS)
        {
            assertElementPresent(Locator.linkWithText(dataset));
        }
        clickAndWait(Locator.linkWithText(DEPENDENT_DATASETS[0]));
        assertTextNotPresent(UPDATED_DATASET_VAL);
        new DataRegionTable("Dataset",getDriver()).goToView("Edit Snapshot");
        doAndWaitForPageToLoad(() ->
        {
            clickButton("Update Snapshot", 0);
            assertAlert("Updating will replace all existing data with a new set of data. Continue?");
        });
        assertTextPresent(UPDATED_DATASET_VAL);
    }

    private void verifySpecimens(int specimenCount, int vialCount)
    {
        log("Verify copied specimens");
        clickFolder(STUDY_NAME);
        waitAndClickAndWait(Locator.linkWithText("Specimen Data"));
        sleep(2000); // the link moves while the specimen search form finishes layout
        waitAndClickAndWait(Locator.linkWithText("By Vial Group"));
        DataRegionTable table = new DataRegionTable("SpecimenSummary", getDriver());
        assertEquals("Did not find expected number of specimens.", specimenCount, table.getDataRowCount());
        assertEquals("Incorrect total vial count.", "Sum: " + vialCount, table.getSummaryStatFooterText("Vial Count"));
        waitAndClickAndWait(Locator.linkWithText("Specimen Data"));
        sleep(2000); // the link moves while the specimen search form finishes layout
        waitAndClickAndWait(Locator.linkWithText("By Individual Vial"));
        table = new DataRegionTable("SpecimenDetail", getDriver());
        assertEquals("Did not find expected number of vials.", vialCount, table.getDataRowCount());

        log("Verify that Ancillary study doesn't support requests.");
        clickAndWait(Locator.linkWithText("Manage"));
        assertTextNotPresent("Specimen Repository Settings", "Specimen Request Settings");
        assertTextPresent("Note: specimen repository and request settings are not available for ancillary studies.");
        assertElementNotPresent(Locator.linkWithText("Change Repository Type"));
        assertElementNotPresent(Locator.linkWithText("Manage Display and Behavior"));
        assertElementNotPresent(Locator.linkWithText("Manage Request Statuses"));
        assertElementNotPresent(Locator.linkWithText("Manage Actors and Groups"));
        assertElementNotPresent(Locator.linkWithText("Manage Default Requirements"));
        assertElementNotPresent(Locator.linkWithText("Manage New Request Form"));
        assertElementNotPresent(Locator.linkWithText("Manage Notifications"));
        assertElementNotPresent(Locator.linkWithText("Manage Requestability Rules"));
    }

    /**
     * Regression test for #17021. Requires Ancillary study.
     */
    public void verifyContainerPathFilter()
    {
        clickFolder(getFolderName());
        goToModule("Wiki");
        WikiHelper wh = new WikiHelper(this);
        wh.createWikiPage("17021", "17021 Regression", new File(TestFileUtils.getApiScriptFolder(), "filterTest.html"));
        DataRegionTable regressionTable = DataRegion(getDriver()).withName("test17021").waitFor();
        regressionTable.setUpFacetedFilter("PrimaryType", "Blood (Whole)");
        assertElementNotPresent(Locator.linkWithText("Semen"));
        clickButton("Cancel",0);
    }

    private void verifyExportImport()
    {
        _studyHelper.exportStudy(STUDY_NAME);
        goToModule("Pipeline");
        clickButton("Process and Import Data");

        _fileBrowserHelper.selectFileBrowserItem("export/study/participant_groups.xml");
        log("Verify protocol document in export");
        _fileBrowserHelper.selectFileBrowserItem("export/study/protocolDocs/" + PROTOCOL_DOC.getName());
        assertTextPresent(PROTOCOL_DOC2.getName());

        _fileBrowserHelper.selectFileBrowserItem("export/study/datasets/datasets_metadata.xml");
        assertTextPresent(".tsv", (DATASETS.length + DEPENDENT_DATASETS.length) * 3);
        assertTextPresent("dataset001.tsv", "dataset019.tsv", "dataset023.tsv", "dataset125.tsv",
                "dataset136.tsv", "dataset144.tsv", "dataset171.tsv", "dataset172.tsv", "dataset200.tsv",
                "dataset300.tsv", "dataset350.tsv", "dataset420.tsv", "dataset423.tsv", "dataset490.tsv");

        log("Verify reloading study");
        _fileBrowserHelper.importFile("export/study/study.xml", "Reload Study");
        waitForText("Import Study from Pipeline");
        clickButton("Start Import");
        waitForPipelineJobsToComplete(1, "study import", false);
    }

    private void assertWizardError(String button, String error)
    {
        clickButton(button, 0);
        _extHelper.waitForExtDialog("Error");
        assertTextPresent(error);
        clickButton("OK", 0);
        _extHelper.waitForExtDialogToDisappear("Error");
    }

    public String getProjectName()
    {
        return PROJECT_NAME;
    }
}
