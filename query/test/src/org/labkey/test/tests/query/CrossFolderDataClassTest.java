package org.labkey.test.tests.query;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.Daily;
import org.labkey.test.pages.query.ExecuteQueryPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.params.experiment.DataClassDefinition;
import org.labkey.test.util.exp.DataClassAPIHelper;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


/**
 * use this test as a place to put cross-folder dataclass testing
 */
@Category({Daily.class})
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
     * Issue 45664: addresses the problem where DataClass metadata wasn't available in query when querying cross-folder
     */
    @Test
    public void testIssue454644() throws Exception
    {
        String dataClass = "TopFolderDataClass";
        var fields = Arrays.asList(
                new FieldDefinition("intColumn", FieldDefinition.ColumnType.Integer),
                new FieldDefinition("decimalColumn", FieldDefinition.ColumnType.Decimal),
                new FieldDefinition("stringColumn", FieldDefinition.ColumnType.String),
                new FieldDefinition("sampleDate", FieldDefinition.ColumnType.DateAndTime),
                new FieldDefinition("boolColumn", FieldDefinition.ColumnType.Boolean));
        // make a dataclass in the top folder, give it some data
        DataClassDefinition testType = new DataClassDefinition(dataClass).setFields(fields);
        var dGen = DataClassAPIHelper.createEmptyDataClass(getProjectName(), testType);
        dGen.generateRows(3);
        dGen.insertRows();

        // now view the dataclass from a subfolder, expanding its view to include all folders and to show rowId and DataClass/Name
        var subfolderQueryPage = ExecuteQueryPage.beginAt(this, SUBFOLDER_A_PATH, "exp.data", dataClass);

        var customizeView = subfolderQueryPage.getDataRegion().openCustomizeGrid();
        customizeView.showHiddenItems();
        customizeView.addColumn("RowId");
        customizeView.addColumn("DataClass/Name");
        customizeView.saveDefaultView();

        // now insert a record into the dataclass, in the subfolder
        subfolderQueryPage.getDataRegion().clickInsertNewRow()
                .setField("Name", "Jeff")
                .setField("intColumn", "5")
                .setField("decimalColumn", "6.7")
                .setField("stringColumn", "hey")
                .submit();

        // gather the data from the view; should only see Jeff
        var shownData = subfolderQueryPage.getDataRegion().getTableData();

        // verify expected container filtering; just the 1 record in the current container should be visible
        assertEquals("Expect grid to only show records in the current container",
                1, shownData.size());

        // ensure the record shows the expected rowId and DataClass/Name values
        var newRecord = shownData.get(0);
        assertEquals("Expect Jeff to be the name value",
                "Jeff", newRecord.get("Name"));
        assertNotNull("Expect metadata rowId to be shown in subfolder view",
                newRecord.get("rowid"));
        assertEquals("Expect metadata DataClass/Name to be shown in modified subfolder view",
                dataClass, newRecord.get("DataClass/Name"));

        // ensure the 'name' is linked up and navigates to experiment-showData.view
        clickAndWait(Locator.linkWithText("Jeff"));
        assertThat("name link should click through to showdata page",
                getURL().toString().toLowerCase(), containsString("experiment-showdata.view"));
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
