<%@ page import="org.labkey.api.data.DbSchema" %>
<%@ page import="org.labkey.data.xml.TablesDocument" %>
<%@ page import="org.apache.xmlbeans.XmlException" %>
<%@ page import="org.labkey.api.data.StandardSchemaTableInfoFactory" %>
<%@ page import="org.labkey.api.data.DatabaseTableType" %>
<%@ page import="org.labkey.api.data.MutableColumnInfo" %>
<%@ page import="org.labkey.api.data.JdbcType" %>
<%@ page import="org.labkey.api.data.BaseColumnInfo" %>
<%@ page import="org.labkey.api.data.SchemaTableInfo" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.query.sql.CalculatedExpressionColumn" %>
<%@ page import="org.jetbrains.annotations.NotNull" %>
<%@ page import="org.labkey.api.data.SQLFragment" %>
<%@ page import="java.sql.Timestamp" %>
<%@ page import="java.util.Date" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.data.DbSchemaType" %>
<%@ page import="org.labkey.api.data.CoreSchema" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.data.xml.TableType" %>
<%@ page import="org.junit.Test" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page import="org.labkey.api.data.SqlSelector" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="org.labkey.api.util.ConfigurationException" %>
<%@ page extends="org.labkey.api.jsp.JspTest.BVT" %>

<%!

DbSchema getDbSchema(TablesDocument tablesDoc) throws XmlException
{
    StandardSchemaTableInfoFactory factoryR = new StandardSchemaTableInfoFactory("R", DatabaseTableType.NOT_IN_DB, null)
    {
        @Override
        public String getTableName()
        {
            return super.getTableName();
        }

        MutableColumnInfo makeColumn(String name, JdbcType type, String alias)
        {
            BaseColumnInfo c = new BaseColumnInfo(name, null, type)
            {
                @Override
                public String getAlias()
                {
                    return super.getAlias();
                }

                @Override
                public void setAlias(String alias)
                {
                    super.setAlias(alias);
                }
            };
            c.setMetaDataName(alias);
            c.setAlias(alias);
            return c;
        }

        @Override
        public SchemaTableInfo getSchemaTableInfo(DbSchema schema)
        {
            var cols = List.of(
                    makeColumn("ZERO", JdbcType.INTEGER, "zero_"),
                    makeColumn("TWO", JdbcType.INTEGER, "two_"),
                    makeColumn("THREE", JdbcType.INTEGER, "three_"),
                    makeColumn("X", JdbcType.VARCHAR, "x_"),
                    makeColumn("Y", JdbcType.VARCHAR, "y_"),
                    makeColumn("Z", JdbcType.VARCHAR, "z_"),
                    makeColumn("EPOCH", JdbcType.TIMESTAMP, "epoch_"),
                    makeColumn("NOW", JdbcType.TIMESTAMP, "now_")
            );
            CalculatedExpressionColumn._SchemaTableInfo ti = new CalculatedExpressionColumn._SchemaTableInfo(schema, DatabaseTableType.NOT_IN_DB, getTableName(), cols, schema.getTableXmlMap().get(getTableName()))
            {
                @Override
                public @NotNull SQLFragment getFromSQL(String alias)
                {
                    // FROM (VALUES (1, 'one'), (2, 'two'), (3, 'three')) AS t (num,letter)
                    Timestamp now = new Timestamp(new Date().getTime()), epoch = new Timestamp(0);
                    return new SQLFragment("FROM (VALUES (0, 2, 3, 'x', 'y', 'z', {ts '" + epoch + "'}, {ts '" + now + "'})) AS " + alias + " (zero_, two_, three_, x_, y_, z_, epoch_, now_)");
                }
            };
            return ti;
        }
    };

    var dbSchema = new DbSchema(CalculatedExpressionColumn.class.getName() + new GUID(), DbSchemaType.Junit, CoreSchema.getInstance().getScope(), Map.of("R",factoryR), ModuleLoader.getInstance().getModule("query"));
    if (null != tablesDoc)
    {
        TableType[] xmlTables = tablesDoc.getTables().getTableArray();

        for (TableType xmlTable : xmlTables)
        {
            String xmlTableName = xmlTable.getTableName();
            dbSchema.getTableXmlMap().put(xmlTable.getTableName(), xmlTable);
        }
    }
    return dbSchema;
}


