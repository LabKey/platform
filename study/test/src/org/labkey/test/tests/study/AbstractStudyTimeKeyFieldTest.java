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

import org.junit.Assert;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.pages.DatasetInsertPage;
import org.labkey.test.pages.DatasetPropertiesPage;
import org.labkey.test.pages.ImportDataPage;
import org.labkey.test.pages.ViewDatasetDataPage;
import org.labkey.test.pages.core.admin.BaseSettingsPage;
import org.labkey.test.pages.core.admin.LookAndFeelSettingsPage;
import org.labkey.test.pages.study.DatasetDesignerPage;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * Created by RyanS on 5/17/2017.
 */
public abstract class AbstractStudyTimeKeyFieldTest extends StudyTest
{
    protected static final File CONTINUOUS_ARCHIVE = TestFileUtils.getSampleData("study/StudyContinuous.folder.zip");
    protected static final File DATEBASED_ARCHIVE = TestFileUtils.getSampleData("study/StudyDateBasedTest.folder.zip");
    protected static final File DUPLICATE_DATASET = TestFileUtils.getSampleData("study/commondata/APX-ExactDuplicateRow.tsv");

    protected static final File DIFFERENT_TIME = TestFileUtils.getSampleData("study/commondata/APX-DiffersInTime.tsv");
    protected static final File DIFFERENT_DATES_DIFFERENT_TIMES = TestFileUtils.getSampleData("study/commondata/RCB-1.tsv");
    protected static final File HAS_TIMESTAMP = TestFileUtils.getSampleData("study/commondata/RCB-1.tsv");
    protected static final File RCE_APPEND = TestFileUtils.getSampleData("study/commondata/RCE-1-2.tsv");
    protected static final String DATE_REGEX = "\\d{4}-\\d{2}\\d{2}";
    protected static final String DATETIME_REGEX = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}";

    @Override
    protected String getDemographicsDescription()
    {
        return "Contains up to one row of DEM-1: Demographics data for each mouse.";
    }

    //Ensure a duplicate row cannot be inserted when time is not set as an additional key
    protected void testCannotInsertExactDuplicateNoTimeKey(Map<String,String> kvp, String dataset,String folder)
    {
        ViewDatasetDataPage dataPage = goToDataset(folder,dataset);
        DatasetPropertiesPage propertiesPage = dataPage.clickManageDataset();
        DatasetDesignerPage datasetDesignerPage = propertiesPage.clickEditDefinition();
        datasetDesignerPage.setDataRowUniquenessType(DatasetDesignerPage.DataRowUniquenessType.PTID_TIMEPOINT);
        propertiesPage = datasetDesignerPage.clickSave();
        dataPage = propertiesPage.clickViewData();
        DatasetInsertPage insertPage = dataPage.insertDatasetRow();
        insertPage.insertExpectingError(kvp, "Duplicates were found in the database or imported data");
    }

    //Ensure additional key column cannot be changed back to none from time if rows exist that differ only in time
    protected void testCannotInsertDifferingOnlyTimeNoTimeKey(Map<String,String> kvp, String dataset,String folder)
    {
        ViewDatasetDataPage dataPage = goToDataset(folder,dataset);
        DatasetPropertiesPage propertiesPage = dataPage.clickManageDataset();
        DatasetDesignerPage datasetDesignerPage = propertiesPage.clickEditDefinition();
        datasetDesignerPage.setDataRowUniquenessType(DatasetDesignerPage.DataRowUniquenessType.PTID_ONLY);
        datasetDesignerPage = datasetDesignerPage.saveExpectFail("This dataset currently contains more than one row of data per Mouse. Demographic data includes one row of data per Mouse.");
        datasetDesignerPage.clickCancel();

        DatasetInsertPage insertPage =goToDataset(folder, dataset)
                .clickManageDataset()
                .clickViewData()
                .insertDatasetRow();
        insertPage.insertExpectingError(kvp,"Duplicates were found in the database or imported data");
    }

    //Ensure that when time is set as an additional key it is possible to insert a row that differs only in the time portion of the timestamp
    protected void testCanInsertWithOnlyTimeDifferentIfTimeKey(Map<String,String> kvp, String dataset,String folder)
    {
        ViewDatasetDataPage dataPage = goToDataset(folder,dataset);
        DatasetPropertiesPage propertiesPage = dataPage.clickManageDataset();
        DatasetDesignerPage datasetDesignerPage = propertiesPage.clickEditDefinition();
        datasetDesignerPage.setAdditionalKeyColDataField("Time (from Date/Time)");
        propertiesPage = datasetDesignerPage.clickSave();
        dataPage = propertiesPage.clickViewData();
        DatasetInsertPage insertPage = dataPage.insertDatasetRow();
        insertPage.insert(kvp);
    }

    //Ensure inserting an exact duplicate row is disallowed when time is specified as an additional key
    protected void testCannotUploadDuplicateIfTimeKey(File toUpload, String dataset,String folder)
    {
        ViewDatasetDataPage dataPage = goToDataset(folder,dataset);
        DatasetPropertiesPage propertiesPage = dataPage.clickManageDataset();
        DatasetDesignerPage datasetDesignerPage = propertiesPage.clickEditDefinition();
        datasetDesignerPage.setAdditionalKeyColDataField("Time (from Date/Time)");
        propertiesPage = datasetDesignerPage.clickSave();
        dataPage = propertiesPage.clickViewData();
        ImportDataPage importPage = dataPage.importBulkData();
        importPage.setFile(toUpload);
        importPage.submitExpectingError("Duplicates were found in the database or imported data");
    }

    //Ensure a row differing only in the time portion of the timestamp can be inserted if time is specified as an additional key
    protected void testCanUploadWithOnlyTimeDifferentIfTimeKey(File toUpload, String dataset, String folder)
    {
        ViewDatasetDataPage dataPage = goToDataset(folder, dataset);
        DatasetPropertiesPage propertiesPage = dataPage.clickManageDataset();
        DatasetDesignerPage datasetDesignerPage = propertiesPage.clickEditDefinition();
        datasetDesignerPage.setAdditionalKeyColDataField("Time (from Date/Time)");
        propertiesPage = datasetDesignerPage.clickSave();
        dataPage = propertiesPage.clickViewData();
        ImportDataPage importPage = dataPage.importBulkData();
        importPage.setFile(toUpload);
        importPage.submit();
    }

    //Ensure that it is possible to change additional key from time if doing so would not result in a collision
    protected void testCanChangeExtraKeyFromTimeIfDoesNotViolateUnique(String folder, String dataset, File toUpload)
    {
        ViewDatasetDataPage dataPage = goToDataset(folder,dataset);
        DatasetPropertiesPage propertiesPage = dataPage.clickManageDataset();
        DatasetDesignerPage datasetDesignerPage = propertiesPage.clickEditDefinition();
        datasetDesignerPage.setAdditionalKeyColDataField("Time (from Date/Time)");
        propertiesPage = datasetDesignerPage.clickSave();
        dataPage = propertiesPage.clickViewData();
        ImportDataPage importPage = dataPage.importBulkData();
        importPage.setFile(toUpload);
        importPage.submit();

        dataPage = goToDataset(folder,dataset);
        propertiesPage = dataPage.clickManageDataset();
        datasetDesignerPage = propertiesPage.clickEditDefinition();
        datasetDesignerPage.setDataRowUniquenessType(DatasetDesignerPage.DataRowUniquenessType.PTID_TIMEPOINT);
        datasetDesignerPage.clickSave();
    }

    //Date field should display the time as well if time is specified as an additional key
    protected void testDateFieldDisplaysTimeIfTimeKey()
    {
        goToAdminConsole().clickLookAndFeelSettings();
        LookAndFeelSettingsPage lookAndFeelSettingsPage = new LookAndFeelSettingsPage(getDriver());
        lookAndFeelSettingsPage.setDefaultDateDisplay(BaseSettingsPage.DATE_FORMAT.Default);
        lookAndFeelSettingsPage.setDefaultDateTimeDisplay(BaseSettingsPage.DATE_FORMAT.Default, BaseSettingsPage.TIME_FORMAT.Default);
        lookAndFeelSettingsPage.save();
        //should be default, just date for now
        ViewDatasetDataPage dataPage = goToDataset(getFolderName(),"AE-1:(VTN) AE Log");
        List<String> dates = dataPage.getColumnData("Date");
        dates.forEach(d-> assertTrue("date was in wrong format", !isDate(d) && isDateTime(d)));
        DatasetPropertiesPage propertiesPage = dataPage.clickManageDataset();
        DatasetDesignerPage definitionPage = propertiesPage.clickEditDefinition();
        definitionPage.setAdditionalKeyColDataField("Time (from Date/Time)");
        propertiesPage = definitionPage.clickSave();
        propertiesPage.clickViewData();
        dates.forEach(d-> assertTrue("date was in wrong format", isDateTime(d)));
    }

    protected void testCannotTurnOffExtraTimeKeyIfViolatesUnique(String folder, String dataset)
    {
        ViewDatasetDataPage dataPage = goToDataset(folder,dataset);
        DatasetPropertiesPage propertiesPage = dataPage.clickManageDataset();
        DatasetDesignerPage datasetDesignerPage = propertiesPage.clickEditDefinition();
        datasetDesignerPage.setAdditionalKeyColDataField("Time (from Date/Time)");
        propertiesPage = datasetDesignerPage.clickSave();

        dataPage = propertiesPage.clickViewData();
        DatasetInsertPage insertPage = dataPage.insertDatasetRow();
        Map<String,String> kvp = new HashMap<>();
        kvp.put(SUBJECT_COL_NAME,"999320016");
        kvp.put("date", "02/01/05 10:15");
        insertPage.insert(kvp);
        dataPage = goToDataset(folder,dataset);
        propertiesPage = dataPage.clickManageDataset();
        datasetDesignerPage = propertiesPage.clickEditDefinition();
        datasetDesignerPage.setDataRowUniquenessType(DatasetDesignerPage.DataRowUniquenessType.PTID_TIMEPOINT);
        datasetDesignerPage.saveExpectFail("Changing the dataset key would result in duplicate keys for dataset " + dataset);
        datasetDesignerPage.clickCancel();
    }

    protected void testCannotSetAdditionalKeyForDemographics()
    {
        DatasetDesignerPage datasetDesignerPage =
                goToDataset(getFolderName(), DEMOGRAPHICS_TITLE)
                        .clickManageDataset()
                        .clickEditDefinition();

        Assert.assertFalse("Additional Key Data Field should not be enabled for a demographics dataset", datasetDesignerPage.isAdditionalKeyDataFieldEnabled());
        Assert.assertFalse("Additional Key Managed Field should not be enabled for a demographics dataset", datasetDesignerPage.isAdditionalKeyManagedEnabled());
        dismissAllAlerts();
    }

    @Override
    protected void verifyHiddenVisits(){}

    @Override
    protected void verifyVisitImportMapping(){}

    @Override
    protected void verifyCohorts(){}

    @Override
    protected void verifyStudyAndDatasets(){}

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

    Locator getDatasetLocator(String datasetName)
    {
        return Locator.linkWithText(datasetName);
    }
}
