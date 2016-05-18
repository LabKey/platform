package org.labkey.test.tests.mothership;

import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.InDevelopment;
import org.labkey.test.util.mothership.MothershipHelper;

import java.util.Arrays;
import java.util.List;

@Category({InDevelopment.class})
public class MothershipTest extends BaseWebDriverTest
{
    private static final String USER = "ms_user@mothership.test";

    private MothershipHelper _mothershipHelper;
    private int _stackTraceId;

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        // Don't delete mothership project
        _userHelper.deleteUsers(afterTest, USER);
    }

    @BeforeClass
    public static void setupProject()
    {
        MothershipTest init = (MothershipTest) getCurrentTest();

        init.doSetup();
    }

    private void doSetup()
    {
        _userHelper.createUser(USER);
    }

    @Before
    public void preTest() throws Exception
    {
        _mothershipHelper = new MothershipHelper(getDriver());
        _stackTraceId = _mothershipHelper.getLatestStackTraceId();

        goToProjectHome();
    }

    @Test
    public void testSomething() throws Exception
    {
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return MothershipHelper.projectName;
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("mothership");
    }
}