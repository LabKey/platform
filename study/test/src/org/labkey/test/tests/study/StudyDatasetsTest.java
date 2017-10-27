/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyA;
import org.labkey.test.components.LookAndFeelScatterPlot;
import org.labkey.test.components.LookAndFeelTimeChart;
import org.labkey.test.components.PagingWidget;
import org.labkey.test.components.ext4.Window;
import org.labkey.test.components.study.DatasetFacetPanel;
import org.labkey.test.pages.EditDatasetDefinitionPage;
import org.labkey.test.pages.TimeChartWizard;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;
import org.labkey.test.util.PortalHelper;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.labkey.test.components.ext4.Window.Window;

@Category({DailyA.class})
public class StudyDatasetsTest extends BaseWebDriverTest
{
    private static final String CATEGORY1 = "Category1";
    private static final String GROUP1A = "Group1A";
    private static final String GROUP1B = "Group1B";
    private static final String CATEGORY2 = "Category2";
    private static final String GROUP2A = "Group2A";
    private static final String GROUP2B = "Group2B";
    private static final String EXTRA_GROUP = "Extra Group";
    private static final String[] PTIDS = {"999320016","999320518","999320529","999320533","999320557","999320565"};
    private static final String CUSTOM_VIEW_WITH_DATASET_JOINS = "Chemistry + Criteria + Demographics";
    private static final String CUSTOM_VIEW_PRIVATE = "My Private Custom View";
    private static final String TIME_CHART_REPORT_NAME = "Time Chart: Body Temp + Pulse For Group 2";
    private static final String SCATTER_PLOT_REPORT_NAME = "Scatter: Systolic vs Diastolic";
    private static final String PTID_REPORT_NAME = "Mouse Report: 2 Dem Vars + 3 Other Vars";
    private static Map<String, String> EXPECTED_REPORTS = new HashMap<>();
    private static Map<String, String> EXPECTED_CUSTOM_VIEWS = new HashMap<>();
    private static final String DATASET_HEADER = "mouseId\tsequenceNum\tXTest\tYTest\tZTest\n";
    private static final String DATASET_B_DATA =
            "a1\t1\tx1\ty1\tz1\n" +
            "a2\t2\tx2\ty2\tz2\n" +
            "a3\t3\tx3\ty3\tz3\n" +
            "a4\t4\tx4\ty4\tz4\n" +
            "a5\t5\tx5\ty5\tz5\n" +
            "a6\t6\tx6\ty6\tz6\n";
    private static final String DATASET_B_MERGE =
            "a4\t4\tx4_merged\ty4_merged\tz4_merged\n";

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Nullable
    @Override
    protected String getProjectName()
    {
        return "StudyDatasetsTest Project";
    }

    protected String getFolderName()
    {
        return "My Study";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("study");
    }

    @BeforeClass
    public static void doSetup()
    {
        StudyDatasetsTest init = (StudyDatasetsTest)getCurrentTest();
        init.doCreateSteps();
    }

    private void doCreateSteps()
    {
        _containerHelper.createProject(getProjectName(), null);
        _containerHelper.createSubfolder(getProjectName(), getProjectName(), getFolderName(), "Study", null, true);
        importFolderFromZip(TestFileUtils.getSampleData("studies/AltIdStudy.folder.zip"));

        _studyHelper.createCustomParticipantGroup(getProjectName(), getFolderName(), GROUP1A, "Mouse", CATEGORY1, true, null, PTIDS[0], PTIDS[1]);
        _studyHelper.createCustomParticipantGroup(getProjectName(), getFolderName(), GROUP1B, "Mouse", CATEGORY1, false, null, PTIDS[2], PTIDS[3]);
        _studyHelper.createCustomParticipantGroup(getProjectName(), getFolderName(), GROUP2A, "Mouse", CATEGORY2, true, null, PTIDS[1], PTIDS[3]);
        _studyHelper.createCustomParticipantGroup(getProjectName(), getFolderName(), GROUP2B, "Mouse", CATEGORY2, false, null, PTIDS[2], PTIDS[4]);
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
        clickFolder(getFolderName());
    }

