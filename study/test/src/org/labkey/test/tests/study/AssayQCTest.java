package org.labkey.test.tests.study;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.InDevelopment;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.pages.AssayDesignerPage;
import org.labkey.test.pages.assay.AssayImportPage;
import org.labkey.test.pages.assay.AssayRunsPage;
import org.labkey.test.pages.assay.ManageAssayQCStatesPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.TestDataGenerator;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@Category({InDevelopment.class})
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
    public void testQCStateVisibility() throws Exception
    {
        String assayName = "QCStateVisibilityTest_assay";
        generateAssay("QCStateVisibilityTest", assayName)
                .addRunField("Color", "Color", FieldDefinition.ColumnType.String)
                .addRunField("Concentration", "Concentration", FieldDefinition.ColumnType.Double)
                .enableQCStates(true)
                .saveAndClose();
        List<FieldDefinition> resultsFieldset = List.of(
                TestDataGenerator.simpleFieldDef("ParticipantID",FieldDefinition.ColumnType.String),
                TestDataGenerator.simpleFieldDef("Date", FieldDefinition.ColumnType.DateTime),
                TestDataGenerator.simpleFieldDef("Color", FieldDefinition.ColumnType.String),
                TestDataGenerator.simpleFieldDef("Concentration", FieldDefinition.ColumnType.Double));
        FieldDefinition.LookupInfo assayLookup = new FieldDefinition.LookupInfo(getProjectName() + "/" + "QCStateVisibilityTest",
                "assay.General.QCStateVisibilityTest_assay", "Runs");

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

        AssayRunsPage runsPage = new AssayRunsPage(getDriver());
        CustomizeView customView = runsPage.getTable().openCustomizeGrid();
        customView.addColumn("QCFLAGS/LABEL", "Label");
        customView.addColumn("QCFLAGS/DESCRIPTION", "Description");
        customView.addColumn("QCFLAGS/PUBLICDATA", "Public Data");
        customView.clickViewGrid();

        // now set each row to a different QC state
        runsPage = new AssayRunsPage(getDriver());
        runsPage = runsPage.setRowQcStatus(0, "Seems shady", "Not so sure about this one");
        runsPage = runsPage.setRowQcStatus(1, "Totally legit", "Yeah, I trust this");
        runsPage = runsPage.setRowQcStatus(2, "WTF", "No way is this legit");

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
