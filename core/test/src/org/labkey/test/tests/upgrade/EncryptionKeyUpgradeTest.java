package org.labkey.test.tests.upgrade;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.Locators;
import org.labkey.test.WebTestHelper;
import org.labkey.test.WebTestHelper.DatabaseType;
import org.labkey.test.pages.ConfigureReportsAndScriptsPage;
import org.labkey.test.pages.ConfigureReportsAndScriptsPage.EngineType;
import org.labkey.test.pages.admin.RConfigurationPage;
import org.labkey.test.pages.mothership.EditUpgradeMessagePage;
import org.labkey.test.pages.query.ExecuteQueryPage;
import org.labkey.test.util.RReportHelper;

import java.util.List;

import static org.junit.Assert.assertEquals;

@Category({})
public class EncryptionKeyUpgradeTest extends BaseUpgradeTest
{
    private static final String DUMMY_R_SERVE = "Dummy RServe";

    @Override
    protected void doCleanup(boolean afterTest)
    {
        _containerHelper.deleteProject(getProjectName(), afterTest);
    }

    @Override
    protected void doSetup()
    {
        ConfigureReportsAndScriptsPage configureReportsAndScriptsPage = ConfigureReportsAndScriptsPage.beginAt(this);
        configureReportsAndScriptsPage.deleteEnginesFromList(List.of(DUMMY_R_SERVE));

        // Create an R engine with a password (which will be encrypted)
        ConfigureReportsAndScriptsPage.EngineConfig config = new ConfigureReportsAndScriptsPage.RServeEngineConfig()
                .setPassword("password")
                .setName(DUMMY_R_SERVE);
        configureReportsAndScriptsPage.addEngine(EngineType.REMOTE_R, config);

        _containerHelper.createProject(getProjectName(), null);
        RConfigurationPage.beginAt(this, getProjectName())
                .setEngineOverrides(DUMMY_R_SERVE, DUMMY_R_SERVE)
                .save();

        if (WebTestHelper.getDatabaseType() == DatabaseType.PostgreSQL)
        {
            // Set StatusCake api key
            EditUpgradeMessagePage.beginAt(this)
                    .setStatusCakeApiKey("password")
                    .save();
        }
    }

    @Test
    public void testDummyRemoteREngine()
    {
        // Trying to contact the R server will trigger an error if there is a problem with password encryption
        ExecuteQueryPage.beginAt(this, getProjectName(), "core", "containers").getDataRegion()
                .goToReport("Create R Report");
        new RReportHelper(this).clickReportTab();

        waitForElement(Locators.labkeyError.withText("Error executing command"));
        assertTextPresent("Could not connect to: 127.0.0.1:6311");

    }

    @Test
    public void testStatusCakeApiKey()
    {
        if (WebTestHelper.getDatabaseType() == DatabaseType.PostgreSQL)
        {
            // Just loading this page can trigger an error if there was a problem with the encryption
            assertEquals("StatusCake API key input should be present but blank",
                    "", EditUpgradeMessagePage.beginAt(this).getStatusCakeApiKey());
        }
    }

    @Override
    protected String getProjectName()
    {
        return "EncryptionKeyUpgradeTest Project";
    }

}
