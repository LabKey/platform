package org.labkey.test.tests.study;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyC;
import org.labkey.test.categories.FileBrowser;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.components.PropertiesEditor;
import org.labkey.test.pages.DatasetPropertiesPage;
import org.labkey.test.pages.EditDatasetDefinitionPage;
import org.labkey.test.tests.StudyBaseTest;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.ListHelper;
import org.labkey.test.util.PortalHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Category({DailyC.class, FileBrowser.class})
public class RespectPhiColumnTest extends StudyBaseTest
{
    {setIsBootstrapWhitelisted(true);}

    private class ListColumnInfo
    {
        private ListHelper.ListColumn _listColumn;
        private PropertiesEditor.PhiSelectType _phiLevel;

        public ListColumnInfo(ListHelper.ListColumn listColumn, PropertiesEditor.PhiSelectType phiLevel)
        {
            _listColumn = listColumn;
            _phiLevel = phiLevel;
        }

        public ListColumnInfo setListColumn(ListHelper.ListColumn newColumnInfo)
        {
            _listColumn = newColumnInfo;
            return this;
        }

        public ListHelper.ListColumn getListColumn()
        {
            return _listColumn;
        }

        public ListColumnInfo setPhiLevel(PropertiesEditor.PhiSelectType newLevel)
        {
            _phiLevel = newLevel;
            return this;
        }

        public PropertiesEditor.PhiSelectType getPhiLevel()
        {
            return _phiLevel;
        }
    }

    // This study was chosen because it is small (loads quickly) and has most of the components that use PHI.
    private final static File STUDY_ARCHIVE = TestFileUtils.getSampleData("study/StudyVisitManagement.folder.zip");

    // This list is used to validate when all columns (except the key) are removed on export.
    private List<ListColumnInfo> _columnsInfo_List01 = new ArrayList<>(
            Arrays.asList(
                    new ListColumnInfo(new ListHelper.ListColumn("int", "Integer", ListHelper.ListColumnType.Integer), PropertiesEditor.PhiSelectType.PHI),
                    new ListColumnInfo(new ListHelper.ListColumn("txt", "Text", ListHelper.ListColumnType.String), PropertiesEditor.PhiSelectType.PHI)));
    private final static String LIST01_NAME = "ListAllPHI";
    private final static String LIST01_VALUES = "int\ttxt\r\n" +
            "1111\tAAAA\r\n" +
            "2222\tBBBB\r\n" +
            "3333\tCCCC";

    private List<ListColumnInfo> _columnsInfo_List02 = new ArrayList<>(
            Arrays.asList(
                    new ListColumnInfo(new ListHelper.ListColumn("int", "Integer", ListHelper.ListColumnType.Integer), PropertiesEditor.PhiSelectType.NotPHI),
                    new ListColumnInfo(new ListHelper.ListColumn("txt", "Text", ListHelper.ListColumnType.String), PropertiesEditor.PhiSelectType.PHI),
                    new ListColumnInfo(new ListHelper.ListColumn("dbl", "Double", ListHelper.ListColumnType.Double), PropertiesEditor.PhiSelectType.Limited),
                    new ListColumnInfo(new ListHelper.ListColumn("int2", "Integer2", ListHelper.ListColumnType.Integer), PropertiesEditor.PhiSelectType.Restricted)));
    private final static String LIST02_NAME = "ListMixedPHI";
    private final static String LIST02_VALUES = "int\ttxt\tdbl\tint2\r\n" +
            "4444\tDDDD\t4444.4444\t40404040\r\n" +
            "5555\tEEEE\t5555.5555\t50505050\r\n" +
            "6666\tFFFF\t6666.6666\t60606060";

    private final static String DATASET_ALL = "VAC-1";
    private static Map<String, PropertiesEditor.PhiSelectType> DATASET_ALL_FIELDS_PHI = new HashMap<>();
    static {
        Map<String, PropertiesEditor.PhiSelectType> aMap = new HashMap<>();
        aMap.put("VACgiven", PropertiesEditor.PhiSelectType.PHI);
        aMap.put("VACdt", PropertiesEditor.PhiSelectType.PHI);
        aMap.put("VACdelt", PropertiesEditor.PhiSelectType.PHI);
        aMap.put("VACtm", PropertiesEditor.PhiSelectType.PHI);
        aMap.put("VACadmby", PropertiesEditor.PhiSelectType.PHI);
        aMap.put("formlang", PropertiesEditor.PhiSelectType.PHI);
        aMap.put("sfdt_135", PropertiesEditor.PhiSelectType.PHI);
        DATASET_ALL_FIELDS_PHI = Collections.unmodifiableMap(aMap);
    }

