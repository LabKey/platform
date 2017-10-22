/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.data;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Selector.ForEachBlock;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.TableSorter;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.NotFoundException;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class DbSchema
{
    private static final Logger _log = Logger.getLogger(DbSchema.class);

    public static final String TEMP_SCHEMA_NAME = "temp";

    private final String _name;
    private String _description;
    private final DbSchemaType _type;
    private final DbScope _scope;
    private final Map<String, SchemaTableInfoFactory> _tableInfoFactoryMap;  // Union of all table names from database and schema.xml
    private final Map<String, TableType> _tableXmlMap = new CaseInsensitiveHashMap<>();
    private final Module _module;

    public DbSchema(String name, DbSchemaType type, DbScope scope, Map<String, SchemaTableInfoFactory> tableInfoFactoryMap, Module module)
    {
        _name = name;
        _type = type;
        _scope = scope;
        _tableInfoFactoryMap = tableInfoFactoryMap;
        _module = module;
    }


    /**
     *  Use get(String fullyQualifiedSchemaName, DbSchemaType type) instead
     */
    @Deprecated
    public static @NotNull DbSchema get(String fullyQualifiedSchemaName)
    {
        return get(fullyQualifiedSchemaName, DbSchemaType.Module);
    }


    public static @NotNull DbSchema get(String fullyQualifiedSchemaName, DbSchemaType type)
    {
        // Quick check to avoid creating Pair object in most common case
        int dot = fullyQualifiedSchemaName.lastIndexOf('.');

        if (-1 == dot)
        {
            return DbScope.getLabKeyScope().getSchema(fullyQualifiedSchemaName, type);
        }
        else
        {
            Pair<DbScope, String> scopeAndSchemaName = getDbScopeAndSchemaName(fullyQualifiedSchemaName);

            return scopeAndSchemaName.first.getSchema(scopeAndSchemaName.second, type);
        }
    }

    /**
     * Get DbSchema for the 'temp' schema.  Similar to a provisioned schema, XML metadata is not applied and tables aren't loaded.
     * @see org.labkey.api.data.TempTableInfo
     * @see org.labkey.api.data.TempTableTracker
     */
    public static @NotNull DbSchema getTemp()
    {
        return DbScope.getLabKeyScope().getSchema(TEMP_SCHEMA_NAME, DbSchemaType.Provisioned);
    }

    // "core" returns <<labkey scope>, "core">
    // "external.myschema" returns <<external scope>, "myschema">
    @NotNull
    public static Pair<DbScope, String> getDbScopeAndSchemaName(String fullyQualifiedSchemaName)
    {
        int dot = fullyQualifiedSchemaName.lastIndexOf('.');

        if (-1 == dot)
        {
            return new Pair<>(DbScope.getLabKeyScope(), fullyQualifiedSchemaName);
        }
        else
        {
            String dsName = fullyQualifiedSchemaName.substring(0, dot);
            DbScope scope = DbScope.getDbScope(dsName);

            if (null == scope)
            {
                scope = DbScope.getDbScope(dsName + "DataSource");

                if (null == scope)
                {
                    throw new NotFoundException("Data source \"" + dsName + "\" has not been configured");
                }
            }

            return new Pair<>(scope, fullyQualifiedSchemaName.substring(dot + 1));
        }
    }


    public Resource getSchemaResource() throws IOException
    {
        return getSchemaResource(getResourcePrefix());
    }


    public Resource getSchemaResource(String xmlFilePrefix) throws IOException
    {
        Resource r = _module.getModuleResource("/schemas/" + xmlFilePrefix + ".xml");
        return null != r && r.isFile() ? r : null;
    }


    public static @NotNull DbSchema createFromMetaData(DbScope scope, String requestedSchemaName, DbSchemaType type) throws SQLException
    {
        String fullyQualifiedSchemaName = DbSchema.getDisplayName(scope, requestedSchemaName);
        Module module = type.getModule(fullyQualifiedSchemaName);

        if (null != module)
            fullyQualifiedSchemaName = module.getDatabaseSchemaName(fullyQualifiedSchemaName);

        String schemaName = DbSchema.getDbScopeAndSchemaName(fullyQualifiedSchemaName).second;
        Map<String, String> schemaNameMap = SchemaNameCache.get().getSchemaNameMap(scope);
        String metaDataName = schemaNameMap.get(schemaName);

        return type.createDbSchema(scope, null == metaDataName ? requestedSchemaName : metaDataName, module);
//        Module module = type.getModule(requestedSchemaName);
//
//        if (null != module)
//            requestedSchemaName = module.getDatabaseSchemaName(requestedSchemaName);
//
//        Map<String, String> schemaNameMap = SchemaNameCache.get().getSchemaNameMap(scope);
//        String metaDataName = schemaNameMap.get(requestedSchemaName);
//
//        // TODO: mark this schema as not in the database
//        if (null == metaDataName)
//            return new DbSchema(requestedSchemaName, type, scope, new HashMap<String, String>());
//
//        return type.createDbSchema(scope, metaDataName, module);
    }


    /**
     * Queries JDBC meta data to retrieve the list of tables in this schema, ignoring any temp tables.
     * Returns a case-insensitive map of table names to canonical (meta data) names.
     *
     * Note: Do not call this unless you really know what you're doing! You should probably be calling DbSchema.getTables()
     * instead, to ensure you get the cached version.
     */
    public static Map<String, SchemaTableInfoFactory> loadTableMetaData(DbScope scope, String schemaName) throws SQLException
    {
        return loadTableMetaData(scope, schemaName, true);
    }

    public static Map<String, SchemaTableInfoFactory> loadTableMetaData(DbScope scope, String schemaName, boolean ignoreTemp) throws SQLException
    {
        final Map<String, SchemaTableInfoFactory> schemaTableInfoFactoryMap = new CaseInsensitiveHashMap<>();

        try (JdbcMetaDataLocator locator = scope.getSqlDialect().getJdbcMetaDataLocator(scope, schemaName, "%"))
        {
            new TableMetaDataLoader(locator, ignoreTemp)
            {
                @Override
                protected void handleTable(String tableName, DatabaseTableType tableType, String description) throws SQLException
                {
                    SchemaTableInfoFactory factory = new StandardSchemaTableInfoFactory(tableName, tableType, description);
                    schemaTableInfoFactoryMap.put(tableName, factory);
                }
            }.load();
        }

        scope.getSqlDialect().addTableInfoFactories(schemaTableInfoFactoryMap, scope, schemaName);

        return schemaTableInfoFactoryMap;
    }


    // Base class that pulls table meta data from the database, based on a supplied table pattern. This lets us share
    // code between schema load (when we capture just the table names for all tables) and table load (when we capture
    // all properties of just a single table). We want consistent transaction, exception, and filtering behavior in
    // both cases.
    public static abstract class TableMetaDataLoader<T>
    {
        private final JdbcMetaDataLocator _locator;
        private final boolean _ignoreTemp;

        protected TableMetaDataLoader(JdbcMetaDataLocator locator, boolean ignoreTemp)
        {
            _locator = locator;
            _ignoreTemp = ignoreTemp;
        }

        protected abstract void handleTable(String tableName, DatabaseTableType tableType, String description) throws SQLException;

        protected T getReturnValue() {return null;}

        public T load() throws SQLException
        {
            final SqlDialect dialect = _locator.getScope().getSqlDialect();

            JdbcMetaDataSelector selector = new JdbcMetaDataSelector(_locator, (dbmd, locator) -> dbmd.getTables(locator.getCatalogName(), locator.getSchemaName(), locator.getTableName(), locator.getTableTypes()));

            selector.forEach(rs -> {
                String tableName = rs.getString("TABLE_NAME").trim();

                // Ignore system tables
                if (dialect.isSystemTable(tableName))
                    return;

                // skip if it looks like one of our temp table names: name$<32hexchars>
                if (_ignoreTemp && tableName.length() > 33 && tableName.charAt(tableName.length() - 33) == '$')
                    return;

                DatabaseTableType tableType = dialect.getTableType(rs.getString("TABLE_TYPE"));
                String description = dialect.getTableDescription(rs.getString("REMARKS"));

                handleTable(tableName, tableType, description);
            });

            return getReturnValue();
        }
    }


    @Nullable <OptionType extends DbScope.SchemaTableOptions> SchemaTableInfo loadTable(String requestedTableName, OptionType options) throws SQLException
    {
        SchemaTableInfo ti = createTableFromDatabaseMetaData(requestedTableName);
        TableType xmlTable = _tableXmlMap.get(requestedTableName);

        if (null != xmlTable)
        {
            if (null == ti)
            {
                ti = new SchemaTableInfo(this, DatabaseTableType.NOT_IN_DB, xmlTable.getTableName());
            }

            try
            {
                ti.loadTablePropertiesFromXml(xmlTable);
            }
            catch (IllegalArgumentException e)
            {
                _log.error("Malformed XML in " + ti.getSchema() + "." + xmlTable.getTableName(), e);
            }
        }

        if (null != ti)
        {
            options.afterLoadTable(ti);
            ti.afterConstruct();
            ti.setLocked(true);
        }

        return ti;
    }

    // Could return null if the requested table doesn't exist in the database
    @Nullable
    public SchemaTableInfo createTableFromDatabaseMetaData(final String requestedTableName) throws SQLException
    {
        SchemaTableInfoFactory factory = _tableInfoFactoryMap.get(requestedTableName);

        return null != factory ? factory.getSchemaTableInfo(this) : null;
    }


    public static Set<DbSchema> getAllSchemasToTest()
    {
        Set<DbSchema> schemas = new LinkedHashSet<>();
        List<Module> modules = ModuleLoader.getInstance().getModules();

        for (Module module : modules)
        {
            try
            {
                schemas.addAll(module.getSchemasToTest());
            }
            catch (Exception e)
            {
                _log.error("Exception retrieving schemas for module \"" + module + "\"", e);
            }
        }

        return schemas;
    }


    // Unqualified schema name
    public String getName()
    {
        return _name;
    }

    // Schema name qualified with data source display name (e.g., external.myschema). Resources like schema.xml files
    // and sql scripts are found using this name.
    public String getDisplayName()
    {
        return getDisplayName(_scope, getName());
    }

    /**
     * Prefix used to retrieve resources such as schema XML files and SQL scripts. Usually identical to getDisplayName(),
     * but subclasses can override.
     *
     * @return Resource prefix
     */
    public String getResourcePrefix()
    {
        return getDisplayName();
    }

    // TODO: Provide mechanism to override this in schema.xml
    public String getQuerySchemaName()
    {
        return (_scope.isLabKeyScope() ? "" : _scope.getDisplayName() + "_") + getName();
    }

    // Schema name qualified with data source display name (e.g., external.myschema)
    public static String getDisplayName(DbScope scope, String name)
    {
        return (scope.isLabKeyScope() ? "" : scope.getDisplayName() + ".") + name;
    }

    public SqlDialect getSqlDialect()
    {
        return _scope.getSqlDialect();
    }

    public DbSchemaType getType()
    {
        return _type;
    }

    public boolean isModuleSchema()
    {
        return getType() == DbSchemaType.Module;
    }

    void setTablesDocument(TablesDocument tablesDoc)
    {
        String[] descriptions = tablesDoc.getTables().getDescriptionArray();
        for (String description : descriptions)
        {
            if (_description == null)
            {
                _description = description;
            }
            else
            {
                _description += " " + description;
            }
        }
        TableType[] xmlTables = tablesDoc.getTables().getTableArray();

        for (TableType xmlTable : xmlTables)
        {
            String xmlTableName = xmlTable.getTableName();
            _tableXmlMap.put(xmlTable.getTableName(), xmlTable);

            // Tables in schema.xml but not in the database need to be added to _tableNames
            if (!_tableInfoFactoryMap.containsKey(xmlTableName))
            {
                _tableInfoFactoryMap.put(xmlTableName, new StandardSchemaTableInfoFactory(xmlTableName, DatabaseTableType.NOT_IN_DB, xmlTable.getDescription()));
            }
        }
    }


    public Collection<String> getTableNames()
    {
        return Collections.unmodifiableCollection(new LinkedList<>(_tableInfoFactoryMap.keySet()));
    }

    public SchemaTableInfo getTable(String tableName)
    {
        // Scope holds cache for all its tables
        DbScope.SchemaTableOptions options = new DbScope.SchemaTableOptions(this, tableName);
        return _scope.getTable(options);
    }

    public <OptionType extends DbScope.SchemaTableOptions> SchemaTableInfo getTable(OptionType options)
    {
        // Scope holds cache for all its tables
        return _scope.getTable(options);
    }

    @Nullable
    public String getDescription()
    {
        return _description;
    }

    /**
     * Get a topologically sorted list of TableInfos within this schema.
     * Not all existing schemas are supported yet since their FKs don't expose the query tableName they join to or they contain loops.
     *
     * @throws IllegalStateException if a loop is detected.
     */
    public List<TableInfo> getSortedTables()
    {
        return TableSorter.sort(this);
    }

    public DbScope getScope()
    {
        return _scope;
    }

    public void dropTableIfExists(String objName)
    {
        getSqlDialect().dropIfExists(this, objName, "TABLE", null);
        getSqlDialect().dropIfExists(this, objName, "VIEW", null);
    }

    public void dropIndexIfExists(String objName, String indexName)
    {
        getSqlDialect().dropIfExists(this, objName, "INDEX", indexName);
    }


    @Override
    public String toString()
    {
        return "DbSchema " + getDisplayName();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DbSchema dbSchema = (DbSchema) o;

        if (!getDisplayName().equals(dbSchema.getDisplayName())) return false;
        if (_type != dbSchema._type) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = getDisplayName().hashCode();
        result = 31 * result + _type.hashCode();
        return result;
    }

    public Map<String, TableType> getTableXmlMap()
    {
        return _tableXmlMap;
    }

    @TestWhen(TestWhen.When.BVT)
    @TestTimeout(240)
    public static class TableSelectTestCase extends Assert
    {
        // Do a simple select from every table in every module schema. This ends up invoking validation code
        // in Table that checks PKs and columns, further validating the schema XML file.
        @Test
        public void testTableSelect()
        {
            Set<DbSchema> schemas = DbSchema.getAllSchemasToTest();

            for (DbSchema schema : schemas)
            {
                for (String tableName : schema.getTableNames())
                {
                    try
                    {
                        TableInfo table = schema.getTable(tableName);

                        if (null == table)
                            fail("Could not create table instance: " + tableName);

                        if (table.getTableType() == DatabaseTableType.NOT_IN_DB)
                            continue;

                        TableSelector selector = new TableSelector(table);
                        selector.setMaxRows(10);
                        selector.getMapCollection();
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException("Exception testing table " + schema.getDisplayName() + "." + tableName, e);
                    }
                }
            }
        }
    }

    public static class TransactionTestCase extends Assert
    {
        @Test
        public void testTransactions() throws Exception
        {
            TestSchema test = TestSchema.getInstance();
            DbSchema testSchema = test.getSchema();
            TableInfo testTable = test.getTableInfoTestTable();
            TestContext ctx = TestContext.get();

            assertNotNull(testTable);

            assertFalse("In transaction when shouldn't be.", testSchema.getScope().isTransactionActive());

            Map<String, Object> m = new HashMap<>();
            m.put("DatetimeNotNull", new Date());
            m.put("BitNotNull", Boolean.TRUE);
            m.put("Text", "Added by Transaction Test Suite");
            m.put("IntNotNull", 0);
            m.put("Container", JunitUtil.getTestContainer());

            Integer rowId;

            try (DbScope.Transaction transaction = testSchema.getScope().beginTransaction())
            {
                assertTrue("Not in transaction when should be.", testSchema.getScope().isTransactionActive());
                m = Table.insert(ctx.getUser(), testTable, m);
                rowId = ((Integer) m.get("RowId"));
                assertNotNull("Inserted Row doesn't have Id", rowId);
                assertTrue(rowId != 0);

                transaction.commit();
                assertFalse("In transaction when shouldn't be.", testSchema.getScope().isTransactionActive());
            }

            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowId);

            try (ResultSet rs = new TableSelector(testTable, filter, null).getResultSet())
            {
                assertTrue("Did not find inserted record.", rs.next());
            }

            try (DbScope.Transaction ignored = testSchema.getScope().beginTransaction())
            {
                m.put("IntNotNull", 1);
                m = Table.update(ctx.getUser(), testTable, m, rowId);
                assertTrue("Update is consistent in transaction?", (Integer) m.get("IntNotNull") == 1);
            }

            //noinspection unchecked
            Map<String, Object>[] maps = new TableSelector(testTable, filter, null).getMapArray();
            assertTrue(maps.length == 1);
            m = maps[0];

            assertTrue("Rollback did not appear to work.", (Integer) m.get("IntNotNull") == 0);

            Table.delete(testTable, rowId);
        }
    }

    public static class CachingTestCase extends Assert
    {
        @Test
        public void testCaching() throws Exception
        {
            TestSchema test = TestSchema.getInstance();
            DbSchema testSchema = test.getSchema();
            TableInfo testTable = test.getTableInfoTestTable();
            TestContext ctx = TestContext.get();

            assertNotNull(testTable);
            DbCache.clear(testTable);

            Map<String, Object> m = new HashMap<>();
            m.put("DatetimeNotNull", new Date());
            m.put("BitNotNull", Boolean.TRUE);
            m.put("Text", "Added by Caching Test Suite");
            m.put("IntNotNull", 0);
            m.put("Container", JunitUtil.getTestContainer());
            m = Table.insert(ctx.getUser(), testTable, m);
            Integer rowId1 = ((Integer) m.get("RowId"));

            String key = "RowId" + rowId1;
            DbCache.put(testTable, key, m);
            Map m2 = (Map) DbCache.get(testTable, key);
            assertEquals(m, m2);

            //Does cache get cleared on delete
            Table.delete(testTable, rowId1);
            m2 = (Map) DbCache.get(testTable, key);
            assertNull(m2);

            //Does cache get cleared on insert
            m.remove("RowId");
            m = Table.insert(ctx.getUser(), testTable, m);
            int rowId2 = ((Integer) m.get("RowId"));
            key = "RowId" + rowId2;
            DbCache.put(testTable, key, m);
            m.remove("RowId");
            m = Table.insert(ctx.getUser(), testTable, m);
            int rowId3 = ((Integer) m.get("RowId"));
            m2 = (Map) DbCache.get(testTable, key);
            assertNull(m2);

            //Make sure things are not inserted in transaction
            m.remove("RowId");
            String key2;
            try (DbScope.Transaction transaction = testSchema.getScope().beginTransaction())
            {
                m = Table.insert(ctx.getUser(), testTable, m);
                int rowId4 = ((Integer) m.get("RowId"));
                key2 = "RowId" + rowId4;
                DbCache.put(testTable, key2, m);
            }
            m2 = (Map) DbCache.get(testTable, key2);
            assertNull(m2);

            // Clean up
            Table.delete(testTable, rowId2);
            Table.delete(testTable, rowId3);
        }
    }

    public static class DDLMethodsTestCase extends Assert
    {
        private final TestSchema test = TestSchema.getInstance();
        private final String tempTableName = test.getSchema().getSqlDialect().getTempTablePrefix() + "TTemp";

        @Test
        public void testDDLMethods() throws Exception
        {
            DbSchema testSchema = test.getSchema();

            // create test objects
            //start with cleanup
            testSchema.getSqlDialect().dropSchema(testSchema, "testdrop");
            testSchema.getSqlDialect().dropSchema(testSchema,"testdrop2");
            testSchema.getSqlDialect().dropSchema(testSchema, "testdrop3");
            testSchema.dropTableIfExists(tempTableName);

            SqlExecutor executor = new SqlExecutor(testSchema);

            if (testSchema.getSqlDialect().isSqlServer())
            {
                // test the 3 ways to create a schema on SQLServer
                executor.execute("EXEC sp_addapprole 'testdrop', 'password'");
                executor.execute("CREATE SCHEMA testdrop2");
                executor.execute(testSchema.getSqlDialect().getCreateSchemaSql("testdrop3"));
            }
            else if (testSchema.getSqlDialect().isPostgreSQL())
            {
                executor.execute("CREATE SCHEMA testdrop");
                executor.execute("CREATE SCHEMA testdrop2");
                executor.execute("CREATE SCHEMA testdrop3");
            }
            else
                return;

            executor.execute("CREATE TABLE testdrop.T0 (c0 INT NOT NULL PRIMARY KEY)");
            executor.execute("CREATE TABLE testdrop.T (c1 CHAR(1), fk_c0 INT REFERENCES testdrop.T0(c0))");
            executor.execute("CREATE INDEX T_c1 ON testdrop.T(c1)");
            executor.execute("CREATE VIEW testdrop.V AS SELECT c1 FROM testdrop.T");
            String sqlCreateTempTable = "CREATE " + testSchema.getSqlDialect().getTempTableKeyword() + " TABLE "
                                        + tempTableName + "(ctemp INT)";
            executor.execute(sqlCreateTempTable);

            executor.execute("CREATE TABLE testdrop2.T0 (c0 INT PRIMARY KEY)");
            executor.execute("CREATE TABLE testdrop2.T (c1 CHAR(10), fk_c0 INT REFERENCES testdrop2.T0(c0))");
            executor.execute("CREATE TABLE testdrop3.T (c1 CHAR(10), fk_c0 INT REFERENCES testdrop2.T0(c0))");
            executor.execute("CREATE INDEX T_c1 ON testdrop2.T(c1)");

            testSchema = DbSchema.createFromMetaData(DbScope.getLabKeyScope(), "testdrop", DbSchemaType.Bare);

            //these exist; ensure they are dropped by re-creating them
            testSchema.dropIndexIfExists("T", "T_c1");
            executor.execute("CREATE INDEX T_c1 ON testdrop.T(c1)");

            testSchema.dropTableIfExists("v");
            executor.execute("CREATE VIEW testdrop.V AS SELECT c0 FROM testdrop.T0");

            testSchema.dropTableIfExists("T");
            executor.execute("CREATE TABLE testdrop.T (c1 CHAR(1))");

            testSchema.dropTableIfExists(tempTableName);
            executor.execute(sqlCreateTempTable);

            testSchema.getSqlDialect().dropSchema(testSchema, "testdrop");

            // these don't exist
            testSchema.dropIndexIfExists("T", "T_notexist") ;
            testSchema.dropTableIfExists("V1");
            testSchema.dropTableIfExists("Tnot");
            testSchema.getSqlDialect().dropSchema(testSchema, "testdrop");

            testSchema.getSqlDialect().dropSchema(testSchema, "testdrop2");
            testSchema.getSqlDialect().dropSchema(testSchema, "testdrop3");
        }

        @After
        public void cleanup()
        {
            test.getSchema().dropTableIfExists(tempTableName);
        }
    }

    public static class SchemaCasingTestCase extends Assert
    {
        @Test   // See #12210
        public void testSchemaCasing() throws Exception
        {
            // If schema cache is case-sensitive then this should clear all capitalizations
            DbScope.getLabKeyScope().invalidateSchema("core", DbSchemaType.Module);

            DbSchema core1 = DbSchema.get("Core", DbSchemaType.Module);
            DbSchema core2 = DbSchema.get("CORE", DbSchemaType.Module);
            DbSchema core3 = DbSchema.get("cOrE", DbSchemaType.Module);
            DbSchema canonical = DbSchema.get("core", DbSchemaType.Module);

            verify("Core", canonical, core1);
            verify("CORE", canonical, core2);
            verify("cOrE", canonical, core3);
        }

        private void verify(String requestedName, DbSchema expected, DbSchema test)
        {
            assertNotNull(test);
            assertTrue(test.getTableNames().size() > 20);
            assertTrue("\"" + requestedName + "\" schema does not match \"" + expected.getDisplayName() + "\" schema", test == expected);
        }
    }

    private static Integer checkContainerColumns(DbSchema curSchema, SQLFragment sbSqlCmd, String tempTableName, String moduleName, Integer rowId) throws SQLException
    {
        int row = rowId;
        SQLFragment sbSql = new SQLFragment();

        for (String tableName : curSchema.getTableNames())
        {
            TableInfo t = curSchema.getTable(tableName);

            if (null == t || t.getTableType()!= DatabaseTableType.TABLE)
                continue;

            for (ColumnInfo col : t.getColumns())
            {
                if (col.getName().equalsIgnoreCase("Container"))
                {
                    sbSql.append( " INSERT INTO "+ tempTableName );
                    sbSql.append(" SELECT " + String.valueOf(++row) + " AS rowId, '" + t.getSelectName() + "' AS TableName, ");
                    List<ColumnInfo> pkColumns = t.getPkColumns();

                    if (pkColumns.size() == 1)
                    {
                        ColumnInfo pkColumn = pkColumns.get(0);
                        sbSql.append(" '" + pkColumn.getSelectName());
                        sbSql.append("' AS FirstPKColName, ");
                        sbSql.append(" CAST( " + t.getSelectName() + "." + pkColumn.getSelectName() + " AS VARCHAR(100)) "
                                + " AS FirstPKValue ,");
                    }
                    else
                    {
                        String tmp = "unknown PK";
                        if (pkColumns.size() > 1)
                            tmp = "multiCol PK ";
                        if(t.getName().equals("ACLs"))
                            tmp = "objectid ";
                        sbSql.append(" '" + tmp + "' AS FirstPKColName, ");
                        sbSql.append(" NULL AS FirstPKValue ,");
                    }
                    sbSql.append(" '" + moduleName + "' AS ModuleName, ");
                    sbSql.append(" CAST( " + t.getSelectName() + "." + col.getName() + " AS VARCHAR(100)) AS OrphanedContainer ");
                    sbSql.append(" FROM " + t.getSelectName());
                    sbSql.append( " LEFT OUTER JOIN " + " core.Containers C ");
                    sbSql.append(" ON (" + t.getSelectName() + ".Container = C.EntityId ) ");
                    sbSql.append( " WHERE C.EntityId IS NULL ");

                    // special handling of MS2 soft deletes
                    if (null != t.getColumn("Deleted"))
                    {
                        sbSql.append( " AND Deleted = ? ");
                        sbSql.add(Boolean.FALSE);
                    }
                    else if (t.getSchema().getName().equals("ms2") && null != t.getColumn("Run"))
                    {
                        sbSql.append(" AND Run IN (SELECT Run FROM ms2.runs WHERE Deleted = ? ) ");
                        sbSql.add(Boolean.FALSE);
                    }

                    sbSql.append(";\n");
                    break;
                }
            }
        }

        sbSqlCmd.append(sbSql);

        return row;
    }

    public static String checkAllContainerCols(User user, boolean bfix) throws SQLException
    {
        List<Module> modules = ModuleLoader.getInstance().getModules();
        Integer lastRowId = 0;
        DbSchema coreSchema = CoreSchema.getInstance().getSchema();

        List<ColumnInfo> listColInfos = new ArrayList<>();
        ColumnInfo col = new ColumnInfo("RowId");
        col.setSqlTypeName("INT");
        col.setNullable(false);
        listColInfos.add(col);

        TempTableInfo tTemplate = new TempTableInfo("cltmp", listColInfos, Collections.singletonList("RowId"));
        String tempTableName = tTemplate.getTempTableName();

        String createTempTableSql =
                "CREATE TABLE " + tempTableName + " ( " +
                        "\tRowId INT NOT NULL,  \n" +
                        "\tTableName VARCHAR(300) NOT NULL,\n" +
                        "\tFirstPKColName VARCHAR(100) NULL,\n" +
                        "\tFirstPKValue VARCHAR(100) NULL,\n" +
                        "\tModuleName VARCHAR(50) NOT NULL,\n" +
                        "\tOrphanedContainer VARCHAR(60) NULL) ;\n\n";

        final StringBuilder sbOut = new StringBuilder();

        SQLFragment sbCheck = new SQLFragment();

        for (Module module : modules)
        {
            Set<DbSchema> schemas = module.getSchemasToTest();

            for (DbSchema schema : schemas)
                lastRowId = checkContainerColumns(schema, sbCheck, tempTableName, module.getName(), lastRowId);
        }

        tTemplate.track();
        final SqlExecutor executor = new SqlExecutor(coreSchema);
        executor.execute(createTempTableSql);
        executor.execute(sbCheck);

        if (bfix)
        {
            // create a recovered objects project
            Random random = new Random();
            int r = random.nextInt();
            String cName = "/_RecoveredObjects" +  String.valueOf(r).substring(1,5);

            final Container recovered = ContainerManager.ensureContainer(cName);
            final Set<Module> modulesOfOrphans = new HashSet<>();

            String selectSql = "SELECT TableName, OrphanedContainer, ModuleName FROM " + tempTableName
                    + " WHERE OrphanedContainer IS NOT NULL GROUP BY TableName, OrphanedContainer, ModuleName";

            new SqlSelector(coreSchema, selectSql).forEach(rs -> {
                modulesOfOrphans.add(ModuleLoader.getInstance().getModule(rs.getString(3)));
                String sql = "UPDATE " + rs.getString(1) + " SET Container = ? WHERE Container = ?";

                try
                {
                    executor.execute(sql, recovered.getId(), rs.getString(2));

                    //remove the ACLs that were there
                    SecurityPolicyManager.removeAll(recovered);
                    sbOut.append("<br> Recovered objects from table ");
                    sbOut.append(rs.getString(1));
                    sbOut.append(" to project ");
                    sbOut.append(recovered.getName());
                }
                catch (Exception se)
                {
                    sbOut.append("<br> Failed attempt to recover some objects from table ");
                    sbOut.append(rs.getString(1));
                    sbOut.append(" due to error ").append(se.getMessage());
                    sbOut.append(". Retrying recovery may work.  ");
                }
            });

            recovered.setActiveModules(modulesOfOrphans, user);

            return sbOut.toString();
        }
        else
        {
            new SqlSelector(coreSchema, " SELECT * FROM " + tempTableName
                    + " WHERE OrphanedContainer IS NOT NULL ORDER BY 1,3 ;").forEach(new ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    sbOut.append("<br/>&nbsp;&nbsp;&nbsp;ERROR:  ");
                    sbOut.append(rs.getString(1));
                    sbOut.append(" &nbsp;&nbsp;&nbsp;&nbsp; ");
                    sbOut.append(rs.getString(2));
                    sbOut.append("." ).append(rs.getString(3));
                    sbOut.append(" = ");
                    sbOut.append(rs.getString(4));
                    sbOut.append("&nbsp;&nbsp;&nbsp;Module:  ");
                    sbOut.append(rs.getString(5));
                    sbOut.append("&nbsp;&nbsp;&nbsp;Container:  ");
                    sbOut.append(rs.getString(6));
                    sbOut.append("\n");
                }
            });

            return sbOut.toString();
        }
    }
}
