package org.labkey.test.tests.study;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyC;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.PipelineStatusTable;

import java.util.Arrays;
import java.util.List;

import java.io.File;

@Category({DailyC.class})
public class StudyReloadColumnInferenceTest extends StudyBaseTest
{
    /*List archive:
        -Languages.tsv
        -Countries.tsv
        -lists.xml
        -settings.xml
    * */
    private static final File LIST_ARCHIVE = TestFileUtils.getSampleData("studyreload/listsSetup.zip");
    /*Study archive: dataset file content match original STUDY_ARCHIVE
        -datasets
            -dataset1000.tsv
            -dataset1001.tsv
            -dataset1002.tsv
            -datasets_manifest.xml
            -datasets_metadata.xml
            -Study.dataset
        -study.xml
    * */
    private static final File STUDY_ARCHIVE = TestFileUtils.getSampleData("studyreload/datasetsSetup.zip");

    /*Study archive:
        -datasets
            -Demographics.xlsx (content same as dataset1000.tsv)
            -Lab Results.tsv (content same as dataset1001.tsv)
            -Physical Exam.xls (content same as dataset1002.tsv)
        -lists
            -Languages.xlsx (content same as Languages.tsv)
            -Countries.xlsx (content same as Countries.tsv)
        -study.xml
    * */
    private static final File initialReloadTestFile = TestFileUtils.getSampleData("studyreload/originalMixedFileTypes.zip");

    /*Study archive:
        -datasets
            -Demographics.xlsx
            -Medical History.tsv
            -Physical Exam.xls
        -lists
            -Languages.xlsx
            -Countries.tsv
            -Instruments.xls
        -study.xml
    * */
    private static final File secondReloadTestFile = TestFileUtils.getSampleData("studyreload/editedMixedFileTypes.zip");

    /*Study archive:
    -datasets
        -Demographics.xlsx (with added int, number, string boolean columns)
    -lists
        -Languages.xlsx (with added int, number, string boolean columns)
    -study.xml
    * */
    private static final File thirdReloadTestFile = TestFileUtils.getSampleData("studyreload/editedMixedFieldTypes.zip");

    private static final String LIST_LANGUAGES = "Languages";
    private static final String COL_LANGUAGEID = "Language Id";
    private static final String COL_LANGUAGENAME = "Language Name";
    private static final String COL_TRANSLATORNAME = "Translator Name";
    private static final String COL_TRANSLATORPHONE = "Translator Phone";
    private static final List<String> LANGUAGES_COLUMNS_SETUP = Arrays.asList(COL_LANGUAGEID, COL_LANGUAGENAME, COL_TRANSLATORNAME, COL_TRANSLATORPHONE);
    private static final DataToVerify LANGUAGES_FIRST_RELOAD = new DataToVerify(LIST_LANGUAGES, LANGUAGES_COLUMNS_SETUP, 5, null, null);
    private static final String COL_TRANSLATORCountry = "Country";
    private static final List<String> LANGUAGES_COLUMNS_AFTER = Arrays.asList(COL_LANGUAGEID, COL_LANGUAGENAME, COL_TRANSLATORNAME, COL_TRANSLATORCountry);
    private static final List<String> LANGUAGES_COUNTRY_VALUES_MODIFIED = Arrays.asList("USA", "Cuba", "Canada");
    private static final DataToVerify LANGUAGES_SECOND_RELOAD = new DataToVerify(LIST_LANGUAGES, LANGUAGES_COLUMNS_AFTER, 6, COL_TRANSLATORCountry, LANGUAGES_COUNTRY_VALUES_MODIFIED);

    private static final String LIST_COUNTRIES = "Countries";
    private static final String COL_COUNTRYNAME = "Country Name";
    private static final DataToVerify COUNTRIES_FIRST_RELOAD = new DataToVerify(LIST_COUNTRIES, Arrays.asList(COL_COUNTRYNAME), 5, null, null);
    private static final String COL_ACTIVE = "Active";
    private static final DataToVerify COUNTRIES_SECOND_RELOAD = new DataToVerify(LIST_COUNTRIES, Arrays.asList(COL_COUNTRYNAME, COL_ACTIVE), 5, null, null);