    private final static String DATASET_MIXED = "CPF-1";
    private static Map<String, PropertiesEditor.PhiSelectType> DATASET_MIXED_FIELDS_PHI = new HashMap<>();
    static {
        Map<String, PropertiesEditor.PhiSelectType> aMap = new HashMap<>();
        aMap.put("CPFdt", PropertiesEditor.PhiSelectType.NotPHI);
        aMap.put("CPFaltn", PropertiesEditor.PhiSelectType.PHI);
        aMap.put("CPFaltdt", PropertiesEditor.PhiSelectType.Limited);
        aMap.put("CPFalt", PropertiesEditor.PhiSelectType.Restricted);
        DATASET_MIXED_FIELDS_PHI = Collections.unmodifiableMap(aMap);
    }

    private static Map<String, PropertiesEditor.PhiSelectType> SPECIMEN_FIELDS_PHI = new HashMap<>();

    static {
        Map<String, PropertiesEditor.PhiSelectType> freezerFields = new HashMap<>();
        freezerFields.put("Fr_Level2", PropertiesEditor.PhiSelectType.NotPHI);
        freezerFields.put("Fr_Level1", PropertiesEditor.PhiSelectType.PHI);
        freezerFields.put("Fr_Position", PropertiesEditor.PhiSelectType.Limited);
        freezerFields.put("Fr_Container", PropertiesEditor.PhiSelectType.Restricted);
        SPECIMEN_FIELDS_PHI = Collections.unmodifiableMap(freezerFields);
    }

    private static String FULL_PHI_PROJECT_NAME = "PHI_Full";
    private static String LIMITED_PHI_PROJECT_NAME = "PHI_Limited";
    private static String RESTRICTED_PHI_PROJECT_NAME = "PHI_Restricted";
    private static String NO_PHI_PROJECT_NAME = "PHI_None";

    private PortalHelper _portalHelper;

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getFolderName()
    {
        return "MainStudyFolder";
    }

    @Override
    protected String getProjectName()
    {
        return "RespectPhiColumnTest";
    }

    @Override
    protected String getStudyLabel()
    {
        return "Study01";
    }

    @Override
    protected void doCreateSteps() {}

    @Override
    protected void doVerifySteps(){}

