package org.labkey.test.tests.experiment;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.Daily;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.TestDataGenerator;
import org.labkey.test.util.UIAssayHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

// Regression coverage for Issue 38448: ExpSchema lookups do not propagate ContainerFilter of parent table

@Category({Daily.class})
public class ExpSchemaPropagateFilterTest extends BaseWebDriverTest
{

    private static final String PROJECT_NAME = "ExpSchemaLookupProject";
    private static final String SUB_FOLDER_A = "SubFolderA";

    private static final String FOLDER_PARENT_DATA_CLASS = "ParentFolderDataClass";
    private static final String FOLDER_A_DATA_CLASS = "FolderADataClass";

    private static final String FOLDER_PARENT_SAMPLE_TYPE = "ParentFolderSampleSet";
    private static final String FOLDER_A_SAMPLE_TYPE = "FolderASampleSet";

    protected static final String ASSAY_NAME_PARENT = "Parent_Assay_38448";
    protected static final String ASSAY_NAME_SUBFOLDER = "Subfolder_A_Assay_38448";

    protected static final String ASSAY_RUN_PARENT = "Run1_Parent";
    protected static final String ASSAY_REIMPORT_RUN_PARENT = "Run1A_Parent";
    protected static final String ASSAY_RUN_SUBFOLDER = "Run1_Subfolder";
    protected static final String ASSAY_REIMPORT_RUN_SUBFOLDER = "Run1A_Subfolder";

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("experiment");
    }

    @Override
    protected String getProjectName()
    {
        return PROJECT_NAME;
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @BeforeClass
    public static void setupProject()
    {
        ExpSchemaPropagateFilterTest init = (ExpSchemaPropagateFilterTest) getCurrentTest();

        init.doSetup();
    }

    private void doSetup()
    {
        PortalHelper portalHelper = new PortalHelper(this);
        _containerHelper.createProject(PROJECT_NAME);

        projectMenu().navigateToProject(PROJECT_NAME);
        portalHelper.doInAdminMode(ph ->
        {
            ph.addBodyWebPart("Sample Types");
            ph.addBodyWebPart("Data Classes");
            ph.addBodyWebPart("Assay List");
            ph.addBodyWebPart("Run Groups");
        });

        _containerHelper.createSubfolder(PROJECT_NAME, SUB_FOLDER_A);

        projectMenu().navigateToFolder(PROJECT_NAME, SUB_FOLDER_A);
        portalHelper.doInAdminMode(ph ->
        {
            ph.addBodyWebPart("Sample Types");
            ph.addBodyWebPart("Data Classes");
            ph.addBodyWebPart("Assay List");
            ph.addBodyWebPart("Run Groups");
        });

        createDataClasses();

        createSampleSets();

        createAssays();

    }

    private void createDataClasses()
    {

        // Create Data Class in the parent folder.
        TestDataGenerator dataGen = createEmptyDataClass(FOLDER_PARENT_DATA_CLASS, PROJECT_NAME);
        List<Map<String, Object>> values = new ArrayList<>();
        for(int i = 1; i <= 5; i++)
        {
            values.add(Map.of("name", "DP_" + i,
                    "Description", "Data in parent folder " + i));
        }
        populateDomainWithData(dataGen, values);

        // Create Data Class in the sub folder A.
        dataGen = createEmptyDataClass(FOLDER_A_DATA_CLASS, PROJECT_NAME + "/" + SUB_FOLDER_A);
        values = new ArrayList<>();
        for(int i = 1; i <= 5; i++)
        {
            values.add(Map.of("name", "DA_" + i,
                    "Description", "Data in sub-folder A " + i));
        }
        populateDomainWithData(dataGen, values);

    }

    protected TestDataGenerator createEmptyDataClass(String dataClassName, String path)
    {
        return createEmptyDomain("exp.data", "DataClass", dataClassName, path, null);
    }

    private void createSampleSets()
    {

        // Create Sample Type in the parent folder and populate it.
        TestDataGenerator dataGen = createEmptySampleSet(FOLDER_PARENT_SAMPLE_TYPE, PROJECT_NAME);
        List<Map<String, Object>> values = new ArrayList<>();
        values.add(Map.of("name", "SP_1"));
        values.add(Map.of("name", "SP_2"));
        values.add(Map.of("name", "SP_3",
                "MaterialInputs/" + FOLDER_PARENT_SAMPLE_TYPE, "SP_2",
                "DataInputs/" + FOLDER_PARENT_DATA_CLASS, "DP_2"));
        values.add(Map.of("name", "SP_4",
                "MaterialInputs/" + FOLDER_PARENT_SAMPLE_TYPE, "SP_2",
                "DataInputs/" + FOLDER_PARENT_DATA_CLASS, "DP_3"));
        values.add(Map.of("name", "SP_5",
                "MaterialInputs/" + FOLDER_PARENT_SAMPLE_TYPE, "SP_3",
                "DataInputs/" + FOLDER_PARENT_DATA_CLASS, "DP_4"));
        populateDomainWithData(dataGen, values);

        // Create Sample Type in folder A and populate it.
        dataGen = createEmptySampleSet(FOLDER_A_SAMPLE_TYPE, PROJECT_NAME + "/" + SUB_FOLDER_A);
        values = new ArrayList<>();
        values.add(Map.of("name", "SA_1"));
        values.add(Map.of("name", "SA_2"));
        values.add(Map.of("name", "SA_3",
                "MaterialInputs/" + FOLDER_A_SAMPLE_TYPE, "SA_2",
                "MaterialInputs/" + FOLDER_PARENT_SAMPLE_TYPE, "SP_2",
                "MaterialData/" + FOLDER_A_DATA_CLASS, "DA_2",
                "DataInputs/" + FOLDER_PARENT_DATA_CLASS, "DP_2"));
        values.add(Map.of("name", "SA_4",
                "MaterialInputs/" + FOLDER_A_SAMPLE_TYPE, "SA_2",
                "MaterialInputs/" + FOLDER_PARENT_SAMPLE_TYPE, "SP_2",
                "MaterialData/" + FOLDER_A_DATA_CLASS, "DA_3",
                "DataInputs/" + FOLDER_PARENT_DATA_CLASS, "DP_3"));
        values.add(Map.of("name", "SA_5",
                "MaterialInputs/" + FOLDER_A_SAMPLE_TYPE, "SA_3",
                "MaterialInputs/" + FOLDER_PARENT_SAMPLE_TYPE, "SP_3",
                "MaterialData/" + FOLDER_A_DATA_CLASS, "DA_4",
                "DataInputs/" + FOLDER_PARENT_DATA_CLASS, "DP_4"));
        populateDomainWithData(dataGen, values);

    }

    protected TestDataGenerator createEmptySampleSet(String dataClassName, String path)
    {
        List<FieldDefinition> fields = new ArrayList<>();
        fields.add(new FieldDefinition("name"));
        return createEmptyDomain("exp.materials", "SampleSet", dataClassName, path, fields);
    }

    protected TestDataGenerator createEmptyDomain(String schema, String domainKind, String domainName, String path, @Nullable List<FieldDefinition> fields)
    {
        TestDataGenerator dgen;

        if(null != fields)
        {
            dgen = new TestDataGenerator(schema, domainName, path).withColumns(fields);
        }
        else
        {
            dgen = new TestDataGenerator(schema, domainName, path);
        }

        try
        {
            dgen.createDomain(createDefaultConnection(), domainKind);
        }
        catch (IOException | CommandException rethrow)
        {
            throw new RuntimeException(rethrow);
        }

        return dgen;
    }

    private void populateDomainWithData(TestDataGenerator dataGen, List<Map<String, Object>> dataValues)
    {
        try
        {
            dataGen.insertRows(createDefaultConnection(), dataValues);
        }
        catch (IOException | CommandException rethrow)
        {
            throw new RuntimeException(rethrow);
        }
    }

    private void createAssays()
    {

        File assayFile = TestFileUtils.getSampleData("/assay/" + ASSAY_NAME_PARENT + ".xar.xml");
        File assayRunFile = TestFileUtils.getSampleData("/assay/" + ASSAY_NAME_PARENT +".tsv");
        createAssayAndPopulate(null, ASSAY_NAME_PARENT, assayFile, Map.of("name", ASSAY_RUN_PARENT), assayRunFile);

        // Re-import the assay.
        assayRunFile = TestFileUtils.getSampleData("/assay/" + ASSAY_NAME_PARENT +"_Reimport.tsv");
        reImportAssayRun(null, ASSAY_NAME_PARENT, ASSAY_RUN_PARENT, Map.of("name", ASSAY_REIMPORT_RUN_PARENT), assayRunFile);

        assayFile = TestFileUtils.getSampleData("/assay/" + ASSAY_NAME_SUBFOLDER + ".xar.xml");
        assayRunFile = TestFileUtils.getSampleData("/assay/" + ASSAY_NAME_SUBFOLDER + ".tsv");
        createAssayAndPopulate(SUB_FOLDER_A, ASSAY_NAME_SUBFOLDER, assayFile, Map.of("name", ASSAY_RUN_SUBFOLDER), assayRunFile);

        // Re-import the assay.
        assayRunFile = TestFileUtils.getSampleData("/assay/" + ASSAY_NAME_SUBFOLDER +"_Reimport.tsv");
        reImportAssayRun(SUB_FOLDER_A, ASSAY_NAME_SUBFOLDER, ASSAY_RUN_SUBFOLDER, Map.of("name", ASSAY_REIMPORT_RUN_SUBFOLDER), assayRunFile);

    }

    private void createAssayAndPopulate(@Nullable String subFolder, String assayName, File assayFile, @Nullable Map<String, Object> runProperties, File assayRunFile)
    {

        if(null == subFolder)
            goToProjectHome(PROJECT_NAME);
        else
            projectMenu().navigateToFolder(PROJECT_NAME, subFolder);

        _assayHelper.uploadXarFileAsAssayDesign(assayFile, 1);

        try
        {
            _assayHelper.importAssay(assayName, assayRunFile, (Map)null, runProperties);
        }
        catch (CommandException | IOException e)
        {
            throw new RuntimeException("Failed to import assay run", e);
        }
    }

    private void reImportAssayRun(@Nullable String subFolder, String assayName, String currentRunName, @Nullable Map<String, Object> runProperties, File assayRunFile)
    {

        UIAssayHelper uiAssayHelper = new UIAssayHelper(this);

        String path;
        if(null == subFolder)
        {
            goToProjectHome(PROJECT_NAME);
            path = PROJECT_NAME;
        }
        else
        {
            projectMenu().navigateToFolder(PROJECT_NAME, subFolder);
            path = PROJECT_NAME + "/" + subFolder;
        }

        uiAssayHelper.reImportAssay(assayName, currentRunName, assayRunFile, path, null, runProperties);

    }

    private boolean validateWithSchemaBrowser(String schemaName, String queryName, List<String> columns)
    {
        boolean pass = true;

        navigateToQuery(schemaName, queryName);
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.showHiddenItems();
        _customizeViewsHelper.clearColumns();

        columns.forEach(n -> _customizeViewsHelper.addColumn(n));

        _customizeViewsHelper.setFolderFilter("Current folder and subfolders");
        _customizeViewsHelper.saveCustomView();

        DataRegionTable drt = new DataRegionTable("query", getDriver());
        for(String columnName : columns)
        {
            List<String> columnData = drt.getColumnDataAsText(columnName);
            for(String data : columnData)
            {
                if(data.contains("<") && data.contains(">"))
                {
                    pass = false;
                    log("**** Broken look-ups in column '" + columnName + "'.");
                    break;
                }
            }
        }

        return pass;
    }

    @Test
    public void validateAssayDataQuery()
    {

        log("Validate that queries for the assay:data have valid look-ups.");

        List<String> columns = Arrays.asList("sample", "Run", "DataId");

        Assert.assertTrue("Look-up values are missing in the exp:data query.", validateWithSchemaBrowser("assay.General." + ASSAY_NAME_PARENT, "Data", columns));

        log("Lookup values are correct for the assay:data query.");
    }

    @Test
    public void validateExpDataQuery()
    {

        log("Validate that queries for the exp:data have valid look-ups.");

        List<String> columns = Arrays.asList("Name", "Run", "SourceProtocolApplication");

        Assert.assertTrue("Look-up values are missing in the exp:data query.", validateWithSchemaBrowser("exp", "Data", columns));

        log("Look-up values are correct for the exp:data query.");
    }

    @Test
    public void validateExpDataInputsQuery()
    {

        log("Validate that queries for the exp:dataInputs have valid look-ups.");

        List<String> columns = Arrays.asList("Data", "Role", "TargetProtocolApplication");

        Assert.assertTrue("Look-up values are missing in the exp:dataInputs query.", validateWithSchemaBrowser("exp", "DataInputs", columns));

        log("Look-up values are correct for the exp:dataInputs query.");
    }

    @Test
    public void validateExpMaterialInputsQuery()
    {

        log("Validate that queries for the exp:MaterialInputs have valid look-ups.");

        List<String> columns = Arrays.asList("Material", "TargetProtocolApplication", "ProtocolInput");

        Assert.assertTrue("Look-up values are missing in the exp:MaterialInputs query.", validateWithSchemaBrowser("exp", "MaterialInputs", columns));

        log("Look-up values are correct for the exp:MaterialInputs query.");
    }

    @Test
    public void validateExpMaterialsQuery()
    {

        log("Validate that queries for the exp:Materials have valid look-ups.");

        List<String> columns = Arrays.asList("Name", "SourceProtocolApplication", "RunApplication");

        Assert.assertTrue("Look-up values are missing in the exp:Materials query.", validateWithSchemaBrowser("exp", "Materials", columns));

        log("Look-up values are correct for the exp:Materials query.");
    }

    @Test
    public void validateExpRunGroupMapQuery()
    {

        log("Validate that queries for the exp:RunGroupMap have valid look-ups.");

        List<String> columns = Arrays.asList("RunGroup", "Run");

        Assert.assertTrue("Look-up values are missing in the exp:RunGroupMap query.", validateWithSchemaBrowser("exp", "RunGroupMap", columns));

        log("Look-up values are correct for the exp:RunGroupMap query.");
    }

    @Test
    public void validateExpRunsQuery()
    {

        log("Validate that queries for the exp:Runs have valid look-ups.");

        List<String> columns = Arrays.asList("RunGroups", "DataOutputs", "DataInputs", "ReplacesRun", "ReplacedByRun");

        Assert.assertTrue("Look-up values are missing in the exp:Runs query.", validateWithSchemaBrowser("exp", "Runs", columns));

        log("Look-up values are correct for the exp:Runs query.");
    }

}
