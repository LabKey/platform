package org.labkey.api.exp.query;

import org.labkey.api.data.UpdateableTableInfo;

/**
 * User: kevink
 * Date: 9/21/15
 */
public interface ExpDataClassDataTable extends ExpTable<ExpDataClassDataTable.Column>, UpdateableTableInfo
{
    enum Column
    {
        RowId,
        LSID,
        Name,
        Description,
        DataClass,
        Created,
        CreatedBy,
        Modified,
        ModifiedBy,
        Folder,
        Flag,
    }

}