    @Test @Ignore
    public void testSteps() {}

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
        _containerHelper.deleteProject(FULL_PHI_PROJECT_NAME, false);
        _containerHelper.deleteProject(LIMITED_PHI_PROJECT_NAME, false);
        _containerHelper.deleteProject(RESTRICTED_PHI_PROJECT_NAME, false);
        _containerHelper.deleteProject(NO_PHI_PROJECT_NAME, false);
    }

    @BeforeClass
    public static void doSetup()
    {
        RespectPhiColumnTest initTest = (RespectPhiColumnTest)getCurrentTest();

        initTest.doCleanup(false);

        initTest._portalHelper = new PortalHelper(initTest.getDriver());

        initTest.initializeFolder();
        initTest.clickFolder(initTest.getFolderName());

        initTest.importStudyFromZip(STUDY_ARCHIVE);

        // This study does not have a list. So need to create one.
        initTest.createLists();

        // Set a few of the columns to PHI levels.
        initTest.setPhiColumnsOnDatasets();

        initTest.setPhiColumnsOnSpecimens();

    }

    @Override
    protected void initializeFolder()
    {
        super.initializeFolder();

        // Not really using the feature that is enabled by the compliance module.
        // If that needs to be implemented then this test should be moved to the compliance module.
    }

    private void createLists()
    {
        goToOriginStudy();

        _portalHelper.addWebPart("Lists");

        createList(LIST01_NAME, _columnsInfo_List01, LIST01_VALUES);

        createList(LIST02_NAME, _columnsInfo_List02, LIST02_VALUES);
    }

    private void createList(String listName, List<ListColumnInfo> listColumnInfo, String listValue)
    {

        ListHelper.ListColumn[] listColumnsLabels = new ListHelper.ListColumn[listColumnInfo.size()];

        int index = 0;
        for(ListColumnInfo columnInfo : listColumnInfo)
        {
            listColumnsLabels[index++] = columnInfo.getListColumn();
        }

        _portalHelper.goToManageLists();

        _listHelper.createList(getProjectName() + "/" + getFolderName(), listName, ListHelper.ListColumnType.AutoInteger, "key", listColumnsLabels);
        _listHelper.clickEditDesign();
        PropertiesEditor listFieldEditor = _listHelper.getListFieldEditor();

        for(ListColumnInfo columnInfo : listColumnInfo)
        {
            listFieldEditor.selectField(columnInfo.getListColumn().getName());
            listFieldEditor.fieldProperties().selectAdvancedTab().phi.set(columnInfo.getPhiLevel());
        }

        _listHelper.clickSave();

        _listHelper.clickImportData();
        _listHelper.submitTsvData(listValue);

    }

    private void setPhiColumnsOnDatasets()
    {
        goToOriginStudy();

        _studyHelper.goToManageDatasets();

        waitAndClickAndWait(Locator.linkWithText(DATASET_ALL));

        DatasetPropertiesPage propertiesPage = new DatasetPropertiesPage(getDriver());
        EditDatasetDefinitionPage editDatasetDefinitionPage = propertiesPage.clickEditDefinition();

        setFieldPHI(editDatasetDefinitionPage, DATASET_ALL_FIELDS_PHI);

        editDatasetDefinitionPage.save();

        _studyHelper.goToManageDatasets();

        waitAndClickAndWait(Locator.linkWithText(DATASET_MIXED));

        propertiesPage = new DatasetPropertiesPage(getDriver());
        editDatasetDefinitionPage = propertiesPage.clickEditDefinition();

        setFieldPHI(editDatasetDefinitionPage, DATASET_MIXED_FIELDS_PHI);

        editDatasetDefinitionPage.save();

    }

    private void setPhiColumnsOnSpecimens()
    {
        goToOriginStudy();

        PropertiesEditor propertiesEditor = _studyHelper.goToEditSpecimenProperties();

        setFieldPHI(propertiesEditor, SPECIMEN_FIELDS_PHI);

        propertiesEditor = PropertiesEditor.PropertiesEditor(getDriver()).withTitleContaining("Vial").waitFor();

        setFieldPHI(propertiesEditor, SPECIMEN_FIELDS_PHI);

        clickButton("Save", 0);
        waitForText("Save successful.");
    }

    private void setFieldPHI(EditDatasetDefinitionPage editDatasetDefinitionPage, Map<String, PropertiesEditor.PhiSelectType> fieldPHIs)
    {
        PropertiesEditor propertiesEditor = editDatasetDefinitionPage.getFieldsEditor("Dataset Fields");
        setFieldPHI(propertiesEditor, fieldPHIs);
    }

    private void setFieldPHI(PropertiesEditor propertiesEditor, Map<String, PropertiesEditor.PhiSelectType> fieldPHIs)
    {
        for(Map.Entry<String, PropertiesEditor.PhiSelectType> entry : fieldPHIs.entrySet())
        {
            propertiesEditor.selectField(entry.getKey());
            propertiesEditor.fieldProperties().selectAdvancedTab().phi.set(entry.getValue());
        }
    }

    @Test
    public void verifyFullPHI()
    {
        log("Verify that exporting with Full PHI gives the expected results.");
        Assert.assertTrue("There were failures when exporting with Full PHI. Review the log to see details.", verifyExport(FULL_PHI_PROJECT_NAME, PropertiesEditor.PhiSelectType.PHI));
    }

    @Test
    public void verifyLimitedPHI()
    {
        log("Verify that exporting with Limited PHI gives the expected results.");
        Assert.assertTrue("There were failures when exporting with Limited PHI. Review the log to see details.", verifyExport(LIMITED_PHI_PROJECT_NAME, PropertiesEditor.PhiSelectType.Limited));
    }

    @Test
    public void verifyRestrictedPHI()
    {
        log("Verify that exporting with Restricted PHI gives the expected results.");
        Assert.assertTrue("There were failures when exporting with Restricted PHI. Review the log to see details.", verifyExport(RESTRICTED_PHI_PROJECT_NAME, PropertiesEditor.PhiSelectType.Restricted));
    }

    @Test
    public void verifyNoPHI()
    {
        log("Verify that exporting with No PHI gives the expected results.");
        Assert.assertTrue("There were failures when exporting with No PHI. Review the log to see details.", verifyExport(NO_PHI_PROJECT_NAME, PropertiesEditor.PhiSelectType.NotPHI));
    }

    protected File exportOriginStudy(PropertiesEditor.PhiSelectType exportPhiLevel)
    {
        goToOriginStudy();

        goToFolderManagement();

        click(Locator.linkWithText("Export"));

        waitForText("Visit Map", "Cohort Settings", "QC State Settings", "CRF Datasets", "Assay Datasets",
                "Dataset Data", "Specimens", "Specimen Settings", "Participant Comment Settings");

        checkRadioButton(Locator.radioButtonByNameAndValue("location", "2"));  // zip file vs. individual files

        if(PropertiesEditor.PhiSelectType.NotPHI != exportPhiLevel)
        {
            checkCheckbox(Locator.name("removePhi"));
            setFormElementJS(Locator.input("exportPhiLevel"), exportPhiLevel.name());
        }
        else
        {
            uncheckCheckbox(Locator.name("removePhi"));
        }

        return clickAndWaitForDownload(Locator.extButton("Export"));
    }

    private boolean verifyExport(String projectName, PropertiesEditor.PhiSelectType phiLevel)
    {

        log("Export the study with the 'exclude columns' option set to '" + phiLevel + "'");
        goToOriginStudy();
        File exportedFile = exportOriginStudy(phiLevel);

        log("Import the study into a new folder named '" + projectName + "' and validate that the expected columns are there.");
        importStudy(projectName, exportedFile);

        log("Validate that the lists were exported correctly.");
        boolean passed = validateExportedList(projectName, LIST01_NAME, _columnsInfo_List01, phiLevel);
        passed = passed && validateExportedList(projectName, LIST02_NAME, _columnsInfo_List02, phiLevel);

        log("Validate that the datasets were exported correctly.");
        passed = passed && validateExportedDataset(projectName, DATASET_ALL, DATASET_ALL_FIELDS_PHI, phiLevel);
        passed = passed && validateExportedDataset(projectName, DATASET_MIXED, DATASET_MIXED_FIELDS_PHI, phiLevel);

        log("Validate that the specimen columns were exported correctly.");
        passed = passed & validateExportedSpecimens(projectName, SPECIMEN_FIELDS_PHI, phiLevel);

        return passed;
    }

    private List<String> getFieldNamesShown(List<PropertiesEditor.FieldRow> listedFields)
    {
        List<String> names = new ArrayList<>();

        for(PropertiesEditor.FieldRow fieldRow : listedFields)
        {
            names.add(fieldRow.getName().trim().toLowerCase());
        }

        return names;
    }

    private boolean validateExportedList(String projectName, String listName, List<ListColumnInfo> listColumnsInfo, PropertiesEditor.PhiSelectType exportSetting)
    {
        boolean pass = true;

        // I think this might be needed if an earlier test failed. Just want to make sure I have a clean reference.
        _portalHelper = new PortalHelper(getDriver());

        goToProjectHome(projectName);
        _portalHelper.goToManageLists();

        click(Locator.linkWithText(listName));
        clickButton("Design");

        // It's easier to identify which fields are there if the form is in edit mode.
        _listHelper.clickEditDesign();

        List<String> listedFieldsName = getFieldNamesShown(_listHelper.getListFieldEditor().getFields());

        for(ListColumnInfo listColumnInfo : listColumnsInfo)
        {
            if((exportSetting == PropertiesEditor.PhiSelectType.NotPHI) || (listColumnInfo.getPhiLevel().getRank() < exportSetting.getRank()))
            {
                // Check for fields that should be there.
                if(!listedFieldsName.contains(listColumnInfo.getListColumn().getName().trim().toLowerCase()))
                {
                    pass = false;
                    log("************** Did not find field '" + listColumnInfo.getListColumn().getName().trim().toLowerCase() + "' in lists, it should be there. **************");
                }
            }
            else if(listColumnInfo.getPhiLevel().getRank() >= exportSetting.getRank())
            {
                // Else check that fields that should not be there are not there.
                if(listedFieldsName.contains(listColumnInfo.getListColumn().getName().trim().toLowerCase()))
                {
                    pass = false;
                    log("************** Found field '" + listColumnInfo.getListColumn().getName().trim().toLowerCase() + "' in lists, it should not be there. **************");
                }
            }

        }

        click(Locator.lkButton("Cancel"));

        return pass;
    }

    private boolean validateExportedDataset(String projectName, String datasetName, Map<String, PropertiesEditor.PhiSelectType> expectedFields, PropertiesEditor.PhiSelectType exportSetting)
    {

        boolean pass = true;

        goToProjectHome(projectName);
        EditDatasetDefinitionPage editDatasetDefinitionPage = _studyHelper
                .goToManageDatasets()
                .selectDatasetByName(datasetName)
                .clickEditDefinition();

        List<String> getListedFields = getFieldNamesShown(editDatasetDefinitionPage.getFieldsEditor("Dataset Fields").getFields());

        for(String expectedField : expectedFields.keySet())
        {
            if((exportSetting == PropertiesEditor.PhiSelectType.NotPHI) || (expectedFields.get(expectedField).getRank() < exportSetting.getRank()))
            {
                // Check for fields that should be there.
                if(!getListedFields.contains(expectedField.trim().toLowerCase()))
                {
                    pass = false;
                    log("************** Did not find field '" + expectedField.trim().toLowerCase() + "' in datasets, it should be there. **************");
                }
            }
            else if(expectedFields.get(expectedField).getRank() >= exportSetting.getRank())
            {
                // Else check that fields that should not be there are not there.
                if(getListedFields.contains(expectedField.trim().toLowerCase()))
                {
                    pass = false;
                    log("************** Found field '" + expectedField.trim().toLowerCase() + "' in datasets, it should not be there. **************");
                }
            }
        }

        // This is to work around some unwanted behavior. If you have no user defined rows, when you go into edit mode
        // the page will add a blank row. This row need to be deleted, otherwise you get a dialog that won't go away even if you cancel.
        for(PropertiesEditor.FieldRow row: editDatasetDefinitionPage.getFieldsEditor("Dataset Fields").getFields())
        {
            if(0 == row.getName().trim().length())
                row.markForDeletion();
        }

        click(Locator.lkButton("Cancel"));

        return pass;

    }

    private boolean validateExportedSpecimens(String projectName, Map<String, PropertiesEditor.PhiSelectType> expectedFields, PropertiesEditor.PhiSelectType exportSetting)
    {

        boolean pass = true;

        goToProjectHome(projectName);
        clickTab("Specimen Data");

        waitAndClickAndWait(Locator.tagWithText("span","Search"));

        DataRegionTable vialsDataRegion = new DataRegionTable("SpecimenDetail", getDriver());
        vialsDataRegion.showAll();
        CustomizeView cv = vialsDataRegion.openCustomizeGrid();
        for(String fieldName : expectedFields.keySet())
        {
            cv.addColumn(fieldName);
        }

        cv.clickViewGrid();

        // I guess because I changed the view I have to get a new dataregion reference.
        waitForElement(Locator.tagWithText("span", "This grid view has been modified."));
        vialsDataRegion = new DataRegionTable("SpecimenDetail", getDriver());

        for(String fieldName : expectedFields.keySet())
        {

            vialsDataRegion.setFilter(fieldName, "Is Not Blank");
            if((exportSetting == PropertiesEditor.PhiSelectType.NotPHI) || (expectedFields.get(fieldName).getRank() < exportSetting.getRank()))
            {
                if (vialsDataRegion.getDataRowCount() == 0)
                {
                    pass = false;
                    log("************** For the field '" + fieldName.trim().toLowerCase() + "' in specimens there was no data, there should have been something. **************");
                }
            }
            else if(expectedFields.get(fieldName).getRank() >= exportSetting.getRank())
            {
                // Else check that fields that should not be there are not there.
                if (vialsDataRegion.getDataRowCount() != 0)
                {
                    pass = false;
                    log("************** The field '" + fieldName.trim().toLowerCase() + "' in specimens has data, there should not be any. **************");
                }
            }
            // Remove the filter.
            vialsDataRegion.clearAllFilters(fieldName);
        }

        return pass;

    }

    private void importStudy(String projectName, File importFile)
    {
        goToHome();

        _containerHelper.createProject(projectName, "Study");
        goToProjectHome(projectName);
        importStudyFromZip(importFile);

    }

    private void goToOriginStudy()
    {
        clickProject(getProjectName());
        clickFolder(getFolderName());
    }

}
