/*
 * Copyright (c) 2017 LabKey Corporation
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

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.Assert;
import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyC;
import org.labkey.test.components.studydesigner.AssayScheduleWebpart;
import org.labkey.test.components.studydesigner.BaseManageVaccineDesignVisitPage;
import org.labkey.test.components.studydesigner.ManageAssaySchedulePage;
import org.labkey.test.pages.AssayQueryConfig;
import org.labkey.test.pages.ProgressReportConfigPage;
import org.labkey.test.tests.ReportTest;
import org.labkey.test.util.ExcelHelper;
import org.labkey.test.util.PortalHelper;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;

@Category({DailyC.class})
public class ProgressReportTest extends ReportTest
{
    private static final File STUDY_ZIP = TestFileUtils.getSampleData("studies/LabkeyDemoStudy.zip");
    private static final String[] ASSAYS = {"Elispot", "Luminex", "VSVG"};

    private static List<BaseManageVaccineDesignVisitPage.Visit> VISITS = Arrays.asList(
            new BaseManageVaccineDesignVisitPage.Visit("Baseline", 0.0, 0.0),
            new BaseManageVaccineDesignVisitPage.Visit("Month 1", 1.0, 31.0),
            new BaseManageVaccineDesignVisitPage.Visit("Month 2", 32.0, 60.0),
            new BaseManageVaccineDesignVisitPage.Visit("Month 3", 61.0, 91.0),
            new BaseManageVaccineDesignVisitPage.Visit("Month 4", 92.0, 121.0),
            new BaseManageVaccineDesignVisitPage.Visit("Month 5", 122.0, 152.0),
            new BaseManageVaccineDesignVisitPage.Visit("Month 6", 153.0, 182.0),
            new BaseManageVaccineDesignVisitPage.Visit("Month 7", 183.0, 213.0)
    );

    private static final String LUMINEX_PROGRESS_REPORT = "SELECT ParticipantId, ParticipantVisit.Visit, Type, MAX(ParticipantVisit.Visit.sequenceNumMin) AS SequenceNum,\n" +
        "CASE WHEN Type = 'X1' THEN 'collected'\n" +
        "WHEN Type = 'X10' THEN 'not-collected'\n" +
        "WHEN Type = 'X11' THEN 'not-received'\n" +
        "WHEN Type = 'X12' THEN 'not-available'\n" +
        "WHEN Type = 'X13' THEN 'unusable'\n" +
        "WHEN Type = 'X14' THEN 'unexpected'\n" +
        "END AS Status FROM LuminexAssay GROUP BY ParticipantId, ParticipantVisit.Visit, Type";

    private static final String LUMINEX_CUSTOM_QUERY = "LuminexProgressReport";
    private static final String REPORT_NAME = "Basic Progress Report";
    private static final String REPORT_NAME_TRICKY_CHARS = "Progress Report" + TRICKY_CHARACTERS;
    private static List<String>[] _expectedLuminexResults = new List[9];
    static
    {
        _expectedLuminexResults[0] = Arrays.asList("#Progress Report for Assay: Luminex");
        _expectedLuminexResults[1] = Arrays.asList("#");
        _expectedLuminexResults[2] = Arrays.asList("ParticipantId", "Baseline", "Month 1", "Month 2", "Month 3", "Month 4", "Month 5", "Month 6", "Month 7");
        _expectedLuminexResults[3] = Arrays.asList("249318596", "expected", "expected", "collected", "expected", "unusable", "expected", "expected", "expected");
        _expectedLuminexResults[4] = Arrays.asList("249320107", "expected", "expected", "unexpected", "expected", "expected", "expected", "expected", "expected");
        _expectedLuminexResults[5] = Arrays.asList("249320127", "expected", "expected", "expected", "expected", "expected", "expected", "expected", "expected");
        _expectedLuminexResults[6] = Arrays.asList("249320489", "expected", "expected", "not-collected", "expected", "expected", "expected", "expected", "expected");
        _expectedLuminexResults[7] = Arrays.asList("249320897", "expected", "expected", "not-received", "expected", "expected", "expected", "expected", "expected");
        _expectedLuminexResults[8] = Arrays.asList("249325717", "expected", "not-available", "expected", "expected", "expected", "expected", "expected", "expected");

    }

    @Override
    protected String getProjectName()
    {
        return "Assay Progress Report Test";
    }

    @Override
    protected String getFolderName()
    {
        return "Demo Study";
    }

    @Override
    protected void doCreateSteps()
    {
        _containerHelper.createProject(getProjectName(), null);
        _containerHelper.createSubfolder(getProjectName(), getProjectName(), getFolderName(), "Study", null);
        importStudyFromZip(STUDY_ZIP);

        // setup the assay schedule
        clickTab("Overview");
        PortalHelper portalHelper = new PortalHelper(getDriver());
        portalHelper.addWebPart("Assay Schedule");

        AssayScheduleWebpart assayScheduleWebpart = new AssayScheduleWebpart(getDriver());
        assertTrue("Unexpected rows in the assay schedule table", assayScheduleWebpart.isEmpty());
        assayScheduleWebpart.manage();

        // show all of the existing visit columns
        ManageAssaySchedulePage assaySchedulePage = new ManageAssaySchedulePage(this, true);

        for (BaseManageVaccineDesignVisitPage.Visit visit : VISITS)
        {
            assaySchedulePage.addExistingVisitColumn(visit.getLabel(), false);
            visit.setRowId(BaseManageVaccineDesignVisitPage.queryVisitRowId(this, getProjectName() + '/' + getFolderName(), visit));
        }

        // add the first assay and define the properties for it
        assaySchedulePage.addNewAssayRow(ASSAYS[0], null, 0);
        assaySchedulePage.selectVisits(VISITS, 0);

        // add the second assay and define the properties for it
        assaySchedulePage.addNewAssayRow(ASSAYS[1], null, 1);
        assaySchedulePage.selectVisits(VISITS, 1);

        // add the third assay and define the properties for it
        assaySchedulePage.addNewAssayRow(ASSAYS[2], null, 2);
        assaySchedulePage.selectVisits(VISITS, 2);

        assaySchedulePage.save();
    }

    @Override
    protected void doVerifySteps() throws Exception
    {
        progressReportBasicTest();
        progressReportExportTest();
    }

    private void progressReportBasicTest()
    {
        log("creating a custom query to attach to the luminex progress report");
        createCustomQuery("study", "LuminexAssay", LUMINEX_CUSTOM_QUERY, LUMINEX_PROGRESS_REPORT);
        createProgressReport(REPORT_NAME);
        verifyProgressReport(false);

        log("attach a dataset to luminex so we can check available statuses");
        clickTab("Overview");
        AssayScheduleWebpart assayScheduleWebpart = new AssayScheduleWebpart(getDriver());
        assayScheduleWebpart.manage();
        ManageAssaySchedulePage assaySchedulePage = new ManageAssaySchedulePage(this, true);
        assaySchedulePage.setBaseProperties(null, null, null, null, "ELISpotAssay", 0);
        assaySchedulePage.setBaseProperties(null, null, null, null, "LuminexAssay", 1);
        assaySchedulePage.save();

        clickReportGridLink(REPORT_NAME, true);
        verifyProgressReport(true);
    }

    private void progressReportExportTest()
    {
        try
        {
            log("create a progress report with tricky characters");
            createProgressReport(REPORT_NAME_TRICKY_CHARS);
            verifyProgressReport(true);

            log("verify export is enabled only for individual progress reports");
            _ext4Helper.selectComboBoxItem("Assay Progress Reports:", "All");
            assertElementPresent(Locators.disabledExportBtn);

            _ext4Helper.selectComboBoxItem("Assay Progress Reports:", "Elispot");
            assertElementPresent(Locators.enabledExportBtn);
            _ext4Helper.selectComboBoxItem("Assay Progress Reports:", "VSVG");
            assertElementPresent(Locators.enabledExportBtn);
            _ext4Helper.selectComboBoxItem("Assay Progress Reports:", "Luminex");
            assertElementPresent(Locators.enabledExportBtn);

            clickTab("Overview");
            AssayScheduleWebpart assayScheduleWebpart = new AssayScheduleWebpart(getDriver());
            assayScheduleWebpart.manage();
            ManageAssaySchedulePage assaySchedulePage = new ManageAssaySchedulePage(this, true);
            assaySchedulePage.setBaseProperties(null, null, null, null, "[none]", 1);
            assaySchedulePage.save();
            clickReportGridLink(REPORT_NAME_TRICKY_CHARS, true);
            _ext4Helper.selectComboBoxItem("Assay Progress Reports:", "Luminex");

            log("verifying the contents of the downloaded file");
            File downloadXLS = doAndWaitForDownload(() -> clickButton("Export to Excel", 0));
            Workbook workbook = ExcelHelper.create(downloadXLS);
            Sheet sheet = workbook.getSheetAt(0);
            for (int row = 0 ; row < _expectedLuminexResults.length; row++)
            {
                List<String> expectedRow = _expectedLuminexResults[row];
                List<String> rowData = ExcelHelper.getRowData(sheet, row);

                Assert.assertArrayEquals("Luminex exported data did not match expected results", expectedRow.toArray(), rowData.toArray());
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void createProgressReport(String reportName)
    {
        goToManageViews().clickAddReport("Assay Progress Report");

        ProgressReportConfigPage config = new ProgressReportConfigPage(getDriver());
        config.setReportName(reportName);
        config.setDescription("A basic progress report");

        // assay configuration
        AssayQueryConfig queryConfig = new AssayQueryConfig(getDriver(), 1);
        queryConfig.setSchemaName("study");
        queryConfig.setQueryName(LUMINEX_CUSTOM_QUERY);
        queryConfig.save();
        config.save();
    }

    private void verifyProgressReport(boolean hasDatasetConfigured)
    {
        log("verify the progress report contents");
        _ext4Helper.selectComboBoxItem("Assay Progress Reports:", "Elispot");
        if (hasDatasetConfigured)
        {
            Assert.assertEquals("Elispot progress report has the wrong number of expected statuses", 44, Locators.expected.findElements(getDriver()).size());
            Assert.assertEquals("Elispot progress report has the wrong number of expected statuses", 4, Locators.available.findElements(getDriver()).size());
        }
        else
        {
            Assert.assertEquals("Elispot progress report has the wrong number of expected statuses", 48, Locators.expected.findElements(getDriver()).size());
        }

        _ext4Helper.selectComboBoxItem("Assay Progress Reports:", "VSVG");
        Assert.assertEquals("VSVG progress report has the wrong number of expected statuses", 48, Locators.expected.findElements(getDriver()).size());

        _ext4Helper.selectComboBoxItem("Assay Progress Reports:", "Luminex");
        if (hasDatasetConfigured)
        {
            Assert.assertEquals("Luminex progress report has the wrong number of expected statuses", 24, Locators.expected.findElements(getDriver()).size());
            Assert.assertEquals("Luminex progress report has the wrong number of collected statuses", 24, Locators.available.findElements(getDriver()).size());
        }
        else
        {
            Assert.assertEquals("Luminex progress report has the wrong number of expected statuses", 42, Locators.expected.findElements(getDriver()).size());
            Assert.assertEquals("Luminex progress report has the wrong number of collected statuses", 1, Locators.collected.findElements(getDriver()).size());
            Assert.assertEquals("Luminex progress report has the wrong number of not collected statuses", 1, Locators.notCollected.findElements(getDriver()).size());
            Assert.assertEquals("Luminex progress report has the wrong number of unusable statuses", 1, Locators.unusable.findElements(getDriver()).size());
            Assert.assertEquals("Luminex progress report has the wrong number of not received statuses", 1, Locators.notReceived.findElements(getDriver()).size());
            Assert.assertEquals("Luminex progress report has the wrong number of unavailable statuses", 1, Locators.noData.findElements(getDriver()).size());
            Assert.assertEquals("Luminex progress report has the wrong number of unexpected statuses", 1, Locators.qcCheck.findElements(getDriver()).size());
        }
    }

    private void createCustomQuery(String schemaName, String queryName, String customQueryName, String sql)
    {
        goToSchemaBrowser();
        createNewQuery(schemaName);
        setFormElement(Locator.name("ff_newQueryName"), customQueryName);
        clickAndWait(Locator.lkButton("Create and Edit Source"));
        setCodeEditorValue("queryText", sql);
        clickButton("Save & Finish");
    }

    public static class Locators
    {
        public static Locator.XPathLocator progressReportBase = Locator.xpath("//table[" + Locator.NOT_HIDDEN  + " and contains(@class, 'progress-report')]");
        public static Locator.XPathLocator available = progressReportBase.append(Locator.tagWithClass("span", "fa-check-circle"));
        public static Locator.XPathLocator expected = progressReportBase.append(Locator.tagWithClass("span", "fa-circle-o"));
        public static Locator.XPathLocator collected = progressReportBase.append(Locator.tagWithClass("span", "fa-flask"));
        public static Locator.XPathLocator notCollected = progressReportBase.append(Locator.tagWithClass("span", "fa-ban"));
        public static Locator.XPathLocator qcCheck = progressReportBase.append(Locator.tagWithClass("span", "fa-flag"));
        public static Locator.XPathLocator notReceived = progressReportBase.append(Locator.tagWithClass("span", "fa-exclamation"));
        public static Locator.XPathLocator noData = progressReportBase.append(Locator.tagWithClass("span", "fa-warning"));
        public static Locator.XPathLocator unusable = progressReportBase.append(Locator.tagWithClass("span", "fa-trash-o"));

        public static Locator.XPathLocator enabledExportBtn = Locator.tagContainingText("span", "Export to Excel");
        public static Locator.XPathLocator disabledExportBtn = Locator.xpath("//span[ancestor-or-self::*[contains(@class, 'x4-btn-disabled')] and contains(text(), 'Export to Excel')]");
    }
}
