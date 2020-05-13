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

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.BVT;
import org.labkey.test.components.html.BootstrapMenu;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.APIAssayHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.ListHelper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

@Category({BVT.class})
public class QuerySnapshotTest extends StudyBaseTest
{
    private final String DEMOGRAPHICS_SNAPSHOT = "Demographics Snapshot";
    private final String APX_SNAPSHOT = "APX Joined Snapshot";
    private final String CROSS_STUDY_SNAPSHOT = "Cross study query snapshot";
    private static final String FOLDER_1 = "054";
    private static final String FOLDER_2 = "065";

    private String _folderName;

    private static final String CROSS_STUDY_QUERY_SQL =
            "SELECT 1 as sequenceNum, '054' as protocol, ds1.MouseId, ds1.demsex AS AliasedDemSex, ds1.demsexor\n" +
            "FROM Project.\"054\".study.\"DEM-1: Demographics\" ds1\n" +
            "UNION \n" +
            "SELECT 2 as sequenceNum, '065' as protocol, ds2.MouseId, ds2.demsex AS AliasedDemSex, ds2.demsexor\n" +
            "FROM Project.\"065\".study.\"DEM-1: Demographics\" ds2";

    private static final String CUSTOM_QUERY_SQL =
            "SELECT \"APX-1\".MouseId,\n" +
                    "\"APX-1\".SequenceNum,\n" +
                    "\"APX-1\".APXdt,\n" +
                    "\"APX-1\".APXwtkg,\n" +
                    "\"APX-1\".APXwtqc,\n" +
                    "\"APX-1\".APXtempc,\n" +
                    "\"APX-1\".APXtemqc,\n" +
                    "FROM \"APX-1\"";

    private static final String CUSTOM_QUERY_CALC_COL =
            "SELECT \n" +
                    "mouseId.mouseId,\n" +
                    "sequenceNum,\n" +
                    "(APXwtkg + APXtempc) AS combinedWeightTemp\n" +
                    "FROM \"APX-1\"";
    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected void doCreateSteps()
    {
    }

    @BeforeClass
    public static void doSetup()
    {
        QuerySnapshotTest test = (QuerySnapshotTest)getCurrentTest();

        // create two study folders (054 and 065) and start importing a study in each
        test.setFolderName(FOLDER_1);
        test.importStudy();

        test.setFolderName(FOLDER_2);
        test.importStudy();

        test.waitForStudyLoad(FOLDER_1);
        test.waitForStudyLoad(FOLDER_2);
    }

    @Override
    protected void doVerifySteps(){}

    @Override
    public void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        if (afterTest)
        {
            //Check for errors before cleanup to avoid SQLException from deleting study so soon after updating it
            checkErrors();
        }

