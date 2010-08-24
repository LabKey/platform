package org.labkey.api.writer;

import org.labkey.api.data.RuntimeSQLException;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Aug 24, 2010
 * Time: 1:40:23 PM
 */
public abstract class ResultSetFastaEntryIterator implements FastaWriter.FastaEntryIterator
{
    private final ResultSet _rs;

    public ResultSetFastaEntryIterator(ResultSet rs)
    {
        _rs = rs;
    }

    abstract public String getHeader(ResultSet rs) throws SQLException;
    abstract public String getSequence(ResultSet rs) throws SQLException;

    @Override
    public boolean hasNext()
    {
        try
        {
            boolean hasNext = _rs.next();

            if (!hasNext)
                _rs.close();

            return hasNext;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    @Override
    public FastaWriter.FastaEntry next()
    {
        return new FastaWriter.FastaEntry() {
            @Override
            public String getHeader()
            {
                try
                {
                    return ResultSetFastaEntryIterator.this.getHeader(_rs);
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }

            @Override
            public String getSequence()
            {
                try
                {
                    return ResultSetFastaEntryIterator.this.getSequence(_rs);
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }
        };
    }

    @Override
    public void remove()
    {
        throw new UnsupportedOperationException();
    }
}
