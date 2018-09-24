package org.labkey.api.exp.query;

public interface ExpDataProtocolInputTable extends ExpTable<ExpDataProtocolInputTable.Column>
{
    enum Column
    {
        RowId,
        Name,
        LSID,
        Protocol,
        Input,
        DataClass,
        MinOccurs,
        MaxOccurs,
    }
}