    @Test
    public void testDatasets()
    {
        createDataset("A");
        renameDataset("A", "Original A", "A", "Original A", "XTest", "YTest", "ZTest");
        createDataset("A");
        deleteFields("A");

        checkFieldsPresent("Original A", "YTest", "ZTest");

        verifySideFilter();

        verifyReportAndViewDatasetReferences();

        createDataset("B");
        importDatasetData("B", DATASET_HEADER, DATASET_B_DATA, "All data");
        checkDataElementsPresent("B",  DATASET_B_DATA.split("\t|\n"));

        // Issue 21234: Dataset import no longer merges rows during import
        importDatasetData("B", DATASET_HEADER, DATASET_B_MERGE, "Duplicate dataset row. All rows must have unique MouseId/SequenceNum values.");
        clickButton("Cancel");
        waitForText("All data");
        checkDataElementsPresent("B", DATASET_B_DATA.split("\t|\n"));
    }

    @LogMethod
    protected void createDataset(@LoggedParam String name)
    {
        _studyHelper.goToManageDatasets();

        waitForText("Create New Dataset");
        click(Locator.xpath("//a[text()='Create New Dataset']"));
        waitForElement(Locator.xpath("//input[@name='typeName']"));
        setFormElement(Locator.xpath("//input[@name='typeName']"), name);
        clickButton("Next");

        waitForElement(Locator.xpath("//input[@id='name0-input']"));
        assertTextNotPresent("XTest");
        setFormElement(Locator.xpath("//input[@id='name0-input']"), "XTest");
        mouseOver(Locator.xpath("//input[@id='name0-input']")); // Moving the mouse because leaving it where it was puts it over the 'move down' icon, which causes a pop-up, which can interfere with following click.
        clickButtonContainingText("Add Field", 0);
        waitForElement(Locator.xpath("//input[@id='name1-input']"));
        assertTextNotPresent("YTest");
        setFormElement(Locator.xpath("//input[@id='name1-input']"), "YTest");
        mouseOver(Locator.xpath("//input[@id='name1-input']")); // Moving the mouse because leaving it where it was puts it over the 'move down' icon, which causes a pop-up, which can interfere with following click.
        clickButtonContainingText("Add Field", 0);
        waitForElement(Locator.xpath("//input[@id='name2-input']"));
        assertTextNotPresent("ZTest");
        setFormElement(Locator.xpath("//input[@id='name2-input']"), "ZTest");
        clickButton("Save");
    }

    @LogMethod
    protected void renameDataset(String orgName, String newName, String orgLabel, String newLabel, String... fieldNames)
    {
        EditDatasetDefinitionPage editDatasetPage = _studyHelper.goToManageDatasets()
                .selectDatasetByName(orgName)
                .clickEditDefinition();

        editDatasetPage
                .setDatasetName(newName)
                .setDatasetLabel(newLabel);

        for (String fieldName : fieldNames)
        {
            assertTextPresent(fieldName);
        }

        editDatasetPage.save();

        // fix dataset label references in report and view mappings
        for (Map.Entry<String, String> entry : EXPECTED_REPORTS.entrySet())
        {
            if (orgLabel.equals(entry.getValue()))
                EXPECTED_REPORTS.put(entry.getKey(), newLabel);
        }
        for (Map.Entry<String, String> entry : EXPECTED_CUSTOM_VIEWS.entrySet())
        {
            if (orgLabel.equals(entry.getValue()))
                EXPECTED_CUSTOM_VIEWS.put(entry.getKey(), newLabel);
        }
    }

