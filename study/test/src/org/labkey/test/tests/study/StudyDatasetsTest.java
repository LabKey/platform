/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyA;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;
import org.labkey.test.util.PortalHelper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

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
        clickButtonContainingText("Add Field", 0);
        waitForElement(Locator.xpath("//input[@id='name1-input']"));
        assertTextNotPresent("YTest");
        setFormElement(Locator.xpath("//input[@id='name1-input']"), "YTest");
        clickButtonContainingText("Add Field", 0);
        waitForElement(Locator.xpath("//input[@id='name2-input']"));
        assertTextNotPresent("ZTest");
        setFormElement(Locator.xpath("//input[@id='name2-input']"), "ZTest");
        clickButton("Save");
    }

    @LogMethod
    protected void renameDataset(String orgName, String newName, String orgLabel, String newLabel, String... fieldNames)
    {
        _studyHelper.goToManageDatasets();

        waitForElement(Locator.xpath("//a[text()='" + orgName + "']"));
        click(Locator.xpath("//a[text()='" + orgName + "']"));
        waitForText("Edit Definition");
        clickButton("Edit Definition");

        waitForElement(Locator.xpath("//input[@name='dsName']"));
        setFormElement(Locator.xpath("//input[@name='dsName']"), newName);
        setFormElement(Locator.xpath("//input[@name='dsLabel']"), newLabel);

        for (String fieldName : fieldNames)
        {
            assertTextPresent(fieldName);
        }

        clickButton("Save");

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
        _studyHelper.goToManageDatasets();
        waitForElement(Locator.xpath("//a[text()='" + datasetName + "']"));
        click(Locator.xpath("//a[text()='" + datasetName + "']"));
        waitForText("Dataset Properties");
        clickButtonContainingText("View Data");
        waitForText("All data");
        clickButtonContainingText("Import Data");
        waitForText("Copy/paste text");
        setFormElement(Locator.xpath("//textarea"), header + tsv);
        clickButton("Submit", 0);
        waitForText(msg);
    }

    @LogMethod
    protected void deleteFields(String name)
    {
        _studyHelper.goToManageDatasets();

        waitForElement(Locator.xpath("//a[text()='" + name + "']"));
        click(Locator.xpath("//a[text()='" + name + "']"));
        waitForText("Edit Definition");
        clickButton("Edit Definition");

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
        _studyHelper.goToManageDatasets();

        waitForElement(Locator.xpath("//a[text()='" + name + "']"));
        click(Locator.xpath("//a[text()='" + name + "']"));
        waitForText("Edit Definition");
        clickButton("Edit Definition");

        for(String item : items)
        {
            waitForText(item);
        }
    }

    @LogMethod
    protected void checkDataElementsPresent(String name, String... items)
    {
        clickProject(getProjectName());
        clickFolder(getFolderName());
        _studyHelper.goToManageDatasets();
        waitForElement(Locator.xpath("//a[text()='" + name + "']"));
        click(Locator.xpath("//a[text()='" + name + "']"));
        waitForText("Dataset Properties");
        clickButtonContainingText("View Data");
        for(String item : items)
        {
            waitForText(item);
        }
    }

    @LogMethod
    private void verifySideFilter()
    {
        clickProject(getProjectName());
        clickFolder(getFolderName());
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));
        DataRegionTable dataregion = new DataRegionTable("Dataset", this);
        verifyFilterPanelOnDemographics(dataregion);

        _studyHelper.deleteCustomParticipantGroup(EXTRA_GROUP, "Mouse");

        clickProject(getProjectName());
        clickFolder(getFolderName());
        PortalHelper portalHelper = new PortalHelper(this);
        portalHelper.addQueryWebPart("Demographics", "study", "DEM-1 (DEM-1: Demographics)", null);
        dataregion = new DataRegionTable("qwp6", this);
        verifyFilterPanelOnDemographics(dataregion);
    }

    @LogMethod
    private void verifyFilterPanelOnDemographics(DataRegionTable dataset)
    {
        dataset.openSideFilterPanel();

        waitForElement(Locator.paginationText(24));
        _ext4Helper.clickParticipantFilterGridRowText(GROUP1A, 0);
        waitForElementToDisappear(Locator.css(".labkey-pagination"), WAIT_FOR_JAVASCRIPT);
        waitForElement(Locator.linkWithText(PTIDS[0]));
        assertElementPresent(Locator.linkWithText(PTIDS[1]));
        assertEquals("Wrong number of rows after filter", 2, dataset.getDataRowCount());

        _ext4Helper.checkGridRowCheckbox(GROUP1B); // GROUP1A OR GROU1B
        waitForElement(Locator.linkWithText(PTIDS[2]));
        assertElementPresent(Locator.linkWithText(PTIDS[0]));
        assertElementPresent(Locator.linkWithText(PTIDS[1]));
        assertElementPresent(Locator.linkWithText(PTIDS[3]));
        assertEquals("Wrong number of rows after filter", 4, dataset.getDataRowCount());

        _ext4Helper.clickParticipantFilterGridRowText(GROUP2A, 0);// (GROUP1A OR GROU1B) AND GROUP2A
        waitForElementToDisappear(Locator.linkWithText(PTIDS[2]));
        waitForElement(Locator.linkWithText(PTIDS[1]));
        assertElementPresent(Locator.linkWithText(PTIDS[3]));
        assertEquals("Wrong number of rows after filter", 2, dataset.getDataRowCount());

        _ext4Helper.clickParticipantFilterGridRowText("Not in any group", 1); // (GROUP1A OR GROUP1B) AND (CATEGORY2 = NULL)
        waitForElementToDisappear(Locator.linkWithText(PTIDS[1]));
        waitForElement(Locator.linkWithText(PTIDS[0]));
        assertEquals("Wrong number of rows after filter", 1, dataset.getDataRowCount());

        _ext4Helper.clickParticipantFilterCategory(CATEGORY2); // (GROUP1A OR GROUP1B)
        waitForElement(Locator.linkWithText(PTIDS[2]));
        assertElementPresent(Locator.linkWithText(PTIDS[0]));
        assertElementPresent(Locator.linkWithText(PTIDS[1]));
        assertElementPresent(Locator.linkWithText(PTIDS[3]));
        assertEquals("Wrong number of rows after filter", 4, dataset.getDataRowCount());

        _ext4Helper.uncheckGridRowCheckbox("Group 1"); // (GROUP1A OR GROUP1B) AND (NOT(COHORT 1))
        waitForElementToDisappear(Locator.linkWithText(PTIDS[0]));
        waitForElement(Locator.linkWithText(PTIDS[1]));
        assertElementPresent(Locator.linkWithText(PTIDS[2]));
        assertEquals("Wrong number of rows after filter", 2, dataset.getDataRowCount());

        dataset.toggleAllFacetsCheckbox();
        waitForElement(Locator.linkWithText(PTIDS[5]));
        assertEquals("Wrong number of rows after filter", 24, dataset.getDataRowCount());

        _extHelper.clickMenuButton(false, "Mouse Groups", "Create Mouse Group", "From All Mice");
        final Locator.XPathLocator window = Ext4Helper.Locators.window("Define Mouse Group");
        final Locator.XPathLocator groupLabelInput = window.append(Locator.id("groupLabel-inputEl").notHidden());
        waitForElement(groupLabelInput);
        setFormElement(groupLabelInput, EXTRA_GROUP);
        _ext4Helper.clickWindowButton("Define Mouse Group", "Save", 0, 0);
        waitForElement(DataRegionTable.Locators.facetRow(EXTRA_GROUP, EXTRA_GROUP));
    }

    // in 13.2 Sprint 1 we changed reports and views so that they are associated with query name instead of label (i.e. dataset name instead of label)
    // there is also a migration step that happens when importing study archives with version < 13.11 to fixup these report/view references
    // this method verifies that migration on import for a handful of reports and views
    @LogMethod
    private void verifyReportAndViewDatasetReferences()
    {
        clickProject(getProjectName());
        clickFolder(getFolderName());

        // verify the reports and views dataset label/name references after study import
        //verifyExpectedReportsAndViewsExist();
        verifyCustomViewWithDatasetJoins("CPS-1: Screening Chemistry Panel", CUSTOM_VIEW_WITH_DATASET_JOINS, true, true, "DataSets/DEM-1/DEMbdt", "DataSets/DEM-1/DEMsex");
        verifyTimeChart("APX-1", "APX-1: Abbreviated Physical Exam");
        verifyScatterPlot("APX-1: Abbreviated Physical Exam");
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
        verifyScatterPlot("Abbreviated Physical Exam");
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
        _customizeViewsHelper.addCustomizeViewColumn("DataSets/ECI-1/ECIelig");
        _customizeViewsHelper.addCustomizeViewColumn("DataSets/DEM-1/DEMbdt");
        _customizeViewsHelper.addCustomizeViewColumn("DataSets/DEM-1/DEMsex");
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

        if (checkSort)
        {
            assertTextPresentInThisOrder("Male", "Female"); // verify joined fields in sort
        }
        if (checkAggregates)
        {
            DataRegionTable drt = new DataRegionTable("Dataset", this); // verify joined fields in filter
            assertEquals("Unexpected number of rows, filter was not applied correctly", 3, drt.getDataRowCount()); // 3 data rows + aggregates
            assertTrue("Expected aggregate row", drt.hasAggregateRow());
            assertTextPresentInThisOrder("Avg Cre:", "Agg Count:"); // verify joined fields in aggregates
        }
        for (String colFieldKey : colFieldKeys) // verify joined fields in column select
        {
            assertElementPresent(Locator.xpath("//td[@title = '" + colFieldKey + "']"));
        }
        _customizeViewsHelper.openCustomizeViewPanel();
        assertTextNotPresent("not found", "Field not found");
        _customizeViewsHelper.applyCustomView();
    }

    @LogMethod
    private void verifyTimeChart(String datasetName, String datasetLabel)
    {
        log("Verfiy dataset label to name fixup for Time Chart");
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
        goToSvgAxisTab("APX Main Title");
        waitAndClick(Locator.xpath("//span[contains(@class, 'iconReload')]"));
        assertEquals(datasetLabel, getFormElement(Locator.name("chart-title-textfield")));
        clickButton("Cancel", 0);
    }

    @LogMethod
    private void verifyScatterPlot(String datasetLabel)
    {
        log("Verify dataset label to name fixup for Scatter Plot");
        goToManageViews();
        clickViewDetailsLink(SCATTER_PLOT_REPORT_NAME);
        clickAndWait(Locator.linkContainingText("Edit Report"));
        _ext4Helper.waitForMaskToDisappear();
        assertTextNotPresent("An unexpected error occurred while retrieving data", "doesn't exist", "may have been deleted");
        // verify that the main title reset goes back to the dataset label - measue name
        goToSvgAxisTab("APX Main Title");
        setFormElement(Locator.name("chart-title-textfield"), "test");
        waitForElementToDisappear(Locator.xpath("//a[contains(@class, 'x4-btn-disabled')]//span[contains(@class, 'iconReload')]"));
        click(Locator.xpath("//span[contains(@class, 'iconReload')]"));
        assertEquals(datasetLabel + " - 3. BP systolic xxx/", getFormElement(Locator.name("chart-title-textfield")));
        clickButton("Cancel", 0);
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