    private static final String LIST_INSTRUMENTS = "Instruments";
    private static final String COL_KEY = "Key";
    private static final String COL_INSTRUMENTID = "Instrument ID";
    private static final DataToVerify INSTRUMENTS = new DataToVerify(LIST_INSTRUMENTS, Arrays.asList(COL_KEY, COL_INSTRUMENTID), 6, COL_INSTRUMENTID, Arrays.asList("ABI-4700"));

    private static final String DATASET_DEMOGRAPHICS = "Demographics";
    private static final String COL_MOUSID = "mouseid";
    private static final String COL_DATE = "date";
    private static final String COL_QCSTATELABEL = "qcstatelabel";
    private static final String COL_STARTDATE = "startdate";
    private static final String COL_HEIGHT = "height";
    private static final String COL_GENDER = "gender";
    private static final String COL_COUNTRY = "country";
    private static final String COL_GROUP = "group";
    private static final String COL_STATUS = "status";
    private static final String COL_COMMENTS = "comments";
    private static final List<String> DEMOGRAPHICS_COLUMNS_BEFORE = Arrays.asList(COL_MOUSID, COL_DATE, COL_QCSTATELABEL, COL_STARTDATE, COL_HEIGHT,
            COL_GENDER, COL_COUNTRY, COL_GROUP, COL_STATUS, COL_COMMENTS);
    private static final DataToVerify DEMOGRAPHICS_FIRST_RELOAD = new DataToVerify(DATASET_DEMOGRAPHICS, DEMOGRAPHICS_COLUMNS_BEFORE, 6, null, null);
    private static final String COL_AGE = "age";

    /*Demographics re-reload: Add column, add row, modify data content (country)*/
    private static final List<String> DEMOGRAPHICS_COLUMNS_AFTER = Arrays.asList(COL_MOUSID, COL_DATE, COL_QCSTATELABEL, COL_STARTDATE, COL_HEIGHT,
            COL_GENDER, COL_COUNTRY, COL_GROUP, COL_STATUS, COL_COMMENTS, COL_AGE);
    private static final List<String> DEMOGRAPHICS_COUNTRY_VALUES_MODIFIED = Arrays.asList("USA", "Canada", "Brazil");
    private static final DataToVerify DEMOGRAPHICS_SECOND_RELOAD = new DataToVerify(DATASET_DEMOGRAPHICS, DEMOGRAPHICS_COLUMNS_AFTER, 7, COL_COUNTRY, DEMOGRAPHICS_COUNTRY_VALUES_MODIFIED);

    private static final String DATASET_LAB_RESULTS = "Lab Results";
    private static final String COL_CD4 = "cd4";
    private static final String COL_LYMPHOCYTES = "lymphocytes";
    private static final String COL_HEMOGLOBIN = "hemoglobin";
    private static final List<String> LAB_RESULTS_COLUMNS_BEFORE = Arrays.asList(COL_MOUSID, COL_DATE, COL_QCSTATELABEL, COL_CD4, COL_LYMPHOCYTES, COL_HEMOGLOBIN);
    private static final DataToVerify LAB_RESULTS_FIRST_RELOAD = new DataToVerify(DATASET_LAB_RESULTS, LAB_RESULTS_COLUMNS_BEFORE, 38, null, null);
    /*Lab Result re-reload: dataset file missing, dataset will skip reload, content and columns won't be updated*/
    private static final DataToVerify LAB_RESULTS_SECOND_RELOAD = LAB_RESULTS_FIRST_RELOAD;

