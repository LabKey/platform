package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Oct 22, 2010
 * Time: 7:52:17 AM
 */
public class AtomicDatabaseInteger
{
    private final TableInfo _table;
    private final ColumnInfo _targetColumn;
    private final User _user;
    private final Container _container;
    private final Object _rowId;

    // Acts like an AtomicInteger, but uses the database for synchronization.  This is convenient for scenarios where
    // multiple threads (eventually, even different servers) might attempt an update but only one should succeed.
    // Currently only implements compareAndSet(), but could add other methods from AtomicInteger.
    public AtomicDatabaseInteger(ColumnInfo targetColumn, User user, @Nullable Container container, Object rowId)
    {
        if (targetColumn.getJavaObjectClass() != Integer.class)
            throw new IllegalArgumentException("Target column must be type integer");

        _table = targetColumn.getParentTable();
        List<ColumnInfo> pks = _table.getPkColumns();

        if (pks.size() != 1)
            throw new IllegalArgumentException("Target table must have a single primary key column");

        _targetColumn = targetColumn;
        _user = user;
        _container = container;
        _rowId = rowId;
    }

    // Atomically sets the value to the given updated value if the current value == the expected value.
    public boolean compareAndSet(int expect, int update) throws SQLException
    {
        String targetColumnName = _targetColumn.getSelectName();
        SimpleFilter filter = new SimpleFilter(targetColumnName, expect);

        if (null != _container)
            filter.addCondition("Container", _container);

        Map<String, Object> in = new HashMap<String, Object>();
        in.put(_targetColumn.getSelectName(), update);

        try
        {
            Map<String, Object> out = Table.update(_user, _table, in, _rowId, filter);
            assert update == (Integer)out.get(targetColumnName);
            return true;
        }
        catch (Table.OptimisticConflictException e)
        {
            return false;
        }
    }
}