        super.doCleanup(afterTest);
    }

    private void waitForStudyLoad(String folderName)
    {
        // Navigate
        clickFolder(folderName);
        clickAndWait(Locator.linkWithText("Data Pipeline"));
        waitForPipelineJobsToComplete(1, "study import", false);

        // enable advanced study security
        enterStudySecurity();
        doAndWaitForPageToLoad(() -> { selectOptionByValue(Locator.name("securityString"), "BASIC_WRITE"); click(Locator.lkButton("Update Type")); });
    }

    @Override
    protected String getProjectName()
    {
        return "QuerySnapshotProject";
    }

    @Override
    protected String getFolderName()
    {
        return _folderName;
    }

    private void setFolderName(String folderName)
    {
        _folderName = folderName;
    }

    @Override
    protected String getStudyLabel()
    {
        return getFolderName();
    }

    @Test
    public void testDatasetSnapshot()
    {
        // create a snapshot from a dataset
        log("create a snapshot from a dataset");
        goToProjectHome();
        clickFolder(FOLDER_1);
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));
        createQuerySnapshot(DEMOGRAPHICS_SNAPSHOT, true, false);

        assertTextPresent("Dataset: " + DEMOGRAPHICS_SNAPSHOT);

        // test automatic updates by altering the source dataset
        DataRegionTable table = new DataRegionTable("Dataset", getDriver());
        log("test automatic updates by altering the source dataset");
        clickFolder(FOLDER_1);
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));
        table.clickInsertNewRow();
        setFormElement(Locator.name("quf_MouseId"), "999121212");
        setFormElement(Locator.name("quf_DEMraco"), "Armenian");

        clickButton("Submit");

        clickFolder(FOLDER_1);
        clickAndWait(Locator.linkWithText(DEMOGRAPHICS_SNAPSHOT));
        table.clickHeaderMenu("QC State", "All data");
        waitForSnapshotUpdate("Armenian");

        log("delete the snapshot");
        table.goToView("Edit Snapshot");
        deleteSnapshot();
    }

    @Test
    public void testCustomView()
    {
        // snapshot over a custom view
        // test automatic updates by altering the source dataset
        log("create a snapshot over a custom view");
        navigateToFolder(getProjectName(), FOLDER_1);
        clickAndWait(Locator.linkWithText("APX-1: Abbreviated Physical Exam"));
        _customizeViewsHelper.openCustomizeViewPanel();

        _customizeViewsHelper.addColumn("DataSets/DEM-1/DEMraco", "DEM-1: Demographics Screening 4f.Other specify");
        _customizeViewsHelper.saveCustomView("APX Joined View");

        createQuerySnapshot(APX_SNAPSHOT, true, false);
        assertTextNotPresent("Slovakian");

        DataRegionTable table = new DataRegionTable("Dataset", getDriver());
        log("test automatic updates for a joined snapshot view");
        navigateToFolder(getProjectName(), FOLDER_1);
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));
        table.clickEditRow(table.getRowIndex("Mouse Id", "999320016"));
        setFormElement(Locator.name("quf_DEMraco"), "Slovakian");
        clickButton("Submit");

        navigateToFolder(getProjectName(), FOLDER_1);
        clickAndWait(Locator.linkWithText(APX_SNAPSHOT));
        table.clickHeaderMenu("QC State", "All data");
        waitForSnapshotUpdate("Slovakian");

        log("delete the snapshot");
        table.goToView("Edit Snapshot");
        deleteSnapshot();
    }

    // Regression coverage for Issue 38311: Creating query snapshots tied to Assay Data table generates java.lang.IllegalStateException error
    @Test
    public void testQuerySnapshotAgainstAssayDataTable() throws IOException, CommandException
    {
        goToProjectHome();
        navigateToFolder(getProjectName(), FOLDER_1);

        final String ASSAY_NAME = "Simple_GPAT";
        final File SIMPLE_ASSAY_FILE = TestFileUtils.getSampleData("/assay/" + ASSAY_NAME + ".xar.xml");
        final File ASSAY_RUN_FILE = TestFileUtils.getSampleData("/assay/GPAT_Run1.tsv");

        Map<String, Object> batchProperties = new HashMap<>();
        batchProperties.put("OperatorEmail", "john.doe@email.com");
        batchProperties.put("Instrument", "iCYT Eclipse Flow");

        log("Create a generic assay");
        APIAssayHelper assayHelper = new APIAssayHelper(this);
        assayHelper.uploadXarFileAsAssayDesign(SIMPLE_ASSAY_FILE, 2);
        assayHelper.importAssay(ASSAY_NAME, ASSAY_RUN_FILE, getCurrentContainerPath(), batchProperties);

        log("Create a query against the Assay Data table.");

        final String CROSS_STUDY_QUERY_SQL =
                "SELECT 1 as sequenceNum, \n" +
                        "Data.ParticipantID, \n" +
                        "Data.M1, \n" +
                        "Data.Date \n" +
                        "FROM assay.General." + ASSAY_NAME + ".Data";

        createQuery(getCurrentContainerPath(), "assay_query", "study", CROSS_STUDY_QUERY_SQL, null, false);
        clickButton("Save & Finish");

        log("Create a snapshot of the query.");
        createQuerySnapshot("Assay.Data Query Snapshot", true, false);

        log("Validate the data in the snapshot.");

        DataRegionTable dataRegionTable = DataRegionTable.DataRegion(getDriver()).find();

        boolean dataThere = true;

        String columnData = dataRegionTable.getDataAsText(4, "Mouse Id");
        if(!columnData.equals("249325717"))
            dataThere = false;

        columnData = dataRegionTable.getDataAsText(4, "Date");
        if(!columnData.equals("2008-04-27"))
            dataThere = false;

        columnData = dataRegionTable.getDataAsText(4, "M1");
        if(!columnData.equals("874"))
            dataThere = false;

        assertTrue("The data shown in this snapshot is not as expected.", dataThere);

    }

    @Test
    public void testCustomQuery()
    {
        // snapshot over a custom query
        log("create a snapshot over a custom query");
        goToProjectHome();
        clickFolder(FOLDER_1);
        goToManageViews();
        new BootstrapMenu(getDriver(),
                Locator.tagWithClassContaining("div", "lk-menu-drop")
                        .waitForElement(getDriver(), WAIT_FOR_JAVASCRIPT)).clickSubMenu(true, "Grid View");

        clickAndWait(Locator.linkWithText("Modify Dataset List (Advanced)"));
        createNewQuery("study");

        setFormElement(Locator.id("ff_newQueryName"), "APX: Custom Query Advanced");
        selectOptionByText(Locator.name("ff_baseTableName"), "APX-1 (APX-1: Abbreviated Physical Exam)");
        clickButton("Create and Edit Source");
        setCodeEditorValue("queryText", CROSS_STUDY_QUERY_SQL);
        clickButton("Save & Finish");

        waitForText(WAIT_FOR_PAGE, "APX: Custom Query Advanced");
        createQuerySnapshot("Custom Query Snapshot", true, true);
        assertTextPresent("Dataset: Custom Query Snapshot");

        // edit snapshot then delete
        DataRegionTable table = new DataRegionTable("Dataset", getDriver());
        log("edit the snapshot");
        table.goToView("Edit Snapshot");
        checkCheckbox(Locator.xpath("//input[@type='radio' and @name='updateType' and not (@id)]"));
        clickButton("Save");
        assertTrue(isChecked(Locator.xpath("//input[@type='radio' and @name='updateType' and not (@id)]")));
        doAndWaitForPageToLoad(() ->
        {
            clickButton("Update Snapshot", 0);
            acceptAlert();
        });
        waitForText(10000, "Dataset: Custom Query Snapshot");

        log("delete the snapshot");
        table.goToView("Edit Snapshot");
        deleteSnapshot();

        clickTab("Manage");
        waitForText(10000, "Manage Datasets");
        assertElementNotPresent(Locator.linkWithText("Custom Query Snapshot"));
    }

    @Test
    public void testCrossFolderSnapshot()
    {
        // create a custom query for a cross study scenario
        goToProjectHome();
        clickFolder(FOLDER_1);
        goToModule("Query");
        createNewQuery("study");

        setFormElement(Locator.id("ff_newQueryName"), "cross study query");
        clickButton("Create and Edit Source");
        setCodeEditorValue("queryText", CROSS_STUDY_QUERY_SQL);
        clickButton("Save & Finish");

        createQuerySnapshot(CROSS_STUDY_SNAPSHOT, true, false, "keyField", 3);

        // verify refresh from both datasets
        clickFolder(FOLDER_1);
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));
        DataRegionTable table = new DataRegionTable("Dataset", getDriver());
        table.clickInsertNewRow();
        setFormElement(Locator.name("quf_MouseId"), "999131313");
        setFormElement(Locator.name("quf_DEMsex"), "Unknown");

        clickButton("Submit");

        clickFolder(FOLDER_1);
        clickAndWait(Locator.linkWithText(CROSS_STUDY_SNAPSHOT));
        waitForSnapshotUpdate("Unknown");

        clickFolder(FOLDER_2);
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));
        table.clickInsertNewRow();
        setFormElement(Locator.name("quf_MouseId"), "999141414");
        setFormElement(Locator.name("quf_DEMsexor"), "Undecided");

        clickButton("Submit");

        clickFolder(FOLDER_1);
        clickAndWait(Locator.linkWithText(CROSS_STUDY_SNAPSHOT));
        waitForSnapshotUpdate("Undecided");

        table.goToView("Edit Snapshot");
        deleteSnapshot();

        clickFolder(FOLDER_2);
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));
        createQuerySnapshot(DEMOGRAPHICS_SNAPSHOT, true, false);
        changeDatasetLabel(DEMOGRAPHICS_SNAPSHOT, "New Demographics");
        clickFolder(FOLDER_2);
        clickAndWait(Locator.linkWithText("New Demographics"));
        table.goToView("Edit Snapshot");
        changeDatasetName(DEMOGRAPHICS_SNAPSHOT, "New Dem");
        clickFolder(FOLDER_2);
        clickAndWait(Locator.linkWithText("New Demographics"));
        table.goToView("Edit Snapshot");
        deleteSnapshot();
    }

    private static final String CALC_COL_QUERY = "CalculatedColumnQuery";
    private static final String CALC_COL_QUERY_SNAPSHOT = "CalculatedColumnQuery Snapshot";

    @Test
    public void testArbitraryCustomQuery()
    {
        // snapshot over a custom query with calculated columns : issue #31255
        log("create a snapshot over a custom query with calculated columns");
        goToProjectHome();
        clickFolder(FOLDER_1);
        goToManageViews();
        new BootstrapMenu(getDriver(),
                Locator.tagWithClassContaining("div", "lk-menu-drop")
                        .waitForElement(getDriver(), WAIT_FOR_JAVASCRIPT)).clickSubMenu(true, "Grid View");

        clickAndWait(Locator.linkWithText("Modify Dataset List (Advanced)"));
        createNewQuery("study");

        setFormElement(Locator.id("ff_newQueryName"), CALC_COL_QUERY);
        selectOptionByText(Locator.name("ff_baseTableName"), "APX-1 (APX-1: Abbreviated Physical Exam)");
        clickButton("Create and Edit Source");
        setCodeEditorValue("queryText", CUSTOM_QUERY_CALC_COL);
        clickButton("Save & Finish");

        waitForText(WAIT_FOR_PAGE, CALC_COL_QUERY);
        createQuerySnapshot(CALC_COL_QUERY_SNAPSHOT, true, false);
        assertTextPresent("Dataset: " + CALC_COL_QUERY_SNAPSHOT);

        // verify refresh
        clickFolder(FOLDER_1);
        clickAndWait(Locator.linkWithText("APX-1: Abbreviated Physical Exam"));
        DataRegionTable table = new DataRegionTable("Dataset", getDriver());
        table.clickInsertNewRow();
        setFormElement(Locator.name("quf_MouseId"), "999151515");
        setFormElement(Locator.name("quf_SequenceNum"), "1701");
        setFormElement(Locator.name("quf_APXwtkg"), "-1");
        setFormElement(Locator.name("quf_APXtempc"), "-1");

        clickButton("Submit");

        clickFolder(FOLDER_1);
        clickAndWait(Locator.linkWithText(CALC_COL_QUERY_SNAPSHOT));
        waitForSnapshotUpdate("-2");

        // delete the snapshot
        table.goToView("Edit Snapshot");
        deleteSnapshot();
    }

    private void createQuerySnapshot(String snapshotName, boolean autoUpdate, boolean isDemographic)
    {
        createQuerySnapshot(snapshotName, autoUpdate, isDemographic, null, 0);
    }

    private void createQuerySnapshot(String snapshotName, boolean autoUpdate, boolean isDemographic, String keyField, int index)
    {
        DataRegionTable table = DataRegionTable.DataRegion(getDriver()).find();
        table.goToReport("Create Query Snapshot");

        setFormElement(Locator.name("snapshotName"), snapshotName);
        if (autoUpdate)
            checkCheckbox(Locator.xpath("//input[@type='radio' and @name='updateType' and not (@id)]"));

        if (keyField != null)
        {
            clickButton("Edit Dataset Definition");
            waitForElement(Locator.input("dsName"), WAIT_FOR_JAVASCRIPT);

            _listHelper.addField("Dataset Fields", keyField, null, ListHelper.ListColumnType.Integer);

            click(Locator.name("ff_name0"));
            click(Locator.radioButtonById("button_managedField"));
            selectOptionByText(Locator.name("list_managedField"), keyField);
            clickButton("Save", WAIT_FOR_JAVASCRIPT);
        }
        clickButton("Create Snapshot");
    }

    @LogMethod
    private void changeDatasetLabel(@LoggedParam String datasetName, @LoggedParam String newLabel)
    {
        _studyHelper.goToManageDatasets()
                .selectDatasetByName(datasetName)
                .clickEditDefinition()
                .setDatasetLabel(newLabel)
                .clickSave();
    }

    @LogMethod
    private void changeDatasetName(@LoggedParam String datasetName, @LoggedParam String newName)
    {
        _studyHelper.goToManageDatasets()
                .selectDatasetByName(datasetName)
                .clickEditDefinition()
                .setName(newName)
                .clickSave();
    }

    @LogMethod
    private void waitForSnapshotUpdate(@LoggedParam String text)
    {
        int time = 0;
        while (!isTextPresent(text) && time < defaultWaitForPage)
        {
            sleep(3000);
            time += 3000;
            refresh();
        }
        assertTextPresent(text);
    }

    private void deleteSnapshot()
    {
        doAndWaitForPageToLoad(() ->
        {
            clickButton("Delete Snapshot", 0);
            acceptAlert();
        });
    }
}
