/*
 * Copyright (c) 2021-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.data.dialect;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.TestSchema;

public class JdbcMetaDataTest extends Assert
{
    private final DbSchema _testSchema = DbSchema.get("test", DbSchemaType.Bare);

    // Test that tables with names containing LIKE wild cards work correctly, see #43821.
    // Verify column counts and ability to query these tables without exceptions.
    @Test
    public void testTablesWithSpecialCharacters()
    {
        test("a$b", TestSchema.getInstance().getTableInfoTestTable(), DatabaseTableType.VIEW);
        test("a_b", CoreSchema.getInstance().getTableInfoContainers(), DatabaseTableType.VIEW);
        test("a%b", CoreSchema.getInstance().getTableInfoContainerAliases(), DatabaseTableType.VIEW);
        test("a\\b", CoreSchema.getInstance().getTableInfoUsers(), DatabaseTableType.VIEW);
    }

    @Test
    public void testMaterializedView()
    {
        // SQL Server doesn't support materialized views
        if (_testSchema.getSqlDialect().isPostgreSQL())
        {
            new SqlExecutor(_testSchema).execute(new SQLFragment("REFRESH MATERIALIZED VIEW test.MaterializedView"));
            test("MaterializedView", CoreSchema.getInstance().getTableInfoContainers(), DatabaseTableType.MATERIALIZED_VIEW);
        }
    }

    private void test(String viewName, TableInfo expectedTable, DatabaseTableType expectedTableType)
    {
        TableInfo testTable = _testSchema.getTable(viewName);
        assertNotNull("Failed to find view " + viewName, testTable);
        assertEquals(expectedTableType, testTable.getTableType());
        assertEquals(expectedTable.getColumns().size(), testTable.getColumns().size());
        assertEquals(new TableSelector(expectedTable).getRowCount(), new TableSelector(testTable).getMapArray().length);
    }
}
