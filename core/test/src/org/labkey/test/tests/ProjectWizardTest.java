package org.labkey.test.tests;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.Git;
import org.labkey.test.components.BodyWebPart;

import java.util.Arrays;
import java.util.List;

@Category({Git.class})
public class ProjectWizardTest extends BaseWebDriverTest
{
    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
    }

    @BeforeClass
    public static void setupProject()
    {
        ProjectWizardTest init = (ProjectWizardTest) getCurrentTest();

        init.doSetup();
    }

    private void doSetup()
    {
        log("this test verifies the project creation wizard from the project menu");
    }

    @Before
    public void preTest() throws Exception
    {
        goToHome();
    }

    @Test
    public void testCreateFromProjectMenu() throws Exception
    {
        projectMenu()
                .navigateToCreateProjectPage()
                .setFolderType("Collaboration")
                .setProjectName(getProjectName())
                .clickNext()
                .setMyUserOnly()
                .clickNext()
                .useDefaultLocation()
                .clickFinish();

        // confirm expected webparts in a collaboration project are present
        new BodyWebPart(getDriver(), "Subfolders");
        new BodyWebPart(getDriver(), "Wiki");
        new BodyWebPart(getDriver(), "Messages");
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return "ProjectWizardTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList();
    }
}
