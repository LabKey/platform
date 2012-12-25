package org.labkey.api.data;

import org.junit.Test;

import java.sql.SQLException;

/**
 * User: adam
 * Date: 12/18/12
 * Time: 8:04 PM
 */
public class SqlSelectorTestCase extends AbstractSelectorTestCase<SqlSelector>
{
    @Test
    public void testSqlSelector() throws SQLException
    {
        SqlSelector selector = new SqlSelector(CoreSchema.getInstance().getSchema(), "SELECT RowId, Body FROM comm.Announcements");
        test(selector, TestClass.class);
    }

    @Override
    protected void verifyResultSets(SqlSelector sqlSelector, int expectedRowCount, boolean expectedComplete) throws SQLException
    {
        super.verifyResultSets(sqlSelector, expectedRowCount, expectedComplete);

        // Test caching and scrolling options
        verifyResultSet(sqlSelector.getResultSet(false, false), expectedRowCount, expectedComplete);
        verifyResultSet(sqlSelector.getResultSet(true, false), expectedRowCount, expectedComplete);
        verifyResultSet(sqlSelector.getResultSet(true, true), expectedRowCount, expectedComplete);
    }

    public static class TestClass
    {
        private int _rowId;
        private String _body;

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public String getBody()
        {
            return _body;
        }

        public void setBody(String body)
        {
            _body = body;
        }
    }
}
