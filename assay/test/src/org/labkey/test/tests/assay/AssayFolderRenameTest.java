package org.labkey.test.tests.assay;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.Assays;
import org.labkey.test.categories.Daily;
import org.labkey.test.pages.ReactAssayDesignerPage;
import org.labkey.test.pages.admin.RenameFolderPage;
import org.labkey.test.util.APIAssayHelper;
import org.labkey.test.util.AbstractAssayHelper;

import java.util.List;

@Category({Daily.class, Assays.class})
@BaseWebDriverTest.ClassTimeout(minutes = 2)
public class AssayFolderRenameTest extends BaseWebDriverTest
{
    private final static String PROJECT_NAME = "Assay Folder Rename Project";
    private final static String RENAMED_PROJECT_NAME = PROJECT_NAME + " Renamed";
    private final static String ASSAY_NAME = "Test Assay";
    private AbstractAssayHelper _assayHelper = new APIAssayHelper(this);

    @BeforeClass
    public static void setupProject()
    {
        AssayFolderRenameTest initTest = (AssayFolderRenameTest) getCurrentTest();
        initTest.doSetup();
    }

    private void doSetup()
    {
        log("Creating an assay project");
        _containerHelper.createProject(getProjectName(), "Assay");
    }

    @Override
    public @Nullable String getProjectName()
    {
        return PROJECT_NAME;
    }

    /*
        Test coverage : Issue 46473: Assays are messed up after folder rename
        https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=46473
     */
    @Test
    public void testAssayFolderRename()
    {
        log("Create an assay design in the project");
        String newProjectName = getProjectName() + " Renamed";
        goToProjectHome();
        _assayHelper.createAssayDesign("General", ASSAY_NAME).clickFinish();

        log("Rename the folder and uncheck the alias option");
        RenameFolderPage renameFolderPage = goToFolderManagement().clickFolderRename();
        renameFolderPage.setProjectName(newProjectName)
                .setTitleSameAsName(true).setAlias(false)
                .save();

        goToProjectHome(newProjectName);
        clickAndWait(Locator.linkWithText(ASSAY_NAME));

        log("Verify the navigations are right");
        ReactAssayDesignerPage designerPage = _assayHelper.clickEditAssayDesign();
        Assert.assertEquals("Navigated to incorrect assay after renaming the folder", ASSAY_NAME, designerPage.getName());
        Assert.assertEquals("Navigated to incorrect folder after renaming the folder", newProjectName, getCurrentProject());
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return null;
    }

    @Override
    protected void doCleanup(boolean afterTest)
    {
        _containerHelper.deleteProject(getProjectName(), false);
        _containerHelper.deleteProject(RENAMED_PROJECT_NAME, false);
    }
}
