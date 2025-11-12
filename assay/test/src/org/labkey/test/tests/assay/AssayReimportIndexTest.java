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
    private static final FieldInfo BATCH_FIELD = DomainKind.Assay.randomField("batchData", ColumnType.String);
    private static final FieldInfo BATCH_FILE_FIELD = DomainKind.Assay.randomField("batchFile", ColumnType.File);
    private static final FieldInfo RUN_FIELD = DomainKind.Assay.randomField("runString", ColumnType.String);
    private static final FieldInfo RUN_FILE_FIELD = DomainKind.Assay.randomField("runFile", ColumnType.File);

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

        List<PropertyDescriptor> batchFields = List.of(BATCH_FIELD.getFieldDefinition(), BATCH_FILE_FIELD.getFieldDefinition());
        List<PropertyDescriptor> runFields = List.of(RUN_FIELD.getFieldDefinition(), RUN_FILE_FIELD.getFieldDefinition());

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

    @Test // Issue 54112, Issue 54218
    public void testFileFieldValuesRetainedRunReimport()
    {
        String runName = TestDataGenerator.randomString(TestDataGenerator.randomInt(10, 50));
        String batchFieldValue = TestDataGenerator.randomString(TestDataGenerator.randomInt(10, 50));
        String runFieldValue = TestDataGenerator.randomString(TestDataGenerator.randomInt(10, 50));
        File batchFile = TestFileUtils.getSampleData("dataLoading/excel/fruits.tsv");
        File runFile = TestFileUtils.getSampleData("dataLoading/excel/ClientAPITestList.xls");
        File dataFile = TestFileUtils.getSampleData("assay/GPAT_Run1.tsv");

        // import the initial run
        importNewRun()
            .setNamedInputText(BATCH_FIELD.getName(), batchFieldValue)
            .setFileField(BATCH_FILE_FIELD.getName(), batchFile)
            .clickNext()
            .setNamedInputText("Name", runName)
            .setNamedInputText(RUN_FIELD.getName(), runFieldValue)
            .setFileField(RUN_FILE_FIELD.getName(), runFile)
            .setDataFile(dataFile)
            .clickSaveAndFinish();

        // Verify batch values during reimport
        var importPage = reimportRun(runName);
        String actualBatchValue = importPage.getFieldValue(BATCH_FIELD.getName());
        checker().withScreenshot("reimport-batch-field-value")
                .verifyEquals("Unexpected batch field value", batchFieldValue, actualBatchValue);
        actualBatchValue = importPage.getFileFieldValue(BATCH_FILE_FIELD.getName());
        checker().withScreenshot("reimport-batch-file-field-value")
                .verifyEquals("Unexpected batch file name", batchFile.getName(), actualBatchValue);

        // Verify run values during reimport
        importPage = importPage.clickNext();
        String actualRunValue = importPage.getFieldValue(RUN_FIELD.getName());
        checker().withScreenshot("reimport-batch-field-value")
                .verifyEquals("Unexpected run field value", runFieldValue, actualRunValue);
        actualRunValue = importPage.getFileFieldValue(RUN_FILE_FIELD.getName());
        checker().withScreenshot("reimport-run-file-field-value")
                .verifyEquals("Unexpected run file name", runFile.getName(), actualRunValue);
        importPage.selectUploadFileRadioButton()
            .clickSaveAndFinish();

        // Verify that the reimport retained the values
        var row = new AssayRunsPage(getDriver())
            .getTable()
            .getRowDataAsMap("Name", runName);
        actualBatchValue = StringUtils.trimToNull(row.get("Batch/" + BATCH_FIELD.getFieldKey()));
        actualRunValue = StringUtils.trimToNull(row.get(RUN_FIELD.getFieldKey().toString()));
        checker().verifyEquals("Unexpected batch field value in grid", batchFieldValue, actualBatchValue);
        checker().verifyEquals("Unexpected run field value in grid", runFieldValue, actualRunValue);

        actualBatchValue = StringUtils.trimToNull(row.get("Batch/" + BATCH_FILE_FIELD.getFieldKey()));
        actualRunValue = StringUtils.trimToNull(row.get(RUN_FILE_FIELD.getFieldKey().toString()));
        checker().verifyEquals("Unexpected batch file in grid", batchFile.getName(), actualBatchValue);
        checker().verifyEquals("Unexpected run file in grid", runFile.getName(), actualRunValue);

        // Verify deleting batch file value is respected
        reimportRun(runName)
            .removeFileValue(BATCH_FILE_FIELD.getName())
            .clickNext()
            .selectUploadFileRadioButton()
            .clickSaveAndFinish();

        row = new AssayRunsPage(getDriver())
            .getTable()
            .getRowDataAsMap("Name", runName);
        actualBatchValue = StringUtils.trimToNull(row.get("Batch/" + BATCH_FILE_FIELD.getFieldKey()));
        actualRunValue = StringUtils.trimToNull(row.get(RUN_FILE_FIELD.getFieldKey().toString()));
        checker().verifyNull("Unexpected batch field value in grid", actualBatchValue);
        checker().verifyEquals("Unexpected run field value in grid", runFile.getName(), actualRunValue);

        // Verify other values remain unchanged
        actualBatchValue = StringUtils.trimToNull(row.get("Batch/" + BATCH_FIELD.getFieldKey()));
        actualRunValue = StringUtils.trimToNull(row.get(RUN_FIELD.getFieldKey().toString()));
        checker().verifyEquals("Unexpected batch field value in grid", batchFieldValue, actualBatchValue);
        checker().verifyEquals("Unexpected run field value in grid", runFieldValue, actualRunValue);

        // Verify deleting run file value is respected
        batchFile = TestFileUtils.getSampleData("dataLoading/excel/fruits.xls");
        runFieldValue = TestDataGenerator.randomString(TestDataGenerator.randomInt(10, 50));
        reimportRun(runName)
            .setFileField(BATCH_FILE_FIELD.getName(), batchFile)
            .clickNext()
            .removeFileValue(RUN_FILE_FIELD.getName())
            .setNamedInputText(RUN_FIELD.getName(), runFieldValue)
            .selectUploadFileRadioButton()
            .clickSaveAndFinish();

        row = new AssayRunsPage(getDriver())
            .getTable()
            .getRowDataAsMap("Name", runName);
        actualBatchValue = StringUtils.trimToNull(row.get("Batch/" + BATCH_FIELD.getFieldKey()));
        actualRunValue = StringUtils.trimToNull(row.get(RUN_FIELD.getFieldKey().toString()));
        checker().verifyEquals("Unexpected batch field value in grid", batchFieldValue, actualBatchValue);
        checker().verifyEquals("Unexpected run field value in grid", runFieldValue, actualRunValue);

        actualBatchValue = StringUtils.trimToNull(row.get("Batch/" + BATCH_FILE_FIELD.getFieldKey()));
        actualRunValue = StringUtils.trimToNull(row.get(RUN_FILE_FIELD.getFieldKey().toString()));
        checker().verifyEquals("Unexpected batch file in grid", batchFile.getName(), actualBatchValue);
        checker().verifyNull("Unexpected run file in grid", actualRunValue);
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
