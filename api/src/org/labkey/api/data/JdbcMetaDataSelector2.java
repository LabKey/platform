package org.labkey.api.data;

import org.labkey.api.data.Selector.ForEachBlock;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;
import org.springframework.dao.DeadlockLoserDataAccessException;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 2/8/2015
 * Time: 10:16 AM
 */
public class JdbcMetaDataSelector2
{
    private final JdbcMetaDataLocator _locator;
    private final JdbcMetaDataResultSetFactory _factory;

    // Follows the basic Selector pattern, but much simpler to keep it out of that class hierarchy.
    public JdbcMetaDataSelector2(JdbcMetaDataLocator locator, JdbcMetaDataResultSetFactory factory)
    {
        _locator = locator;
        _factory = factory;
    }

    private static final int DEADLOCK_RETRIES = 5;

    public TableResultSet getResultSet() throws SQLException
    {
        return handleResultSet(new ResultSetHandler<TableResultSet>()
        {
            @Override
            public TableResultSet handle(ResultSet rs) throws SQLException
            {
                return new ResultSetImpl(rs, QueryLogging.emptyQueryLogging());
            }
        });
    }

    public void forEach(final ForEachBlock<ResultSet> block) throws SQLException
    {
        handleResultSet(new ResultSetHandler<Object>()
        {
            @Override
            public Object handle(ResultSet rs) throws SQLException
            {
                while (rs.next())
                    block.exec(rs);

                return null;
            }
        });
    }

    private <T> T handleResultSet(ResultSetHandler<T> handler) throws SQLException
    {
        // Retry on deadlock, up to five times, see #22148 and #15640.
        int tries = 1;

        while (true)
        {
            try
            {
                return handler.handle(_factory.getResultSet(_locator.getDatabaseMetaData(), _locator));
            }
            catch (DeadlockLoserDataAccessException e)
            {
                // Retry on deadlock, up to five times, see #22148 and #15640.
                if (tries++ >= DEADLOCK_RETRIES)
                    throw e;
            }
        }
    }

    private interface ResultSetHandler<T>
    {
        T handle(ResultSet rs) throws SQLException;
    }

    public interface JdbcMetaDataResultSetFactory
    {
        ResultSet getResultSet(DatabaseMetaData dbmd, JdbcMetaDataLocator locator) throws SQLException;
    }
}
