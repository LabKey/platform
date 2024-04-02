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
<%@ page import="org.labkey.api.data.DbScope" %>
<%@ page import="org.apache.commons.lang3.tuple.Triple" %>
<%@ page import="org.labkey.api.query.DefaultSchema" %>
<%@ page import="org.labkey.api.util.TestContext" %>
<%@ page import="org.labkey.api.util.JunitUtil" %>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="org.labkey.api.query.QueryDefinition" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.QueryException" %>
<%@ page import="java.util.ArrayList" %>
<%@ page extends="org.labkey.api.jsp.JspTest.BVT" %>

<%!
DbSchema getDbSchema() throws XmlException
{
    return getDbSchema(null);
}


DbSchema getDbSchema(String columns) throws XmlException
{
    var tablesDoc = null == columns ? null : TablesDocument.Factory.parse(schemaXml(columns));
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
            dbSchema.getTableXmlMap().put(xmlTable.getTableName(), xmlTable);
    }
    return dbSchema;
}


private String schemaXml(String columns)
{
    return """
            <tables xmlns="http://labkey.org/data/xml" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <table tableName="R">
                  <columns>
                     %s
                  </columns>
                </table>
            </tables>""".formatted(columns);
}


private TableInfo getSchemaTableInfo(String columns) throws XmlException
{
    return getDbSchema(columns).getTable("R");
}


@Test
public void testBasicSchemaXML() throws Exception
{
    DbSchema dbSchema;
    TableInfo t;
    SQLFragment sql;

    // NO METADATA
    dbSchema = getDbSchema();
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
    dbSchema = getDbSchema("""
                    <column columnName="ZED" wrappedColumnName="zero" />
                    <column columnName="SIX">
                        <valueExpression>TWO * "THREE"</valueExpression>
                    </column>""");
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
    try
    {
        var dbSchema = getDbSchema("""
                 <column columnName="SIX">
                    <valueExpression>TWO times 3</valueExpression>
                </column>""");
        dbSchema.getTable("R");
        fail("Expected exception");
    }
    catch (ConfigurationException e)
    {
        assertTrue(e.getMessage().contains("Syntax"));
    }

    try
    {
        var dbSchema = getDbSchema("""
                 <column columnName="SIX">
                    <valueExpression>two.lookup</valueExpression>
                </column>""");
        dbSchema.getTable("R");
        fail("Expected exception");
    }
    catch (ConfigurationException e)
    {
        assertTrue(e.getMessage().contains("Lookup"));
    }

    try
    {
        var dbSchema = getDbSchema("""
                 <column columnName="ME">
                    <valueExpression>ME+1</valueExpression>
                </column>""");
        dbSchema.getTable("R");
        fail("Expected exception");
    }
    catch (ConfigurationException e)
    {
        assertTrue(e.getMessage().contains("itself"));
    }

    try
    {
        var dbSchema = getDbSchema("""
                 <column columnName="SIX">
                    <valueExpression>TWO times 3</valueExpression>
                </column>""");
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
    try
    {
        getSchemaTableInfo("""
                <column columnName="ERR" wrappedColumnName="nosuchcolumn" />""");
        fail("Expected exception");
    }
    catch (ConfigurationException e)
    {
        assertTrue(e.getMessage().contains("nosuchcolumn"));
    }

    // In schema xml, this used to silently fail ignore this column. We expect this to fail now.
    try
    {
        getSchemaTableInfo("""
                <column columnName="SIX">
                <valueExpression>TWO * "nosuchcolumn"</valueExpression>
                </column>""");
        fail("Expected exception");
    }
    catch (ConfigurationException e)
    {
        assertTrue(e.getMessage().contains("nosuchcolumn"));
    }
}


@Test
public void testPHI()
{

}


@Test
public void testDisallowedSyntax()throws Exception
{
    var disallowed = List.of(
        "<column columnName=\"DISALLOW\"><valueExpression>MAX(two)</valueExpression></column>",
        "<column columnName=\"DISALLOW\"><valueExpression>(SELECT 1)</valueExpression></column>",
        "<column columnName=\"DISALLOW\"><valueExpression>(EXISTS (SELECT 1))</valueExpression></column>"
    );

    for (String xml : disallowed)
    {
        try
        {
            getSchemaTableInfo(xml);
            fail("Expected exception for xml: " + xml);
        }
        catch (ConfigurationException e)
        {
            System.err.println("\n\n" + xml + "\n" + e.getMessage() + "\n\n");
        }
    }
}

@Test
public void testExpressions()throws Exception
{
    DbScope scope = CoreSchema.getInstance().getScope();
    var expressions = List.of(
            Triple.of("<column columnName=\"EXPR\"><valueExpression>CASE WHEN two=2 THEN 'success' ELSE 'fail' END</valueExpression></column>", JdbcType.VARCHAR, "success"),
            Triple.of("<column columnName=\"EXPR\"><valueExpression>5*three</valueExpression></column>", JdbcType.INTEGER, 15),
            Triple.of("<column columnName=\"EXPR\"><valueExpression>AGE_IN_YEARS(\"epoch\", {ts '2001-02-04 05:06:07'})</valueExpression></column>", JdbcType.INTEGER, 31)
    );

    for (var pair : expressions)
    {
        String xml = pair.getLeft();
        JdbcType type = pair.getMiddle();
        Object expected = pair.getRight();

        var r = getSchemaTableInfo(xml);
        assertEquals(type, r.getColumn("EXPR").getJdbcType());
        SQLFragment sql = new SQLFragment().append("SELECT ").append(r.getColumn("EXPR").getValueSql("t")).append("\n").append(r.getFromSQL("t"));
        var result = new SqlSelector(scope, sql).getObject(type.getJavaClass());
        assertEquals(expected, result);
    }
}



/********** SCHEMA OVERRIDE ***********/



/*
 * NOTE: Both tables and queries in UserSchema use AbstractTableInfo.overlayMetadata();
 * For the unit test It's mostly redundant to test both paths.
 */
TableInfo getQueryTableInfo(String columns) throws XmlException
{
    UserSchema qs = (UserSchema)DefaultSchema.get(TestContext.get().getUser(), JunitUtil.getTestContainer()).getSchema("core");
    assertNotNull(qs);

    QueryDefinition qdef = QueryService.get().createQueryDef(qs.getUser(), qs.getContainer(), qs, "R");
    Timestamp now = new Timestamp(new Date().getTime()), epoch = new Timestamp(0);
    var sql = "SELECT 0 as ZERO, 2 as TWO, 3 as THREE, 'x' as X, 'y' as Y, 'z' as Z, {ts '" + epoch + "'} as EPOCK, {ts '" + now + "'} AS NOW";
    qdef.setSql(sql);
    if (null != columns)
        qdef.setMetadataXml(schemaXml(columns));
    List<QueryException> errors = new ArrayList<>();
    var ret = qdef.getTable(qs, errors, true, true);
    assertNotNull(ret);
    assertTrue(errors.isEmpty());
    return ret;
}


@Test
public void testQuery()throws Exception
{
    TableInfo r = getQueryTableInfo(null);
    assertNotNull(r);
    assertNotNull(r.getColumn("ZERO"));
}
%>
