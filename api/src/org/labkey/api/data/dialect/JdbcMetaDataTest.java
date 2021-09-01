package org.labkey.api.data.dialect;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.TestSchema;

public class JdbcMetaDataTest extends Assert
{
    DbSchema _testSchema = DbSchema.get("test", DbSchemaType.Bare);

    // Test that tables with names containing LIKE wild cards work correctly, see #43821.
    // Verify column counts and ability to query these tables without exceptions.
    @Test
    public void testTablesWithSpecialCharacters()
    {
        test("a$b", TestSchema.getInstance().getTableInfoTestTable());
        test("a_b", CoreSchema.getInstance().getTableInfoContainers());
        test("a%b", CoreSchema.getInstance().getTableInfoContainerAliases());
        test("a\\b", CoreSchema.getInstance().getTableInfoUsers());
    }

    private void test(String viewName, TableInfo expected)
    {
        TableInfo testTable = _testSchema.getTable(viewName);
        assertNotNull("Failed to find view " + viewName, testTable);
        assertEquals(testTable.getTableType(), DatabaseTableType.VIEW);
        assertEquals(expected.getColumns().size(), testTable.getColumns().size());
        assertEquals(new TableSelector(expected).getRowCount(), new TableSelector(testTable).getMapArray().length);
    }
}
