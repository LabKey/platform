package org.labkey.test.tests.assay;

import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.DailyC;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.pages.ReactAssayDesignerPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.util.DataRegionTable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Category({DailyC.class})
public class AssayImportProvenanceTest extends BaseWebDriverTest
{
    private static final String PROVENANCE_DATA_FILE = "AssayImportProvenanceRun.xls";

    @BeforeClass
    public static void setupProject()
    {
        AssayImportProvenanceTest init = (AssayImportProvenanceTest) getCurrentTest();
        init.doSetup();
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    private void doSetup()
    {
        log("Create a simple Assay project.");
        _containerHelper.createProject(getProjectName(), "Assay");
        goToProjectHome(getProjectName());
    }

    @Override
    protected @Nullable String getProjectName()
    {
        return "AssayImportProvenance Test";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("experiment","provenance");
    }

    @Test
    public void testProvenanceOnAssayImport() throws MalformedURLException
    {
        String runData = "participantID\tprov:objectInputs" + "\n" +
                "P100\t" + uploadFile(PROVENANCE_DATA_FILE);

        String assayName = "Provenance Assay";
        String runName = "ProvenanceAssayRun";
        createSimpleAssay(assayName);
        populateAssay(assayName, runName, runData);

        String inputLsid = getInputLsid();
        String outputLsid = getResultRowLsid(assayName, runName);
        verifyProvenance(inputLsid, outputLsid);
    }

    private String uploadFile(String fileName)
    {
        goToModule("FileContent");
        File datFile = TestFileUtils.getSampleData(PROVENANCE_DATA_FILE);
        _fileBrowserHelper.uploadFile(datFile);
        goToSchemaBrowser();
        DataRegionTable dataTable = viewQueryData("exp", "Data");
        CustomizeView dataTableCustomizeView = dataTable.openCustomizeGrid();
        dataTableCustomizeView.showHiddenItems();
        dataTableCustomizeView.addColumn("LSID");
        dataTableCustomizeView.applyCustomView();
        Map<String, String> dataTableRow = dataTable.getRowDataAsMap(0);
        return dataTableRow.get("LSID");
    }

    private void createSimpleAssay(String assayName)
    {
        log("Creating a simple assay.");
        goToProjectHome(getProjectName());
        ReactAssayDesignerPage assayDesignerPage = _assayHelper.createAssayDesign("General", assayName);

        assayDesignerPage.setEditableResults(true);
        assayDesignerPage.setEditableRuns(true);

        assayDesignerPage.clickFinish();
    }

    private void populateAssay(String assayName, String runName, String runData)
    {
        log("Populate assay with data rows having provenance inputs.");
        goToProjectHome(getProjectName());
        clickAndWait(Locator.linkWithText(assayName));
        waitForElement(Locator.lkButton("Import Data"));
        clickAndWait(Locator.lkButton("Import Data"));
        waitForElement(Locator.tagWithName("select", "targetStudy"));
        clickAndWait(Locator.lkButton("Next"));

        setFormElement(Locator.name("name"), runName);
        click(Locator.xpath("//input[@value='textAreaDataProvider']"));
        setFormElement(Locator.id("TextAreaDataCollector.textArea"), runData);
        clickAndWait(Locator.lkButton("Save and Finish"));
    }

    private String getInputLsid()
    {
        goToSchemaBrowser();
        DataRegionTable materials = viewQueryData("exp", "Materials");
        CustomizeView materialsCustomizeView = materials.openCustomizeGrid();
        materialsCustomizeView.showHiddenItems();
        materialsCustomizeView.addColumn("LSID");
        materialsCustomizeView.applyCustomView();
        Map<String, String> materialsRow = materials.getRowDataAsMap(0);
        return materialsRow.get("LSID");
    }

    private String getResultRowLsid(String assayName, String runName)
    {
        goToProjectHome(getProjectName());
        clickAndWait(Locator.linkWithText(assayName));
        clickAndWait(Locator.linkWithText(runName));
        DataRegionTable assayResults = new DataRegionTable("Data", this);
        CustomizeView assayResultsCustomView = assayResults.openCustomizeGrid();
        assayResultsCustomView.addColumn("LSID");
        assayResultsCustomView.applyCustomView();

        return assayResults.getColumnDataAsText("LSID").get(0);
    }

    private void verifyProvenance(String inputLsid, String outputLsid) throws MalformedURLException
    {
        log("Verify provenance information for assay result row.");
        goToSchemaBrowser();
        DataRegionTable runTable = viewQueryData("exp", "Runs");
        CustomizeView runTableCustomizeView = runTable.openCustomizeGrid();
        runTableCustomizeView.showHiddenItems();
        runTableCustomizeView.addColumn("RowId");
        runTableCustomizeView.applyCustomView();

        Map<String, String> runRow = runTable.getRowDataAsMap(0);
        String runId = runRow.get("RowId");

        URL url = new URL(WebTestHelper.getBaseURL() + getCurrentContainerPath() + "/" + "experiment-showRunText.view?rowId=" + runId + "&_debug=1");
        goToURL(url, longWaitForPage);

        assertTextPresent("Provenance");
        assertElementPresent(Locator.linkWithTitle(inputLsid));
        assertElementPresent(Locator.linkWithTitle(outputLsid));
    }
}
