package org.labkey.api.exp.query;

public interface ExpMaterialProtocolInputTable extends ExpTable<ExpMaterialProtocolInputTable.Column>
{
    enum Column
    {
        RowId,
        Name,
        LSID,
        Protocol,
        Input,
        SampleSet,
        MinOccurs,
        MaxOccurs,
    }
}