    private static final String DATASET_PHYSICAL_EXAM = "Physical Exam";
    private static final String COL_WEIGHT_KG = "weight_kg";
    private static final String COL_TEMP_C = "temp_c";
    private static final String COL_SYSTOLICBLOODPRESSURE = "systolicbloodpressure";
    private static final String COL_DIASTOLICBLOODPRESSURE = "diastolicbloodpressure";
    private static final String COL_LANGUAGE = "language";
    private static final List<String> PHYSICAL_EXAM_COLUMNS_BEFORE = Arrays.asList(COL_MOUSID, COL_DATE, COL_QCSTATELABEL, COL_WEIGHT_KG, COL_TEMP_C,
            COL_SYSTOLICBLOODPRESSURE, COL_DIASTOLICBLOODPRESSURE, COL_LANGUAGE);
    private static final DataToVerify PHYSICAL_EXAM_FIRST_RELOAD = new DataToVerify(DATASET_PHYSICAL_EXAM, PHYSICAL_EXAM_COLUMNS_BEFORE, 38, null, null);
    private static final String COL_PREGNANCY = "pregnancy";
    /*Physical Exam re-reload: removed column, added new column, removed rows, added new rows, modified data content for language field*/
    private static final List<String> PHYSICAL_EXAM_COLUMNS_AFTER = Arrays.asList(COL_MOUSID, COL_DATE, COL_QCSTATELABEL, COL_WEIGHT_KG, COL_TEMP_C,
            COL_SYSTOLICBLOODPRESSURE, COL_LANGUAGE, COL_PREGNANCY);
    private static final List<String> PHYSICAL_EXAM_LANGUAGE_VALUES_MODIFIED = Arrays.asList("English", "Spanish");
    private static final DataToVerify PHYSICAL_EXAM_SECOND_RELOAD = new DataToVerify(DATASET_PHYSICAL_EXAM, PHYSICAL_EXAM_COLUMNS_AFTER, 32, COL_LANGUAGE, PHYSICAL_EXAM_LANGUAGE_VALUES_MODIFIED);

    private static final String DATASET_MEDICAL_HISTORY = "Medical History";
    private static final String COL_LASTEXAMEDATE = "LastExamDate";
    private static final String COL_INSURANCE = "Insurance";
    private static final List<String> MEDICAL_HISTORY_COLUMNS = Arrays.asList(COL_MOUSID, COL_DATE, COL_LASTEXAMEDATE, COL_INSURANCE);
    /*Medical History re-reload: define new dataset that didn't exist*/
    private static final DataToVerify MEDICAL_HISTORY = new DataToVerify(DATASET_MEDICAL_HISTORY, MEDICAL_HISTORY_COLUMNS, 0, null, null);

    protected String getProjectName()
    {
        return "StudyReloadColumnInferenceProject";
    }

    protected String getFolderName()
    {
        return "Study Reload";
    }

    @Override
    protected void doCreateSteps()
    {
        initializeFolder();
        importStudyFromZip(STUDY_ARCHIVE);
        clickFolder(getFolderName());
        _listHelper.importListArchive(LIST_ARCHIVE);
    }

    @Override
    protected void doVerifySteps() throws Exception
    {
        clickFolder(getFolderName());
        log("Reload study with the dataset files in xlsx, tsv and xls format but have same content as original study import");
        reloadStudyFromZip(initialReloadTestFile, false ,2);
        new PipelineStatusTable(this).clickStatusLink(0);

        log("Verify datasets after initial study reload without any column or content change");
        verifyDataset(true, DEMOGRAPHICS_FIRST_RELOAD, LAB_RESULTS_FIRST_RELOAD, PHYSICAL_EXAM_FIRST_RELOAD);
        log("Verify existing list after study reload with excel list file");
        verifyDataset(false, LANGUAGES_FIRST_RELOAD, COUNTRIES_FIRST_RELOAD);

        clickFolder(getFolderName());
        log("Reload study with modifed datasets and lists columns and data content");
        reloadStudyFromZip(secondReloadTestFile, false, 3);
        new PipelineStatusTable(this).clickStatusLink(0);
        log("Verify datasets after second study reload with added/removed column, added/movoed/modified rows");
        verifyDataset(true, DEMOGRAPHICS_SECOND_RELOAD, PHYSICAL_EXAM_SECOND_RELOAD);
        log("Verify datasets after second study reload with absent dataset file should not remove the dataset");
        verifyDataset(true, LAB_RESULTS_SECOND_RELOAD);
        log("Verify that after second study reload with new dataset file creates the new dataset");
        // this use case is not a requirement and may be revamped in a future sprint
        verifyDataset(true, MEDICAL_HISTORY);
        log("Verify lists after study reload with added/removed column, added/movoed/modified rows");
        verifyDataset(false, LANGUAGES_SECOND_RELOAD, COUNTRIES_SECOND_RELOAD);

        // TODO: use case not yet supported as of Dec 2017, uncomment after supporting list creation from excel/tsv without lists.xml file
//        log("Verify that after second study reload with new list file creates the new list");
//        verifyDataset(true, INSTRUMENTS);

        clickFolder(getFolderName());
        log("Reload study again with added datasets and lists columns of various field types");
        reloadStudyFromZip(thirdReloadTestFile, false, 4);
        new PipelineStatusTable(this).clickStatusLink(0);

        log("Verify correct types are inferred from file for list");
        gotoList(LIST_LANGUAGES);
        clickButton("Design");
        verifyColumnTypes(true);

        log("Verify correct types are inferred from file for dataset");
        gotoDataset(DATASET_DEMOGRAPHICS);
        clickButton("Manage");
        verifyColumnTypes(false);
    }

