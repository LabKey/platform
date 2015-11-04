package org.labkey.api.exp.query;

/**
 * User: kevink
 * Date: 9/21/15
 */
public interface ExpDataClassTable extends ExpTable<ExpDataClassTable.Column>
{
    enum Column
    {
        RowId,
        LSID,
        Name,
        Description,
        Created,
        Modified,
        CreatedBy,
        ModifiedBy,
        Folder,
        NameExpression,
        SampleSet,
        DataCount,
    }
}
