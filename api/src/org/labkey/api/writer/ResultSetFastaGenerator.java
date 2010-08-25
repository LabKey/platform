package org.labkey.api.writer;

import org.labkey.api.data.RuntimeSQLException;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Aug 24, 2010
 * Time: 1:40:23 PM
 */
public abstract class ResultSetFastaGenerator implements FastaWriter.FastaGenerator
{
    private final ResultSet _rs;

    public ResultSetFastaGenerator(ResultSet rs)
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
                    return ResultSetFastaGenerator.this.getHeader(_rs);
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
                    return ResultSetFastaGenerator.this.getSequence(_rs);
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
