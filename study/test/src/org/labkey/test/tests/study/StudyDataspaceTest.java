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

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;
import org.junit.Assert;
import org.junit.experimental.categories.Category;
import org.labkey.api.util.Pair;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyC;
import org.labkey.test.components.ext4.Checkbox;
import org.labkey.test.components.studydesigner.ManageAssaySchedulePage;
import org.labkey.test.components.studydesigner.ManageStudyProductsPage;
import org.labkey.test.components.studydesigner.ManageTreatmentsPage;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.StudyHelper;
import org.openqa.selenium.WebElement;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@Category({DailyC.class})
public class StudyDataspaceTest extends StudyBaseTest
{
    protected final String FOLDER_STUDY1 = "Study 1";
    protected final String FOLDER_STUDY2 = "Study 2";
    protected final String FOLDER_STUDY5 = "Study 5";
    protected final String SUBFOLDER_STUDY5 = "SubFolder 5";
    protected final String VISIT_TAG_QWP_TITLE = "VisitTag";
    private final PortalHelper _portalHelper = new PortalHelper(this);

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    protected String getProjectName()
    {
        return "DataspaceStudyVerifyProject";
    }

    protected String getStudyLabel()
    {
        return FOLDER_STUDY1;
    }

    protected String getFolderName()
    {
        return FOLDER_STUDY1;
    }

    @LogMethod
    @Override
    protected void checkQueries()
    {
        // Don't check the queries because CDS module has invalid queries without the CDS app
    }

    @Override
    protected void doCreateSteps()
    {
        initializeFolder();
        setPipelineRoot(StudyHelper.getPipelinePath());
    }

    @Override
    protected void initializeFolder()
    {
        _containerHelper.createProject(getProjectName(), "Dataspace");
        _containerHelper.createSubfolder(getProjectName(), getProjectName(), FOLDER_STUDY1, "Study", null, true);
        _containerHelper.createSubfolder(getProjectName(), getProjectName(), FOLDER_STUDY2, "Study", null, true);
        _containerHelper.createSubfolder(getProjectName(), getProjectName(), FOLDER_STUDY5, "Study", null, true);
    }

