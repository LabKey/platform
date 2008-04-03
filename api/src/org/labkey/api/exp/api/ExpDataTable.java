package org.labkey.api.exp.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.PropertyDescriptor;

public interface ExpDataTable extends ExpTable<ExpDataTable.Column>
{
    static final public String COLUMN_ROWID = "exp.data.rowid";
    enum Column
    {
        RowId,
        LSID,
        Name,
        Protocol,
        DataFileUrl,
        Run,
        Created,
        Container,
        Flag,
    }

    void populate(ExpSchema schema);

    void setExperiment(ExpExperiment experiment);
    ExpExperiment getExperiment();
    void setRun(ExpRun run);
    ExpRun getRun();
    
    void setDataType(DataType type);
    DataType getDataType();

    ColumnInfo addMaterialInputColumn(String alias, SamplesSchema schema, PropertyDescriptor inputRole, ExpSampleSet sampleSet);
    ColumnInfo addDataInputColumn(String alias, PropertyDescriptor role);
    ColumnInfo addInputRunCountColumn(String alias);
}
