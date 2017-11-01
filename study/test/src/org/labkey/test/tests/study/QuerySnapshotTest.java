/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.BVT;
import org.labkey.test.components.html.BootstrapMenu;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.ListHelper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;

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
    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected void doCreateSteps()
    {
        // create two study folders (054 and 065) and start importing a study in each
        setFolderName(FOLDER_1);
        importStudy();

        setFolderName(FOLDER_2);
        importStudy();

        waitForStudyLoad(FOLDER_1);
        waitForStudyLoad(FOLDER_2);
    }

    @Override
    protected void doVerifySteps()
    {
        doQuerySnapshotTest();
    }

    @Override
    public void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        //Check for errors before cleanup to avoid SQLException from deleting study so soon after updating it
        super.checkLeaksAndErrors();

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

    protected void doQuerySnapshotTest()
    {
        // create a snapshot from a dataset
        log("create a snapshot from a dataset");
        clickFolder(getStudyLabel());
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));
        createQuerySnapshot(DEMOGRAPHICS_SNAPSHOT, true, false);

        assertTextPresent("Dataset: " + DEMOGRAPHICS_SNAPSHOT);

        // test automatic updates by altering the source dataset
        DataRegionTable table = new DataRegionTable("Dataset", getDriver());
        log("test automatic updates by altering the source dataset");
        clickFolder(getStudyLabel());
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));
        table.clickInsertNewRow();
        setFormElement(Locator.name("quf_MouseId"), "999121212");
        setFormElement(Locator.name("quf_DEMraco"), "Armenian");

        clickButton("Submit");

        clickFolder(getStudyLabel());
        clickAndWait(Locator.linkWithText(DEMOGRAPHICS_SNAPSHOT));
        table.clickHeaderMenu("QC State", "All data");
        waitForSnapshotUpdate("Armenian");

        log("delete the snapshot");
        table.goToView("Edit Snapshot");
        deleteSnapshot();

        // snapshot over a custom view
        // test automatic updates by altering the source dataset
        log("create a snapshot over a custom view");
        navigateToFolder(getProjectName(), getStudyLabel());
        clickAndWait(Locator.linkWithText("APX-1: Abbreviated Physical Exam"));
        _customizeViewsHelper.openCustomizeViewPanel();

        _customizeViewsHelper.addColumn("DataSets/DEM-1/DEMraco", "DEM-1: Demographics Screening 4f.Other specify");
        _customizeViewsHelper.saveCustomView("APX Joined View");

        createQuerySnapshot(APX_SNAPSHOT, true, false);
        assertTextNotPresent("Slovakian");

        log("test automatic updates for a joined snapshot view");
        navigateToFolder(getProjectName(), getStudyLabel());
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));
        table.clickEditRow(table.getRowIndex("Mouse Id", "999320016"));
        setFormElement(Locator.name("quf_DEMraco"), "Slovakian");
        clickButton("Submit");

        navigateToFolder(getProjectName(), getStudyLabel());
        clickAndWait(Locator.linkWithText(APX_SNAPSHOT));
        table.clickHeaderMenu("QC State", "All data");
        waitForSnapshotUpdate("Slovakian");

        log("delete the snapshot");
        table.goToView("Edit Snapshot");
        deleteSnapshot();

        // snapshot over a custom query
        log("create a snapshot over a custom query");
        clickFolder(getStudyLabel());
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

        // create a custom query for a cross study scenario
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
        table.clickInsertNewRow();
        setFormElement(Locator.name("quf_MouseId"), "999121212");
        setFormElement(Locator.name("quf_DEMsex"), "Unknown");

        clickButton("Submit");

        clickFolder(FOLDER_2);
        clickAndWait(Locator.linkWithText(CROSS_STUDY_SNAPSHOT));
        waitForSnapshotUpdate("Unknown");
        
        clickFolder(FOLDER_2);
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));
        table.clickInsertNewRow();
        setFormElement(Locator.name("quf_MouseId"), "999151515");
        setFormElement(Locator.name("quf_DEMsexor"), "Undecided");

        clickButton("Submit");

        clickFolder(FOLDER_2);
        clickAndWait(Locator.linkWithText(CROSS_STUDY_SNAPSHOT));
        waitForSnapshotUpdate("Undecided");

        table.goToView("Edit Snapshot");
        deleteSnapshot();

        clickFolder(getStudyLabel());
        clickAndWait(Locator.linkWithText("DEM-1: Demographics"));
        createQuerySnapshot(DEMOGRAPHICS_SNAPSHOT, true, false);
        changeDatasetLabel(DEMOGRAPHICS_SNAPSHOT, "New Demographics");
        clickFolder(getStudyLabel());
        clickAndWait(Locator.linkWithText("New Demographics"));
        table.goToView("Edit Snapshot");
        changeDatasetName(DEMOGRAPHICS_SNAPSHOT, "New Dem");
        clickFolder(getStudyLabel());
        clickAndWait(Locator.linkWithText("New Demographics"));
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
            waitForElement(Locator.xpath("//input[@id='DatasetDesignerName']"), WAIT_FOR_JAVASCRIPT);

            _listHelper.addField("Dataset Fields", keyField, null, ListHelper.ListColumnType.Integer);

            click(Locator.name("ff_name0"));
            click(Locator.radioButtonById("button_managedField"));
            selectOptionByText(Locator.xpath("//select[@id='list_managedField']"), keyField);
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
                .save();
    }

    @LogMethod
    private void changeDatasetName(@LoggedParam String datasetName, @LoggedParam String newName)
    {
        _studyHelper.goToManageDatasets()
                .selectDatasetByName(datasetName)
                .clickEditDefinition()
                .setDatasetName(newName)
                .save();
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
