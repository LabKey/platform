package org.labkey.test.tests;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.Daily;
import org.labkey.test.util.PortalHelper;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.assertThat;

@Category({Daily.class})
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
        assertThat("WebParts for collaboration folder type.",
                new PortalHelper(getDriver()).getWebPartTitles(), hasItems("Subfolders"));
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
