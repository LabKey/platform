package org.labkey.test.tests.professional;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.categories.Git;
import org.labkey.test.pages.study.ConfigureImporterPage;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;

@Category({Git.class})
public class SpecimenImporterProviderBannerTest extends BaseWebDriverTest
{

    @BeforeClass
    public static void setupProject()
    {
        SpecimenImporterProviderBannerTest init = getCurrentTest();

        init.doSetup();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName(), "Study (ITN)");
        _studyHelper.startCreateStudy()
                .createStudy();
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    /**
     *  This verifies that if no importer is enabled for the study folder, but a module is available that could be enabled,
     *  the user will be notified of their options when they click on the 'configure specimen import' link in the Manage Study page.
     */
    @Test
    public void testEnableModulePageIsServedIfPrimaryNotEnabled()
    {
        ConfigureImporterPage configPage = goToManageStudy()
                .clickConfigureSpecimenImport();

        assertTrue("If Specimen is not enabled in page but module is present, call to action banner should appear",
                configPage.isEnableModuleBannerShown());
    }

    @Test
    public void testQueryBasedImportConfigurePageIsShownWhenEnabled()
    {
        // arrange
        goToFolderManagement()
                .goToFolderTypeTab()
                .enableModule("Specimen")
                .save();

        // assert
        ConfigureImporterPage configPage = goToManageStudy()
                .clickConfigureSpecimenImport();

        assertTrue("If Specimen is enabled in page, query-based configuration should appear",
                configPage.isQueryConfigurationShown());

        // clean up after
        goToFolderManagement()
                .goToFolderTypeTab()
                .disableModule("Specimen")
                .save();
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return "ProfessionalImporterProviderBannerTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList();
    }
}