    @Override
    protected void doVerifySteps()
    {
        log("Verify project has study, but can't import or export");
        clickFolder(getProjectName());
        verifyStudyExportButtons(false);
        verifyStudyProductTableInfo(true, 0, null, null);
        verifyStudyTreatmentTableInfo(false, 0, null, null);
        verifyStudyAssayScheduleTableInfo(false, 0, null, null);

        // Import first study
        log("Import first study and verify");
        clickFolder(FOLDER_STUDY1);
        startImportStudyFromZip(TestFileUtils.getSampleData("studies/Dataspace/DataspaceStudyTest-Study2B.zip"), true, false);
        waitForPipelineJobsToComplete(1, "Study import", false);
        clickTab("Overview");
        assertTextPresent("tracks data in", "over 103 time points", "Data is present for 8 Participants");
        Map<String, Pair<Integer, String>> rowValueMap = new HashMap<>();
        rowValueMap.put("Lab", new Pair<>(1, "Buffalo1"));
        rowValueMap.put("Product", new Pair<>(1, "gakkon"));
        rowValueMap.put("Treatment", new Pair<>(1, "Placebo"));
        rowValueMap.put("CD4", new Pair<>(1, "520"));
        rowValueMap.put("Treatment By", new Pair<>(1, "Frankie Lee"));
        verifyLabResultsDataset(6, rowValueMap);
        verifyDatasetNames(Arrays.asList("Lab Results", "Arms"));
        verifyStudyExportButtons(true);
        verifyStudyProductTableInfo(false, 4, Arrays.asList("pol", "gp145", "Gag", "gakkon"), FOLDER_STUDY1);
        verifyStudyTreatmentTableInfo(true, 3, Arrays.asList("VRC-HIVADV014-00-VP", "Placebo", "VHS1"), FOLDER_STUDY1);
        verifyStudyAssayScheduleTableInfo(true, 0, null, FOLDER_STUDY1);

        // Import second study
        clickFolder(FOLDER_STUDY2);
        startImportStudyFromZip(TestFileUtils.getSampleData("/studies/Dataspace/DataspaceStudyTest-Study1B.zip"), true, false);
        waitForPipelineJobsToComplete(1, "Study import", false);
        clickTab("Overview");
        assertTextPresent("tracks data in", "over 97 time points", "Data is present for 8 Participants");
        rowValueMap = new HashMap<>();
        rowValueMap.put("Lab", new Pair<>(0, "Buffalo1"));
        rowValueMap.put("Product", new Pair<>(0, "gp145"));
        rowValueMap.put("Treatment", new Pair<>(0, "VRC-HIVADV014-00-VP"));
        rowValueMap.put("CD4", new Pair<>(0, "543"));
        rowValueMap.put("Treatment By", new Pair<>(0, "Frankie Lee"));
        verifyLabResultsDataset(11, rowValueMap);
        verifyDatasetNames(Arrays.asList("Luminex", "Demographics", "Lab Results", "Arms"));
        verifyStudyExportButtons(true);
        verifyStudyProductTableInfo(false, 8, Arrays.asList("Gag", "nef", "gag/pol", "gp140", "gag-pol-nef"), FOLDER_STUDY2);
        verifyStudyTreatmentTableInfo(true, 3, Arrays.asList("VRC-HIVDNA016-00-VP", "VRC-HIVADV014-00-VP", "Placebo"), FOLDER_STUDY2);
        verifyStudyAssayScheduleTableInfo(true, 0, null, FOLDER_STUDY2);

        // Import third study
        clickFolder(FOLDER_STUDY5);
        startImportStudyFromZip(TestFileUtils.getSampleData("studies/Dataspace/DataspaceStudyTest-Study5.zip"), true, false);
        waitForPipelineJobsToComplete(1, "Study import", false);
        clickTab("Overview");
        assertTextPresent("tracks data in", "over 6 time points", "Data is present for 3 Participants");
        verifyLabResultsDataset(6, null);
        verifyDatasetNames(Arrays.asList("Lab Results"));
        verifyStudyExportButtons(true);
        verifyStudyProductTableInfo(false, 8, Arrays.asList("gakkon", "gag-pol-nef", "gp145"), FOLDER_STUDY5);
        verifyStudyTreatmentTableInfo(true, 1, Arrays.asList("Test Treatment"), FOLDER_STUDY5);
        verifyStudyAssayScheduleTableInfo(true, 1, Arrays.asList("<Test Assay>"), FOLDER_STUDY5);

        // Export archive without Treatment, Product, etc.
        clickFolder(FOLDER_STUDY5);
        goToFolderManagement();
        clickAndWait(Locator.linkWithText("Export"));
        new Checkbox(Locator.tagWithText("label", "Assay Schedule").precedingSibling("input").findElement(getDriver())).uncheck();
        new Checkbox(Locator.tagWithText("label", "Treatment Data").precedingSibling("input").findElement(getDriver())).uncheck();
        checkRadioButton(Locator.tagWithClass("table", "export-location").index(0));
        clickButton("Export");

        // Load study in another folder
        _fileBrowserHelper.selectFileBrowserItem("export/study/study.xml");
        _containerHelper.createSubfolder(getProjectName(), getProjectName(), SUBFOLDER_STUDY5, "Collaboration", null, true);
        clickFolder(SUBFOLDER_STUDY5);
        setPipelineRoot(StudyHelper.getPipelinePath());
        importFolderFromPipeline("/export/folder.xml", 1, false);
        clickFolder(SUBFOLDER_STUDY5);
        assertTextPresent("tracks data in", "over 6 time points", "Data is present for 3 Participants");
        verifyLabResultsDataset(6, null);
        verifyDatasetNames(Arrays.asList("Lab Results"));
        verifyStudyExportButtons(true);
        verifyStudyProductTableInfo(false, 8, Arrays.asList("gakkon", "gag-pol-nef", "gp145"), SUBFOLDER_STUDY5);
        verifyStudyTreatmentTableInfo(true, 0, null, SUBFOLDER_STUDY5);
        verifyStudyAssayScheduleTableInfo(true, 0, null, SUBFOLDER_STUDY5);

        verifyVisitTags();
    }

