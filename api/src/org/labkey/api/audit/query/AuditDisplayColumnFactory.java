package org.labkey.api.audit.query;

import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.ColumnInfo;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 19, 2007
 */
public interface AuditDisplayColumnFactory extends DisplayColumnFactory
{
    public void init(ColumnInfo columnInfo);
    public int getPosition();
}
