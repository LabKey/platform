/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.ms2.MS2Service;
import org.labkey.api.util.*;
import org.labkey.common.util.Pair;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;

import javax.naming.*;
import javax.servlet.ServletException;
import javax.sql.DataSource;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class DbSchema
{
    private static final HashMap<String, DbSchema> loadedSchemas = new HashMap<String, DbSchema>();
    private static Logger _log = Logger.getLogger(DbSchema.class);
    private static final Map<String, DbScope> scopes = new HashMap<String, DbScope>();

    private Map<String, SchemaTableInfo> tables = new CaseInsensitiveHashMap<SchemaTableInfo>();
    private DbScope scope = null;
    private String name = null;
    private SqlDialect sqlDialect;
    private String _catalog;
    private String _owner;  // also called "schema" in sql
    private long _schemaXmlTimestamp = -1;

    // Stash database config info
    private String _URL;
    private String _databaseProductName;
    private String _databaseProductVersion;
    private String _driverName;
    private String _driverVersion;

    public static DbSchema get(String schemaName)
    {
        // synchronized ensures one thread at a time.  This assert detects same-thread re-entrancy (e.g., the schema
        // load process directly or indirectly causing another call to this method.)
        assert !Thread.holdsLock(loadedSchemas) : "Schema load re-entrancy detected";

        synchronized (loadedSchemas)
        {
            DbSchema schema = loadedSchemas.get(schemaName);

            if (null != schema && !AppProps.getInstance().isDevMode())
                return schema;

            InputStream xmlStream = null;

            try
            {
                Pair<InputStream, Long> pair;

                if (null == schema)
                {
                    pair = getXmlStreamIfChanged(schemaName, -1);
                }
                else
                {
                    long tsPrevious = schema.getSchemaXmlTimestamp();

                    if (-1 == tsPrevious)
                        return schema;

                    pair = getXmlStreamIfChanged(schemaName, tsPrevious);

                    if (null == pair)
                        return schema;
                }

                schema = createFromMetaData(schemaName);
                if (null != schema)
                {
                    if (pair != null)
                    {
                        xmlStream = pair.first;
                        schema.setSchemaXmlTimestamp(pair.second);
                        if (null != xmlStream)
                        {
                            TablesDocument tablesDoc = TablesDocument.Factory.parse(xmlStream);
                            schema.loadXml(tablesDoc, true);
                        }
                    }
                    loadedSchemas.put(schema.getName(), schema);
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);  // Changed from "return null" to "throw runtimeexception" so admin is made aware of the cause of the problem
            }
            finally
            {
                try
                {
                    if (null != xmlStream) xmlStream.close();
                }
                catch (Exception x)
                {
                    _log.error("DbSchema.get()", x);
                }
            }

            return schema;
        }
    }

    public static InputStream getSchemaXmlStream(String schemaName) throws FileNotFoundException
    {
        Module module = getModuleForSchemaName(schemaName);
        if (null == module)
        {
            _log.info("no module for schema '" + schemaName + "'");
            return null;
        }
        return module.getResourceStream("/META-INF/" + schemaName + ".xml");
    }


    public static Pair<InputStream, Long> getXmlStreamIfChanged(String schemaName, long tsPrevious) throws FileNotFoundException
    {
        Module module = getModuleForSchemaName(schemaName);
        if (null == module)
        {
            _log.info("no module for schema '" + schemaName + "'");
            return null;
        }
        return module.getResourceStreamIfChanged("/META-INF/" + schemaName + ".xml", tsPrevious);
    }


    private static Module getModuleForSchemaName(String schemaName)
    {
        Module module = ModuleLoader.getInstance().getModuleForSchemaName(schemaName);
        return module;
    }


    private static DbScope getDbScope(String dsName) throws NamingException
    {
        DbScope scope;

        synchronized (scopes)
        {
            scope = scopes.get(dsName);
            if (null == scope)
            {
                scope = new DbScope(dsName);
                scopes.put(dsName, scope);
            }
        }

        return scope;
    }


    public DataSource getDataSource()
    {
        return getScope().getDataSource();
    }


    public static Map<String, DbScope> getDbScopes()
    {
        return scopes;
    }


    public String getDataSourceName()
    {
        return getScope().getDataSourceName();
    }


    public static Set<String> getNames()
    {
        Properties props = getDbSchemaProperties();
        Set<String> set = new HashSet<String>(props.size());

        for (Object key : props.keySet())
            set.add((String)key);

        return set;
    }

    private static Properties getDbSchemaProperties()
    {
        Properties result = new Properties();

        try
        {
            InitialContext ctx = new InitialContext();
            Context envCtx = (Context) ctx.lookup("java:comp/env");
            CompositeName rootName = new CompositeName("dbschema");
            NamingEnumeration<NameClassPair> e = envCtx.list(rootName);

            while (e.hasMoreElements())
            {
                NameClassPair pair = e.next();
                String key = pair.getName();
                String schemaInfo = (String) envCtx.lookup("dbschema/" + key);
                result.setProperty(key, schemaInfo);
            }
        }
        catch (NamingException e)
        {
            _log.error("Problem getting dbschemas out of JNDI", e);
        }
        return result;
    }

    public static DbSchema getDbSchema(String catalog, String owner)
    {
        Properties props = getDbSchemaProperties();
        String schemaInfo;
        String [] schemaStrings;
        String catalogName = null;
        String ownerName = "dbo";
        String key;
        for (Object o : props.keySet())
        {
            key = (String) o;
            schemaInfo = (String) props.get(key);
            schemaStrings = schemaInfo.split(",");
            if (schemaStrings.length > 1 && schemaStrings[1].trim().length() > 0)
                catalogName = schemaStrings[1].trim();

            if (schemaStrings.length > 2 && schemaStrings[2].trim().length() > 0)
                ownerName = schemaStrings[2].trim();

            if (catalog.equals(catalogName) && owner.equals(ownerName))
                return get(key);
        }
        return null;
    }

    /*
        public static DbSchema createFromXml(String name, File xmlFile) throws Exception
            {
            return createFromXml(name, TablesDocument.Factory.parse(xmlFile));
            }


        public static DbSchema createFromXml(String name, TablesDocument tablesDoc) throws Exception
            {
            DbSchema schema = new DbSchema(name);
            schema.loadXml(tablesDoc, false);
            return schema;
            }
    */
    public static DbSchema createFromMetaData(String dbSchemaName) throws SQLException, NamingException, ServletException
    {
        DbSchema dbSchema;
        Properties props = getDbSchemaProperties();
        String schemaInfo = props.getProperty(dbSchemaName);
        if (null == schemaInfo)
        {
            schemaInfo = props.getProperty("--default--") + "," + dbSchemaName;
        }

        String[] schemaStrings = schemaInfo.split(",");
        String dsName = schemaStrings[0];
        String ownerName = "dbo";

        DbScope scope = getDbScope(dsName);
        String dbName = SqlDialect.getDatabaseName2(scope.getDataSource());

        // Support old format that included catalog
        if (3 == schemaStrings.length)
        {
            String catalogName = schemaStrings[1].trim();
            ownerName = schemaStrings[2].trim();

            if (!dbName.equalsIgnoreCase(catalogName))
            {
                String error = "Catalog name \"" + catalogName + "\" specified in \"" + dbSchemaName + "\" configuration doesn't match database name \"" + dbName + "\" specified in the corresponding datasource \"" + dsName + "\".\n";
                error = error + "This mismatch means meta data will be read from one database and all database operations will be directed to a different database.  Review your settings in labkey.xml or cpas.xml.";
                throw new ServletException(error);
            }
        }

        // New format doesn't specify catalog
        if (2 == schemaStrings.length)
        {
            ownerName = schemaStrings[1].trim();
        }

        dbSchema = createFromMetaData(dbSchemaName, scope, dbName, ownerName);
        scope.setSqlDialect(dbSchema.getSqlDialect());
        return dbSchema;
    }

    private static DatabaseMetaData _dbmdLast;

    private static DbSchema createFromMetaData(String name, DbScope scope, String catalogName, String schemaName) throws SQLException, SqlDialect.SqlDialectNotSupportedException
    {
        Connection conn = null;
        DbSchema schema = new DbSchema(name);
        schema._catalog = catalogName;
        schema._owner = schemaName;
        schema.scope = scope;

        long startLoad = System.currentTimeMillis();

        try
        {
            DataSource ds = scope.getDataSource();
            conn = ds.getConnection();
            DatabaseMetaData dbmd = conn.getMetaData();
            schema.sqlDialect = SqlDialect.getFromMetaData(dbmd);

            if (_dbmdLast != null && _dbmdLast.getURL().equals(dbmd.getURL()) &&
                    _dbmdLast.getDatabaseProductName().equals(dbmd.getDatabaseProductName()) &&
                    _dbmdLast.getDatabaseProductVersion().equals(dbmd.getDatabaseProductVersion()) &&
                    _dbmdLast.getDriverName().equals(dbmd.getDriverName()) &&
                    _dbmdLast.getDriverVersion().equals(dbmd.getDriverVersion()))
            {
                _log.info("Loading DbSchema \"" + name + "\"");
            }
            else
            {
                _log.info("Loading DbSchema \"" + name + "\" using the following configuration:" +
                        "\n    Server URL:               " + dbmd.getURL() +
                        "\n    Database Product Name:    " + dbmd.getDatabaseProductName() +
                        "\n    Database Product Version: " + dbmd.getDatabaseProductVersion() +
                        "\n    JDBC Driver Name:         " + dbmd.getDriverName() +
                        "\n    JDBC Driver Version:      " + dbmd.getDriverVersion());
                _dbmdLast = dbmd;
            }

            schema.setURL(dbmd.getURL());
            schema.setDatabaseProductName(dbmd.getDatabaseProductName());
            schema.setDatabaseProductVersion(dbmd.getDatabaseProductVersion());
            schema.setDriverName(dbmd.getDriverName());
            schema.setDriverVersion(dbmd.getDriverVersion());

            String[] types = {"TABLE", "VIEW",};
            ResultSet rs = dbmd.getTables(catalogName, schemaName, "%", types);
            ArrayList<SchemaTableInfo> list = new ArrayList<SchemaTableInfo>();

            try
            {
                while (rs.next())
                {
                    String metaDataName = rs.getString("TABLE_NAME");

                    // Ignore system tables
                    if (schema.sqlDialect.isSystemTable(metaDataName))
                        continue;

                    // skip if it looks like one of our temp table names: name$<32hexchars>
                    if (metaDataName.length() > 33 && metaDataName.charAt(metaDataName.length()-33) == '$')
                        continue;

                    SchemaTableInfo ti = new SchemaTableInfo(metaDataName, schema);
                    ti.setMetaDataName(metaDataName);
                    ti.setTableType(rs.getString("TABLE_TYPE"));
                    list.add(ti);
                }
            }
            finally
            {
                ResultSetUtil.close(rs);
            }

            for (SchemaTableInfo ti : list)
            {
                ti.loadFromMetaData(dbmd, catalogName, schemaName);
                schema.tables.put(ti.getName(), ti);
            }

            schema.sqlDialect.prepareNewDbSchema(schema);
        }
        catch (SQLException e)
        {
            _log.log(Priority.ERROR, "Exception loading schema \"" + name + "\" from database metadata", e);
            throw e;
        }
        finally
        {
            try
            {
                if (null != conn) conn.close();
            }
            catch (Exception x)
            {
                _log.error("DbSchema.createFromMetaData()", x);
            }
        }

        _log.debug("" + schema.getTables().length + " tables loaded in " + DateUtil.formatDuration(System.currentTimeMillis()-startLoad));
        return schema;
    }


    public static void rollbackAllTransactions()
    {
        for (DbScope scope : scopes.values())
        {
            if (scope.isTransactionActive())
                try
                {
                    scope.rollbackTransaction();
                }
                catch (Exception x)
                {
                    _log.error("Rollback All Transactions", x);
                }
        }
    }

    public static void invalidateSchemas()
    {
        synchronized (loadedSchemas)
        {
            loadedSchemas.clear();
            scopes.clear();
        }
    }


    public static void invalidateSchema(String schemaName)
    {
        synchronized (loadedSchemas)
        {
            loadedSchemas.remove(schemaName);
        }
    }

    public DbSchema(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public String getCatalog()
    {
        return _catalog;
    }

    public String getOwner()
    {
        return _owner;
    }

    public SqlDialect getSqlDialect()
    {
        return sqlDialect;
    }

    private long getSchemaXmlTimestamp()
    {
        return _schemaXmlTimestamp;
    }

    public void setSchemaXmlTimestamp(long schemaXmlTimestamp)
    {
        _schemaXmlTimestamp = schemaXmlTimestamp;
    }

    /* unused
      public void loadXml (File xmlFile, boolean merge) throws Exception
          {
          TablesDocument tablesDoc = TablesDocument.Factory.parse(xmlFile);
          loadXml (tablesDoc, merge);
          }
    */
    public void loadXml(TablesDocument tablesDoc, boolean merge) throws Exception
    {
        TableType[] xmlTables = tablesDoc.getTables().getTableArray();
        for (TableType xmlTable : xmlTables)
        {
            SchemaTableInfo tableInfo;

            if (merge)
            {
                tableInfo = getTable(xmlTable.getTableName());
                if (null == tableInfo)
                {
                    tableInfo = new SchemaTableInfo(xmlTable.getTableName(), this);
                    tableInfo.setTableType(TableInfo.TABLE_TYPE_NOT_IN_DB);

                    /*              We now allow this. Admin tools can create if necessary
                                    _log.warn("Table " + xmlTables[i].getTableName() + " not found in database.");
                                    continue;
                    */
                }
            }
            else
                tableInfo = new SchemaTableInfo(xmlTable.getTableName(), this);

            tableInfo.loadFromXml(xmlTable, merge);
            this.tables.put(tableInfo.getName(), tableInfo);
        }
    }


    public SchemaTableInfo[] getTables()
    {
        return tables.values().toArray(new SchemaTableInfo[tables.size()]);
    }

    public SchemaTableInfo getTable(String tableName)
    {
        return tables.get(tableName);
    }

    public TableInfo[] getTables(String tableNames)
    {
        String[] names = tableNames.split(",");
        TableInfo[] tables = new TableInfo[names.length];
        for (int i = 0; i < names.length; i++)
            tables[i] = getTable(names[i]);

        return tables;
    }

    public void writeCreateTableSql(Writer out) throws IOException
    {
        SchemaTableInfo[] tableArray = getTables();
        for (SchemaTableInfo aTableArray : tableArray)
        {
            aTableArray.writeCreateTableSql(out);
        }
    }

    public void writeCreateConstraintsSql(Writer out) throws IOException
    {
        SchemaTableInfo[] tableArray = getTables();
        for (SchemaTableInfo aTableArray : tableArray)
        {
            aTableArray.writeCreateConstraintsSql(out);
        }
    }

    public void writeBeanSource(Writer out) throws IOException
    {
        SchemaTableInfo[] tableArray = getTables();
        for (SchemaTableInfo aTableArray : tableArray)
        {
            aTableArray.writeBean(out);
        }
    }

    public DbScope getScope()
    {
        return scope;
    }

    public String getURL()
    {
        return _URL;
    }

    public void setURL(String URL)
    {
        _URL = URL;
    }

    public String getDatabaseProductName()
    {
        return _databaseProductName;
    }

    public void setDatabaseProductName(String databaseProductName)
    {
        _databaseProductName = databaseProductName;
    }

    public String getDatabaseProductVersion()
    {
        return _databaseProductVersion;
    }

    public void setDatabaseProductVersion(String databaseProductVersion)
    {
        _databaseProductVersion = databaseProductVersion;
    }

    public String getDriverName()
    {
        return _driverName;
    }

    public void setDriverName(String driverName)
    {
        _driverName = driverName;
    }

    public String getDriverVersion()
    {
        return _driverVersion;
    }

    public void setDriverVersion(String driverVersion)
    {
        _driverVersion = driverVersion;
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


    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }

        // Ask each module for the schemas to test, then compare XML vs. meta data
        public void testKnownSchemas() throws Exception
        {
            Set<DbSchema> schemas = new HashSet<DbSchema>();
            List<Module> modules = ModuleLoader.getInstance().getModules();

            for (Module module : modules)
                schemas.addAll(module.getSchemasToTest());

            for (DbSchema schema : schemas)
                testSchemaXml(schema);
        }


        public void testSchemaXml(DbSchema schema) throws Exception
        {
             String sOut = TableXmlUtils.compareXmlToMetaData(schema.getName(), false, false);

             assertNull("<div>Errors in schema " + schema.getName()
                     + ".xml  <a href=\"" + AppProps.getInstance().getContextPath() + "/admin/getSchemaXmlDoc.view?dbSchema="
                     + schema.getName() + "\">&nbsp;&nbsp;&nbsp;Click here for an XML doc with fixes. </a> "
                     + "<br/>"
                     + sOut + "</div>", sOut);

        }

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
            m.put("Text", "Added by Test Suite");
            m.put("IntNotNull", 0);
            m.put("Container", ContainerManager.getForPath(ContainerManager.HOME_PROJECT_PATH).getId());

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
            m = Table.update(ctx.getUser(), testTable, m, rowId, null);
            assertTrue("Update is consistent in transaction?", (Integer) m.get("IntNotNull") == 1);
            testSchema.getScope().rollbackTransaction();

            //noinspection unchecked
            Map<String, Object>[] maps = (Map<String, Object>[]) Table.select(testTable, Table.ALL_COLUMNS, filter, null, Map.class);
            assertTrue(maps != null && maps.length == 1);
            m = maps[0];

            assertTrue("Rollback did not appear to work.", (Integer) m.get("IntNotNull") == 0);

        }

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
            m.put("Text", "Added by Test Suite");
            m.put("IntNotNull", 0);
            m.put("Container", ContainerManager.getForPath(ContainerManager.HOME_PROJECT_PATH).getId());
            m = Table.insert(ctx.getUser(), testTable, m);
            Integer rowId = ((Integer) m.get("RowId"));

            String key = "RowId" + rowId;
            DbCache.put(testTable, key, m);
            Map m2 = (Map) DbCache.get(testTable, key);
            assertEquals(m, m2);


            //Does cache get cleared on delete
            Table.delete(testTable, rowId, null);
            m2 = (Map) DbCache.get(testTable, key);
            assertNull(m2);

            //Does cache get cleard on insert
            m.remove("RowId");
            m = Table.insert(ctx.getUser(), testTable, m);
            rowId = ((Integer) m.get("RowId"));
            key = "RowId" + rowId;
            DbCache.put(testTable, key, m);
            m.remove("RowId");
            Table.insert(ctx.getUser(), testTable, m);
            m2 = (Map) DbCache.get(testTable, key);
            assertNull(m2);

            //Make sure things are not inserted in transaction
            m.remove("RowId");
            testSchema.getScope().beginTransaction();
            m = Table.insert(ctx.getUser(), testTable, m);
            rowId = ((Integer) m.get("RowId"));
            String key2 = "RowId" + rowId;
            DbCache.put(testTable, key2, m);
            testSchema.getScope().rollbackTransaction();
            m2 = (Map) DbCache.get(testTable, key2);
            assertNull(m2);
        }

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

            if (testSchema.getSqlDialect().isSqlServer())
            {
                Table.execute(testSchema, "EXEC sp_addapprole 'testdrop', 'password' ", new Object[]{});
                Table.execute(testSchema, "EXEC sp_addapprole 'testdrop2', 'password' ", new Object[]{});
                Table.execute(testSchema, "EXEC sp_addapprole 'testdrop3', 'password' ", new Object[]{});
            }
            else if (testSchema.getSqlDialect().isPostgreSQL())
            {
                Table.execute(testSchema, "create schema testdrop", new Object[]{});
                Table.execute(testSchema, "create schema testdrop2", new Object[]{});
                Table.execute(testSchema, "create schema testdrop3", new Object[]{});
            }
            else
                return;

            Table.execute(testSchema, "create table testdrop.T0 (c0 int not null primary key)", new Object[]{});
            Table.execute(testSchema, "create table testdrop.T (c1 char(1), fk_c0 int REFERENCES testdrop.T0(c0))", new Object[]{});
            Table.execute(testSchema, "create index T_c1 ON testdrop.T(c1)", new Object[]{});
            Table.execute(testSchema, "create view testdrop.V AS SELECT c1 FROM testdrop.T", new Object[]{});
            String sqlCreateTempTable = "create " + testSchema.getSqlDialect().getTempTableKeyword() + " table "
                                        + tempTableName + "(ctemp int)";
            Table.execute(testSchema, sqlCreateTempTable, new Object[]{});

            Table.execute(testSchema, "create table testdrop2.T0 (c0 int primary key)", new Object[]{});
            Table.execute(testSchema, "create table testdrop2.T (c1 char(10), fk_c0 int REFERENCES testdrop2.T0(c0))", new Object[]{});
            Table.execute(testSchema, "create table testdrop3.T (c1 char(10), fk_c0 int REFERENCES testdrop2.T0(c0))", new Object[]{});
            Table.execute(testSchema, "create index T_c1 ON testdrop2.T(c1)", new Object[]{});

            testSchema = DbSchema.createFromMetaData("testdrop");

            //these exist; ensure they are dropped by re-creating them
            testSchema.dropIndexIfExists("T", "T_c1");
            Table.execute(testSchema, "create index T_c1 ON testdrop.T(c1)", new Object[]{});

            testSchema.dropTableIfExists("v");
            Table.execute(testSchema, "create view testdrop.V AS SELECT c0 FROM testdrop.T0", new Object[]{});

            testSchema.dropTableIfExists("T");
            Table.execute(testSchema, "create table testdrop.T (c1 char(1))", new Object[]{});

            testSchema.dropTableIfExists(tempTableName);
            Table.execute(testSchema, sqlCreateTempTable, new Object[]{});

            testSchema.getSqlDialect().dropSchema(testSchema,"testdrop");

            // these don't exist
            testSchema.dropIndexIfExists("T",  "T_notexist") ;
            testSchema.dropTableIfExists("V1");
            testSchema.dropTableIfExists("Tnot");
            testSchema.getSqlDialect().dropSchema(testSchema,"testdrop");

            testSchema.getSqlDialect().dropSchema(testSchema,"testdrop2");
            testSchema.getSqlDialect().dropSchema(testSchema,"testdrop3");

        }

        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
    public static Integer checkContainerColumns(String dbSchemaName, SQLFragment sbSqlCmd, String tempTableName, String moduleName, Integer rowId ) throws SQLException
    {

        int row = rowId.intValue();
        DbSchema curSchema = DbSchema.get(dbSchemaName);
        TableInfo[] ta = curSchema.getTables();
        SQLFragment sbSql = new SQLFragment();

        for (TableInfo t : ta)
        {
            if (t.getTableType()!= TableInfo.TABLE_TYPE_TABLE)
                continue;
            for (ColumnInfo col : t.getColumns())
            {
                if (col.getName().equalsIgnoreCase("Container"))
                {
                    sbSql.append( " INSERT INTO "+ tempTableName );
                    sbSql.append(" SELECT " + String.valueOf(++row) + " AS rowId, '" + t.getFromSQL() + "' AS TableName, ");
                    List<String> pkColumnNames = t.getPkColumnNames();

                    if (pkColumnNames.size() == 1)
                    {
                        String pkColumnName = pkColumnNames.get(0);
                        sbSql.append(" '" + pkColumnName);
                        sbSql.append("' AS FirstPKColName, ");
                        sbSql.append(" CAST( " + t.getFromSQL() + "." + pkColumnName + " AS VARCHAR(100)) "
                                + " AS FirstPKValue ,");
                    }
                    else
                    {
                        String tmp = "unknown PK";
                        if (pkColumnNames.size() > 1)
                            tmp = "multiCol PK ";
                        if(t.getName().equals("ACLs"))
                            tmp = "objectid ";
                        sbSql.append(" '" + tmp + "' AS FirstPKColName, ");
                        sbSql.append(" NULL AS FirstPKValue ,");
                    }
                    sbSql.append(" '" + moduleName + "' AS ModuleName, ");
                    sbSql.append(" CAST( " + t.getFromSQL() + "." + col.getName() + " AS VARCHAR(100)) AS OrphanedContainer ");
                    sbSql.append(" FROM " + t.getFromSQL());
                    sbSql.append( " LEFT OUTER JOIN " + " core.Containers C ");
                    sbSql.append(" ON (" + t.getFromSQL() + ".Container = C.EntityId ) ");
                    sbSql.append( " WHERE C.EntityId IS NULL ");

                    // special handling of MS2 soft deletes
                    if (null!=t.getColumn("Deleted"))
                    {
                        sbSql.append( " AND Deleted = ? ");
                        sbSql.add(Boolean.FALSE);
                    }
                    else if (t.getSchema().getName().equals("ms2") && null!=t.getColumn("Run"))
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
        return new Integer(row);

    }

    public static String checkAllContainerCols(boolean bfix) throws SQLException
    {
        List<Module> modules = ModuleLoader.getInstance().getModules();
        ResultSet rs1 = null;
        Integer lastRowId = new Integer(0);
        DbSchema coreSchema = CoreSchema.getInstance().getSchema();

        List<ColumnInfo> listColInfos = new ArrayList<ColumnInfo>();
        ColumnInfo col = new ColumnInfo("RowId");
        col.setSqlTypeName("INT");
        col.setNullable(false);
        listColInfos.add(col);

        Table.TempTableInfo tTemplate = new Table.TempTableInfo(coreSchema, "cltmp", listColInfos, Collections.singletonList("RowId"));
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

        try {
            SQLFragment sbCheck = new SQLFragment();

            for (Module module : modules)
            {
                Set<DbSchema> schemas = module.getSchemasToTest();

                for (DbSchema schema : schemas)
                    lastRowId = checkContainerColumns(schema.getName(), sbCheck, tempTableName, module.getName(), lastRowId);
            }
            Table.execute(coreSchema, createTempTableSql, new Object[]{});
            tTemplate.track();
            Table.execute(coreSchema, sbCheck.toString(), sbCheck.getParams().toArray());

            if (bfix)
            {
                // create a recovered objects project
                Random random = new Random();
                int r = random.nextInt();
                String cName = "/_RecoveredObjects" +  String.valueOf(r).substring(1,5);
                Container recovered = ContainerManager.ensureContainer(cName);

                Set<Module> modulesOfOrphans = new HashSet<Module>();

                rs1 = Table.executeQuery(coreSchema," SELECT TableName, OrphanedContainer, ModuleName FROM " + tempTableName
                        + " WHERE OrphanedContainer IS NOT NULL GROUP BY TableName, OrphanedContainer, ModuleName" +
                        " ;",new Object[]{});
                while (rs1.next())
                {
                    modulesOfOrphans.add(ModuleLoader.getInstance().getModule(rs1.getString(3)));

                    String sql = "UPDATE " + rs1.getString(1) +" SET Container = ? WHERE Container = ? ";
                    try
                    {
                        Table.execute(coreSchema, sql, new Object[]{recovered.getId(), rs1.getString(2)});
                        //remove the ACLs that were there
                        org.labkey.api.security.SecurityManager.removeAll(recovered);
                        sbOut.append("<br> Recovered objects from table ");
                        sbOut.append(rs1.getString(1));
                        sbOut.append(" to project ");
                        sbOut.append(recovered.getName());
                    }
                    catch (SQLException se)
                    {
                        sbOut.append("<br> Failed attempt to recover some objects from table ");
                        sbOut.append(rs1.getString(1));
                        sbOut.append(" due to error " + se.getMessage());
                        sbOut.append(". Retrying recovery may work.  ");
                    }
                }
                recovered.setActiveModules(modulesOfOrphans);
                return sbOut.toString();

            }
            else
            {
                rs1 = Table.executeQuery(coreSchema," SELECT * FROM " + tempTableName
                        + " WHERE OrphanedContainer IS NOT NULL ORDER BY 1,3 ;",new Object[]{});
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
