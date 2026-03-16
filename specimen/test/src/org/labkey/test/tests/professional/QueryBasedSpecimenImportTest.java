package org.labkey.test.tests.professional;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.Git;
import org.labkey.test.pages.professional.ConfigureSpecimenImportPage;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@Category({Git.class})
public class QueryBasedSpecimenImportTest extends BaseWebDriverTest
{
    String TEST_SCHEMA = "lists";
    String TEST_QUERY = "test_specimen_data";
    String TEST_VIEW = "Default";
    File SPECIMENS_FILE = TestFileUtils.getSampleData("import/100_specimens.xlsx");

    @BeforeClass
    public static void setupProject()
    {
        QueryBasedSpecimenImportTest init = getCurrentTest();
        init.doSetup();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName(), "Study (ITN)");
        _studyHelper.startCreateStudy()
            .createStudy();
        goToFolderManagement()
            .goToFolderTypeTab()
            .enableModule("Specimen")
            .save();

        // create a list of specimens to import from
        _listHelper.createListFromFile(getProjectName(), TEST_QUERY, SPECIMENS_FILE);
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @Test
    public void testQueryBasedSpecimenImport()
    {
        // the test configures one query-based importer
        goToManageStudy()
            .clickConfigureSpecimenImport();
        new ConfigureSpecimenImportPage(getDriver())
            .configureQueryBasedImport(true, TEST_SCHEMA, TEST_QUERY, TEST_VIEW)
            .clickSave();

        // navigate directly to configure query-based import, user 'reload now' button to load the specimens from the query
        ConfigureSpecimenImportPage.beginAt(this, getProjectName())
                .clickReloadNow();
        waitForPipelineJobsToComplete(1, false);

        // navigate to Specimens tab, verify
        clickTab("Specimens", true);

        // iterate over the list, ensure that for each there is a matching specimen in specimenDetails
        List<Map<String, Object>> sourceSpecimenData =executeSelectRowCommand(TEST_SCHEMA, TEST_QUERY).getRows();
        List<Map<String, Object>> specimenDetailsList = executeSelectRowCommand("study", "SpecimenDetail").getRows();
        List<Map<String, Object>> specimenPrimaryTypes = executeSelectRowCommand("study", "SpecimenPrimaryType").getRows();

        List<String> primaryTypes = specimenPrimaryTypes.stream().map(a-> a.get("PrimaryType").toString()).collect(Collectors.toList());
        assertThat("expect 4 kinds of primary vial types",
                primaryTypes, hasItems("PBMC-Na Hep", "Serum-Clot", "urine super", "whole blood"));

        for (Map<String, Object> specimenRow : sourceSpecimenData)
        {
            String globalUniqueSpecimenId = specimenRow.get("global_unique_specimen_id").toString();

            Optional<Map<String, Object>> matchingSpecimenDetail = specimenDetailsList.stream()
                    .filter(a-> a.get("GlobalUniqueId").equals(globalUniqueSpecimenId)).findFirst();
            assertTrue("Expect to find matching SpecimenDetail for [" +globalUniqueSpecimenId+ "]",
                    matchingSpecimenDetail.isPresent());
        }
    }



    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return "SpecimenImportTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("Specimen");
    }
}
