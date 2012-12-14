package org.labkey.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;

/**
* User: kevink
* Date: 12/10/12
*/
public class LinkedTableInfo extends SimpleUserSchema.SimpleTable
{
    public LinkedTableInfo(UserSchema schema, TableInfo table)
    {
        super(schema, table);
    }

    @Override
    protected void addTableURLs()
    {
        // Disallow all table URLs
        setGridURL(LINK_DISABLER);
        setDetailsURL(LINK_DISABLER);
        setImportURL(LINK_DISABLER);
        setInsertURL(LINK_DISABLER);
        setUpdateURL(LINK_DISABLER);
        setDeleteURL(LINK_DISABLER);
    }

    @Override
    protected void fixupWrappedColumn(ColumnInfo wrap, ColumnInfo col)
    {
        super.fixupWrappedColumn(wrap, col);

        // Remove FK and URL. LinkedTableInfo doesn't include FKs or URLs.
        wrap.setFk(null);
        wrap.setURL(LINK_DISABLER);
    }

    @Override
    protected void addDomainColumns()
    {
        // LinkedTableInfos only adds columns from the source table and has no Domain columns.
    }
}
