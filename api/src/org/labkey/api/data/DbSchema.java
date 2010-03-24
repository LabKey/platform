/*
 * Copyright (c) 2004-2010 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.ms2.MS2Service;
import org.labkey.api.resource.Resource;
import org.labkey.api.resource.ResourceRef;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.*;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;

import javax.naming.*;
import javax.servlet.ServletException;
import javax.sql.DataSource;
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
    private static final HashMap<String, DbSchema> _loadedSchemas = new HashMap<String, DbSchema>();
    private static final Logger _log = Logger.getLogger(DbSchema.class);

    private final Map<String, SchemaTableInfo> _tables = new CaseInsensitiveHashMap<SchemaTableInfo>();
    private final DbScope _scope;
    private final String _name;
    private final String _owner;  // also called "schema" in sql

    private ResourceRef _resourceRef = null;

    public static DbSchema get(String schemaName)
    {
        return get(schemaName, DbScope.getLabkeyScope());
    }

    public static DbSchema get(String schemaName, DbScope scope)
    {
        // synchronized ensures one thread at a time.  This assert detects same-thread re-entrancy (e.g., the schema
        // load process directly or indirectly causing another call to this method.)
        assert !Thread.holdsLock(_loadedSchemas) : "Schema load re-entrancy detected";

        synchronized (_loadedSchemas)
        {
            DbSchema schema = _loadedSchemas.get(schemaName);

            if (null != schema && !AppProps.getInstance().isDevMode())
                return schema;

            InputStream xmlStream = null;

            try
            {
                Resource resource = null;
                if (null == schema)
                {
                    resource = getSchemaResource(schemaName);
                }
                else
                {
                    if (AppProps.getInstance().isDevMode() && schema.isStale())
                    {
                        resource = schema.getResource();
                    }
                    else
                    {
                        return schema;
                    }
                }

                schema = createFromMetaData(schemaName, scope);

                if (null != schema)
                {
                    if (resource != null)
                    {
                        schema.setResource(resource);
                        xmlStream = resource.getInputStream();
                        if (null != xmlStream)
                        {
                            TablesDocument tablesDoc = TablesDocument.Factory.parse(xmlStream);
                            schema.loadXml(tablesDoc, true);
                        }
                    }

                    _loadedSchemas.put(schema.getName(), schema);
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

    public static Resource getSchemaResource(String schemaName) throws IOException
    {
        Module module = getModuleForSchemaName(schemaName);
        if (null == module)
        {
            _log.debug("no module for schema '" + schemaName + "'");
            return null;
        }
        return module.getModuleResource("/schemas/" + schemaName + ".xml");
    }

    private static Module getModuleForSchemaName(String schemaName)
    {
        return ModuleLoader.getInstance().getModuleForSchemaName(schemaName);
    }


    public static DbSchema createFromMetaData(String dbSchemaName) throws SQLException, NamingException, ServletException
    {
        return createFromMetaData(dbSchemaName, DbScope.getLabkeyScope());
    }


    protected static DbSchema createFromMetaData(String dbSchemaName, DbScope scope) throws SQLException, NamingException, ServletException
    {
        String ownerName = dbSchemaName;  // TODO: Get rid of this

        return createFromMetaData(dbSchemaName, scope, ownerName);
    }

    public static DbSchema createFromMetaData(String name, DbScope scope, String owner) throws SQLException, SqlDialect.SqlDialectNotSupportedException
    {
        Connection conn = null;
        DbSchema schema = new DbSchema(name, scope, owner);
        String dbName = scope.getDatabaseName();

        long startLoad = System.currentTimeMillis();

        _log.info("Loading DbSchema \"" + name + "\"");

        try
        {
            DataSource ds = scope.getDataSource();
            conn = ds.getConnection();
            DatabaseMetaData dbmd = conn.getMetaData();

            String[] types = {"TABLE", "VIEW",};
            ResultSet rs = dbmd.getTables(dbName, owner, "%", types);
            ArrayList<SchemaTableInfo> list = new ArrayList<SchemaTableInfo>();

            try
            {
                while (rs.next())
                {
                    String metaDataName = rs.getString("TABLE_NAME").trim();

                    // Ignore system tables
                    if (schema.getSqlDialect().isSystemTable(metaDataName))
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
                ti.loadFromMetaData(dbmd, dbName, owner);
                schema.addTable(ti.getName(), ti);
            }

            schema.getSqlDialect().prepareNewDbSchema(schema);
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

        _log.debug("" + schema.getTables().size() + " tables loaded in " + DateUtil.formatDuration(System.currentTimeMillis()-startLoad));
        return schema;
    }


    public static void invalidateSchemas()
    {
        synchronized (_loadedSchemas)
        {
            _loadedSchemas.clear();
        }
    }


    // Once a module is done with its upgrade then all database schemas it owns are upgraded.  This clears out only
    // schemas of modules that are not upgraded, so we don't, for example, reload core, prop, etc. after they're
    // complete.
    public static void invalidateIncompleteSchemas()
    {
        synchronized (_loadedSchemas)
        {
            List<String> schemaNames = new ArrayList<String>(_loadedSchemas.keySet());

            for (String schemaName : schemaNames)
            {
                Module module = getModuleForSchemaName(schemaName);
                ModuleContext context = ModuleLoader.getInstance().getModuleContext(module);

                if (!context.isInstallComplete())
                    _loadedSchemas.remove(schemaName);
            }
        }
    }


    public static void invalidateSchema(String schemaName)
    {
        synchronized (_loadedSchemas)
        {
            _loadedSchemas.remove(schemaName);
        }
    }

    protected DbSchema(String name, DbScope scope, String owner)
    {
        _name = name;
        _scope = scope;
        _owner = owner;
    }

    public String getName()
    {
        return _name;
    }

    public String getOwner()
    {
        return _owner;
    }

    public SqlDialect getSqlDialect()
    {
        return _scope.getSqlDialect();
    }

    private boolean isStale()
    {
        return _resourceRef == null || _resourceRef.isStale();
    }

    private Resource getResource()
    {
        return _resourceRef != null ? _resourceRef.getResource() : null;
    }

    private void setResource(Resource r)
    {
        if (_resourceRef == null || _resourceRef.getResource() != r)
            _resourceRef = new ResourceRef(r);
        else
            _resourceRef.updateVersionStamp();
    }

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
            addTable(tableInfo.getName(), tableInfo);
        }
    }


    public Collection<SchemaTableInfo> getTables()
    {
        return Collections.unmodifiableCollection(_tables.values());
    }

    public SchemaTableInfo getTable(String tableName)
    {
        return _tables.get(tableName);
    }

    private void addTable(String tableName, SchemaTableInfo table)
    {
        _tables.put(tableName, table);
    }

    public void writeCreateTableSql(Writer out) throws IOException
    {
        for (SchemaTableInfo table : getTables())
        {
            table.writeCreateTableSql(out);
        }
    }

    public void writeCreateConstraintsSql(Writer out) throws IOException
    {
        for (SchemaTableInfo table : getTables())
        {
            table.writeCreateConstraintsSql(out);
        }
    }

    public void writeBeanSource(Writer out) throws IOException
    {
        for (SchemaTableInfo table : getTables())
        {
            table.writeBean(out);
        }
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
            m.put("Container", ContainerManager.getHomeContainer().getId());

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
            m.put("Container", ContainerManager.getHomeContainer().getId());
            m = Table.insert(ctx.getUser(), testTable, m);
            Integer rowId = ((Integer) m.get("RowId"));

            String key = "RowId" + rowId;
            DbCache.put(testTable, key, m);
            Map m2 = (Map) DbCache.get(testTable, key);
            assertEquals(m, m2);

            //Does cache get cleared on delete
            Table.delete(testTable, rowId);
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

    public static Integer checkContainerColumns(String dbSchemaName, SQLFragment sbSqlCmd, String tempTableName, String moduleName, Integer rowId) throws SQLException
    {
        int row = rowId.intValue();
        DbSchema curSchema = DbSchema.get(dbSchemaName);
        SQLFragment sbSql = new SQLFragment();

        for (TableInfo t : curSchema.getTables())
        {
            if (t.getTableType()!= TableInfo.TABLE_TYPE_TABLE)
                continue;

            for (ColumnInfo col : t.getColumns())
            {
                if (col.getName().equalsIgnoreCase("Container"))
                {
                    sbSql.append( " INSERT INTO "+ tempTableName );
                    sbSql.append(" SELECT " + String.valueOf(++row) + " AS rowId, '" + t.getSelectName() + "' AS TableName, ");
                    List<String> pkColumnNames = t.getPkColumnNames();

                    if (pkColumnNames.size() == 1)
                    {
                        String pkColumnName = pkColumnNames.get(0);
                        sbSql.append(" '" + pkColumnName);
                        sbSql.append("' AS FirstPKColName, ");
                        sbSql.append(" CAST( " + t.getSelectName() + "." + pkColumnName + " AS VARCHAR(100)) "
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
                    sbSql.append(" CAST( " + t.getSelectName() + "." + col.getName() + " AS VARCHAR(100)) AS OrphanedContainer ");
                    sbSql.append(" FROM " + t.getSelectName());
                    sbSql.append( " LEFT OUTER JOIN " + " core.Containers C ");
                    sbSql.append(" ON (" + t.getSelectName() + ".Container = C.EntityId ) ");
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
            Table.execute(coreSchema, createTempTableSql, new Object[]{});
            Table.execute(coreSchema, sbCheck);

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
                        " ;", new Object[]{});

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
