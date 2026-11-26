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
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcMetaDataSelector;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.TestSchema;
import org.labkey.api.exp.api.ExperimentService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
        new SqlExecutor(_testSchema).execute(SQLFragment.unsafe("REFRESH MATERIALIZED VIEW test.MaterializedView"));
        test("MaterializedView", CoreSchema.getInstance().getTableInfoContainers(), DatabaseTableType.MATERIALIZED_VIEW);
    }

    @Test
    public void testImportedKeysMatchJdbc() throws SQLException
    {
        DbScope scope = _testSchema.getScope();

        for (String schemaName : List.of(CoreSchema.getInstance().getSchemaName(), ExperimentService.get().getSchema().getName(), AbstractAuditTypeProvider.SCHEMA_NAME))
        {
            for (String tableName : DbSchema.createFromMetaData(scope, schemaName, DbSchemaType.Bare).getTableNames())
                assertImportedKeysMatch(scope, schemaName, tableName);
        }

        // Mixed case, LIKE wild cards, a multi-column FK, and a cross-schema FK, on tables visible only to this uncommitted transaction
        try (DbScope.Transaction ignored = scope.ensureTransaction())
        {
            new SqlExecutor(scope).execute(SQLFragment.unsafe(
                "CREATE TABLE test.\"FkParent_%X\" (\"Container\" VARCHAR(36) NOT NULL, \"RowId\" INT NOT NULL, PRIMARY KEY (\"Container\", \"RowId\"));\n" +
                "CREATE TABLE test.\"FkChild$Y\" (\"Container\" VARCHAR(36) NOT NULL, \"ParentRowId\" INT, \"ContainerRowId\" INT, " +
                "CONSTRAINT \"FK_Child_Parent\" FOREIGN KEY (\"Container\", \"ParentRowId\") REFERENCES test.\"FkParent_%X\" (\"Container\", \"RowId\"), " +
                "CONSTRAINT \"FK_Child_Containers\" FOREIGN KEY (\"ContainerRowId\") REFERENCES ").append(CoreSchema.getInstance().getTableInfoContainers()).append(SQLFragment.unsafe(" (RowId))")));
            assertEquals(3, assertImportedKeysMatch(scope, _testSchema.getName(), "FkChild$Y"));
        }
    }

    /** Asserts the dialect's FK selector returns the rows JDBC getImportedKeys() does, in the same order; returns the row count */
    private static int assertImportedKeysMatch(DbScope scope, String schemaName, String tableName) throws SQLException
    {
        try (JdbcMetaDataLocator locator = scope.getSqlDialect().getTableResolver().getSingleTableLocator(scope, schemaName, tableName))
        {
            List<List<Object>> expected = readImportedKeys(new JdbcMetaDataSelector(locator, (dbmd, l) -> dbmd.getImportedKeys(l.getCatalogName(), l.getSchemaName(), l.getTableName())));
            List<List<Object>> actual = readImportedKeys(scope.getSqlDialect().getImportedKeysSelector(locator));
            assertEquals(schemaName + "." + tableName, expected, actual);
            return actual.size();
        }
    }

    private static List<List<Object>> readImportedKeys(JdbcMetaDataSelector selector) throws SQLException
    {
        List<List<Object>> rows = new ArrayList<>();
        try (ResultSet rs = selector.getResultSet())
        {
            while (rs.next())
                rows.add(List.of(rs.getString("PKTABLE_SCHEM"), rs.getString("PKTABLE_NAME"), rs.getString("PKCOLUMN_NAME"), rs.getString("FKCOLUMN_NAME"), rs.getInt("KEY_SEQ"), rs.getString("FK_NAME")));
        }
        return rows;
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
