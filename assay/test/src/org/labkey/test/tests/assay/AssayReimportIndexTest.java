package org.labkey.test.tests.assay;

import org.assertj.core.api.Assertions;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.Assays;
import org.labkey.test.categories.Daily;
import org.labkey.test.pages.assay.AssayImportPage;
import org.labkey.test.pages.assay.AssayRunsPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.params.assay.GeneralAssayDesign;
import org.labkey.test.util.search.SearchAdminAPIHelper;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Category({Daily.class, Assays.class})
public class AssayReimportIndexTest extends BaseWebDriverTest
{
    public static String ASSAY_NAME = "test_assay";
    public static String STRING_FIELD_NAME = "string_field" + TRICKY_CHARACTERS_NO_QUOTES;

    @Override
    protected void doCleanup(boolean afterTest)
    {
        _containerHelper.deleteProject(getProjectName(), afterTest);
    }

    @BeforeClass
    public static void setupProject() throws Exception
    {
        AssayReimportIndexTest init = getCurrentTest();

        init.doSetup();
    }

    private void doSetup() throws Exception
    {
        _containerHelper.createProject(getProjectName(), "Assay");
        goToProjectHome();

        new GeneralAssayDesign(ASSAY_NAME)
                .setBatchFields(List.of(new FieldDefinition("batchData", FieldDefinition.ColumnType.String)), true)
                .setRunFields(List.of(new FieldDefinition(STRING_FIELD_NAME, FieldDefinition.ColumnType.String)), true)
                .createAssay(getProjectName(), createDefaultConnection());
    }

    @Test
    public void testIndexLatestAssayRun()
    {
        String firstRun = "versionOne";
        String secondRun = "versionTwo";

        String firstRunData = """
                SpecimenID	ParticipantID	VisitID	Date	txt
                1	1	1	11/11/2024	argh
                2	2	1	11/12/2024	whee
                """;
        String secondRunData = """
                SpecimenID	ParticipantID	VisitID	Date	txt
                1	1	1	11/11/2024	woo
                2	2	1	11/12/2024	whee
                """;

        // import the first run
        goToProjectHome();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        clickButton("Import Data");
        clickButton("Next");
        AssayImportPage importPage = new AssayImportPage(getDriver());
        importPage.setNamedInputText("name", firstRun);
        importPage.setNamedTextAreaValue("TextAreaDataCollector.textArea", firstRunData);
        importPage.clickSaveAndFinish();
        SearchAdminAPIHelper.waitForIndexer();

        // verify it can be searched
        var searchResultPage1 = navBar().search(firstRun);
        checker().withScreenshot("first_run_not_found_after_import").awaiting(Duration.ofSeconds(2),
                ()-> Assertions.assertThat(searchResultPage1.hasResultLocatedBy(Locator.linkWithText("Assay Run - " + firstRun)))
                        .as("expect to find assay run")
                        .isTrue());

        // re-import with secondRunData, call the run version 2
        goToProjectHome();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        clickAndWait(Locator.linkWithText(firstRun));
        clickButton("Re-import run");
        clickButton("Next");
        AssayImportPage importPage2 = new AssayImportPage(getDriver());
        importPage2.setNamedInputText("name", secondRun);
        importPage2.setNamedTextAreaValue("TextAreaDataCollector.textArea", secondRunData);
        importPage2.clickSaveAndFinish();
        SearchAdminAPIHelper.waitForIndexer();

        // verify it can be searched
        var searchResultPage2 = navBar().search(secondRun);
        checker().withScreenshot("second_run_not_found_after_import").awaiting(Duration.ofSeconds(2),
                ()-> Assertions.assertThat(searchResultPage2.hasResultLocatedBy(Locator.linkWithText("Assay Run - " + secondRun)))
                        .as("expect to find second assay run after re-import")
                        .isTrue());
        // verify first run cannot be searched
        searchResultPage2.searchForm().searchFor(firstRun);
        checker().withScreenshot("first_run_unexpectedly_found_after_reimport").awaiting(Duration.ofSeconds(2),
                ()-> Assertions.assertThat(searchResultPage2.hasResultLocatedBy(Locator.linkWithText("Assay Run - " + firstRun)))
                        .as("expect not to find first assay run")
                        .isFalse());

        // now delete secondRun
        goToProjectHome();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        var runsPage = new AssayRunsPage(getDriver());
        runsPage.getTable().checkCheckbox(runsPage.getTable().getRowIndex("Assay ID", secondRun));
        runsPage.getTable().deleteSelectedRows();
        SearchAdminAPIHelper.waitForIndexer();

        // verify second run cannot be searched
        var searchResultPage3 = navBar().search(firstRun);
        checker().withScreenshot("first_run_not_found_after_de-indexing_second_run")
                .verifyTrue("expect to find first assay run",
                        searchResultPage3.hasResultLocatedBy(Locator.linkWithText("Assay Run - " + firstRun)));
        // verify first run cannot be searched post-delete
        searchResultPage3.searchForm().searchFor(secondRun);
        checker().withScreenshot("second_run_found_after_delete")
                .verifyFalse("expect not to find second assay run after deletion",
                        searchResultPage3.hasResultLocatedBy(Locator.linkWithText("Assay Run - " + secondRun)));
    }

    @Override
    protected String getProjectName()
    {
        return "AssayReimportIndexTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList();
    }
}
