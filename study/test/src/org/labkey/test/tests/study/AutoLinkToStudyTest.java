package org.labkey.test.tests.study;

import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.Assays;
import org.labkey.test.categories.Daily;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.components.ext4.Window;
import org.labkey.test.pages.query.ExecuteQueryPage;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.PermissionsHelper;
import org.labkey.test.util.StudyHelper;
import org.openqa.selenium.WebElement;

import java.io.File;
import java.util.List;

@Category({Daily.class, Assays.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class AutoLinkToStudyTest extends BaseWebDriverTest
{
    private final static String ASSAY_NAME = "Test Assay";
    private static final String READER_USER = "reader@assaylinktostudy.test";
    private static int cnt = 0; // to keep count of rows which are already linked.
    private final String STUDY1 = getProjectName() + " Study 1";
    private final String STUDY2 = getProjectName() + " Study 2";
    private final String STUDY3 = getProjectName() + " Study 3";

    @BeforeClass
    public static void setupProject()
    {
        AutoLinkToStudyTest initTest = (AutoLinkToStudyTest) getCurrentTest();
        initTest.doSetup();
    }

    private void doSetup()
    {
        log("Creating a date based study");
        _containerHelper.createProject(getProjectName(), "Study");
        _studyHelper.startCreateStudy()
                .setTimepointType(StudyHelper.TimepointType.DATE)
                .createStudy();

        _containerHelper.createProject(STUDY1, "Study");
        _studyHelper.startCreateStudy().setTimepointType(StudyHelper.TimepointType.DATE)
                .createStudy();

        _containerHelper.createProject(STUDY2, "Study");
        _studyHelper.startCreateStudy().setTimepointType(StudyHelper.TimepointType.DATE)
                .createStudy();

        _containerHelper.createProject(STUDY3, "Study");
        _studyHelper.startCreateStudy().setTimepointType(StudyHelper.TimepointType.DATE)
                .createStudy();

        log("Creating an assay");
        goToProjectHome();
        goToManageAssays();
        _assayHelper.createAssayDesign("General", ASSAY_NAME).clickSave();

        log("Creating a reader user");
        _userHelper.createUser(READER_USER);
        ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);
        permissionsHelper.addMemberToRole(READER_USER, "Reader", PermissionsHelper.MemberType.user, STUDY1);
    }

    @Override
    protected @Nullable String getProjectName()
    {
        return "Auto Link To Study Test";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return null;
    }

    @Test
    public void testAutoLinkInSameFolder()
    {
        String runName = "Auto link in same folder";
        String categoryName = "Auto linked category";

        log("Editing the assay design for auto link");
        goToProjectHome();
        goToManageAssays();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        _assayHelper.clickEditAssayDesign()
                .setAutoLinkTarget("(Data import folder)")
                .setAutoLinkCategory(categoryName)
                .clickSave();

        File runFile = new File(TestFileUtils.getSampleData("AssayImportExport"), "GenericAssay_Run1.xls");
        importAssayRun(runFile, ASSAY_NAME, runName);

        log("Verifying data is auto imported in study");
        clickTab("Clinical and Assay Data");
        waitAndClickAndWait(Locator.linkWithText(ASSAY_NAME));
        DataRegionTable table = new DataRegionTable("Dataset", getDriver());
        table.setFilter("Run/Name", "Equals", runName);
        checker().verifyEquals("New dataset is not created in Study from Assay import", 6, table.getDataRowCount());
        checker().verifyEquals("Incorrect category for the dataset(Category exists case)", categoryName, getCategory(getProjectName(), ASSAY_NAME));
    }

    /*
        Test coverage for : Issue 42937: Assay results grid loading performance can degrade with a large number of "copied to study" columns
     */
    @Test
    public void testLinkedColumnNotDisplayedCase()
    {
        String runName = "Link to multiple study run";
        log("Creating the study projects");

        log("Editing the assay design for auto link");
        goToProjectHome();
        goToManageAssays();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        _assayHelper.clickEditAssayDesign()
                .setAutoLinkTarget("(Data import folder)")
                .clickSave();

        File runFile = new File(TestFileUtils.getSampleData("AssayImportExport"), "GenericAssay_Run2.xls");
        importAssayRun(runFile, ASSAY_NAME, runName);

        linkToStudy(runName, STUDY1, 1, null);
        linkToStudy(runName, STUDY2, 1, null);
        linkToStudy(runName, STUDY3, 1, null);

        log("Verifying linked column does not exists because more then 3 studies are linked");
        goToProjectHome();
        goToManageAssays();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        clickAndWait(Locator.linkWithText(runName));
        DataRegionTable runsTable = DataRegionTable.DataRegion(getDriver()).withName("Data").waitFor();
        checker().verifyFalse("Linked column for Study 1 should not be present",
                runsTable.getColumnNames().contains("linked_to_Auto_Link_To_Study_Test_Study_1_Study"));
        checker().verifyFalse("Linked column for Study 2 should not be present",
                runsTable.getColumnNames().contains("linked_to_Auto_Link_To_Study_Test_Study_2_Study"));
        checker().verifyFalse("Linked column for Study 3 should not be present",
                runsTable.getColumnNames().contains("linked_to_Auto_Link_To_Study_Test_Study_3_Study"));

        log("Verifying if columns can be added from customize grid");
        CustomizeView customizeView = runsTable.openCustomizeGrid();
        customizeView.addColumn("linked_to_Auto_Link_To_Study_Test_Study_1_Study");
        customizeView.addColumn("linked_to_Auto_Link_To_Study_Test_Study_2_Study");
        customizeView.addColumn("linked_to_Auto_Link_To_Study_Test_Study_3_Study");
        customizeView.addColumn("linked_to_Auto_Link_To_Study_Test_Study");
        customizeView.clickViewGrid();

        /*
            Ensuring additional 'Linked to Study' columns are not visible for linked Datasets.
            Test coverage for issue https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=43440
         */

        waitAndClickAndWait(Locator.linkWithText("linked").index(0));
        DataRegionTable datasetTable = new DataRegionTable("Dataset", getDriver());
        checker().verifyFalse("Linked column for Study 1 should not be present",
                datasetTable.getColumnNames().contains("linked_to_Auto_Link_To_Study_Test_Study_1_Study"));
    }

    @Test
    public void testPredefinedDatasetCategories()
    {
        String categoryName = "CAT1";
        String runName = "Predefined Dataset Category";
        createDatasetCategory(STUDY1, categoryName);

        goToProjectHome();
        goToManageAssays();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        _assayHelper.clickEditAssayDesign().setAutoLinkTarget("").clickSave();

        goToProjectHome();
        File runFile = new File(TestFileUtils.getSampleData("AssayImportExport"), "GenericAssay_Run3.xls");
        importAssayRun(runFile, ASSAY_NAME, runName);

        linkToStudy(runName, STUDY1, 1, categoryName); // Category exists in Study 1
        linkToStudy(runName, STUDY2, 1, "CAT2"); // New category.
        linkToStudy(runName, STUDY3, 1, null); // Uncategorized

        checker().verifyEquals("Incorrect category for the dataset(Category exists case)", categoryName, getCategory(STUDY1, ASSAY_NAME));
        checker().verifyEquals("Incorrect category for the dataset(New category case)", "CAT2", getCategory(STUDY2, ASSAY_NAME));
        checker().verifyEquals("Incorrect category for the dataset(Uncategorized case)", " ", getCategory(STUDY3, ASSAY_NAME));
    }

    @Test
    public void testOverWritingDatasetCategory()
    {
        String runName = "Over writing dataset category";
        String categoryName = "CAT1";

        log("Creating dataset category");
        createDatasetCategory(STUDY1, categoryName);

        log("Editing the assay design");
        goToProjectHome();
        goToManageAssays();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        _assayHelper.clickEditAssayDesign()
                .setAutoLinkTarget("")
                .setAutoLinkCategory("")
                .clickSave();

        goToProjectHome();
        File runFile = new File(TestFileUtils.getSampleData("AssayImportExport"), "GenericAssay_Run4.xls");
        importAssayRun(runFile, ASSAY_NAME, runName);

        linkToStudy(runName, STUDY1, 1, categoryName);

        log("Linking more rows to same study with different category name");
        linkToStudy(runName, STUDY1, 2, "CAT2");

        checker().verifyEquals("Category should not have overridden", categoryName, getCategory(STUDY1, ASSAY_NAME));
    }

    /*
        Test coverage for : https://www.labkey.org/home/Developer/issues/Secure/issues-details.view?issueId=45071
        Assay Link to Study Dataset View Permissions
     */
    @Test
    public void testReaderRoleLinkToStudy()
    {
        String runName = "Reader role testing";
        log("Editing the assay design for auto link");
        goToProjectHome();
        goToManageAssays();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        _assayHelper.clickEditAssayDesign()
                .setAutoLinkTarget("/" + STUDY1)
                .clickSave();

        goToProjectHome();
        File runFile = new File(TestFileUtils.getSampleData("AssayImportExport"), "GenericAssay_Run4.xls");
        importAssayRun(runFile, ASSAY_NAME, runName);

        goToProjectHome(STUDY1);
        clickAndWait(Locator.linkContainingText("dataset"));
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        checker().verifyTrue("View Source Assay button should be visible for admin user", isElementPresent(Locator.tagWithText("span", "View Source Assay")));

        impersonate(READER_USER);
        DataRegionTable table = DataRegionTable.DataRegion(getDriver()).withName("Dataset").waitFor();
        checker().verifyEquals("Incorrect number of row in dataset table", 6, table.getDataRowCount());
        checker().verifyFalse("View Source Assay button should not be visible for reader user", isElementPresent(Locator.tagWithText("span", "View Source Assay")));
    }

    private void linkToStudy(String runName, String targetStudy, int numOfRows, @Nullable String categoryName)
    {
        goToProjectHome();
        goToManageAssays();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        clickAndWait(Locator.linkWithText(runName));

        DataRegionTable runTable = DataRegionTable.DataRegion(getDriver()).withName("Data").waitFor();
        for (int i = cnt; i < numOfRows + cnt; i++)
        {
            runTable.checkCheckbox(i);
        }
        cnt = numOfRows;
        runTable.clickHeaderButtonAndWait("Link to Study");

        log("Link to study: Choose target");
        selectOptionByText(Locator.id("targetStudy"), "/" + targetStudy + " (" + targetStudy + " Study)");
        if (categoryName != null)
            setFormElement(Locator.name("autoLinkCategory"), categoryName);
        clickButton("Next");

        new DataRegionTable("Data", getDriver()).clickHeaderButtonAndWait("Link to Study");
    }

    private void importAssayRun(File runFile, String assayName, String runName)
    {
        log("Importing the Assay run");
        goToProjectHome();
        goToManageAssays();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        DataRegionTable runTable = new DataRegionTable("Runs", getDriver());
        runTable.clickHeaderButtonAndWait("Import Data");
        clickButton("Next");
        setFormElement(Locator.name("name"), runName);
        checkRadioButton(Locator.radioButtonById("Fileupload"));
        setFormElement(Locator.input("__primaryFile__"), runFile);
        clickButton("Save and Finish");
    }

    private void createDatasetCategory(String projectName, String name)
    {
        goToProjectHome(projectName);
        goToManageViews();
        Locator.linkWithText("Manage Categories").findElement(getDriver()).click();
        _extHelper.waitForExtDialog("Manage Categories");
        Window categoryWindow = new Window.WindowFinder(getDriver()).withTitle("Manage Categories").waitFor();
        categoryWindow.clickButton("New Category", 0);
        WebElement newCategoryField = Locator.input("label").withAttributeContaining("id", "textfield").notHidden().waitForElement(getDriver(), WAIT_FOR_JAVASCRIPT);
        setFormElementJS(newCategoryField, name);
        fireEvent(newCategoryField, SeleniumEvent.blur);
        waitForElement(Ext4Helper.Locators.window("Manage Categories").append("//div").withText(name));
        clickButton("Done", 0);
        _extHelper.waitForExtDialogToDisappear("Manage Categories");
    }

    private String getCategory(String projectName, String datasetName)
    {
        goToProjectHome(projectName);
        goToSchemaBrowser();
        ExecuteQueryPage executeQueryPage = ExecuteQueryPage.beginAt(this, "study", "DataSets");
        DataRegionTable table = executeQueryPage.getDataRegion();
        table.setFilter("Label", "Equals", datasetName);
        return table.getDataAsText(0, "categoryid");
    }

    private void deleteDatasets(String projectName)
    {
        goToProjectHome(projectName);
        _studyHelper.goToManageDatasets().clickDeleteMultipleDatasets();
        if (Locator.css("[class='table labkey-data-region-legacy'] tr").findElements(getDriver()).size() > 0)
        {
            checkCheckbox(Locator.name("datasetIds"));
            clickButton("Delete Selected");
            acceptAlert().contains("Are you sure you want to delete the selected rows?");
        }
    }

    @Before
    public void datasetCleanUp()
    {
        deleteDatasets(STUDY1);
        deleteDatasets(STUDY2);
        deleteDatasets(STUDY3);
        cnt = 0; // resetting the counter before new test
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _containerHelper.deleteProject(getProjectName(), afterTest);
        _containerHelper.deleteProject(STUDY1, afterTest);
        _containerHelper.deleteProject(STUDY2, afterTest);
        _containerHelper.deleteProject(STUDY3, afterTest);

        _userHelper.deleteUsers(false, READER_USER);
    }
}
