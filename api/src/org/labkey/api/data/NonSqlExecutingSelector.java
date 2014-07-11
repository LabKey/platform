package org.labkey.api.data;

import org.apache.commons.lang3.mutable.MutableLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 7/11/2014
 * Time: 6:51 AM
 */
public abstract class NonSqlExecutingSelector<SELECTOR extends NonSqlExecutingSelector> extends BaseSelector<SELECTOR>
{
    protected NonSqlExecutingSelector(@NotNull DbScope scope, @Nullable Connection conn)
    {
        super(scope, conn);
    }

    @Override
    public long getRowCount()
    {
        final MutableLong count = new MutableLong();

        forEach(new ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                count.increment();
            }
        });

        return count.getValue();
    }

    @Override
    public boolean exists()
    {
        return handleResultSet(getStandardResultSetFactory(), new ResultSetHandler<Boolean>()
        {
            @Override
            public Boolean handle(ResultSet rs, Connection conn) throws SQLException
            {
                return rs.next();
            }
        });
    }
}
