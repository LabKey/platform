/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

package org.labkey.api.dataiterator;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.CachedResultSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.util.ResultSetUtil;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * {@link DataIterator} implementation backed by a JDBC {@link ResultSet}
 * User: matthewb
 * Date: Jun 8, 2011
 */
public class ResultSetDataIterator extends AbstractDataIterator implements ScrollableDataIterator
{
    static public DataIterator wrap(ResultSet rs, DataIteratorContext context)
    {
        if (rs instanceof DataIterator)
        {
            return (DataIterator)rs;
        }
        if (rs instanceof DataIteratorBuilder)
        {
            return ((DataIteratorBuilder)rs).getDataIterator(context);
        }

        return new ResultSetDataIterator(rs, context);
    }

    protected ResultSetDataIterator(ResultSet rs,  DataIteratorContext context)
    {
        super(context);

        try
        {
            _rs = rs;
            ResultSetMetaData rsmd = _rs.getMetaData();
            _columns = new ColumnInfo[rsmd.getColumnCount()+1];
            _columns[0] = new ColumnInfo("_row", JdbcType.INTEGER);
            for (int i=1 ; i<=rsmd.getColumnCount() ; i++)
                _columns[i] = new ColumnInfo(rsmd, i);
        }
        catch (SQLException x)
        {
            throw new RuntimeException(x);
        }
    }

    int _row = 0;
    ResultSet _rs;
    ColumnInfo[] _columns;

    @Override
    public int getColumnCount()
    {
        return _columns.length-1;
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return _columns[i];
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        try
        {
            ++_row;
            return _rs.next();
        }
        catch (SQLException x)
        {
            throw new RuntimeException(x);
        }
    }

    @Override
    public Object get(int i)
    {
        try
        {
            if (0==i)
                return _row;
            return _rs.getObject(i);
        }
        catch (SQLException x)
        {
            throw new RuntimeException(x);
        }
    }

    @Override
    public boolean isScrollable()
    {
        return (_rs instanceof CachedResultSet);
    }

    @Override
    public void beforeFirst()
    {
        try
        {
            _row = 0;
            _rs.beforeFirst();
        }
        catch (SQLException x)
        {
            throw new RuntimeException(x);
        }
    }

    @Override
    public void close() throws IOException
    {
        ResultSetUtil.close(_rs);
        _rs = null;
    }


    public static class TestCase extends Assert
    {
        String _testSql =
                "SELECT 1 as a, CAST('one' as VARCHAR) as b UNION ALL " +
                "SELECT 2 as a, CAST('two' as VARCHAR) as b UNION ALL " +
                "SELECT CAST(null as INTEGER) as a, CAST('null' as VARCHAR) as b " +
                "ORDER BY b";
        
        @Test
        public void test() throws Exception
        {
            DataIteratorContext context = new DataIteratorContext();
            ResultSet rs = new SqlSelector(CoreSchema.getInstance().getSchema(), _testSql).getResultSet();

            try (DataIterator it = ResultSetDataIterator.wrap(rs, context))
            {
                assertEquals("a", it.getColumnInfo(1).getName());
                assertEquals(JdbcType.INTEGER, it.getColumnInfo(1).getJdbcType());
                assertEquals("b", it.getColumnInfo(2).getName());
                assertEquals(JdbcType.VARCHAR, it.getColumnInfo(2).getJdbcType());
                assertTrue(it.next());
                assertEquals(1, it.get(0));
                assertEquals(null, it.get(1));
                assertEquals("null", it.get(2));
                assertTrue(it.next());
                assertEquals(2, it.get(0));
                assertEquals(1, it.get(1));
                assertEquals("one", it.get(2));
                assertTrue(it.next());
                assertEquals(3, it.get(0));
                assertEquals(2, it.get(1));
                assertEquals("two", it.get(2));
                assertFalse(it.next());
            }
            finally
            {
                assert(rs.isClosed());  // DataIterator auto-close should close ResultSet
            }
        }
    }
}