@Test
public void testBasic() throws Exception
{
    DbSchema dbSchema;
    TableInfo t;
    SQLFragment sql;

    // NO METADATA
    dbSchema = getDbSchema(null);
    assertNotNull(dbSchema);
    t = dbSchema.getTable("R");
    assertNotNull(t);
    assertNull(t.getColumn("WRAPPED"));

    sql = new SQLFragment().append("SELECT ").append(t.getColumn("ZERO").getValueSql("t")).append("\n").append(t.getFromSQL("t"));
    try (ResultSet rs = new SqlSelector(dbSchema, sql).getResultSet())
    {
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1));
    }

    // WRAPPED AND CALCULATED
    dbSchema = getDbSchema(TablesDocument.Factory.parse("""
            <tables xmlns="http://labkey.org/data/xml" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <table tableName="R">
                  <columns>
                    <column columnName="ZED" wrappedColumnName="zero" />
                    <column columnName="SIX">
                        <valueExpression>TWO * "THREE"</valueExpression>
                    </column>
                  </columns>
                </table>
            </tables>
            """));
    assertNotNull(dbSchema);
    t = dbSchema.getTable("R");
    assertNotNull(t);
    var wrapped = t.getColumn("ZED");
    assertNotNull(wrapped);
    assertTrue(wrapped.isCalculated());
    assertFalse(wrapped.isUserEditable());
    var calculated = t.getColumn("SIX");
    assertNotNull(calculated);
    assertTrue(calculated.isCalculated());
    assertFalse(calculated.isUserEditable());

    sql = new SQLFragment()
            .append("SELECT\n")
            .append("  ").append(wrapped.getValueSql("t")).append(" AS ").append(wrapped.getAlias()).append(",\n")
            .append("  ").append(calculated.getValueSql("t")).append(" AS ").append(calculated.getAlias()).append(",\n")
            .append("  ").append("*\n")
            .append(t.getFromSQL("t"))
            .append("\nWHERE ").append(t.getColumn("ZED").getValueSql("t")).append(" = 0 AND ").append(t.getColumn("SIX").getValueSql("t")).append(" = 6");
    try (ResultSet rs = new SqlSelector(dbSchema, sql).getResultSet())
    {
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1));
        assertEquals(0, t.getColumn("ZED").getValue(rs));
        assertEquals(6, rs.getInt(2));
        assertEquals(6, t.getColumn("SIX").getValue(rs));
        assertEquals("x", t.getColumn("X").getValue(rs));
    }
}


@Test
public void testParseError() throws Exception
{
    // In schema xml, this used to silently fail ignore this column. We expect this to fail now.
    var dbSchema = getDbSchema(TablesDocument.Factory.parse("""
            <tables xmlns="http://labkey.org/data/xml" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <table tableName="R">
                  <columns>
                     <column columnName="SIX">
                        <valueExpression>TWO times 3</valueExpression>
                    </column>
                  </columns>
                </table>
            </tables>
            """));
    try
    {
        dbSchema.getTable("R");
        fail("Expected exception");
    }
    catch (ConfigurationException e)
    {
        assertTrue(e.getMessage().contains("Syntax"));
    }
}


@Test
public void testNotFound()throws Exception
{
    // In schema xml, this used to silently fail ignore this column. We expect this to fail now.
    var dbSchema = getDbSchema(TablesDocument.Factory.parse("""
            <tables xmlns="http://labkey.org/data/xml" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <table tableName="R">
                  <columns>
                    <column columnName="ERR" wrappedColumnName="nosuchcolumn" />
                  </columns>
                </table>
            </tables>
            """));
    try
    {
        dbSchema.getTable("R");
        fail("Expected exception");
    }
    catch (ConfigurationException e)
    {
        assertTrue(e.getMessage().contains("nosuchcolumn"));
    }

    // In schema xml, this used to silently fail ignore this column. We expect this to fail now.
    dbSchema = getDbSchema(TablesDocument.Factory.parse("""
            <tables xmlns="http://labkey.org/data/xml" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <table tableName="R">
                  <columns>
                     <column columnName="SIX">
                        <valueExpression>TWO * "nosuchcolumn"</valueExpression>
                    </column>
                  </columns>
                </table>
            </tables>
            """));
    try
    {
        dbSchema.getTable("R");
        fail("Expected exception");
    }
    catch (ConfigurationException e)
    {
        assertTrue(e.getMessage().contains("nosuchcolumn"));
    }
}


@Test
public void testAllowedSyntax()throws Exception
{
}

@Test
public void testCalculatedColumnUser()throws Exception
{
}

@Test
public void testQuery()throws Exception
{
}
%>