    @LogMethod
    protected void importDatasetData(String datasetName, String header, String tsv, String msg)
    {
        _studyHelper.goToManageDatasets()
                .selectDatasetByName(datasetName)
                .clickViewData();
        waitForText("All data");
        new DataRegionTable("Dataset", getDriver()).clickImportBulkData();
        waitForText("Copy/paste text");
        setFormElement(Locator.xpath("//textarea"), header + tsv);
        clickButton("Submit", 0);
        waitForText(WAIT_FOR_PAGE, msg);
    }

    @LogMethod
    protected void deleteFields(String name)
    {
        EditDatasetDefinitionPage editDatasetPage = _studyHelper.goToManageDatasets()
                .selectDatasetByName(name)
                .clickEditDefinition();

        waitForElement(Locator.xpath("//div[@id='partdelete_2']"));
        click(Locator.id("partdelete_2"));
        clickButtonContainingText("OK", 0);
        waitForElement(Locator.xpath("//div[@id='partdelete_1']"));
        click(Locator.id("partdelete_1"));

        assertTextPresent("XTest");
        assertElementNotPresent(Locator.xpath("//input[@id='name1-input']"));
        assertElementNotPresent(Locator.xpath("//input[@id='name2-input']"));
        clickButton("Save");
    }

    @LogMethod
    protected void checkFieldsPresent(String name, String... items)
    {
        EditDatasetDefinitionPage editDatasetPage = _studyHelper.goToManageDatasets()
                .selectDatasetByName(name)
                .clickEditDefinition();

        for (String item : items)
        {
            waitForText(item);
        }
    }

    @LogMethod
    protected void checkDataElementsPresent(String name, String... items)
    {
        navigateToFolder(getProjectName(), getFolderName());
        _studyHelper.goToManageDatasets()
                .selectDatasetByName(name)
                .clickViewData();
        waitForText(items);
    }

