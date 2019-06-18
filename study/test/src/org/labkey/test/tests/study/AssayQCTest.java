package org.labkey.test.tests.study;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyC;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.pages.AssayDesignerPage;
import org.labkey.test.pages.EditDatasetDefinitionPage;
import org.labkey.test.pages.admin.ExportFolderPage;
import org.labkey.test.pages.admin.ImportFolderPage;
import org.labkey.test.pages.assay.AssayImportPage;
import org.labkey.test.pages.assay.AssayRunsPage;
import org.labkey.test.pages.assay.ManageAssayQCStatesPage;
import org.labkey.test.pages.study.QCStateTableRow;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.TestDataGenerator;
import org.openqa.selenium.WebElement;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Category({DailyC.class})
public class AssayQCTest extends BaseWebDriverTest
{
    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
    }

    @BeforeClass
    public static void setupProject()
    {
        AssayQCTest init = (AssayQCTest) getCurrentTest();

        init.doSetup();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName(), null);

        // the pattern here will be to leave creation of assays to tests; they will do so in subfolders of this project
    }

    private AssayDesignerPage generateAssay(String subfolderName, String assayName)
    {
        _containerHelper.createSubfolder(getProjectName(), subfolderName);
        navigateToFolder(getProjectName(), subfolderName);
        goToManageAssays();
        AssayDesignerPage designerPage = _assayHelper.createAssayAndEdit("General", assayName);

        return designerPage;
    }

    @Before
    public void preTest() throws Exception
    {
        goToProjectHome();
    }

    @Test
    public void testQCStateRequiredPermissions() throws Exception
    {
        String assayName = "QCStatePermissionsTest_assay";
        String assayFolder = "QCStatePermissionsTest";
        generateAssay(assayFolder, assayName)
                .addDataField("Color", "Color", FieldDefinition.ColumnType.String)
                .addDataField("Concentration", "Concentration", FieldDefinition.ColumnType.Double)
                .enableQCStates(true)
                .saveAndClose();
        insertAssayData(assayName,  new FieldDefinition.LookupInfo(getProjectName() + "/" + assayFolder,
                "assay.General.QCStatePermissionsTest_assay", "Runs"));

        // capture this location for later use
        String runsPageUrl = getDriver().getCurrentUrl();

        // make sure reader can't access QCState management, but admin can
        DataRegionTable adminTable = new AssayRunsPage(getDriver()).getTable();
        assertEquals("Header button QC State should be present",1, adminTable.getHeaderButtons().stream()
                .filter(a-> a.getText().equals("QC State")).collect(Collectors.toList())
                .size());
        impersonateRole("Reader");
        DataRegionTable readerTable = new AssayRunsPage(getDriver()).getTable();
        assertEquals("Header button QC State not be present",0, readerTable.getHeaderButtons().stream()
                        .filter(a-> a.getText().equals("QC State")).collect(Collectors.toList())
                .size());
        stopImpersonating();
        // for some reason stopping impersonation drops you at /home?

        beginAt(runsPageUrl);

        // set the QC states as admin
        setQCStates();

        DataRegionTable adminView = new AssayRunsPage(getDriver())
                .getTable();
        assertEquals("expect all 3 run records to be shown", 3, adminView.getDataRowCount());
        assertEquals(Arrays.asList("true", "true", "false"), adminView.getColumnDataAsText("Public Data"));

        impersonateRole("Reader");  // currently fails here with issue https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=37681
        waitForText("There are 1 rows not shown due to unapproved QC state");
        DataRegionTable withheld = new AssayRunsPage(getDriver())
                .getTable();
        assertEquals("expect only 2 (publicdata=true) records to be shown", 2, withheld.getDataRowCount());
        assertEquals(Arrays.asList("true", "true"), withheld.getColumnDataAsText("Public Data"));
        stopImpersonating();

        // clean up the subfolder on success
        _containerHelper.deleteFolder(getProjectName(), assayFolder);
    }

    @Test
    public void testQCStateVisibility() throws Exception
    {
        String assayName = "QCStateVisibilityTest_assay";
        String assayFolder = "QCStateVisibilityTest";
        generateAssay(assayFolder, assayName)
                .addDataField("Color", "Color", FieldDefinition.ColumnType.String)
                .addDataField("Concentration", "Concentration", FieldDefinition.ColumnType.Double)
                .enableQCStates(true)
                .saveAndClose();
        insertAssayData(assayName,  new FieldDefinition.LookupInfo(getProjectName() + "/" + assayFolder,
                "assay.General.QCStateVisibilityTest_assay", "Runs"));

        AssayRunsPage runsPage = setQCStates();

        Map<String, String> run1Data = runsPage.getTable().getRowDataAsMap(0);
        Map<String, String> run2Data = runsPage.getTable().getRowDataAsMap(1);
        Map<String, String> run3Data = runsPage.getTable().getRowDataAsMap(2);

        // validate expected visibility
        assertEquals("Seems shady", run1Data.get("QCFlags/Label"));
        assertEquals("Better review this one", run1Data.get("QCFlags/Description"));
        assertEquals("true", run1Data.get("QCFlags/PublicData"));

        assertEquals("Totally legit", run2Data.get("QCFlags/Label"));
        assertEquals("Looks good", run2Data.get("QCFlags/Description"));
        assertEquals("true", run2Data.get("QCFlags/PublicData"));

        assertEquals("WTF", run3Data.get("QCFlags/Label"));
        assertEquals("What, was this found on the lab floor somewhere?", run3Data.get("QCFlags/Description"));
        assertEquals("false", run3Data.get("QCFlags/PublicData"));

        // now bulk update qc --> totally legit, all rows
        runsPage.getTable().checkAllOnPage();
        runsPage = runsPage.updateSelectedQcStatus()
                .selectState("Totally legit")
                .setComment("Glad we got this all straightened out.")
                .clickUpdate();

        // ensure expected values in runsPage table
        assertEquals(Arrays.asList("Totally legit", "Totally legit","Totally legit"), runsPage.getTable().getColumnDataAsText("Label"));

        runsPage.getTable().checkCheckbox(0);
        DataRegionTable qcHistoryTable = runsPage.updateSelectedQcStatus()
            .getHistoryTable();

        // validate audit history for this row
        Map<String, String> history1 = qcHistoryTable.getRowDataAsMap(0);
        Map<String, String> history2 = qcHistoryTable.getRowDataAsMap(1);
        Map<String, String> history3 = qcHistoryTable.getRowDataAsMap(2);

        assertEquals("QC State was set to: Totally legit", history1.get("message"));
        assertEquals("Totally legit", history1.get("qcstate"));
        assertEquals("Glad we got this all straightened out.", history1.get("comment"));

        assertEquals("QC State was removed: Seems shady", history2.get("message"));
        assertEquals("Seems shady", history2.get("qcstate"));
        assertEquals("Not so sure about this one", history2.get("comment"));

        assertEquals("QC State was set to: Seems shady", history3.get("message"));
        assertEquals("Seems shady", history3.get("qcstate"));
        assertEquals("Not so sure about this one", history3.get("comment"));

        // clean up the subfolder on success
        _containerHelper.deleteFolder(getProjectName(), "QCStateVisibilityTest");
    }

    /**
     * coverage for https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=37704
     * @throws Exception
     */
    @Test
    @Ignore  // un-mark as 'ignore' when issue is resolved
    public void testQCStateRoundTrip() throws Exception
    {
        String importDestProjectName = "AssayQCTest_exportDestination";
        String destSubfolder= "ImportExportedAssaySubdir";
        // create the destination project and subfolder
        _containerHelper.addCreatedProject(importDestProjectName);
        _containerHelper.deleteProject(importDestProjectName, false);
        _containerHelper.createProject(importDestProjectName, null);
        _containerHelper.createSubfolder(importDestProjectName, destSubfolder);

        String assayName = "RoundTripQCStateTest_assay";
        generateAssay("RoundTripQCStateTest", assayName)
                .addDataField("Color", "Color", FieldDefinition.ColumnType.String)
                .addDataField("Concentration", "Concentration", FieldDefinition.ColumnType.Double)
                .enableQCStates(true)
                .saveAndClose();
        insertAssayData(assayName, new FieldDefinition.LookupInfo(getProjectName() + "/" + "RoundTripQCStateTest",
                "assay.General.RoundTripQCStateTest_assay", "Runs"));

        AssayRunsPage runsPage = setQCStates();

        Map<String, String> run1Data = runsPage.getTable().getRowDataAsMap(0);
        Map<String, String> run2Data = runsPage.getTable().getRowDataAsMap(1);
        Map<String, String> run3Data = runsPage.getTable().getRowDataAsMap(2);

        // validate expected visibility
        assertEquals("Seems shady", run1Data.get("QCFlags/Label"));
        assertEquals("Better review this one", run1Data.get("QCFlags/Description"));
        assertEquals("true", run1Data.get("QCFlags/PublicData"));

        assertEquals("Totally legit", run2Data.get("QCFlags/Label"));
        assertEquals("Looks good", run2Data.get("QCFlags/Description"));
        assertEquals("true", run2Data.get("QCFlags/PublicData"));

        assertEquals("WTF", run3Data.get("QCFlags/Label"));
        assertEquals("What, was this found on the lab floor somewhere?", run3Data.get("QCFlags/Description"));
        assertEquals("false", run3Data.get("QCFlags/PublicData"));


        // now export the assay to a zip archive
        goToFolderManagement()
                .goToExportTab();    // todo: make a FolderExportPage  and implement
        File exportArchive = new ExportFolderPage(getDriver())
                .includeExperimentsAndRuns(true)
                .exportToBrowserAsZipFile();

        // navigate into the destination folder and import there
        ImportFolderPage.beginAt(this, importDestProjectName +"/"+ destSubfolder)
                .selectLocalZipArchive()
                .chooseFile(exportArchive)
                .clickImportFolder();
        waitForPipelineJobsToFinish(1);

        navigateToFolder(importDestProjectName, destSubfolder);

        navBar().enterPageAdminMode();
        new PortalHelper(getDriver()).addBodyWebPart("Assay List");
        navBar().exitPageAdminMode();
        clickAndWait(Locator.linkWithText(assayName));

        AssayRunsPage importedRunsPage = new AssayRunsPage(getDriver());
        CustomizeView importedView = importedRunsPage.getTable().openCustomizeGrid();
        importedView.addColumn("QCFLAGS/LABEL", "Label");
        importedView.addColumn("QCFLAGS/DESCRIPTION", "Description");
        importedView.addColumn("QCFLAGS/PUBLICDATA", "Public Data");
        importedView.clickViewGrid();

        ManageAssayQCStatesPage importedStatesPage = importedRunsPage.manageQCStates();
        List<QCStateTableRow> states = importedStatesPage.getStateRows();
        assertNotNull("expect qc states to have imported with data",
                states.stream().filter(a->a.getState().equals("Seems shady")).findFirst().orElse(null));
        assertNotNull("expect qc states to have imported with data",
                states.stream().filter(a->a.getState().equals("Totally legit")).findFirst().orElse(null));
        assertNotNull("expect qc states to have imported with data",
                states.stream().filter(a->a.getState().equals("WTF")).findFirst().orElse(null));
        importedStatesPage.clickCancel();

        // now verify expected qc states
        importedRunsPage = new AssayRunsPage(getDriver());
        Map<String, String> imported1 = importedRunsPage.getTable().getRowDataAsMap(0);
        Map<String, String> imported2 = importedRunsPage.getTable().getRowDataAsMap(1);
        Map<String, String> imported3 = importedRunsPage.getTable().getRowDataAsMap(2);

        // validate expected visibility
        assertEquals("Seems shady", imported1.get("QCFlags/Label"));
        assertEquals("Better review this one", imported1.get("QCFlags/Description"));
        assertEquals("true", imported1.get("QCFlags/PublicData"));

        assertEquals("Totally legit", imported2.get("QCFlags/Label"));
        assertEquals("Looks good", imported2.get("QCFlags/Description"));
        assertEquals("true", imported2.get("QCFlags/PublicData"));

        assertEquals("WTF", imported3.get("QCFlags/Label"));
        assertEquals("What, was this found on the lab floor somewhere?", imported3.get("QCFlags/Description"));
        assertEquals("false", imported3.get("QCFlags/PublicData"));

        // clean up the subfolder on success
        _containerHelper.deleteFolder(getProjectName(), "QCStateVisibilityTest");
        // also the import destination project
        _containerHelper.deleteProject(importDestProjectName);
    }

    @Test
    public void testQCStateCopyToStudy() throws Exception
    {
        String studyProjectName = "AssayQCTest_studyProject";
        String assayFolder = "CopyToStudyTest";

        // create the destination project
        _containerHelper.deleteProject(studyProjectName, false);
        _containerHelper.addCreatedProject(studyProjectName);
        _containerHelper.createProject(studyProjectName, "Study");
        goToProjectHome(studyProjectName);
        EditDatasetDefinitionPage datasetDefPage = _studyHelper.startCreateStudy()
                .createStudy()
                .goToManageStudy()
                .manageDatasets()
                .clickCreateNewDataset()
                .setName("testDataset")
                .submit();
        datasetDefPage
                .getFieldsEditor()
                .selectField(0).setName("Color").setLabel("Color").setType(FieldDefinition.ColumnType.String);
        datasetDefPage.getFieldsEditor()
                .addField(new FieldDefinition("Concentration", FieldDefinition.ColumnType.Double));
        datasetDefPage.save();

        goToProjectHome();
        // now create the assay we'll copy to study
        String assayName = "CopyToStudyTest_assay";
        generateAssay(assayFolder, assayName)
                .addDataField("Color", "Color", FieldDefinition.ColumnType.String)
                .addDataField("Concentration", "Concentration", FieldDefinition.ColumnType.Double)
                .enableQCStates(true)
                .saveAndClose();
        insertAssayData(assayName, new FieldDefinition.LookupInfo(getProjectName() + "/" + assayFolder,
                "assay.General.CopyToStudyTest_assay", "Runs"));
        AssayRunsPage runsPage = setQCStates();

        DataRegionTable runsTable = runsPage.getTable();
        runsTable.checkAllOnPage();
        runsTable.clickHeaderButtonAndWait("Copy to Study");
        waitForText("QC checks failed. There are unapproved rows of data in the copy to study selection, please change your selection or request a QC Analyst to approve the run data.");
        clickAndWait(Locator.linkWithText(assayName));

        new AssayRunsPage(getDriver()).getTable().checkAllOnPage();
        new AssayRunsPage(getDriver()).updateSelectedQcStatus()
                .selectState("Totally legit")
                .setComment("Nice work getting all this ready to be copied to the study.")
                .clickUpdate()
                .getTable().checkAllOnPage();
        new AssayRunsPage(getDriver()).getTable().clickHeaderButtonAndWait("Copy to Study");


        selectOptionByText(Locator.name("targetStudy"), "/"+studyProjectName + " ("+studyProjectName+ " Study)");
        clickAndWait(Locator.linkWithSpan("Next"));

        // hack: put a dummy value of 1 into all of the 'visitId' inputs
        DataRegionTable data = DataRegionTable.DataRegion(getDriver()).withName("Data").waitFor();
        List<WebElement> visitIdInputs = Locator.input("visitId").findElements(data.getComponentElement());
        for (WebElement input : visitIdInputs)
        {
            setFormElement(input, "1");
        }
        data.clickHeaderButton("Copy to Study");

        DataRegionTable importedData = DataRegionTable.DataRegion(getDriver()).withName("Dataset").waitFor();
        assertEquals("Expect all dozen rows to make it to the study",12, importedData.getDataRowCount());
    }

    /**
     * generates data (runs) for the gpat assay used by tests in this class.
     * @param assayName
     * @return
     */
    private List<TestDataGenerator> insertAssayData(String assayName, FieldDefinition.LookupInfo assayLookup)
    {
        List<FieldDefinition> resultsFieldset = List.of(
                TestDataGenerator.simpleFieldDef("ParticipantID",FieldDefinition.ColumnType.String),
                TestDataGenerator.simpleFieldDef("Date", FieldDefinition.ColumnType.DateTime),
                TestDataGenerator.simpleFieldDef("Color", FieldDefinition.ColumnType.String),
                TestDataGenerator.simpleFieldDef("Concentration", FieldDefinition.ColumnType.Double));

        clickAndWait(Locator.linkWithText(assayName));
        DataRegionTable.DataRegion(getDriver()).withName("Runs").find().clickHeaderMenu("QC State", "Manage states");
        new ManageAssayQCStatesPage(getDriver())
                .addStateRow("Seems shady", "Better review this one", true)
                .addStateRow("Totally legit", "Looks good", true)
                .addStateRow("WTF", "What, was this found on the lab floor somewhere?", false)
                .clickSave();

        TestDataGenerator dgen1 = new TestDataGenerator(assayLookup)
                .withColumnSet(resultsFieldset)
                .addCustomRow(Map.of("ParticipantID", "Jeff", "Date", "11/11/2018", "Color", "Green", "Concentration", 12.5))
                .addCustomRow(Map.of("ParticipantID", "Jim", "Date", "11/12/2018", "Color", "Red", "Concentration", 14.5))
                .addCustomRow(Map.of("ParticipantID", "Billy", "Date", "11/13/2018", "Color", "Yellow", "Concentration", 17.5))
                .addCustomRow(Map.of("ParticipantID", "Michael", "Date", "11/14/2018", "Color", "Orange", "Concentration", 11.5));
        String pasteData1 = dgen1.writeTsvContents();

        TestDataGenerator dgen2 = new TestDataGenerator(assayLookup)
                .withColumnSet(resultsFieldset)
                .addCustomRow(Map.of("ParticipantID", "Harry", "Date", "10/11/2018", "Color", "Green", "Concentration", 12.5))
                .addCustomRow(Map.of("ParticipantID", "William", "Date", "10/12/2018", "Color", "Red", "Concentration", 14.5))
                .addCustomRow(Map.of("ParticipantID", "Jenny", "Date", "10/13/2018", "Color", "Yellow", "Concentration", 17.5))
                .addCustomRow(Map.of("ParticipantID", "Hermione", "Date", "10/14/2018", "Color", "Orange", "Concentration", 11.5));
        String pasteData2 = dgen2.writeTsvContents();

        TestDataGenerator dgen3 = new TestDataGenerator(assayLookup)
                .withColumnSet(resultsFieldset)
                .addCustomRow(Map.of("ParticipantID", "George", "Date", "10/11/2018", "Color", "Green", "Concentration", 12.5))
                .addCustomRow(Map.of("ParticipantID", "Arthur", "Date", "10/12/2018", "Color", "Red", "Concentration", 14.5))
                .addCustomRow(Map.of("ParticipantID", "Colin", "Date", "10/13/2018", "Color", "Yellow", "Concentration", 17.5))
                .addCustomRow(Map.of("ParticipantID", "Ronald", "Date", "10/14/2018", "Color", "Orange", "Concentration", 11.5));
        String pasteData3 = dgen3.writeTsvContents();

        DataRegionTable runsTable = DataRegionTable.DataRegion(getDriver()).withName("Runs").find();
        runsTable.clickHeaderButton("Import Data");
        clickButton("Next");

        // insert 3 runs
        new AssayImportPage(getDriver()).setNamedTextAreaValue("TextAreaDataCollector.textArea", pasteData1);
        clickButton("Save and Import Another Run");
        new AssayImportPage(getDriver()).setNamedTextAreaValue("TextAreaDataCollector.textArea", pasteData2);
        clickButton("Save and Import Another Run");
        new AssayImportPage(getDriver()).setNamedTextAreaValue("TextAreaDataCollector.textArea", pasteData3);
        clickButton("Save and Finish");

        return Arrays.asList(dgen1, dgen2, dgen3);
    }

    /**
     * sets qc states in the assay run page it is currently in
     * @return
     */
    private AssayRunsPage setQCStates()
    {
        AssayRunsPage runsPage = new AssayRunsPage(getDriver());
        CustomizeView customView = runsPage.getTable().openCustomizeGrid();
        customView.addColumn("QCFLAGS/LABEL", "Label");
        customView.addColumn("QCFLAGS/DESCRIPTION", "Description");
        customView.addColumn("QCFLAGS/PUBLICDATA", "Public Data");
        customView.saveDefaultView();

        // now set each row to a different QC state
        return new AssayRunsPage(getDriver())
            .setRowQcStatus("Seems shady", "Not so sure about this one", 0)
            .setRowQcStatus( "Totally legit", "Yeah, I trust this", 1)
            .setRowQcStatus("WTF", "No way is this legit", 2);
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return "AssayQCTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList();
    }
}
