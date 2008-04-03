package org.labkey.api.exp.api;

public interface ExpProtocolTable extends ExpTable<ExpProtocolTable.Column>
{
    enum Column
    {
        RowId,
        Name,
        LSID,
    }
    void populate(ExpSchema schema);
}
