/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.test.tests;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.Study;
import org.labkey.test.pages.TimeChartWizard;
import org.labkey.test.util.LogMethod;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Created by cnathe on 2/4/2015.
 */
@Category({DailyB.class, Study.class})
public class StudyLotsOfParticipantsTest extends BaseWebDriverTest
{
    // Study folder archive with > 130,000 participants, 2 datasets, 11 visits, 3 cohorts, and 3 participants groups
    private static final File STUDY_FOLDER_ZIP = TestFileUtils.getSampleData("study/LotsOfPtidsStudy.folder.zip");

    @BeforeClass
    public static void initProject()
    {
        StudyLotsOfParticipantsTest init = (StudyLotsOfParticipantsTest)getCurrentTest();
        init.setupFolder();
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
        waitForText("Data is present for 132070 Participants.");
        TimeChartWizard timeChartWizard = new TimeChartWizard(this);
        timeChartWizard.createNewChart();
        timeChartWizard.chooseInitialMeasure("Results", "Value1");
        timeChartWizard.waitForWarningMessage("No calculated interval values (i.e. Days, Months, etc.) for the selected 'Measure Date' and 'Interval Start Date'.");
        timeChartWizard.changeXAxisToVisitBased("Days Since Start Date", "Visits");

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
        timeChartWizard.showGroupIndividualLines();
        timeChartWizard.waitForWarningMessage("Unable to display individual series lines for greater than 10,000 total participants.");
        timeChartWizard.uncheckFilterGridRow("UNK");
        timeChartWizard.uncheckFilterGridRow("Group A");
        timeChartWizard.uncheckFilterGridRow("Group B");
        timeChartWizard.uncheckFilterGridRow("Group C");
        timeChartWizard.verifySvgChart(14, null);

        // save so we don't get confirm navigation alert
        timeChartWizard.saveReport("Test time chart", "", true);
    }

    @LogMethod(category = LogMethod.MethodType.SETUP)
    protected void setupFolder()
    {
        _containerHelper.createProject(getProjectName(), null);
        importFolderFromZip(STUDY_FOLDER_ZIP);
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
}
