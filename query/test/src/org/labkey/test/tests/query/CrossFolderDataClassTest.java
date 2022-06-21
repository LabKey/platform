package org.labkey.test.tests.query;

import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.pages.query.ExecuteQueryPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.params.experiment.DataClassDefinition;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.TestUser;
import org.labkey.test.util.exp.DataClassAPIHelper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * use this test as a place to put cross-folder dataclass testing
 */
@Category({})
public class CrossFolderDataClassTest extends BaseWebDriverTest
{
    private static final String SUBFOLDER_A = "subA";
    private static String SUBFOLDER_A_PATH;

    @Override
    protected void doCleanup(boolean afterTest)
    {
        _containerHelper.deleteProject(getProjectName(), afterTest);
    }

    @BeforeClass
    public static void setupProject()
    {
        CrossFolderDataClassTest init = (CrossFolderDataClassTest) getCurrentTest();

        init.doSetup();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName(), null);
        _containerHelper.createSubfolder(getProjectName(), SUBFOLDER_A);
        SUBFOLDER_A_PATH = getProjectName() + "/" + SUBFOLDER_A;
    }

    /**
     * Issue 45644 addresses the problem where DataClass metadata wasn't available in query when querying cross-folder
     */
    @Test
    public void testIssue454644() throws Exception
    {
        String dataClass = "TopFolderDataClass";
        int numberOfTestRecords = 3;
        var fields = Arrays.asList(
                new FieldDefinition("intColumn", FieldDefinition.ColumnType.Integer),
                new FieldDefinition("decimalColumn", FieldDefinition.ColumnType.Decimal),
                new FieldDefinition("stringColumn", FieldDefinition.ColumnType.String),
                new FieldDefinition("sampleDate", FieldDefinition.ColumnType.DateAndTime),
                new FieldDefinition("boolColumn", FieldDefinition.ColumnType.Boolean));
        // make a dataclass in the top folder, give it some data
        DataClassDefinition testType = new DataClassDefinition(dataClass).setFields(fields);
        var dGen = DataClassAPIHelper.createEmptyDataClass(getProjectName(), testType);
        dGen.generateRows(numberOfTestRecords);
        var insertResponse = dGen.insertRows();

        // now view the dataclass from a subfolder, expanding its view to include all folders and to show rowId and DataClass/Name
        var subfolderQueryPage = ExecuteQueryPage.beginAt(this, SUBFOLDER_A_PATH, "exp.data", dataClass);
        subfolderQueryPage.getDataRegion().setContainerFilter(DataRegionTable.ContainerFilterType.ALL_FOLDERS);

        var customizeView = subfolderQueryPage.getDataRegion().openCustomizeGrid();
        customizeView.showHiddenItems();
        customizeView.addColumn("RowId");
        customizeView.addColumn("DataClass/Name");
        customizeView.saveDefaultView();

        // gather the inserted and shown data for comparison
        var insertedRows = insertResponse.getRows();
        var shownData = subfolderQueryPage.getDataRegion().getTableData();

        // make sure they all got there
        assertEquals("Expect inserted records to equal shown records",
                insertedRows.size(), shownData.size());
        assertEquals("Also expect all intended records to have been created",
                numberOfTestRecords, shownData.size());

        // ensure the records match, also ensure they show the expected rowId and DataClass/Name values
        for (int i=0; i < insertedRows.size(); i++)
        {
            Map insertedMap = insertedRows.get(i);
            Map shownMap = shownData.stream().filter(a-> a.get("name").equals(insertedMap.get("name"))).collect(Collectors.toList()).get(0);

            assertEquals("Expect metadata rowId to be shown in modified subfolder view",
                    insertedMap.get("rowid").toString(), shownMap.get("RowId"));
            assertEquals("Expect metadata DataClass/Name to be shown in modified subfolder view",
                    dataClass, shownMap.get("DataClass/Name"));
        }
    }

    @Override
    protected String getProjectName()
    {
        return "CrossFolderDataClassTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList();
    }
}
