package org.labkey.test.tests;

import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestCredentials;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.InDevelopment;
import org.labkey.test.credentials.ApiKey;
import org.labkey.test.pages.redcap.ConfigurePage;
import org.labkey.test.util.redcap.ConfigXmlBuilder;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@Category({InDevelopment.class})
public class RedCapTest extends BaseWebDriverTest
{
    public static final String SURVEY_NAME_LONGITUDINAL = "Longitudinal";
    public static final String SURVEY_NAME_CLASSIC = "Classic";
    public static final String SURVEY_NAME_LONGITUDINAL_ONE_ARM = "Longitudinal_1Arm";
    public static final String SURVEY_NAME_DATA_TYPES = "Data_Types";

    public static final String SERVER_CREDENTIALS_KEY = "REDCap";
    public static String REDCAP_HOST;
    public static List<ApiKey> API_KEYS;

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
    }

    @BeforeClass
    public static void setupProject()
    {
        RedCapTest init = (RedCapTest) getCurrentTest();

        init.doSetup();
    }

    private void doSetup()
    {
        REDCAP_HOST = TestCredentials.getServer(SERVER_CREDENTIALS_KEY).getHost();
        API_KEYS = TestCredentials.getServer(SERVER_CREDENTIALS_KEY).getApiKeys();
        _containerHelper.createProject(getProjectName(), null);
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @Test
    public void testConfiguration() throws Exception
    {
        final String projectName = API_KEYS.get(0).getName();
        final String token = API_KEYS.get(0).getToken();

        _containerHelper.createSubfolder(getProjectName(), "testConfiguration", "Study");
        _containerHelper.enableModule("REDCap");
        createDefaultStudy();
        clickTab("Manage");
        clickAndWait(Locator.linkWithText("Manage External Reloading"));
        clickAndWait(Locator.linkWithText("Configure REDCap"));

        ConfigXmlBuilder configBuilder = new ConfigXmlBuilder()
                .withProjects(new ConfigXmlBuilder.RedCapProject(REDCAP_HOST, projectName, "subject_id")
                        .withDemographic(false)
                        .withMatchSubjectIdByLabel(true)
                        .withForms(new ConfigXmlBuilder.RedCapProjectForm("testName", "testDate", true)))
                .withDuplicateNamePolicy("dupPolicy")
                .withTimepointType("visit");

        ConfigurePage configPage = new ConfigurePage(this)
                .addToken(projectName, token)
                .setConfigurationXml(configBuilder.build());
        String result = configPage.save();

        assertNull("Save was not successful", result);
    }

    @Test
    public void testLongitudinal()
    {
        _containerHelper.createSubfolder(getProjectName(), SURVEY_NAME_LONGITUDINAL, new String[]{"REDCap"});
    }

    @Test
    public void testVisitBased()
    {}

    @Test
    public void testDateBased() throws Exception
    {}

    @Test
    public void testSeparateDemographicProject() throws Exception
    {}

    @Test
    public void testMergeDuplicateNames() throws Exception
    {}

    @Test
    public void testDataTypes() throws Exception
    {}

    private void configureRedCap(String folder, ApiKey... apiKeys)
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
        return "RedCapTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Collections.singletonList("redcap");
    }
}