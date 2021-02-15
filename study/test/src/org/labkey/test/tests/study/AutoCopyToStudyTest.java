package org.labkey.test.tests.study;

import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.Assays;
import org.labkey.test.categories.DailyC;
import org.labkey.test.util.DataRegionTable;

import java.io.File;
import java.util.List;

@Category({DailyC.class, Assays.class})
@BaseWebDriverTest.ClassTimeout(minutes = 2)
public class AutoCopyToStudyTest extends BaseWebDriverTest
{
    private final static String ASSAY_NAME = "Test Assay";

    @BeforeClass
    public static void setupProject()
    {
        AutoCopyToStudyTest initTest = (AutoCopyToStudyTest) getCurrentTest();
        initTest.doSetup();
    }

    private void doSetup()
    {
        log("Creating a date based study");
        _containerHelper.createProject(getProjectName(), "Study");
        clickButton("Create Study");
        checkRadioButton(Locator.radioButtonById("dateTimepointType"));
        clickButton("Create Study");

    }

    @Override
    protected @Nullable String getProjectName()
    {
        return "Auto Copy To Study Test";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return null;
    }

    @Test
    public void testAutoCopyInSameFolder()
    {
        String runName = "Run 1";
        File runFile = new File(TestFileUtils.getSampleData("AssayImportExport"), "GenericAssay_Run1.xls");

        log("Creating an assay");
        goToManageAssays();
        _assayHelper.createAssayDesign("General", ASSAY_NAME)
                .setAutoCopyTarget("(Assay import folder)")
                .clickSave();

        log("Importing the Assay run");
        goToManageAssays();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        DataRegionTable runTable = new DataRegionTable("Runs", getDriver());
        runTable.clickHeaderButtonAndWait("Import Data");
        clickButton("Next");
        setFormElement(Locator.name("name"), runName);
        checkRadioButton(Locator.radioButtonById("Fileupload"));
        setFormElement(Locator.input("__primaryFile__"), runFile);
        clickButton("Save and Finish");

        log("Verifying data is auto imported in study");
        clickTab("Clinical and Assay Data");
        checker().verifyTrue("New dataset is not created in Study from Assay import", isElementPresent(Locator.linkWithText(ASSAY_NAME)));
    }

}
