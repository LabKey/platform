/*
 * Copyright (c) 2017-2019 LabKey Corporation
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
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.Locators;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.Daily;
import org.labkey.test.categories.Study;
import org.labkey.test.pages.StartImportPage;
import org.labkey.test.pages.pipeline.PipelineStatusDetailsPage;
import org.labkey.test.pages.query.ExecuteQueryPage;
import org.labkey.test.pages.study.DeleteMultipleVisitsPage;
import org.labkey.test.pages.study.ManageVisitPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.core.webdav.WebDavUploadHelper;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category({Daily.class, Study.class})
@BaseWebDriverTest.ClassTimeout(minutes = 10)
public class StudyVisitManagementTest extends BaseWebDriverTest
{
    private final File INITIAL_FOLDER_ARCHIVE = TestFileUtils.getSampleData("study/StudyVisitManagement.folder.zip");
    private final File DATASETS_ONLY_FOLDER_ARCHIVE = TestFileUtils.getSampleData("study/StudyVisitManagement_Datasets.folder.zip");
    private final File SPECIMENS_ONLY_FOLDER_ARCHIVE = TestFileUtils.getSampleData("study/StudyVisitManagement_Specimens.folder.zip");
    private final File EXPLODED_FOLDER_ARCHIVE = TestFileUtils.getSampleData("study/StudyVisitManagement.folder");

    private final String SPECIMEN_UNDEFINED_VISIT_MSG = "The following undefined visits exist in the specimen data:";
    private final String DATASET_UNDEFINED_VISIT_MSG = "The following undefined visits exist in the dataset data:";

    @BeforeClass
    public static void setupProject()
    {
        StudyVisitManagementTest init = (StudyVisitManagementTest) getCurrentTest();
        init.doSetup();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName(), null);
    }

    @Before
    public void preTest()
    {
        resetErrors();
        goToProjectHome();
    }

    @Test
    public void testDeleteMultipleVisits()
    {
        _containerHelper.createSubfolder(getProjectName(), "testDeleteMultipleVisits");
        importFolderArchiveWithFailureFlag(INITIAL_FOLDER_ARCHIVE, true, 1, false);
        List<String> definedVisits = Arrays.asList("301.0 - 391.0", "401.0", "411.0 - 491.0", "501.0", "601.0 - 691.0", "701.0");
        verifyStudyVisits(definedVisits, null);
        verifySpecimenDataRowCount(189);
        verifyDatasetRowCount("VAC-1", 7);

        // verify dataset and specimen row counts on delete multiple visits page
        DeleteMultipleVisitsPage deleteMultipleVisitsPage = goToDeleteMultipleVisits();
        Map<String, Pair<Integer, Integer>> datasetVisitCounts = new HashMap<>();
        datasetVisitCounts.put("1 week Post-V#1", Pair.of(7, 119));
        datasetVisitCounts.put("2 week Post-V#1", Pair.of(33, 2));
        datasetVisitCounts.put("411.0 - 491.0", Pair.of(15, 0));
        datasetVisitCounts.put("3 week Post-V#1", Pair.of(6, 12));
        datasetVisitCounts.put("4 week Post-V#1", Pair.of(17, 50));
        datasetVisitCounts.put("1 week Post-V#2", Pair.of(3, 2));
        verifyDeleteVisitDataCounts(datasetVisitCounts);

        // verify error message for no visit selection
        deleteMultipleVisitsPage.clickDeleteSelected();
        assertEquals("Unexpected error message", "No visits selected.", deleteMultipleVisitsPage.getErrorMessage());

        // delete a visit and verify dataset/specimen data removed
        deleteMultipleVisitsPage = new DeleteMultipleVisitsPage(getDriver());
        deleteMultipleVisitsPage.selectVisitForDeletion("4 week Post-V#1");
        deleteMultipleVisitsPage.clickDeleteSelected();
        definedVisits = Arrays.asList("301.0 - 391.0", "401.0", "411.0 - 491.0", "501.0", "701.0");
        List<String> undefinedVisits = Arrays.asList("601.0 - 691.0");
        verifyStudyVisits(definedVisits, undefinedVisits);
        verifySpecimenDataRowCount(139);
        verifyDatasetRowCount("VAC-1", 0);

        // delete all of the rest and verify dataset/specimen data removed
        goToDeleteMultipleVisits();
        deleteMultipleVisits(Arrays.asList("1 week Post-V#1", "2 week Post-V#1", "411.0 - 491.0", "3 week Post-V#1", "1 week Post-V#2"));
        verifySpecimenDataRowCount(4); // 4 left because they do not have visit values
        verifyDatasetRowCount("APX-1", 0);
    }

    private void verifyDeleteVisitDataCounts(Map<String, Pair<Integer, Integer>> visitDataCounts)
    {
        DeleteMultipleVisitsPage deleteMultipleVisitsPage = new DeleteMultipleVisitsPage(getDriver());
        for (Map.Entry<String, Pair<Integer, Integer>> countEntry : visitDataCounts.entrySet())
        {
            assertEquals("Unexpected visit dataset row count", countEntry.getValue().getLeft().intValue(), deleteMultipleVisitsPage.getVisitDatasetRowCount(countEntry.getKey()));
            assertEquals("Unexpected visit specimen row count", countEntry.getValue().getRight().intValue(), deleteMultipleVisitsPage.getVisitSpecimenRowCount(countEntry.getKey()));
        }
    }

    private DeleteMultipleVisitsPage goToDeleteMultipleVisits()
    {
        return _studyHelper.goToManageVisits().goToDeleteMultipleVisits();
    }

    private void verifySpecimenDataRowCount(int expectedRowCount)
    {
        DataRegionTable table = ExecuteQueryPage.beginAt(this,"study", "SpecimenDetail").getDataRegion();
        if (expectedRowCount < 100)
            assertEquals("Unexpected number of specimen rows", expectedRowCount, table.getDataRowCount());
        else
            assertElementPresent(Locator.paginationText(1, 100, expectedRowCount));
    }

    private void verifyDatasetRowCount(String datasetName, int expectedRowCount)
    {
        goToModule("Query");
        viewQueryData("study", datasetName, null);
        DataRegionTable table = new DataRegionTable("query", this);
        assertEquals("Unexpected number of dataset rows", expectedRowCount, table.getDataRowCount());
    }

    @Test
    public void testFailForUndefinedVisitsSpecimen()
    {
        _containerHelper.createSubfolder(getProjectName(), "testFailForUndefinedVisitsSpecimen");
        testFailForUndefinedVisits(SPECIMENS_ONLY_FOLDER_ARCHIVE, SPECIMEN_UNDEFINED_VISIT_MSG, 3);
    }

    @Test
    public void testFailForUndefinedVisitsDataset()
    {
        _containerHelper.createSubfolder(getProjectName(), "testFailForUndefinedVisitsDataset");
        testFailForUndefinedVisits(DATASETS_ONLY_FOLDER_ARCHIVE, DATASET_UNDEFINED_VISIT_MSG, 11);
    }

    private void testFailForUndefinedVisits(File archive, String errorMsgPrefix, int numExpectedErrors)
    {
        // first try importing the datasets only archive, expecting this to give an error
        importFolderArchiveWithFailureFlag(archive, true, 1, true);
        List<String> definedVisits = Arrays.asList("301.0 - 391.0", "400.0 - 499.0", "501.0");
        List<String> undefinedVisits = Arrays.asList("601.0", "701.0");
        verifyUndefinedVisitError(errorMsgPrefix, definedVisits, undefinedVisits);

        // then import the full archive, expecting this to succeed
        deleteSingleVisit("2 week Post-V#1");
        importFolderArchiveWithFailureFlag(INITIAL_FOLDER_ARCHIVE, true, 2, true);
        definedVisits = Arrays.asList("301.0 - 391.0", "401.0", "411.0 - 491.0", "501.0", "601.0 - 691.0", "701.0");
        verifyStudyVisits(definedVisits, null);

        // test the reload without first removing the overlapping visits
        importFolderArchiveWithFailureFlag(archive, true, 3, true);
        clickAndWait(Locator.linkWithText("ERROR"));
        new PipelineStatusDetailsPage(getDriver()).assertLogTextContains("ERROR: New visit 2 week Post-V#1 (400.0 - 499.0) overlaps existing visit 2 week Post-V#1 (401.0)");

        // delete some visits so that the reload will have the failure case
        deleteMultipleVisits(Arrays.asList("2 week Post-V#1", "411.0 - 491.0", "4 week Post-V#1", "1 week Post-V#2"));

        // test reload of the specimens only archive and check for expected error message
        importFolderArchiveWithFailureFlag(archive, true, 4, true);
        definedVisits = Arrays.asList("301.0 - 391.0", "400.0 - 499.0", "501.0");
        verifyUndefinedVisitError(errorMsgPrefix, definedVisits, undefinedVisits);

        // reload one last time without the failure bit checked to verify creation of undefined visits
        importFolderArchiveWithFailureFlag(archive, false, 5, true);
        assertElementPresent(Locator.linkWithText("Folder import"), 2);
        assertElementPresent(Locator.linkWithText("ERROR"), 3);
        definedVisits = Arrays.asList("301.0 - 391.0", "400.0 - 499.0", "501.0", "601.0", "701.0");
        verifyStudyVisits(definedVisits, null);

        checkExpectedErrors(numExpectedErrors);
    }

    @Test
    public void testFailForUndefinedVisitsReload() throws IOException
    {
        String folderName = "testFailForUndefinedVisitsReload";
        _containerHelper.createSubfolder(getProjectName(), folderName);

        // import the full archive and verify the defined visits
        importFolderArchiveWithFailureFlag(INITIAL_FOLDER_ARCHIVE, true, 1, false);
        List<String> definedVisits = Arrays.asList("301.0 - 391.0", "401.0", "411.0 - 491.0", "501.0", "601.0 - 691.0", "701.0");
        verifyStudyVisits(definedVisits, null);

        // delete some visits so that the reload will have the failure case
        deleteMultipleVisits(Arrays.asList("2 week Post-V#1", "411.0 - 491.0", "4 week Post-V#1", "1 week Post-V#2"));

        // change pipeline root and then attempt reload again
        new WebDavUploadHelper(getProjectName() + "/" + folderName).uploadDirectoryContents(EXPLODED_FOLDER_ARCHIVE);
        startFolderImport(true);
        waitForPipelineJobsToComplete(2, "Folder import", true);

        // verify the expected import failure message and defined visits
        definedVisits = Arrays.asList("301.0 - 391.0", "400.0 - 499.0", "501.0");
        List<String> undefinedVisits = Arrays.asList("601.0", "701.0");
        verifyUndefinedVisitError(DATASET_UNDEFINED_VISIT_MSG, definedVisits, undefinedVisits);

        // test reload again with the failure bit unset
        startFolderImport(false);
        waitForPipelineJobsToComplete(3, "Folder import", true);

        // verify undefined visits now created
        definedVisits = Arrays.asList("301.0 - 391.0", "400.0 - 499.0", "501.0", "601.0", "701.0");
        verifyStudyVisits(definedVisits, null);

        checkExpectedErrors(6);
    }

    private void startFolderImport(boolean failForUndefinedVisits)
    {
        goToModule("Pipeline");
        clickButton("Process and Import Data");
        _fileBrowserHelper.selectFileBrowserItem("folder.xml");
        _fileBrowserHelper.selectImportDataAction("Import Folder");

        StartImportPage importPage = new StartImportPage(getDriver());
        importPage.setFailForUndefinedVisitsCheckBox(failForUndefinedVisits);
        importPage.clickStartImport();
        waitForElement(Locators.bodyTitle("Data Pipeline"));
    }

    private void verifyUndefinedVisitError(String errorMsgPrefix, @NotNull List<String> definedVisits, @Nullable List<String> undefinedVisits)
    {
        clickAndWait(Locator.linkWithText("ERROR"));
        new PipelineStatusDetailsPage(getDriver()).assertLogTextContains(errorMsgPrefix + " " + StringUtils.join(undefinedVisits, ", "));
        verifyStudyVisits(definedVisits, undefinedVisits);
    }

    private void verifyStudyVisits(@NotNull List<String> definedVisits, @Nullable List<String> undefinedVisits)
    {
        ManageVisitPage manageVisitPage = _studyHelper.goToManageVisits();
        assertEquals("Unexpected number of existing visits.", definedVisits.size(), manageVisitPage.getVisitRowCount());
        for (String visitSeqRangeStr : definedVisits)
            assertTrue("Expected visit range not found: " + visitSeqRangeStr, manageVisitPage.hasVisitForSequenceRange(visitSeqRangeStr));

        if (undefinedVisits != null && !undefinedVisits.isEmpty())
        {
            for (String visitSeqRangeStr : undefinedVisits)
                assertTrue("Unexpected visit range found: " + visitSeqRangeStr, !manageVisitPage.hasVisitForSequenceRange(visitSeqRangeStr));
        }
    }

    private void importFolderArchiveWithFailureFlag(File archive, boolean failForUndefinedVisits, int expectedCompleted, boolean expectedError)
    {
        // Importing from folder management triggers sometimes triggers 'AccessDeniedException' on Windows
        // Import from pipeline instead
        goToModule("FileContent");
        _fileBrowserHelper.uploadFile(archive, null, null, _fileBrowserHelper.fileIsPresent(archive.getName()));
        _fileBrowserHelper.importFile(archive.getName(), "Import Folder");

        StartImportPage importPage = new StartImportPage(getDriver());
        importPage.setFailForUndefinedVisitsCheckBox(failForUndefinedVisits);
        importPage.clickStartImport();
        waitForElement(Locators.bodyTitle("Data Pipeline"));
        waitForPipelineJobsToComplete(expectedCompleted, "Folder import", expectedError);
    }

    private void deleteSingleVisit(String visitLabel)
    {
        _studyHelper.goToManageVisits().goToEditVisit(visitLabel);
        clickButton("Delete Visit");
        clickButton("Delete");
    }

    private void deleteMultipleVisits(List<String> visitLabels)
    {
        DeleteMultipleVisitsPage page = DeleteMultipleVisitsPage.beginAt(this);
        for (String visitLabel : visitLabels)
            page.selectVisitForDeletion(visitLabel);
        page.clickDeleteSelected();
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return "StudyVisitManagementTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }
}
