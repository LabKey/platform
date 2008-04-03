package org.labkey.api.audit.query;

import org.labkey.api.data.ColumnInfo;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 19, 2007
 */
public abstract class SimpleAuditColumnFactory implements AuditDisplayColumnFactory
{
    public void init(ColumnInfo columnInfo)
    {
    }

    public int getPosition()
    {
        return -1;
    }
}
