package org.labkey.api.exp.query;

public interface ExpExclusionTable extends ExpTable<ExpExclusionTable.Column>
{
    enum Column
    {
        RowId,
        RunId,
        Comment,
        Created,
        CreatedBy,
        Modified,
        ModifiedBy
    }
}
