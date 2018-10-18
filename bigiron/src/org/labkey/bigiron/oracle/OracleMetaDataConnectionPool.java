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
    private final int _openCursors;

    public OracleMetaDataConnectionPool(DbScope scope)
    {
        super(scope, 5, "SELECT 1 FROM DUAL");
        _openCursors = new SqlSelector(scope, "SELECT VALUE FROM V$PARAMETER WHERE Name = 'open_cursors'").getObject(Integer.class);
    }

    @Override
    protected boolean validateConnection(Connection conn)
    {
        try
        {
            AtomicInteger count = MAP.get(conn, () -> new AtomicInteger(0));
            return count.incrementAndGet() < _openCursors && super.validateConnection(conn);
        }
        catch (ExecutionException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
            return false;
        }
    }
}
