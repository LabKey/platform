package org.labkey.bigiron.oracle;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.labkey.api.data.ConnectionPool;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.util.ExceptionUtil;

import java.sql.Connection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class OracleMetaDataConnectionPool extends ConnectionPool
{
    // A concurrent weak map to count usages of each connection
    private final Cache<Connection, AtomicInteger> MAP = CacheBuilder.newBuilder().weakKeys().build();
    private final int _openCursorsMax;
    private final DbScope _dbScope;
    private final int _openCursorTarget;

    public OracleMetaDataConnectionPool(DbScope scope)
    {
        super(scope, 5, "SELECT 1 FROM DUAL");
        _dbScope = scope;
        _openCursorsMax = new SqlSelector(_dbScope, "SELECT VALUE FROM V$PARAMETER WHERE Name = 'open_cursors'").getObject(Integer.class);
        _openCursorTarget = ((Double)(_openCursorsMax * .8)).intValue(); //a target value to get a new connection when no. of open cursors reach 80% of max open cursor limit (rather than getting a new connection after the limit has reached).
    }

    @Override
    protected boolean validateConnection(Connection conn)
    {
        try
        {
            AtomicInteger count = MAP.get(conn, () -> new AtomicInteger(0));

            // In case we ever need to get the current open cursor count, uncomment below code section, which gets number of cursors
            // utilized by a session id or sid (this is an overloaded term in Oracle) for an incoming connection. Current open cursor count
            // is different than max open cursors set for an Oracle instance (_openCursorMax variable above).
            // Knowing a current open cursor count is useful if we want to see if a certain session in a connection is spiking up the number of cursors.
            // Further investigation on the type of cursor revealed that oc.CURSOR_TYPE='OPEN' seem to be the one that spiked often, so further you can add that filter in as well, if necessary.
            // We had done this investigation in order to debunk or prove the assumption that more than one cursor was associated with a query, and our conclusion was that the current open cursor count was 1 to 1 with the 'count' above.
//            SQLFragment sql = new SQLFragment();
//            sql.append("SELECT count(*) ");
//            sql.append("FROM v$open_cursor oc, v$session s ");
//            sql.append("WHERE s.sid = oc.sid ");
//            sql.append("AND s.sid = (select distinct sid from v$mystat)");
//
//            int openCursorCurrent = new SqlSelector(_dbScope, conn, sql).getObject(Integer.class);

            return count.incrementAndGet() < _openCursorTarget && super.validateConnection(conn);
        }
        catch (ExecutionException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
            return false;
        }
    }
}
