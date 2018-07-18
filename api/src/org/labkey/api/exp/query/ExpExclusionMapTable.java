package org.labkey.api.exp.query;

public interface ExpExclusionMapTable extends ExpTable<ExpExclusionMapTable.Column>
{
    enum Column
    {
        RowId,
        ExclusionId,
        DataRowId,
        Comment,
        Created,
        CreatedBy,
        Modified,
        ModifiedBy
    }
}
