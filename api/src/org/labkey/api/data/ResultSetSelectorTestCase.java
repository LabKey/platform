package org.labkey.api.data;

import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.User;
import org.labkey.api.util.ResultSetUtil;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * User: adam
 * Date: 12/18/12
 * Time: 8:04 PM
 */
public class ResultSetSelectorTestCase extends Assert
{
    @Test
    public void test() throws SQLException
    {
        CoreSchema core = CoreSchema.getInstance();
        test(core.getTableInfoActiveUsers(), User.class);
        test(core.getTableInfoModules(), ModuleContext.class);
        test(core.getSchema().getScope(), new SqlSelector(core.getSchema(), "SELECT RowId, Body FROM comm.Announcements").getResultSet(), SqlSelectorTestCase.TestClass.class);

        DbScope scope = core.getSchema().getScope();
        ResultSet rs = null;
        Connection conn = null;

        try
        {
            conn = scope.getConnection();
            DatabaseMetaData dbmd = conn.getMetaData();

            rs = dbmd.getSchemas();

            test(scope, rs, SchemaBean.class);
        }
        finally
        {
            ResultSetUtil.close(rs);
            scope.releaseConnection(conn);
        }
    }

    private <K> void test(TableInfo tinfo, Class<K> clazz) throws SQLException
    {
        test(tinfo.getSchema().getScope(), new TableSelector(tinfo).getResultSet(), clazz);
    }

    private <K> void test(DbScope scope, ResultSet rs, Class<K> clazz) throws SQLException
    {
        try
        {
            ResultSetSelector selector = new ResultSetSelector(scope, rs, null);
            selector.setCompletionAction(ResultSetSelector.CompletionAction.ScrollToTop);

            int count = (int)selector.getRowCount();

            assertTrue(selector.exists() || 0 == count);

            assertEquals(count, selector.getValueMap().size());

            final MutableInt rowCount = new MutableInt();

            // Test map operations

            assertEquals(count, selector.getArrayList(Map.class).size());
            assertEquals(count, selector.getCollection(Map.class).size());
            assertEquals(count, selector.getArray(Map.class).length);

            rowCount.setValue(0);
            selector.forEachMap(new Selector.ForEachBlock<Map<String, Object>>()
            {
                @Override
                public void exec(Map object) throws SQLException
                {
                    rowCount.increment();
                }
            });
            assertEquals(count, rowCount.intValue());

            // Test bean operations

            assertEquals(count, selector.getArrayList(clazz).size());
            assertEquals(count, selector.getCollection(clazz).size());
            assertEquals(count, selector.getArray(clazz).length);

            rowCount.setValue(0);
            selector.forEach(new Selector.ForEachBlock<K>()
            {
                @Override
                public void exec(K bean) throws SQLException
                {
                    rowCount.increment();
                }
            }, clazz);
            assertEquals(count, rowCount.intValue());

            // Test ResultSet operations

            rowCount.setValue(0);
            selector.forEach(new Selector.ForEachBlock<ResultSet>(){
                @Override
                public void exec(ResultSet object) throws SQLException
                {
                    rowCount.increment();
                }
            });
            assertEquals(count, rowCount.intValue());

            // Operations prior to this should not have closed the ResultSet
            verifyClosedStatus(rs, false);

            // This test method does close the ResultSet
            TableSelectorTestCase.verifyResultSets(selector, count, true);

            verifyClosedStatus(rs, true);
        }
        finally
        {
            // Just in case a failure occurs during the test
            ResultSetUtil.close(rs);
        }
    }

    private void verifyClosedStatus(ResultSet rs, boolean expectedClosed) throws SQLException
    {
        try
        {
            if (expectedClosed)
                assertTrue("ResultSet should be closed", rs.isClosed());
            else
                assertFalse("ResultSet shouldn't be closed", rs.isClosed());
        }
        catch (AbstractMethodError e)
        {
            // We are testing a mix of JDBC3 and JDBC4 ResultSets (with no good way to distinguish them). JDBC3
            // ResultSets don't support isClosed(), so just ignore any exception.
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public static class SchemaBean
    {
        private String _table_schem;
        private String _table_catalog;

        public String getTable_schem()
        {
            return _table_schem;
        }

        public void setTable_schem(String table_schema)
        {
            _table_schem = table_schema;
        }

        public String getTable_catalog()
        {
            return _table_catalog;
        }

        public void setTable_catalog(String table_catalog)
        {
            _table_catalog = table_catalog;
        }
    }
}
