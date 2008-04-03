package org.labkey.api.exp.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.exp.PropertyDescriptor;

public interface ExpProtocolApplicationTable extends ExpTable<ExpProtocolApplicationTable.Column>
{
    enum Column
    {
        RowId,
        DataCount,
        MaterialCount,
    }

    public ColumnInfo createMaterialInputColumn(String alias, SamplesSchema schema, ExpSampleSet sampleSet, PropertyDescriptor ... pd);
    public ColumnInfo createDataInputColumn(String alias, ExpSchema schema, PropertyDescriptor ... pds);
}
