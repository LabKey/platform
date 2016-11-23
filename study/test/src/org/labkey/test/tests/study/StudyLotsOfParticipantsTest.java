/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
    private static final File STUDY_FOLDER_NODATA_ZIP = TestFileUtils.getSampleData("study/LotsOfPtidsStudy_NoData.folder.zip");
    private static final File DATA_DEMOGRAPHICS1 = TestFileUtils.getSampleData("study/Demographics.tsv");
    private static final File DATA_DEMOGRAPHICS2 = TestFileUtils.getSampleData("study/Demographics2.tsv");
    private static final File DATA_DEMOGRAPHICS3 = TestFileUtils.getSampleData("study/Demographics3.tsv");
    private static final File DATA_RESULTS = TestFileUtils.getSampleData("study/Results.tsv");

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
        importFolderFromZip(STUDY_FOLDER_ZIP, false, 1, false);
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @Test
    public void testTimeChart()
    {
        // regression testing for issue 22254
        waitForText("Data is present for 132,070 Participants.");
        goToManageViews();
        _extHelper.clickExtMenuButton(true, Locator.linkContainingText("Add Chart"));
        TimeChartWizard timeChartWizard = new TimeChartWizard(this);
        ChartTypeDialog chartTypeDialog = new ChartTypeDialog(getDriver());
        chartTypeDialog.setChartType(ChartTypeDialog.ChartType.Time)
                .selectStudyQuery("Results")
                .setYAxis("Value1")
                .clickApply();
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
        chartLayoutDialog.checkShowIndividualLines().clickApply();
        timeChartWizard.waitForWarningMessage("Unable to display individual series lines for greater than 10,000 total participants.");
        timeChartWizard.uncheckFilterGridRow("UNK");
        timeChartWizard.uncheckFilterGridRow("Group A");
        timeChartWizard.uncheckFilterGridRow("Group B");
        timeChartWizard.uncheckFilterGridRow("Group C");
        timeChartWizard.verifySvgChart(14, null);

        // save so we don't get confirm navigation alert
        timeChartWizard.saveReport("Test time chart", "", true);
    }

    private void importEmptyFolderAndLoadData()
    {
        importFolderFromZip(STUDY_FOLDER_NODATA_ZIP, false, 1, false);
        clickTab("Clinical and Assay Data");
        waitAndClickAndWait(Locator.linkWithText("Demographics"));
        _listHelper.importDataFromFile(DATA_DEMOGRAPHICS1);
        _listHelper.importDataFromFile(DATA_DEMOGRAPHICS2);
        _listHelper.importDataFromFile(DATA_DEMOGRAPHICS3);

        clickTab("Clinical and Assay Data");
        waitAndClickAndWait(Locator.linkWithText("Results"));
        _listHelper.importDataFromFile(DATA_RESULTS, 5 * 60000);

        // Group A = 00001 - 00500
        _studyHelper.createCustomParticipantGroup(getProjectName(), getProjectName(), "Group A", "Participant", "Test Category", true, true, getPtidsForGroup(1));
        // Group B = 00501 - 01000
        _studyHelper.createCustomParticipantGroup(getProjectName(), getProjectName(), "Group B", "Participant", "Test Category", false, true, getPtidsForGroup(501));
        // Group C = 99500 - 99999
        _studyHelper.createCustomParticipantGroup(getProjectName(), getProjectName(), "Group C", "Participant", "Test Category", false, true, getPtidsForGroup(99500));
    }

    private String[] getPtidsForGroup(int startId)
    {
        String[] ptids = new String[500];
        for (int i = 0; i < 500; i++)
            ptids[i] = ("00000" + (i+startId)).substring((""+(i+startId)).length());
        return ptids;
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
