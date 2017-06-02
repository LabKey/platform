package org.labkey.test.tests.study;

import org.junit.Assert;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.pages.DatasetInsertPage;
import org.labkey.test.pages.DatasetPropertiesPage;
import org.labkey.test.pages.EditDatasetDefinitionPage;
import org.labkey.test.pages.ImportDataPage;
import org.labkey.test.pages.ViewDatasetDataPage;
import org.labkey.test.pages.core.admin.LookAndFeelSettingsPage;
import org.labkey.test.util.DataRegionTable;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by RyanS on 5/17/2017.
 */
public abstract class AbstractStudyTimeKeyFieldTest extends StudyTest
{
    protected static final File CONTINUOUS_ARCHIVE = TestFileUtils.getSampleData("study/StudyContinuous.folder.zip");
    protected static final File DATEBASED_ARCHIVE = TestFileUtils.getSampleData("study/StudyDateBasedTest.folder.zip");

    //TODO: Uncomment and fix after sprint 17.2.3 has branched
    //protected static final File DUPLICATE_DATASET = TestFileUtils.getSampleData("study/commondata/APX-ExactDuplicateRow.tsv");

    protected static final File DIFFERENT_TIME = TestFileUtils.getSampleData("study/commondata/APX-DiffersInTime.tsv");
    protected static final File DIFFERENT_DATES_DIFFERENT_TIMES = TestFileUtils.getSampleData("study/commondata/RCB-1.tsv");
    protected static final File HAS_TIMESTAMP = TestFileUtils.getSampleData("study/commondata/RCB-1.tsv");
    protected static final File RCE_APPEND = TestFileUtils.getSampleData("study/commondata/RCE-1-2.tsv");
    protected static final String DEMOGRAPHICS_DATASET = "DEM-1: Demographics";
    protected static final String DATE_REGEX = "\\d{4}-\\d{2}\\d{2}";
    protected static final String DATETIME_REGEX = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}";
    protected static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    protected static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd HH:mm";

    @Override
    protected String getDemographicsDescription()
    {
        return "Contains up to one row of DEM-1: Demographics data for each mouse.";
    }

    protected void testCannotInsertExactDuplicateNoTimeKey(Map<String,String> kvp, String dataset,String folder)
    {
        ViewDatasetDataPage dataPage = goToDataset(folder,dataset);
        DatasetPropertiesPage propertiesPage = dataPage.clickManageDataset();
        EditDatasetDefinitionPage editDatasetDefinitionPage = propertiesPage.clickEditDefinition();
        editDatasetDefinitionPage.setAdditionalKeyColumnType(EditDatasetDefinitionPage.LookupAdditionalKeyColType.NONE);
        propertiesPage = editDatasetDefinitionPage.save();
        dataPage = propertiesPage.clickViewData();
        DatasetInsertPage insertPage = dataPage.insertDatasetRow();
        insertPage.insert(kvp,false,"Duplicates were found in the database or imported data");
    }

    protected void testCannotInsertDifferingOnlyTimeNoTimeKey(Map<String,String> kvp, String dataset,String folder)
    {
        ViewDatasetDataPage dataPage = goToDataset(folder,dataset);
        DatasetPropertiesPage propertiesPage = dataPage.clickManageDataset();
        EditDatasetDefinitionPage editDatasetDefinitionPage = propertiesPage.clickEditDefinition();
        editDatasetDefinitionPage.setAdditionalKeyColumnType(EditDatasetDefinitionPage.LookupAdditionalKeyColType.NONE);
        editDatasetDefinitionPage = editDatasetDefinitionPage.saveExpectFail("Changing the dataset key would result in duplicate keys");
        dataPage = propertiesPage.clickViewData();
        DatasetInsertPage insertPage = dataPage.insertDatasetRow();
        insertPage.insert(kvp,false,"Duplicates were found in the database or imported data");
    }

    protected void testCanInsertWithOnlyTimeDifferentIfTimeKey(Map<String,String> kvp, String dataset,String folder)
    {
        ViewDatasetDataPage dataPage = goToDataset(folder,dataset);
        DatasetPropertiesPage propertiesPage = dataPage.clickManageDataset();
        EditDatasetDefinitionPage editDatasetDefinitionPage = propertiesPage.clickEditDefinition();
        editDatasetDefinitionPage.setAdditionalKeyColumnType(EditDatasetDefinitionPage.LookupAdditionalKeyColType.DATAFIELD);
        editDatasetDefinitionPage.setAdditionalKeyColDataField("Time (from Date/Time)");
        propertiesPage = editDatasetDefinitionPage.save();
        dataPage = propertiesPage.clickViewData();
        DatasetInsertPage insertPage = dataPage.insertDatasetRow();
        insertPage.insert(kvp,true,"Duplicates were found in the database or imported data");
    }

    protected void testCannotUploadDuplicateIfTimeKey(File toUpload, String dataset,String folder)
    {
        ViewDatasetDataPage dataPage = goToDataset(folder,dataset);
        DatasetPropertiesPage propertiesPage = dataPage.clickManageDataset();
        EditDatasetDefinitionPage editDatasetDefinitionPage = propertiesPage.clickEditDefinition();
        editDatasetDefinitionPage.setAdditionalKeyColumnType(EditDatasetDefinitionPage.LookupAdditionalKeyColType.DATAFIELD);
        editDatasetDefinitionPage.setAdditionalKeyColDataField("Time (from Date/Time)");
        propertiesPage = editDatasetDefinitionPage.save();
        dataPage = propertiesPage.clickViewData();
        ImportDataPage importPage = dataPage.importBulkData();
        importPage.uploadData(toUpload.getAbsolutePath(),false,"Duplicates were found in the database or imported data");
    }

    protected void testCanUploadWithOnlyTimeDifferentIfTimeKey(File toUpload, String dataset, String folder)
    {
        ViewDatasetDataPage dataPage = goToDataset(folder, dataset);
        DatasetPropertiesPage propertiesPage = dataPage.clickManageDataset();
        EditDatasetDefinitionPage editDatasetDefinitionPage = propertiesPage.clickEditDefinition();
        editDatasetDefinitionPage.setAdditionalKeyColumnType(EditDatasetDefinitionPage.LookupAdditionalKeyColType.DATAFIELD);
        editDatasetDefinitionPage.setAdditionalKeyColDataField("Time (from Date/Time)");
        propertiesPage = editDatasetDefinitionPage.save();
        dataPage = propertiesPage.clickViewData();
        ImportDataPage importPage = dataPage.importBulkData();
        importPage.uploadData(toUpload.getAbsolutePath(),true,"Duplicates were found in the database or imported data");
    }

    protected void testCanChangeExtraKeyFromTimeIfDoesNotViolateUnique(String folder, String dataset, File toUpload)
    {
        ViewDatasetDataPage dataPage = goToDataset(folder,dataset);
        DatasetPropertiesPage propertiesPage = dataPage.clickManageDataset();
        EditDatasetDefinitionPage editDatasetDefinitionPage = propertiesPage.clickEditDefinition();
        editDatasetDefinitionPage.setAdditionalKeyColumnType(EditDatasetDefinitionPage.LookupAdditionalKeyColType.DATAFIELD);
        editDatasetDefinitionPage.setAdditionalKeyColDataField("Time (from Date/Time)");
        propertiesPage = editDatasetDefinitionPage.save();
        dataPage = propertiesPage.clickViewData();
        ImportDataPage importPage = dataPage.importBulkData();
        importPage.uploadData(toUpload.getAbsolutePath(),true,"Duplicates were found in the database or imported data");
        dataPage = goToDataset(folder,dataset);
        propertiesPage = dataPage.clickManageDataset();
        editDatasetDefinitionPage = propertiesPage.clickEditDefinition();
        editDatasetDefinitionPage.setAdditionalKeyColumnType(EditDatasetDefinitionPage.LookupAdditionalKeyColType.NONE);
        
    }

    protected void testDateFieldDisplaysTimeIfTimeKey()
    {
        goToAdminConsole();
        waitAndClickAndWait(Locator.linkWithText("look and feel settings"));
        LookAndFeelSettingsPage lookAndFeelSettingsPage = new LookAndFeelSettingsPage(getDriver());
        lookAndFeelSettingsPage.setDefaultDateDisplay(DEFAULT_DATE_FORMAT);
        lookAndFeelSettingsPage.setDefaultDateTimeDisplay(DEFAULT_DATETIME_FORMAT);
        lookAndFeelSettingsPage.save();
        //should be default, just date for now
        ViewDatasetDataPage dataPage = goToDataset(getFolderName(),"AE-1:(VTN) AE Log");
        List<String> dates = dataPage.getColumnData("Date");
        dates.forEach((d) -> Assert.assertTrue("date was in wrong format", !isDate(d) && isDateTime(d)));
        DatasetPropertiesPage propertiesPage = dataPage.clickManageDataset();
        EditDatasetDefinitionPage definitionPage = propertiesPage.clickEditDefinition();
        definitionPage.setAdditionalKeyColumnType(EditDatasetDefinitionPage.LookupAdditionalKeyColType.DATAFIELD);
        definitionPage.setAdditionalKeyColDataField("Time (from Date/Time)");
        propertiesPage = definitionPage.save();
        dataPage = propertiesPage.clickViewData();
        dates.forEach((d) -> Assert.assertTrue("date was in wrong format", isDateTime(d)));
    }

    protected void testCannotTurnOffExtraTimeKeyIfViolatesUnique(String folder, String dataset)
    {
        ViewDatasetDataPage dataPage = goToDataset(folder,dataset);
        DatasetPropertiesPage propertiesPage = dataPage.clickManageDataset();
        EditDatasetDefinitionPage editDatasetDefinitionPage = propertiesPage.clickEditDefinition();
        editDatasetDefinitionPage.setAdditionalKeyColumnType(EditDatasetDefinitionPage.LookupAdditionalKeyColType.DATAFIELD);
        editDatasetDefinitionPage.setAdditionalKeyColDataField("Time (from Date/Time)");
        propertiesPage = editDatasetDefinitionPage.save();
        dataPage = propertiesPage.clickViewData();
        DatasetInsertPage insertPage = dataPage.insertDatasetRow();
        Map<String,String> kvp = new HashMap<>();
        kvp.put("MouseId","999320016");
        kvp.put("date", "10/25/05 10:11");
        insertPage.insert(kvp,true,"Duplicates were found in the database or imported data");
        dataPage = goToDataset(folder,dataset);
        propertiesPage = dataPage.clickManageDataset();
        editDatasetDefinitionPage = propertiesPage.clickEditDefinition();
        editDatasetDefinitionPage.setAdditionalKeyColumnType(EditDatasetDefinitionPage.LookupAdditionalKeyColType.NONE);
        editDatasetDefinitionPage.saveExpectFail("Changing the dataset key would result in duplicate keys");
    }

    protected void testCannotSetAdditionalKeyForDemographics()
    {
        goToDataset(getFolderName(),DEMOGRAPHICS_DATASET);
        DataRegionTable dataSet = new DataRegionTable("Dataset",getDriver());
        dataSet.clickHeaderButton("Manage");
        waitAndClickAndWait(Locator.lkButton("Edit Definition"));
        EditDatasetDefinitionPage editDatasetDefinitionPage = new EditDatasetDefinitionPage(getDriver());
        Assert.assertFalse("Additional Key None should not be enabled for a demographics dataset", editDatasetDefinitionPage.isAdditionalFieldNoneEnabled());
        Assert.assertFalse("Additional Key Data Field should not be enabled for a demographics dataset", editDatasetDefinitionPage.isAdditionalKeyDataFieldEnabled());
        Assert.assertFalse("Additional Key Managed Field should not be enabled for a demographics dataset", editDatasetDefinitionPage.isAdditionalKeyManagedEnabled());
        dismissAllAlerts();
    }

    @Override
    protected void verifyHiddenVisits(){};

    @Override
    protected void verifyVisitImportMapping(){};

    @Override
    protected void verifyCohorts(){};

    @Override
    protected void verifyStudyAndDatasets(){};

    protected ViewDatasetDataPage goToDataset(String folder, String datasetName)
    {
        goToProjectHome();
        clickFolder(folder);
        waitAndClickAndWait(getDatasetLocator(datasetName));
        return new ViewDatasetDataPage(getDriver());
    }

    protected boolean isDate(String date)
    {
        Pattern dateRegex = Pattern.compile(DATE_REGEX);
        Matcher m = dateRegex.matcher(date);
        return m.matches();
    }

    protected boolean isDateTime(String dateTime)
    {
        Pattern dateRegex = Pattern.compile(DATETIME_REGEX);
        Matcher m = dateRegex.matcher(dateTime);
        return m.matches();
    }

    protected void verifySpecimens()
    {
        verifySpecimens();
    }

    Locator getDatasetLocator(String datasetName)
    {
        return Locator.linkWithText(datasetName);
    }
}
