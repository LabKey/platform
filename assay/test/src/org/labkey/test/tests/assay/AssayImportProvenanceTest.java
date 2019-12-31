package org.labkey.test.tests.assay;

import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.DailyC;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.pages.ReactAssayDesignerPage;
import org.labkey.test.util.DataRegionTable;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Category({DailyC.class})
public class AssayImportProvenanceTest extends BaseWebDriverTest
{
    private static final String PROVENANCE_DATA_FILE = "AssayImportProvenanceRun.xls";
    private Connection cn;

    @BeforeClass
    public static void setupProject()
    {
        AssayImportProvenanceTest init = (AssayImportProvenanceTest) getCurrentTest();
        init.doSetup();
    }

    @Before
    public void preTest()
    {
        cn = createDefaultConnection(false);
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
    public void testProvenanceOnAssayImport() throws IOException, CommandException
    {
        String runData = "participantID\tprov:objectInputs" + "\n" +
                "P100\t" + uploadFile(PROVENANCE_DATA_FILE);

        String assayName = "Provenance Assay";
        String runName = "ProvenanceAssayRun";
        createSimpleAssay(assayName);
        populateAssay(assayName, runName, runData);

        String inputLsid = getInputLsid();
        String outputLsid = getResultRowLsid();
        verifyProvenance(inputLsid, outputLsid);
    }

    private String uploadFile(String fileName) throws IOException, CommandException
    {
        goToModule("FileContent");
        File datFile = TestFileUtils.getSampleData(PROVENANCE_DATA_FILE);
        _fileBrowserHelper.uploadFile(datFile);
        SelectRowsCommand selectCmd = new SelectRowsCommand("exp", "Data");
        selectCmd.setColumns(List.of("LSID"));
        SelectRowsResponse selResp = selectCmd.execute(cn, getProjectName());
        Map<String, Object> dataTableRow = selResp.getRows().get(0);
        return dataTableRow.get("LSID").toString();
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

    private String getInputLsid() throws IOException, CommandException
    {
        SelectRowsCommand selectCmd = new SelectRowsCommand("exp", "Materials");
        selectCmd.setColumns(List.of("LSID"));
        SelectRowsResponse selResp = selectCmd.execute(cn, getProjectName());
        Map<String, Object> materialsRow = selResp.getRows().get(0);
        return materialsRow.get("LSID").toString();
    }

    private String getResultRowLsid() throws IOException, CommandException
    {
        SelectRowsCommand cmd = new SelectRowsCommand("assay.General.Provenance Assay", "Data");
        cmd.setColumns(List.of("LSID"));
        SelectRowsResponse response = cmd.execute(cn, getProjectName());
        Map<String, Object> resultRow = response.getRows().get(0);
        return resultRow.get("LSID").toString();
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
