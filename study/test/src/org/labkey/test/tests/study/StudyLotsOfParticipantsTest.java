/*
 * Copyright (c) 2016-2019 LabKey Corporation
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

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.InDevelopment;
import org.labkey.test.components.ChartTypeDialog;
import org.labkey.test.components.LookAndFeelTimeChart;
import org.labkey.test.pages.TimeChartWizard;
import org.labkey.test.util.Crawler;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.LogMethod;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@Category({InDevelopment.class})
public class StudyLotsOfParticipantsTest extends BaseWebDriverTest
{
    // TODO add test case for Scatter plot binning

    // Study folder archive with > 130,000 participants, 2 datasets, 11 visits, 3 cohorts, and 3 participants groups
    private static final File STUDY_FOLDER_ZIP = TestFileUtils.getSampleData("study/LotsOfPtidsStudy.folder.zip");

    // If you are ever unlucky and have to work on this test, only do the import once. Uncomment this method and
    // comment out the initProject method.
//    @Override
//    protected void doCleanup(boolean afterTest) throws TestTimeoutException
//    {
//        // Don't reimport the data.
//    }

    @BeforeClass
    public static void initProject()
    {
        StudyLotsOfParticipantsTest init = (StudyLotsOfParticipantsTest)getCurrentTest();
        init.setupFolder();
    }

    @LogMethod
    protected void setupFolder()
    {
        _containerHelper.createProject(getProjectName(), null);

        // Sometimes this import takes longer than the default import wait.
        importFolderFromZip(STUDY_FOLDER_ZIP, false, 1, false, MAX_WAIT_SECONDS * 2000);
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @Test
    public void testTimeChart()
    {
        // Issue 22254
        waitForText("Data is present for 132,070 Participants.");
        clickAndWait(Locator.tagWithId("a", "ClinicalandAssayDataTab"));
        _extHelper.waitForLoadingMaskToDisappear(WAIT_FOR_JAVASCRIPT);
        waitForElement(Locator.linkWithText("Results"));
        click(Locator.linkWithText("Results"));
        DataRegionTable resultsTable = new DataRegionTable("Dataset", this);
        ChartTypeDialog chartTypeDialog = resultsTable.createChart();
        chartTypeDialog.setChartType(ChartTypeDialog.ChartType.Time)
                .selectStudyQuery("Results")
                .setYAxis("Value1")
                .clickApply();
        TimeChartWizard timeChartWizard = new TimeChartWizard(this);
        timeChartWizard.waitForWarningMessage("No calculated interval values (i.e. Days, Months, etc.) for the selected 'Measure Date' and 'Interval Start Date'.");
        chartTypeDialog = timeChartWizard.clickChartTypeButton();
        chartTypeDialog.setTimeAxisType(ChartTypeDialog.TimeAxisType.Visit).clickApply();

        // verify paging for large list of ptids
        timeChartWizard.verifySvgChart(5, new String[]{"00001", "00002", "00003", "00004", "00005"});
        timeChartWizard.goToNextParticipantsPage();
        timeChartWizard.checkFilterGridRow("02000");
        timeChartWizard.verifySvgChart(6, new String[]{"00001", "00002", "00003", "00004", "00005", "02000"});

        // verify switch to ptid groups
        timeChartWizard.clickSwitchToGroupButton(true);
        timeChartWizard.verifySvgChart(5, new String[]{"Group A", "Group B", "UNK", "female", "male"});
        timeChartWizard.checkFilterGridRow("Group C");
        timeChartWizard.verifySvgChart(6, new String[]{"Group A", "Group B", "Group C", "UNK", "female", "male"});
        LookAndFeelTimeChart chartLayoutDialog = timeChartWizard.clickChartLayoutButton();
        chartLayoutDialog.checkShowIndividualLines();

        // Cannot call chartLayoutDialog.clickApplyWithError() it expects a labkey-error not a warning message in the chart.
        clickButton("Apply", 0);

        timeChartWizard.waitForWarningMessage("Unable to display individual series lines for greater than 10,000 total participants.");
        timeChartWizard.uncheckFilterGridRow("UNK");
        timeChartWizard.uncheckFilterGridRow("Group A");
        timeChartWizard.uncheckFilterGridRow("Group B");
        timeChartWizard.uncheckFilterGridRow("Group C");
        timeChartWizard.verifySvgChart(14, null);

        // save so we don't get confirm navigation alert
        timeChartWizard.saveReport("Test time chart", "");
    }

    @Override
    protected String getProjectName()
    {
        return "StudyLotsOfParticipantsTest Project";
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

    @Override
    protected List<Crawler.ControllerActionId> getUncrawlableActions()
    {
        return Arrays.asList(new Crawler.ControllerActionId("cohort", "manageCohorts")); // Page is too long with this many participants
    }
}