    private void verifyStudyExportButtons(boolean canExport)
    {
        clickTab("Manage");
        if (canExport)
        {
            assertElementPresent(Locator.lkButton("Export Study"));
            assertElementPresent(Locator.lkButton("Reload Study"));
        }
        else
        {
            assertElementNotPresent(Locator.lkButton("Export Study"));
            assertElementNotPresent(Locator.lkButton("Reload Study"));
        }
    }

    private void verifyStudyProductTableInfo(boolean canInsert, int expectedRowCount, List<String> expectedValues, String folderName)
    {
        goToModule("Query");
        viewQueryData("study", "Product");
        DataRegionTable productTable = new DataRegionTable("query", this);
        Assert.assertTrue("Product row count incorrect.", productTable.getDataRowCount() == expectedRowCount);
        verifyInsertButtonsExist(canInsert);
        verifyRecordContainerLocation(true, folderName);
        verifyRecordLabels("Label", expectedValues);

        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Study Products"));
        ManageStudyProductsPage page = new ManageStudyProductsPage(this, canInsert);
        Assert.assertEquals("Unexpected link to 'add new row'", canInsert, page.canAddNewRow());
    }

    private void verifyStudyTreatmentTableInfo(boolean canInsert, int expectedRowCount, List<String> expectedValues, String folderName)
    {
        goToModule("Query");
        viewQueryData("study", "Treatment");
        DataRegionTable treatmentTable = new DataRegionTable("query", this);
        Assert.assertTrue("Treatment row count incorrect.", treatmentTable.getDataRowCount() == expectedRowCount);
        verifyInsertButtonsExist(canInsert);
        verifyRecordContainerLocation(false, folderName);
        verifyRecordLabels("Label", expectedValues);

        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Treatments"));
        ManageTreatmentsPage page = new ManageTreatmentsPage(this, canInsert);
        Assert.assertEquals("Unexpected link to 'add new row'", canInsert, page.canAddNewRow());
    }

    private void verifyStudyAssayScheduleTableInfo(boolean canInsert, int expectedRowCount, List<String> expectedValues, String folderName)
    {
        goToModule("Query");
        viewQueryData("study", "AssaySpecimen");
        DataRegionTable assaySpecimenTable = new DataRegionTable("query", this);
        Assert.assertTrue("Assay specimen row count incorrect.", assaySpecimenTable.getDataRowCount() == expectedRowCount);
        verifyInsertButtonsExist(canInsert);
        verifyRecordLabels("Assay Name", expectedValues);

        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage Assay Schedule"));
        ManageAssaySchedulePage page = new ManageAssaySchedulePage(this, canInsert);
        Assert.assertEquals("Unexpected link to 'add new row'", canInsert, page.canAddNewRow());
    }

    private void verifyInsertButtonsExist(boolean expected)
    {
        if (expected)
            assertTextPresent(DataRegionTable.getInsertNewButtonText(), DataRegionTable.getImportBulkDataText());
        else
            assertTextNotPresent(DataRegionTable.getInsertNewButtonText(), DataRegionTable.getImportBulkDataText());
    }

    private void verifyRecordContainerLocation(boolean atProject, String folderName)
    {
        DataRegionTable table = new DataRegionTable("query", this);
        List<String> containerNames = table.getColumnDataAsText("Container");
        if (table.getDataRowCount() > 0)
        {
            Assert.assertEquals("Records expected to be at the " + (atProject ? "project" : "folder") + " container", atProject, containerNames.contains(getProjectName()));
            if (folderName != null)
                Assert.assertEquals("Records expected to be at the " + (atProject ? "project" : "folder") + " container", atProject, !containerNames.contains(folderName));
        }
    }

    private void verifyRecordLabels(String colName, List<String> expectedValues)
    {
        if (expectedValues != null)
        {
            DataRegionTable table = new DataRegionTable("query", this);
            List<String> labels = table.getColumnDataAsText(colName);
            for (String expectedValue : expectedValues)
                Assert.assertTrue("Expected record label missing", labels.contains(expectedValue));
        }
    }

