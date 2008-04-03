package org.labkey.api.exp.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpTable;
import org.labkey.api.exp.PropertyDescriptor;

import java.util.Map;
import java.util.Collection;
import java.util.List;

public interface ExpRunTable extends ExpTable<ExpRunTable.Column>
{
    static public final String COLUMN_ROWID = "exp.experimentrun.rowid";

    void clearContainer();

    enum Column
    {
        RowId,
        ProtocolStep,
        LSID,
        Name,
        Protocol,
        Comments,
        Created,
        CreatedBy,
        Modified,
        ModifiedBy,
        Container,
        FilePathRoot,
        Flag,
        Links,
        RunGroups
    }

    void populate(ExpSchema schema);
    /**
     * Add a filter to only show runs using the specified protocol.
     */
    void setProtocol(ExpProtocol protocol);
    ExpProtocol getProtocol();

    void setProtocolPatterns(String... pattern);

    void setExperiment(ExpExperiment experiment);
    ExpExperiment getExperiment();

    void setInputMaterial(ExpMaterial material);
    ExpMaterial getInputMaterial();

    void setInputData(ExpData expData);
    ExpData getInputData();

    void setRuns(List<ExpRun> runs);


    /**
     * Returns a column which links to a data input of the specified type.
     * @param
     */
    ColumnInfo addDataInputColumn(String alias, PropertyDescriptor pd);

    /**
     * Returns a column which displays the number of data inputs of the specified role.
     * Returns 0 if pd in null. 
     */
    ColumnInfo addDataCountColumn(String alias, PropertyDescriptor pd);

    /**
     * Returns a column which is a lookup to a table which has columns for input datas and materials from the run protocol app.
     */
    ColumnInfo createInputLookupColumn(String alias, ExpSchema schema, Collection<Map.Entry<String, PropertyDescriptor>> dataInputs, Collection<Map.Entry<String, PropertyDescriptor>> materialInputs);
}
