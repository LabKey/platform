/*
 * Copyright (c) 2004-2012 Fred Hutchinson Cancer Research Center
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

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.ms2.MS2Service;
import org.labkey.api.resource.Resource;
import org.labkey.api.resource.ResourceRef;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.TestContext;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
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

    private final String _name;
    private final DbScope _scope;
    private final boolean _moduleSchema;
    private final Map<String, TableType> _tableXmlMap = new CaseInsensitiveHashMap<TableType>();
    private final Map<String, String> _metaDataTableNames = new CaseInsensitiveHashMap<String>();  // Union of all table names from database and schema.xml

    private ResourceRef _resourceRef = null;

    public static @NotNull DbSchema get(String schemaName)
    {
        return DbScope.getLabkeyScope().getSchema(schemaName);
    }

    public static Resource getSchemaResource(String schemaName) throws IOException
    {
        Module module = ModuleLoader.getInstance().getModuleForSchemaName(schemaName);
        if (null == module)
        {
            _log.debug("no module for schema '" + schemaName + "'");
            return null;
        }
        Resource r = module.getModuleResource("/schemas/" + schemaName + ".xml");
        return null != r && r.isFile() ? r : null;
    }


    public static DbSchema createFromMetaData(String dbSchemaName) throws SQLException, NamingException, ServletException
    {
        return createFromMetaData(dbSchemaName, DbScope.getLabkeyScope());
    }


    public static @NotNull DbSchema createFromMetaData(String schemaName, DbScope scope) throws SQLException
    {
        DbSchema schema = new DbSchema(schemaName, scope);
        schema.loadMetaData();
        scope.invalidateAllTables(schemaName); // Need to invalidate the table cache

        return schema;
    }


    private DbSchema(String name, DbScope scope)
    {
        _name = name;
        _scope = scope;
        _moduleSchema = scope.isModuleSchema(name);
    }


    private void loadMetaData() throws SQLException
    {
        TableMetaDataLoader loader = new TableMetaDataLoader("%") {
            @Override
            protected void handleTable(String name, ResultSet rs, DatabaseMetaData dbmd) throws SQLException
            {
                _metaDataTableNames.put(name, name);
            }
        };

        loader.load();
    }


    // Base class that pulls table meta data from the database, based on a supplied table pattern.  This lets us share
    // code between schema load (when we capture just the table names for all tables) and table load (when we capture
    // all properties of just a single table).  We want consistent transaction, exception, and filtering behavior in
    // both cases.
    private abstract class TableMetaDataLoader
    {
        private final String _tableNamePattern;

        private TableMetaDataLoader(String tableNamePattern)
        {
            _tableNamePattern = tableNamePattern;
        }

        protected abstract void handleTable(String name, ResultSet rs, DatabaseMetaData dbmd) throws SQLException;

        void load() throws SQLException
        {
            DbScope scope = getScope();
            String dbName = scope.getDatabaseName();

            // Remember if we're using a connection that somebody lower on the call stack checked out,
            // and therefore shouldn't close it out from under them
            boolean inTransaction = scope.isTransactionActive();
            Connection conn = null;

            try
            {
                conn = scope.getConnection();
                DatabaseMetaData dbmd = conn.getMetaData();

                String[] types = {"TABLE", "VIEW",};

                ResultSet rs;

                if (getSqlDialect().treatCatalogsAsSchemas())
                    rs = dbmd.getTables(getName(), null, _tableNamePattern, types);
                else
                    rs = dbmd.getTables(dbName, getName(), _tableNamePattern, types);

                try
                {
                    while (rs.next())
                    {
                        String tableName = rs.getString("TABLE_NAME").trim();

                        // Ignore system tables
                        if (getSqlDialect().isSystemTable(tableName))
                            continue;

                        // skip if it looks like one of our temp table names: name$<32hexchars>
                        if (tableName.length() > 33 && tableName.charAt(tableName.length()-33) == '$')
                            continue;

                        handleTable(tableName, rs, dbmd);
                    }
                }
                finally
                {
                    ResultSetUtil.close(rs);
                }
            }
            catch (SQLException e)
            {
                _log.error("Exception loading schema \"" + getName() + "\" from database metadata", e);
                throw e;
            }
            finally
            {
                try
                {
                    if (!inTransaction && null != conn) scope.releaseConnection(conn);
                }
                catch (Exception x)
                {
                    _log.error("DbSchema.createFromMetaData()", x);
                }
            }
        }
    }


    @Nullable SchemaTableInfo loadTable(String tableName) throws SQLException
    {
        // When querying table metadata we must use the name from the database
        String metaDataTableName = _metaDataTableNames.get(tableName);

        // Didn't find a hard table with that name... maybe it's a query.  See #12822
        if (null == metaDataTableName)
            return null;

        SchemaTableInfo ti = createTableFromDatabaseMetaData(metaDataTableName);
        TableType xmlTable = _tableXmlMap.get(tableName);

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
            ti.setLocked(true);
        return ti;
    }


    SchemaTableInfo createTableFromDatabaseMetaData(final String tableName) throws SQLException
    {
        SingleTableMetaDataLoader loader = new SingleTableMetaDataLoader(tableName);

        loader.load();

        return loader.getTableInfo();
    }


    private class SingleTableMetaDataLoader extends TableMetaDataLoader
    {
        private final String _tableName;
        private SchemaTableInfo _ti = null;

        private SingleTableMetaDataLoader(String tableName)
        {
            super(tableName);
            _tableName = tableName;
        }

        @Override
        protected void handleTable(String name, ResultSet rs, DatabaseMetaData dbmd) throws SQLException
        {
            assert _tableName.equalsIgnoreCase(name);
            DatabaseTableType tableType = DatabaseTableType.valueOf(DatabaseTableType.class, rs.getString("TABLE_TYPE"));
            _ti = new SchemaTableInfo(DbSchema.this, tableType, _tableName);
            String description = rs.getString("REMARKS");
            if (null != description && !"No comments".equals(description))  // Consider: Move "No comments" exclusion to SAS dialect?
                _ti.setDescription(description);
        }

        private SchemaTableInfo getTableInfo()
        {
            return _ti;
        }
    }


    public static Set<DbSchema> getAllSchemasToTest()
    {
        Set<DbSchema> schemas = new LinkedHashSet<DbSchema>();
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


    public String getName()
    {
        return _name;
    }

    public SqlDialect getSqlDialect()
    {
        return _scope.getSqlDialect();
    }

    boolean isStale()
    {
        assert _resourceRef != null;
        return _resourceRef.isStale() && _resourceRef.getResource().exists();
    }

    public boolean isModuleSchema()
    {
        return _moduleSchema;
    }

    Resource getResource()
    {
        return _resourceRef != null ? _resourceRef.getResource() : null;
    }

    void setResource(Resource r)
    {
        if (_resourceRef == null || _resourceRef.getResource() != r)
            _resourceRef = new ResourceRef(r);
        else
            _resourceRef.updateVersionStamp();
    }

    void setTablesDocument(TablesDocument tablesDoc)
    {
        TableType[] xmlTables = tablesDoc.getTables().getTableArray();

        for (TableType xmlTable : xmlTables)
        {
            String xmlTableName = xmlTable.getTableName();
            _tableXmlMap.put(xmlTable.getTableName(), xmlTable);

            // Tables in schema.xml but not in the database need to be added to _tableNames
            if (!_metaDataTableNames.containsKey(xmlTableName))
            {
                _metaDataTableNames.put(xmlTableName, xmlTableName);
            }
        }
    }


    public Collection<String> getTableNames()
    {
        return Collections.unmodifiableCollection(new LinkedList<String>(_metaDataTableNames.keySet()));
    }

    public SchemaTableInfo getTable(String tableName)
    {
        // Scope holds cache for all its tables
        return _scope.getTable(this, tableName);
    }

    public DbScope getScope()
    {
        return _scope;
    }

    public void dropTableIfExists(String objName) throws SQLException
    {
        getSqlDialect().dropIfExists(this, objName, "TABLE", null);
        getSqlDialect().dropIfExists(this, objName, "VIEW", null);
    }

    public void dropIndexIfExists(String objName, String indexName) throws SQLException
    {
        getSqlDialect().dropIfExists(this, objName, "INDEX", indexName);
    }


    @Override
    public String toString()
    {
        return "DbSchema " + getName();
    }

    public static class TestCase extends Assert
    {
        // Compare schema XML vs. meta data for all module schemas
        @Test
        public void testSchemaXML() throws Exception
        {
            Set<DbSchema> schemas = DbSchema.getAllSchemasToTest();

            for (DbSchema schema : schemas)
                testSchemaXml(schema);
        }


        // Do a simple select from every table in every module schema. This ends up invoking validation code
        // in Table that checks PKs and columns, further validating the schema XML file.
        @Test
        public void testTableSelect() throws Exception
        {
            Set<DbSchema> schemas = DbSchema.getAllSchemasToTest();

            for (DbSchema schema : schemas)
            {
                for (String tableName : schema.getTableNames())
                {
                    TableInfo table = schema.getTable(tableName);

                    if (table.getTableType() == DatabaseTableType.NOT_IN_DB)
                        continue;

                    TableSelector selector = new TableSelector(table);
                    selector.setMaxRows(10);
                    selector.getCollection(Map.class);
                }
            }
        }


        private void testSchemaXml(DbSchema schema) throws Exception
        {
            String sOut = TableXmlUtils.compareXmlToMetaData(schema.getName(), false, false);

            assertNull("<div>Errors in schema " + schema.getName()
                     + ".xml.  <a href=\"" + AppProps.getInstance().getContextPath() + "/admin/getSchemaXmlDoc.view?dbSchema="
                     + schema.getName() + "\">Click here for an XML doc with fixes</a>."
                     + "<br>"
                     + sOut + "</div>", sOut);

/* TODO: Uncomment once we change to all generic type names in schema .xml files

            StringBuilder typeErrors = new StringBuilder();

            for (TableInfo ti : schema.getTables())
            {
                for (ColumnInfo ci : ti.getColumns())
                {
                    String sqlTypeName = ci.getSqlTypeName();

                    if ("OTHER".equals(sqlTypeName))
                        typeErrors.append(ti.getName()).append(".").append(ci.getColumnName()).append(": getSqlTypeName() returned 'OTHER'<br>");

                    int sqlTypeInt = ci.getSqlTypeInt();

                    if (Types.OTHER == sqlTypeInt)
                        typeErrors.append(ti.getName()).append(".").append(ci.getColumnName()).append(": getSqlTypeInt() returned 'Types.OTHER'<br>");
                }
            }

            assertTrue("<div>Type errors in schema " + schema.getName() + ":<br><br>" + typeErrors + "<div>", "".equals(typeErrors.toString()));
*/
        }

        @Test
        public void testTransactions() throws Exception
        {
            TestSchema test = TestSchema.getInstance();
            DbSchema testSchema = test.getSchema();
            TableInfo testTable = test.getTableInfoTestTable();
            TestContext ctx = TestContext.get();

            assertNotNull(testTable);

            assertFalse("In transaction when shouldn't be.", testSchema.getScope().isTransactionActive());

            Map<String, Object> m = new HashMap<String, Object>();
            m.put("DatetimeNotNull", new Date());
            m.put("BitNotNull", Boolean.TRUE);
            m.put("Text", "Added by Transaction Test Suite");
            m.put("IntNotNull", 0);
            m.put("Container", JunitUtil.getTestContainer());

            Integer rowId;
            testSchema.getScope().beginTransaction();
            assertTrue("Not in transaction when should be.", testSchema.getScope().isTransactionActive());
            m = Table.insert(ctx.getUser(), testTable, m);
            rowId = ((Integer) m.get("RowId"));
            assertNotNull("Inserted Row doesn't have Id", rowId);
            assertTrue(rowId != 0);

            testSchema.getScope().commitTransaction();
            assertFalse("In transaction when shouldn't be.", testSchema.getScope().isTransactionActive());

            SimpleFilter filter = new SimpleFilter("RowId", rowId);
            ResultSet rs = Table.select(testTable, Table.ALL_COLUMNS, filter, null);
            assertTrue("Did not find inserted record.", rs.next());
            rs.close();

            testSchema.getScope().beginTransaction();
            m.put("IntNotNull", 1);
            m = Table.update(ctx.getUser(), testTable, m, rowId);
            assertTrue("Update is consistent in transaction?", (Integer) m.get("IntNotNull") == 1);
            testSchema.getScope().closeConnection();

            //noinspection unchecked
            Map<String, Object>[] maps = (Map<String, Object>[]) Table.select(testTable, Table.ALL_COLUMNS, filter, null, Map.class);
            assertTrue(maps.length == 1);
            m = maps[0];

            assertTrue("Rollback did not appear to work.", (Integer) m.get("IntNotNull") == 0);

            Table.delete(testTable, rowId);
        }

        @Test
        public void testCaching() throws Exception
        {
            TestSchema test = TestSchema.getInstance();
            DbSchema testSchema = test.getSchema();
            TableInfo testTable = test.getTableInfoTestTable();
            TestContext ctx = TestContext.get();

            assertNotNull(testTable);
            DbCache.clear(testTable);

            Map<String, Object> m = new HashMap<String, Object>();
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
            testSchema.getScope().beginTransaction();
            m = Table.insert(ctx.getUser(), testTable, m);
            int rowId4 = ((Integer) m.get("RowId"));
            String key2 = "RowId" + rowId4;
            DbCache.put(testTable, key2, m);
            testSchema.getScope().closeConnection();
            m2 = (Map) DbCache.get(testTable, key2);
            assertNull(m2);

            // Clean up
            Table.delete(testTable, rowId2);
            Table.delete(testTable, rowId3);
        }

        @Test
        public void testDDLMethods() throws Exception
        {
            TestSchema test = TestSchema.getInstance();
            DbSchema testSchema = test.getSchema();
            String tempTableName = testSchema.getSqlDialect().getTempTablePrefix() + "TTemp";

            // create test objects
            //start with cleanup
            testSchema.getSqlDialect().dropSchema(testSchema,"testdrop");
            testSchema.getSqlDialect().dropSchema(testSchema,"testdrop2");
            testSchema.getSqlDialect().dropSchema(testSchema,"testdrop3");
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

            testSchema = DbSchema.createFromMetaData("testdrop");

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
    }

    private static Integer checkContainerColumns(String dbSchemaName, SQLFragment sbSqlCmd, String tempTableName, String moduleName, Integer rowId) throws SQLException
    {
        int row = rowId;
        DbSchema curSchema = DbSchema.get(dbSchemaName);
        SQLFragment sbSql = new SQLFragment();

        for (String tableName : curSchema.getTableNames())
        {
            TableInfo t = curSchema.getTable(tableName);

            if (t.getTableType()!= DatabaseTableType.TABLE)
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
                        sbSql.append( " AND Run IN (SELECT Run FROM " + MS2Service.get().getRunsTableName() +
                                " WHERE Deleted = ? ) ");
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

    public static String checkAllContainerCols(boolean bfix) throws SQLException
    {
        List<Module> modules = ModuleLoader.getInstance().getModules();
        ResultSet rs1 = null;
        Integer lastRowId = 0;
        DbSchema coreSchema = CoreSchema.getInstance().getSchema();

        List<ColumnInfo> listColInfos = new ArrayList<ColumnInfo>();
        ColumnInfo col = new ColumnInfo("RowId");
        col.setSqlTypeName("INT");
        col.setNullable(false);
        listColInfos.add(col);

        TempTableInfo tTemplate = new TempTableInfo(coreSchema, "cltmp", listColInfos, Collections.singletonList("RowId"));
        String tempTableName = tTemplate.getTempTableName();

        String createTempTableSql =
                "CREATE TABLE " + tempTableName + " ( " +
                        "\tRowId INT NOT NULL,  \n" +
                        "\tTableName VARCHAR(300) NOT NULL,\n" +
                        "\tFirstPKColName VARCHAR(100) NULL,\n" +
                        "\tFirstPKValue VARCHAR(100) NULL,\n" +
                        "\tModuleName VARCHAR(50) NOT NULL,\n" +
                        "\tOrphanedContainer VARCHAR(60) NULL) ;\n\n";

        StringBuilder sbOut = new StringBuilder();

        try
        {
            SQLFragment sbCheck = new SQLFragment();

            for (Module module : modules)
            {
                Set<DbSchema> schemas = module.getSchemasToTest();

                for (DbSchema schema : schemas)
                    lastRowId = checkContainerColumns(schema.getName(), sbCheck, tempTableName, module.getName(), lastRowId);
            }

            tTemplate.track();
            new SqlExecutor(coreSchema).execute(createTempTableSql);
            new SqlExecutor(coreSchema).execute(sbCheck);

            if (bfix)
            {
                // create a recovered objects project
                Random random = new Random();
                int r = random.nextInt();
                String cName = "/_RecoveredObjects" +  String.valueOf(r).substring(1,5);
                Container recovered = ContainerManager.ensureContainer(cName);

                Set<Module> modulesOfOrphans = new HashSet<Module>();

                rs1 = Table.executeQuery(coreSchema, "SELECT TableName, OrphanedContainer, ModuleName FROM " + tempTableName
                        + " WHERE OrphanedContainer IS NOT NULL GROUP BY TableName, OrphanedContainer, ModuleName", new Object[]{});

                while (rs1.next())
                {
                    modulesOfOrphans.add(ModuleLoader.getInstance().getModule(rs1.getString(3)));
                    String sql = "UPDATE " + rs1.getString(1) + " SET Container = ? WHERE Container = ?";

                    try
                    {
                        Table.execute(coreSchema, sql, recovered.getId(), rs1.getString(2));
                        //remove the ACLs that were there
                        SecurityPolicyManager.removeAll(recovered);
                        sbOut.append("<br> Recovered objects from table ");
                        sbOut.append(rs1.getString(1));
                        sbOut.append(" to project ");
                        sbOut.append(recovered.getName());
                    }
                    catch (SQLException se)
                    {
                        sbOut.append("<br> Failed attempt to recover some objects from table ");
                        sbOut.append(rs1.getString(1));
                        sbOut.append(" due to error ").append(se.getMessage());
                        sbOut.append(". Retrying recovery may work.  ");
                    }
                }

                recovered.setActiveModules(modulesOfOrphans);

                return sbOut.toString();
            }
            else
            {
                rs1 = Table.executeQuery(coreSchema, " SELECT * FROM " + tempTableName
                        + " WHERE OrphanedContainer IS NOT NULL ORDER BY 1,3 ;", new Object[]{});

                while (rs1.next())
                {
                    sbOut.append("<br/>&nbsp;&nbsp;&nbsp;ERROR:  ");
                    sbOut.append(rs1.getString(1));
                    sbOut.append(" &nbsp;&nbsp;&nbsp;&nbsp; ");
                    sbOut.append(rs1.getString(2));
                    sbOut.append(" = ");
                    sbOut.append(rs1.getString(3));
                    sbOut.append("&nbsp;&nbsp;&nbsp;Container:  ");
                    sbOut.append(rs1.getString(5));
                    sbOut.append("\n");
                }

                return sbOut.toString();
            }
        }
        finally
        {
            ResultSetUtil.close(rs1);
        }
    }
}
