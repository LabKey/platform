<%@ page import="org.junit.AfterClass" %>
<%@ page import="org.junit.Assume" %>
<%@ page import="org.junit.BeforeClass" %>
<%@ page import="org.junit.Test" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="org.labkey.api.data.DbScope" %>
<%@ page import="org.labkey.api.data.JdbcType" %>
<%@ page import="org.labkey.api.data.PropertyStorageSpec" %>
<%@ page import="org.labkey.api.data.SQLFragment" %>
<%@ page import="org.labkey.api.data.Sort" %>
<%@ page import="org.labkey.api.data.SqlSelector" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.exp.list.ListDefinition" %>
<%@ page import="org.labkey.api.exp.list.ListService" %>
<%@ page import="org.labkey.api.exp.property.Domain" %>
<%@ page import="org.labkey.api.query.BatchValidationException" %>
<%@ page import="org.labkey.api.query.DefaultSchema" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.query.FilteredTable" %>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="org.labkey.api.reader.TabLoader" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.JunitUtil" %>
<%@ page import="org.labkey.api.util.TestContext" %>
<%@ page import="org.labkey.query.QueryServiceImpl" %>
<%@ page import="org.postgresql.util.ReaderInputStream" %>
<%@ page import="java.io.StringReader" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.data.LookupResolutionType" %>
<%@ page extends="org.labkey.api.jsp.JspTest.DRT" %>
<%!
    final static String tableName = "testGetSelectSqlSort";
    DbScope scope = null;

    @Test
    public void testGetSelectSqlSort() throws Exception
    {
        User user = TestContext.get().getUser();
        Container c = JunitUtil.getTestContainer();
        ListService s = ListService.get();
        ListDefinition list = s.getList(c, tableName);

        var data = new ReaderInputStream(new StringReader("A,B,C\n6,4,3\n1,8,6\n7,1,9\n2,5,1\n8,9,4\n3,2,7\n9,6,10\n4,10,2\n10,3,5\n5,7,8\n"));
        list.insertListItems(user, c, new TabLoader.CsvFactory().createLoader(data,true), new BatchValidationException(), null, null, false, LookupResolutionType.primaryKey);

        // wrap this list so we can mangle the sort properties
        UserSchema schema = (UserSchema)DefaultSchema.get(user,c,"lists");
        assertNotNull(schema);
        scope = schema.getDbSchema().getScope();
        TableInfo listTable =schema.getTable(tableName, null);
        assertNotNull(listTable);
        FilteredTable wrapped = new FilteredTable(listTable, schema, null);
        wrapped.wrapAllColumns(true);
        (wrapped.getMutableColumn("B")).setSortFieldKeys(Arrays.asList(new FieldKey(null,"C")));
        // add xyz as duplicate of C so we can test isSorted(C) even when C is not selected
        wrapped.addWrapColumn("xyz", listTable.getColumn("C"));

        var A = wrapped.getColumn("A");
        var B = wrapped.getColumn("B");
        var C = wrapped.getColumn("C");
        var xyz = wrapped.getColumn("xyz");

        // test choose PK
        SQLFragment test1 = QueryServiceImpl.get().getSelectBuilder(wrapped).columns(List.of(C,B,A)).buildSqlFragment();
        assertTrue(test1.toDebugString().contains("ORDER BY A ASC"));
        assertTrue(isSorted(test1,3));

        // test choose non-PK
        SQLFragment test2 = QueryServiceImpl.get().getSelectBuilder(wrapped).columns(List.of(C)).buildSqlFragment();
        assertTrue(test2.toDebugString().contains("ORDER BY C ASC"));
        assertTrue(isSorted(test2,1));

        // test explicit in select list
        SQLFragment test3 = QueryServiceImpl.get().getSelectBuilder(wrapped).columns(List.of(A,C)).sort(new Sort("C")).buildSqlFragment();
        assertTrue(test3.toDebugString().contains("ORDER BY C ASC"));
        assertTrue(isSorted(test3,2));

        // test explicit not in select list
        SQLFragment test4 = QueryServiceImpl.get().getSelectBuilder(wrapped).columns(List.of(A,B,xyz)).sort(new Sort("C")).buildSqlFragment();
        assertTrue(test4.toDebugString().contains("ORDER BY C ASC"));
        assertTrue(isSorted(test4,3));

        // test sortFieldKeys
        SQLFragment test5 = QueryServiceImpl.get().getSelectBuilder(wrapped).columns(List.of(A,B,xyz)).sort(new Sort("B")).buildSqlFragment();
        assertFalse(test5.toDebugString().contains("ORDER BY B ASC"));
        assertTrue(test5.toDebugString().contains("ORDER BY C ASC"));
        assertTrue(isSorted(test5,3));

        /* broken sortFieldsKey */
        (wrapped.getMutableColumn("B")).setSortFieldKeys(Arrays.asList(new FieldKey(null,"D")));

        // implicit sort, sort by B because D not found
        SQLFragment test6 = QueryServiceImpl.get().getSelectBuilder(wrapped).columns(List.of(B)).buildSqlFragment();
        assertFalse(test6.toDebugString().contains("ORDER BY D ASC"));
        assertTrue(test6.toDebugString().contains("ORDER BY B ASC"));
        assertTrue(isSorted(test6,1));

        // explicit sort, sort by B because D not found
        (wrapped.getMutableColumn("B")).setSortFieldKeys(Arrays.asList(new FieldKey(null,"D")));
        SQLFragment test7 = QueryServiceImpl.get().getSelectBuilder(wrapped).columns(List.of(A,B,C)).sort(new Sort("B")).buildSqlFragment();
        assertFalse(test7.toDebugString().contains("ORDER BY D ASC"));
        assertTrue(test7.toDebugString().contains("ORDER BY B ASC"));
        assertTrue(isSorted(test7,2));
    }

    @BeforeClass
    public static void createList() throws Exception
    {
        Assume.assumeTrue("This test requires the list module.", ListService.get() != null);

        JunitUtil.deleteTestContainer();

        User user = TestContext.get().getUser();
        Container c = JunitUtil.getTestContainer();
        ListService s = ListService.get();
        ListDefinition list = s.createList(c, tableName, ListDefinition.KeyType.Integer, null, null);
        list.setKeyName("A");
        Domain d = list.getDomain();
        d.addProperty(new PropertyStorageSpec("A", JdbcType.INTEGER));
        d.addProperty(new PropertyStorageSpec("B", JdbcType.INTEGER));
        d.addProperty(new PropertyStorageSpec("C", JdbcType.INTEGER));
        list.save(user,true, null, null);
    }


    @AfterClass
    public static void cleanup() throws Exception
    {
        JunitUtil.deleteTestContainer();
    }


    boolean isSorted(SQLFragment sqlf, int col) throws SQLException
    {
        try (var rs = new SqlSelector(scope,sqlf).getResultSet())
        {
            int prev = Integer.MIN_VALUE;
            while (rs.next())
            {
                int i = rs.getInt(col);
                if (i < prev)
                    return false;
                prev = i;
            }
            return true;
        }
    }
%>