    private void verifyColumnTypes(boolean isList)
    {
        waitForElement(getLoc("booleancol", isList ? "Boolean" : "True/False (Boolean)", isList), WAIT_FOR_JAVASCRIPT);
        assertElementPresent(getLoc("intcol", "Integer", isList));
        assertElementPresent(getLoc("numcol", "Number (Double)", isList));
        assertElementPresent(getLoc("stringcol", "Text (String)", isList));
    }

    private Locator.XPathLocator getLoc(String fieldName, String fieldType, boolean isList)
    {
        String divPath = (isList ? "/div" : "");
        String path = "//tr[./td" + divPath + "[text()='" + fieldName + "'] and ./td" + divPath + "[text()='" + fieldType + "']]";
        return Locator.xpath(path);
    }

    private DataRegionTable gotoList(String listName)
    {
        goToProjectHome();
        clickFolder(getFolderName());
        goToManageLists();
        clickAndWait(Locator.linkWithText(listName));

        return new DataRegionTable("query", getDriver());
    }

    private DataRegionTable gotoDataset(String datasetName)
    {
        goToProjectHome();
        clickFolder(getFolderName());
        clickTab("Clinical and Assay Data");
        waitForElement(Locator.linkContainingText(datasetName));
        clickAndWait(Locator.linkContainingText(datasetName));

        return new DataRegionTable("Dataset", getDriver());
    }


    private void verifyDataset(boolean isDataset, DataToVerify... datas)
    {
        for (DataToVerify data : datas)
        {
            String name = data.getName();
            log("Verify " + (isDataset ? "dataset " : "list ") + name);

            DataRegionTable datasetTable = isDataset ? gotoDataset(name) : gotoList(name);
            List<String> columnLabels = datasetTable.getColumnLabels();
            Assert.assertTrue(name + " columns are not as expected", columnLabels.size() == data.getColumns().size());
            for (String expectedColumn : data.getColumns())
            {
                Assert.assertTrue(name + " doesn't contain column " + expectedColumn, columnLabels.contains(expectedColumn));
            }

            Assert.assertTrue(name + " row count is not as expected: " + datasetTable.getDataRowCount(), data.getRowCount() == datasetTable.getDataRowCount());

            String columnToVerify = data.getColumnNameToVerify();
            if (!StringUtils.isEmpty(columnToVerify) && data.getColumnValuesToVerify() != null)
            {
                List<String> expectedColumnValues = data.getColumnValuesToVerify();
                List<String> actualColumnValues = datasetTable.getColumnDataAsText(columnToVerify);
                for (String expectedValue : expectedColumnValues)
                {
                    Assert.assertTrue(columnToVerify + " doesn't contain value " + expectedValue, actualColumnValues.contains(expectedValue));
                }
            }
        }
    }

    public static class DataToVerify
    {
        private String name;
        private List<String> columns;
        private int rowCount;
        private String columnNameToVerify;
        private List<String> columnValuesToVerify;

        public DataToVerify(String name, List<String> columns, int rowCount, String columnNameToVerify, List<String> columnValuesToVerify)
        {
            this.name = name;
            this.columns = columns;
            this.rowCount = rowCount;
            this.columnNameToVerify = columnNameToVerify;
            this.columnValuesToVerify = columnValuesToVerify;
        }

        public String getName()
        {
            return name;
        }

        public List<String> getColumns()
        {
            return columns;
        }

        public int getRowCount()
        {
            return rowCount;
        }

        public String getColumnNameToVerify()
        {
            return columnNameToVerify;
        }

        public List<String> getColumnValuesToVerify()
        {
            return columnValuesToVerify;
        }

    }
}
