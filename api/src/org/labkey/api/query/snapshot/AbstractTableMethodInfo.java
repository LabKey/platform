package org.labkey.api.query.snapshot;

import org.labkey.api.query.AbstractMethodInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.DbSchema;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Mar 23, 2009
 * Time: 9:29:03 AM
 */
public abstract class AbstractTableMethodInfo extends AbstractMethodInfo
{
    protected AbstractTableMethodInfo(int sqlType)
    {
        super(sqlType);
    }
    
    public final SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
    {
        throw new IllegalStateException("Table name required for this method");
    }
}