    private void verifyLabResultsDataset(int expectedRowCount, Map<String, Pair<Integer, String>> rowValueMap)
    {
        goToModule("Query");
        viewQueryData("study", "Lab Results");

        DataRegionTable labResultsTable = new DataRegionTable("query", this);
        Assert.assertTrue("Lab Results row count incorrect.", labResultsTable.getDataRowCount() == expectedRowCount);

        if (rowValueMap != null)
        {
            for (Map.Entry<String, Pair<Integer, String>> entry : rowValueMap.entrySet())
                assertEquals("Unexpected cell value.", entry.getValue().getValue(), labResultsTable.getDataAsText(entry.getValue().getKey(), entry.getKey()));
        }
    }

    private void verifyDatasetNames(List<String> datasetNames)
    {
        clickTab("Manage");
        assertElementPresent(Locator.tagWithText("td", "This study defines " + datasetNames.size() + " datasets"));
        clickAndWait(Locator.linkWithText("Manage Datasets"));
        for (String datasetName : datasetNames)
            assertElementPresent(Locator.linkWithText(datasetName));
    }

    private void verifyVisitTags()
    {
        final List<String> VISIT_TAG_MAP_TAGS =
                Arrays.asList("First Vaccination", "First Vaccination", "Second Vaccination", "Second Vaccination",
                              "Follow Up", "Follow Up", "Follow Up", "Follow Up", "First Vaccination",
                              "Second Vaccination", "First Vaccination", "Second Vaccination");
        final List<String> VISIT_TAG_MAP_VISITS =
                Arrays.asList("Day -1001", "Day -1001", "Day -1316", "Day -1316", "Day -1351", "Day -1351",
                              "Day -1377", "Day -1377", "Day 0", "Day 101", "Day 3", "Day 62");
        final List<String> VISIT_TAG_MAP_COHORTS =
                Arrays.asList(" ", " ", " ", " ", " ", " ", " ", " ",
                              "1/3 - Heterologous boost regimen", "2/4 - Heterologous boost regimen",
                              "2/4 - Heterologous boost regimen", "1/3 - Heterologous boost regimen");

        Bag<List<String>> expectedRows = new HashBag<>(DataRegionTable.collateColumnsIntoRows(VISIT_TAG_MAP_TAGS, VISIT_TAG_MAP_VISITS, VISIT_TAG_MAP_COHORTS));

        // Check visit tags
        clickFolder(getProjectName());
        goToModule("Query");
        viewQueryData("study", "VisitTagMap");
        DataRegionTable visitTagMaps = new DataRegionTable("query", this);
        Bag<List<String>> actualRows = new HashBag<>(visitTagMaps.getRows("VisitTag", "Visit", "Cohort"));

        assertEquals("Wrong Rows", expectedRows, actualRows);

        List<WebElement> buttons = visitTagMaps.getHeaderButtons();
        Assert.assertFalse("Should not be able to 'Insert New' into VisitTagMap from dataspace project", getTexts(buttons).contains("Insert"));


        final List<String> STUDY5_VISIT_TAG_MAP_TAGS = Arrays.asList("First Vaccination", "Second Vaccination", "Follow Up", "Follow Up");
        final List<String> STUDY5_VISIT_TAG_MAP_VISITS = Arrays.asList("Day -1001", "Day -1316", "Day -1351", "Day -1377");
        final List<String> STUDY5_VISIT_TAG_MAP_COHORTS = Arrays.asList(" ", " ", " ", " ");
        expectedRows = new HashBag<>(DataRegionTable.collateColumnsIntoRows(STUDY5_VISIT_TAG_MAP_TAGS, STUDY5_VISIT_TAG_MAP_VISITS, STUDY5_VISIT_TAG_MAP_COHORTS));

        clickFolder(FOLDER_STUDY5);
        goToModule("Query");
        viewQueryData("study", "VisitTagMap");
        visitTagMaps = new DataRegionTable("query", this);
        actualRows = new HashBag<>(visitTagMaps.getRows("VisitTag", "Visit", "Cohort"));

        assertEquals("Wrong Visit Tag Map Rows in study folder", expectedRows, actualRows);

        visitTagMaps.clickInsertNewRow();

        clickTab("Overview");
        _portalHelper.removeWebPart("VisitTagMap");
        _portalHelper.addQueryWebPart(VISIT_TAG_QWP_TITLE, "study", "VisitTag", null);
        assertElementNotPresent(Locator.xpath("//a[@class='labkey-menu-button']//span[text()='Insert']"));
    }
}
