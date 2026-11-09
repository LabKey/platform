/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.test.tests.assay;

import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.domain.PropertyDescriptor;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.Assays;
import org.labkey.test.categories.Daily;
import org.labkey.test.pages.assay.AssayImportPage;
import org.labkey.test.pages.assay.AssayRunsPage;
import org.labkey.test.params.FieldDefinition.ColumnType;
import org.labkey.test.params.FieldInfo;
import org.labkey.test.params.assay.GeneralAssayDesign;
import org.labkey.test.util.DomainUtils.DomainKind;
import org.labkey.test.util.TestDataGenerator;
import org.labkey.test.util.search.SearchAdminAPIHelper;

import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Category({Daily.class, Assays.class})
public class AssayReimportIndexTest extends BaseWebDriverTest
{
    private static final String ASSAY_NAME = DomainKind.Assay.randomName("test+assay");
    // TODO: Replace each of the following with DomainKind.Assay.randomField(<fieldName>, ColumnType.File) once Issue 54218 is fixed.
    private static final FieldInfo BATCH_FILE_FIELD = new FieldInfo("batchFile", ColumnType.File);
    private static final FieldInfo RUN_FILE_FIELD = new FieldInfo("runFile", ColumnType.File);

    @BeforeClass
    public static void setupProject() throws Exception
    {
        AssayReimportIndexTest init = getCurrentTest();
        init.doSetup();
    }

    private void doSetup() throws Exception
    {
        _containerHelper.createProject(getProjectName(), "Assay");
        goToProjectHome();

        List<PropertyDescriptor> batchFields = List.of(
            DomainKind.Assay.randomField("batchData", ColumnType.String).getFieldDefinition(),
            BATCH_FILE_FIELD.getFieldDefinition()
        );

        List<PropertyDescriptor> runFields = List.of(
            DomainKind.Assay.randomField("runString", ColumnType.String).getFieldDefinition(),
            RUN_FILE_FIELD.getFieldDefinition()
        );

        new GeneralAssayDesign(ASSAY_NAME)
                .setBatchFields(batchFields, true)
                .setRunFields(runFields, true)
                .createAssay(getProjectName(), createDefaultConnection());
    }

    @Test
    public void testIndexLatestAssayRun()
    {
        String firstRun = "versionOne";
        String secondRun = "versionTwo";

        String firstRunData = """
                SpecimenID	ParticipantID	VisitID	Date	txt
                1	1	1	11/11/2024	argh
                2	2	1	11/12/2024	whee
                """;
        String secondRunData = """
                SpecimenID	ParticipantID	VisitID	Date	txt
                1	1	1	11/11/2024	woo
                2	2	1	11/12/2024	whee
                """;

        // import a run
        importNewRun()
            .clickNext()
            .setNamedInputText("Name", firstRun)
            .setDataText(firstRunData)
            .clickSaveAndFinish();
        SearchAdminAPIHelper.waitForIndexer();

        // verify it can be searched
        var searchResultPage1 = navBar().search(firstRun);
        checker().withScreenshot("first_run_not_found_after_import").awaiting(Duration.ofSeconds(2),
                ()-> Assertions.assertThat(searchResultPage1.hasResultLocatedBy(Locator.linkWithText("Assay Run - " + firstRun)))
                        .as("expect to find assay run")
                        .isTrue());

        // re-import the run with a different name and data
        reimportRun(firstRun)
            .clickNext()
            .setNamedInputText("Name", secondRun)
            .setDataText(secondRunData)
            .clickSaveAndFinish();
        SearchAdminAPIHelper.waitForIndexer();

        // verify it can be searched
        var searchResultPage2 = navBar().search(secondRun);
        checker().withScreenshot("second_run_not_found_after_import").awaiting(Duration.ofSeconds(2),
                ()-> Assertions.assertThat(searchResultPage2.hasResultLocatedBy(Locator.linkWithText("Assay Run - " + secondRun)))
                        .as("expect to find second assay run after re-import")
                        .isTrue());
        // verify the first run cannot be searched
        searchResultPage2.searchForm().searchFor(firstRun);
        checker().withScreenshot("first_run_unexpectedly_found_after_reimport").awaiting(Duration.ofSeconds(2),
                ()-> Assertions.assertThat(searchResultPage2.hasResultLocatedBy(Locator.linkWithText("Assay Run - " + firstRun)))
                        .as("expect not to find first assay run")
                        .isFalse());

        // now delete secondRun
        goToProjectHome();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        var runsPage = new AssayRunsPage(getDriver());
        runsPage.getTable().checkCheckbox(runsPage.getTable().getRowIndex("Assay ID", secondRun));
        runsPage.getTable().deleteSelectedRows();
        SearchAdminAPIHelper.waitForIndexer();

        // verify the second run cannot be searched
        var searchResultPage3 = navBar().search(firstRun);
        checker().withScreenshot("first_run_not_found_after_de-indexing_second_run")
                .verifyTrue("expect to find first assay run",
                        searchResultPage3.hasResultLocatedBy(Locator.linkWithText("Assay Run - " + firstRun)));
        // verify the first run cannot be searched post-delete
        searchResultPage3.searchForm().searchFor(secondRun);
        checker().withScreenshot("second_run_found_after_delete")
                .verifyFalse("expect not to find second assay run after deletion",
                        searchResultPage3.hasResultLocatedBy(Locator.linkWithText("Assay Run - " + secondRun)));
    }

