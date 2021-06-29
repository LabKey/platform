package org.labkey.test.tests.study;

import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.Assays;
import org.labkey.test.categories.DailyC;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.StudyHelper;

import java.io.File;
import java.util.List;

@Category({DailyC.class, Assays.class})
@BaseWebDriverTest.ClassTimeout(minutes = 4)
public class AutoLinkToStudyTest extends BaseWebDriverTest
{
    private final static String ASSAY_NAME = "Test Assay";

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

        log("Creating an assay");
        goToProjectHome();
        goToManageAssays();
        _assayHelper.createAssayDesign("General", ASSAY_NAME)
                .setAutoLinkTarget("(Data import folder)")
                .clickSave();
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
        String runName = "Run 1";
        File runFile = new File(TestFileUtils.getSampleData("AssayImportExport"), "GenericAssay_Run1.xls");
        importAssayRun(runFile, ASSAY_NAME, runName);

        log("Verifying data is auto imported in study");
        clickTab("Clinical and Assay Data");
        waitAndClickAndWait(Locator.linkWithText(ASSAY_NAME));
        DataRegionTable table = new DataRegionTable("Dataset", getDriver());
        table.setFilter("Run/Name", "Equals", runName);
        checker().verifyEquals("New dataset is not created in Study from Assay import", 6, table.getDataRowCount());
    }

    /*
        Test coverage for : Issue 42937: Assay results grid loading performance can degrade with a large number of "copied to study" columns
     */
    @Test
    public void testLinkedColumnNotDisplayedCase()
    {
        String runName = "Link to study run";
        log("Creating the study projects");
        _containerHelper.createProject("Study 1", "Study");
        _studyHelper.startCreateStudy().setTimepointType(StudyHelper.TimepointType.DATE)
                .createStudy();

        _containerHelper.createProject("Study 2", "Study");
        _studyHelper.startCreateStudy().setTimepointType(StudyHelper.TimepointType.DATE)
                .createStudy();

        _containerHelper.createProject("Study 3", "Study");
        _studyHelper.startCreateStudy().setTimepointType(StudyHelper.TimepointType.DATE)
                .createStudy();

        File runFile = new File(TestFileUtils.getSampleData("AssayImportExport"), "GenericAssay_Run2.xls");
        importAssayRun(runFile, ASSAY_NAME, runName);

        linkToStudy(runName, "Study 1", 1);
        linkToStudy(runName, "Study 2", 1);
        linkToStudy(runName, "Study 3", 1);

        log("Verifying linked column does not exists because more then 3 studies are linked");
        goToProjectHome();
        goToManageAssays();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        clickAndWait(Locator.linkWithText(runName));
        DataRegionTable runsTable = DataRegionTable.DataRegion(getDriver()).withName("Data").waitFor();
        checker().verifyFalse("Linked column for Study 1 should not be present",
                runsTable.getColumnNames().contains("linked_to_Study_1_Study"));
        checker().verifyFalse("Linked column for Study 2 should not be present",
                runsTable.getColumnNames().contains("linked_to_Study_2_Study"));
        checker().verifyFalse("Linked column for Study 3 should not be present",
                runsTable.getColumnNames().contains("linked_to_Study_3_Study"));
    }

    private void linkToStudy(String runName, String targetStudy, int numOfRows)
    {
        goToProjectHome();
        goToManageAssays();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        clickAndWait(Locator.linkWithText(runName));

        DataRegionTable runTable = DataRegionTable.DataRegion(getDriver()).withName("Data").waitFor();
        for (int i = 0; i < numOfRows; i++)
            runTable.checkCheckbox(i);

        runTable.clickHeaderButtonAndWait("Link to Study");

        log("Link to study: Choose target");
        selectOptionByText(Locator.id("targetStudy"), "/" + targetStudy + " (" + targetStudy + " Study)");
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

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _containerHelper.deleteProject(getProjectName(), afterTest);
        _containerHelper.deleteProject("Study 1");
        _containerHelper.deleteProject("Study 2");
        _containerHelper.deleteProject("Study 3");
    }
}