    @LogMethod
    private void verifySideFilter()
    {
        navigateToFolder(getProjectName(), getFolderName());
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));
        verifyFilterPanelOnDemographics("Dataset");

        _studyHelper.deleteCustomParticipantGroup(EXTRA_GROUP, "Mouse");

        navigateToFolder(getProjectName(), getFolderName());
        PortalHelper portalHelper = new PortalHelper(this);
        portalHelper.addQueryWebPart("Demographics", "study", "DEM-1 (DEM-1: Demographics)", null);
        verifyFilterPanelOnDemographics("qwp6");
    }

    @LogMethod
    private void verifyFilterPanelOnDemographics(String regionName)
    {
        DataRegionTable dataregion = new DataRegionTable(regionName, getDriver());
        DatasetFacetPanel facetPanel = dataregion.openSideFilterPanel();

        PagingWidget pagingWidget = dataregion.getPagingWidget();

        waitForElement(Locator.paginationText(24));
        facetPanel.clickGroupLabel(GROUP1A);
        Assert.assertFalse(pagingWidget.getComponentElement().isDisplayed());
        waitForElement(Locator.linkWithText(PTIDS[0]));
        assertElementPresent(Locator.linkWithText(PTIDS[1]));
        assertEquals("Wrong number of rows after filter", 2, dataregion.getDataRowCount());

        facetPanel.getGroupCheckbox(GROUP1B).check(); // GROUP1A OR GROUP1B
        waitForElement(Locator.linkWithText(PTIDS[2]));
        assertElementPresent(Locator.linkWithText(PTIDS[0]));
        assertElementPresent(Locator.linkWithText(PTIDS[1]));
        assertElementPresent(Locator.linkWithText(PTIDS[3]));
        assertEquals("Wrong number of rows after filter", 4, dataregion.getDataRowCount());

        facetPanel.clickGroupLabel(GROUP2A);// (GROUP1A OR GROUP1B) AND GROUP2A
        waitForElementToDisappear(Locator.linkWithText(PTIDS[2]));
        waitForElement(Locator.linkWithText(PTIDS[1]));
        assertElementPresent(Locator.linkWithText(PTIDS[3]));
        assertEquals("Wrong number of rows after filter", 2, dataregion.getDataRowCount());

        facetPanel.clickGroupLabel("Not in any group", 1); // (GROUP1A OR GROUP1B) AND (CATEGORY2 = NULL)
        waitForElementToDisappear(Locator.linkWithText(PTIDS[1]));
        waitForElement(Locator.linkWithText(PTIDS[0]));
        assertEquals("Wrong number of rows after filter", 1, dataregion.getDataRowCount());

        facetPanel.getCategoryCheckbox(CATEGORY2).check(); // (GROUP1A OR GROUP1B)
        waitForElement(Locator.linkWithText(PTIDS[2]));
        assertElementPresent(Locator.linkWithText(PTIDS[0]));
        assertElementPresent(Locator.linkWithText(PTIDS[1]));
        assertElementPresent(Locator.linkWithText(PTIDS[3]));
        assertEquals("Wrong number of rows after filter", 4, dataregion.getDataRowCount());

        facetPanel.getGroupCheckbox("Group 1").uncheck(); // (GROUP1A OR GROUP1B) AND (COHORT 2)
        waitForElementToDisappear(Locator.linkWithText(PTIDS[0]));
        waitForElement(Locator.linkWithText(PTIDS[1]));
        assertElementPresent(Locator.linkWithText(PTIDS[2]));
        assertEquals("Wrong number of rows after filter", 2, dataregion.getDataRowCount());

        facetPanel.toggleAll();
        waitForElement(Locator.linkWithText(PTIDS[5]));
        assertEquals("Wrong number of rows after filter", 24, dataregion.getDataRowCount());

        dataregion.clickHeaderMenu("Groups", false, "Create Mouse Group", "From All Mice");
        Window window = Window(getDriver()).withTitle("Define Mouse Group").waitFor();
        final WebElement groupLabelInput = Locator.id("groupLabel-inputEl").notHidden().waitForElement(window, 10000);
        setFormElement(groupLabelInput, EXTRA_GROUP);
        dataregion.doAndWaitForUpdate(() ->
        {
            window.clickButton("Save", 0);
            window.waitForClose();
        });
        refresh(); // New group doesn't AJAX into facet panel

        // Verify synchronization between URL filters and facet panel

        facetPanel = dataregion.openSideFilterPanel();
        dataregion.doAndWaitForUpdate(() -> dataregion.clickHeaderMenu("Groups", false, CATEGORY2, GROUP2A)); // (Category2 = GROUP2A)
        assertEquals("Wrong number of rows after URL filter", 2, dataregion.getDataRowCount());
        assertTrue("New group should be selected by default", facetPanel.getCategoryCheckbox(EXTRA_GROUP).isChecked());

        // GROUP2A was selected via menu, GROUP2B should initialize unchecked
        assertFalse("Group filter not applied from URL", facetPanel.getGroupCheckbox(GROUP2B).isChecked());

        facetPanel.getGroupCheckbox(GROUP2B).check(); // (Category2 IS ONE OF (Group2A, Group2B))
        waitForElement(Locator.linkWithText(PTIDS[2]));
        assertEquals("Wrong number of rows after filter", 4, dataregion.getDataRowCount());

        dataregion.clickHeaderMenu("Groups", false, CATEGORY2, GROUP2B);
        waitForElementToDisappear(Locator.linkWithText(PTIDS[1]));
        assertEquals("Wrong number of rows after filter", 2, dataregion.getDataRowCount());

        // GROUP2B was selected via menu, GROUP2A should update to unchecked
        assertFalse("Group filter not applied from menu", facetPanel.getGroupCheckbox(GROUP2A).isChecked());

        facetPanel.getGroupCheckbox(GROUP2B).uncheck(); // Should equal NO FILTER
        waitForElement(Locator.paginationText(24));
        facetPanel.getGroupCheckbox("Not in any group", 1).check(); // (Category2 is blank)
        waitForElementToDisappear(Locator.linkWithText(PTIDS[4]));
        assertElementPresent(Locator.linkWithText(PTIDS[0]));
        assertEquals("Wrong number of rows for not in any group", 20, dataregion.getDataRowCount());

        // Regression coverage #26744
        dataregion.setPageSize(40, false); // just to do something else to get the region to update
        sleep(1000);
        assertElementNotPresent(Locator.linkWithText(PTIDS[4]));
        assertElementPresent(Locator.linkWithText(PTIDS[0]));
        assertTrue("Facet panel failed to reflect filters", facetPanel.getGroupCheckbox("Not in any group", 1).isChecked());
        assertFalse("Facet panel failed to reflect opposite filter", facetPanel.getGroupCheckbox(GROUP2A).isChecked());
    }

    // in 13.2 Sprint 1 we changed reports and views so that they are associated with query name instead of label (i.e. dataset name instead of label)
    // there is also a migration step that happens when importing study archives with version < 13.11 to fixup these report/view references
    // this method verifies that migration on import for a handful of reports and views
    @LogMethod
    private void verifyReportAndViewDatasetReferences()
    {
        navigateToFolder(getProjectName(), getFolderName());

        // verify the reports and views dataset label/name references after study import
        //verifyExpectedReportsAndViewsExist();
        verifyCustomViewWithDatasetJoins("CPS-1: Screening Chemistry Panel", CUSTOM_VIEW_WITH_DATASET_JOINS, true, true, "DataSets/DEM-1/DEMbdt", "DataSets/DEM-1/DEMsex");
        verifyTimeChart("APX-1", "APX-1: Abbreviated Physical Exam");
        verifyScatterPlot();
        verifyParticipantReport("DEM-1: 1.Date of Birth", "DEM-1: 2.What is your sex?", "APX-1: 1. Weight", "APX-1: 2. Body Temp", "ECI-1: 1.Meet eligible criteria?");

        // create a private custom view with dataset joins
        createPrivateCustomView();
        verifyCustomViewWithDatasetJoins("CPS-1: Screening Chemistry Panel", CUSTOM_VIEW_PRIVATE, false, false, "DataSets/DEM-1/DEMbdt", "DataSets/DEM-1/DEMsex");

        // rename and relabel the datasets related to these reports and views
        renameDataset("DEM-1", "demo", "DEM-1: Demographics", "Demographics");
        renameDataset("APX-1", "abbrphy", "APX-1: Abbreviated Physical Exam", "Abbreviated Physical Exam");
        renameDataset("ECI-1", "eligcrit", "ECI-1: Eligibility Criteria", "Eligibility Criteria");
        renameDataset("CPS-1", "scrchem", "CPS-1: Screening Chemistry Panel", "Screening Chemistry Panel");

        // verify the reports and views dataset label/name references after dataset rename and relabel
        //verifyExpectedReportsAndViewsExist();
        verifyCustomViewWithDatasetJoins("Screening Chemistry Panel", CUSTOM_VIEW_WITH_DATASET_JOINS, true, true, "DataSets/eligcrit/ECIelig", "DataSets/demo/DEMbdt", "DataSets/demo/DEMsex");
        verifyCustomViewWithDatasetJoins("Screening Chemistry Panel", CUSTOM_VIEW_PRIVATE, false, false, "DataSets/eligcrit/ECIelig", "DataSets/demo/DEMbdt", "DataSets/demo/DEMsex");
        verifyTimeChart("abbrphy", "Abbreviated Physical Exam");
        verifyScatterPlot();
        verifyParticipantReport("demo: 1.Date of Birth", "demo: 2.What is your sex?", "abbrphy: 1. Weight", "abbrphy: 2. Body Temp", "eligcrit: 1.Meet eligible criteria?");
    }

    /**
     * In 13.3 we merged dataViews and manageViews. The implicit categories prevelant in manageViews was discarded in favor of the
     * explicit category management created for dataViews. Don't use this obsolete method for the near future.
     */
    @LogMethod
    private void verifyExpectedReportsAndViewsExist()
    {
        if (EXPECTED_REPORTS.size() == 0)
        {
            EXPECTED_REPORTS.put("Chart View: Systolic vs Diastolic", "APX-1: Abbreviated Physical Exam");
            EXPECTED_REPORTS.put("Crosstab: MouseId Counts", "APX-1: Abbreviated Physical Exam");
            EXPECTED_REPORTS.put("R Report: Dataset Column Names", "APX-1: Abbreviated Physical Exam");
            EXPECTED_REPORTS.put(SCATTER_PLOT_REPORT_NAME, "APX-1: Abbreviated Physical Exam");
            EXPECTED_REPORTS.put(TIME_CHART_REPORT_NAME, "APX-1: Abbreviated Physical Exam");
            EXPECTED_REPORTS.put(PTID_REPORT_NAME, "Stand-alone views");
        }

        if (EXPECTED_CUSTOM_VIEWS.size() == 0)
        {
            EXPECTED_CUSTOM_VIEWS.put(CUSTOM_VIEW_WITH_DATASET_JOINS, "CPS-1: Screening Chemistry Panel");
            EXPECTED_CUSTOM_VIEWS.put("Abbreviated Demographics", "DEM-1: Demographics");
        }

        goToManageViews();

        log("Verify that all reports were imported");
        for (Map.Entry<String, String> entry : EXPECTED_REPORTS.entrySet())
        {
            expandManageViewsRow(entry.getKey(), entry.getValue());
        }
        log("Verify that all custom views were imported");
        for (Map.Entry<String, String> entry : EXPECTED_CUSTOM_VIEWS.entrySet())
        {
            expandManageViewsRow(entry.getKey(), entry.getValue());
        }
    }

    @LogMethod
    private void createPrivateCustomView()
    {
        log("Create private custom view");
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText("CPS-1: Screening Chemistry Panel"));
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.addColumn("DataSets/ECI-1/ECIelig");
        _customizeViewsHelper.addColumn("DataSets/DEM-1/DEMbdt");
        _customizeViewsHelper.addColumn("DataSets/DEM-1/DEMsex");
        _customizeViewsHelper.saveCustomView(CUSTOM_VIEW_PRIVATE, false);

        EXPECTED_CUSTOM_VIEWS.put(CUSTOM_VIEW_PRIVATE, "CPS-1: Screening Chemistry Panel");
    }

    @LogMethod
    private void verifyCustomViewWithDatasetJoins(String datasetLabel, String viewName, boolean checkSort, boolean checkAggregates, String... colFieldKeys)
    {
        log("Verify dataset label to name fixup for custom view import");
        goToManageViews();
        clickAndWait(getViewLocator(viewName));
        waitForElement(Locator.tagWithText("span", viewName));

        DataRegionTable drt = new DataRegionTable("query", getDriver());
        if (checkSort)
        {
            assertTextPresentInThisOrder("Male", "Female"); // verify joined fields in sort
        }
        if (checkAggregates)
        {
            assertEquals("Unexpected number of rows, filter was not applied correctly", 3, drt.getDataRowCount()); // 3 data rows + aggregates
            assertTrue("Expected aggregate row", drt.hasSummaryStatisticRow());
            assertElementPresent(Locator.tagWithClass("span", "summary-stat-label").containing("Avg Cre"));
            assertElementPresent(Locator.tagWithClass("span", "summary-stat-label").containing("Agg Count"));
        }

        _customizeViewsHelper.openCustomizeViewPanel();
        assertTextNotPresent("not found", "Field not found");
        _customizeViewsHelper.closePanel();

        if (colFieldKeys.length > 0)
        {
            List<String> columnNames = drt.getColumnNames();
            List<String> missingColumnNames = new ArrayList<>(Arrays.asList(colFieldKeys));
            missingColumnNames.removeAll(columnNames);
            assertTrue("Missing columns: " + String.join(", ", missingColumnNames), missingColumnNames.isEmpty());
        }
    }

    @LogMethod
    private void verifyTimeChart(String datasetName, String datasetLabel)
    {
        log("Verify dataset label to name fix-up for Time Chart");
        goToManageViews();
        clickViewDetailsLink(TIME_CHART_REPORT_NAME);
        clickAndWait(Locator.linkContainingText("Edit Report"));
        waitForElement(Locator.css("svg text").withText("APX Main Title"));
        assertTextNotPresent("Error: Could not find query"); // error message from 13.1 when dataset label was changed
        waitAndClick(Locator.css("svg a>path")); // click first data point
        _extHelper.waitForExtDialog("Data Point Information");
        assertTextPresent("Query Name:" + datasetName);
        assertTextNotPresent("Query Name:" + datasetLabel);
        clickButton("OK", 0);
        waitForElement(Locator.css("svg"));

        TimeChartWizard timeChartWizard = new TimeChartWizard(this);
        LookAndFeelTimeChart lookAndFeelDialog = timeChartWizard.clickChartLayoutButton();
        lookAndFeelDialog.clickResetTitle();
        assertEquals(datasetLabel, lookAndFeelDialog.getPlotTitle());
        lookAndFeelDialog.clickCancel();

        timeChartWizard.reSaveReport();
    }

    @LogMethod
    private void verifyScatterPlot()
    {
        log("Verify dataset label to name fixup for Scatter Plot");
        goToManageViews();
        clickViewDetailsLink(SCATTER_PLOT_REPORT_NAME);
        clickAndWait(Locator.linkContainingText("Edit Report"));
        _ext4Helper.waitForMaskToDisappear();
        assertTextNotPresent("An unexpected error occurred while retrieving data", "doesn't exist", "may have been deleted");
        // verify that the main title reset goes back to the dataset label - measue name
        waitForElement(Ext4Helper.Locators.ext4Button("Chart Layout").enabled());
        clickButton("Chart Layout", 0);
        LookAndFeelScatterPlot layoutDialog = new LookAndFeelScatterPlot(getDriver());
        layoutDialog.setPlotTitle("test");
        layoutDialog.clickCancel();
        assertTextPresent("APX Main Title", 2); // The count is 2 because the text is present on the plot, and in the dialog (which is now hidden).
    }

    @LogMethod
    private void verifyParticipantReport(String... measureKeys)
    {
        log("Verify dataset label to name fixup for Participant Report");
        goToManageViews();
        clickAndWait(getViewLocator(PTID_REPORT_NAME));
        waitForText("999320016");
        assertTextPresent("Showing 10 Results");
        for (String measureKey : measureKeys)
        {
            // element will be 'td' for demographic measures and 'th' for others
            if (!isElementPresent(Locator.xpath("//td[contains(@data-qtip, '" + measureKey + "')]")) && !isElementPresent(Locator.xpath("//th[contains(@data-qtip, '" + measureKey + "')]")))
            {
                fail("Unable to find measure with key: " + measureKey);
            }
        }
    }

    private void expandManageViewsRow(String name, String category)
    {
        String categoryXpath = "//div[contains(@class, 'x-grid-group-title') and text() = '" + category + "']/../../";
        waitForElement(Locator.xpath(categoryXpath + "/div[text() = '" + name + "']"));
        click(Locator.tagWithText("div", name));
    }

    private Locator getViewLocator(String viewName)
    {
        waitForElement(Locator.linkContainingText(viewName), WAIT_FOR_JAVASCRIPT);
        return Locator.linkContainingText(viewName);
    }

    protected void clickViewDetailsLink(String reportName)
    {
        Locator link = Locator.xpath("//tr").withClass("x4-grid-row").containing(reportName).append("//a[contains(@data-qtip, 'Click to navigate to the Detail View')]");

        waitForElement(link);
        clickAndWait(link);
    }
}