    @Test // Issue 54112
    public void testFileFieldValuesRetainedRunReimport()
    {
        String runName = TestDataGenerator.randomString(TestDataGenerator.randomInt(10, 50));
        File batchFile = TestFileUtils.getSampleData("dataLoading/excel/fruits.tsv");
        File runFile = TestFileUtils.getSampleData("dataLoading/excel/ClientAPITestList.xls");
        File dataFile = TestFileUtils.getSampleData("assay/GPAT_Run1.tsv");

        // import the initial run
        importNewRun()
            .setFileField(BATCH_FILE_FIELD.getName(), batchFile)
            .clickNext()
            .setNamedInputText("Name", runName)
            .setFileField(RUN_FILE_FIELD.getName(), runFile)
            .setDataFile(dataFile)
            .clickSaveAndFinish();

        // Reimport the run and verify expected file field values
        var importPage = reimportRun(runName);
        var reimportBatchFileValue = importPage.getFileFieldValue(BATCH_FILE_FIELD.getName());
        checker().withScreenshot("reimport-run-batch-field-value")
                .verifyEquals("Expected batch file name", batchFile.getName(), reimportBatchFileValue);
        importPage = importPage.clickNext();
        var reimportRunFileValue = importPage.getFileFieldValue(RUN_FILE_FIELD.getName());
        checker().withScreenshot("reimport-run-run-field-value")
                .verifyEquals("Expected run file name", runFile.getName(), reimportRunFileValue);
        importPage.selectUploadFileRadioButton()
            .clickSaveAndFinish();

        // Verify that the reimport retained the file values
        var row = new AssayRunsPage(getDriver())
            .getTable()
            .getRowDataAsMap("Name", runName);
        reimportBatchFileValue = StringUtils.trimToNull(row.get("Batch/" + BATCH_FILE_FIELD.getName()));
        reimportRunFileValue = StringUtils.trimToNull(row.get(RUN_FILE_FIELD.getName()));

        checker().verifyEquals("Expected batch file to appear in data", batchFile.getName(), reimportBatchFileValue);
        checker().verifyEquals("Expected run file to appear in data", runFile.getName(), reimportRunFileValue);
    }

    private AssayImportPage importNewRun()
    {
        goToProjectHome();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        clickButton("Import Data");

        return new AssayImportPage(getDriver());
    }

    private AssayImportPage reimportRun(String runName)
    {
        goToProjectHome();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        clickAndWait(Locator.linkWithText(runName));
        clickButton("Re-import run");

        return new AssayImportPage(getDriver());
    }

    @Override
    protected String getProjectName()
    {
        return "AssayReimportIndexTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Collections.emptyList();
    }
}
