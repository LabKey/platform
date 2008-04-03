package org.labkey.experiment.api;

import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.*;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnInfo;

import java.util.Map;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * This class is a foreign key which has columns for data and material inputs of various types.
 */
public class InputForeignKey extends LookupForeignKey
{
    ExpSchema _schema;
    Map<String, PropertyDescriptor[]> _dataInputs;
    Map<String, PropertyDescriptor[]> _materialInputs;
    public InputForeignKey(ExpSchema schema, Map<String, PropertyDescriptor> dataInputs, Map<String, PropertyDescriptor> materialInputs)
    {
        this(schema, dataInputs.entrySet(), materialInputs.entrySet());
    }

    public InputForeignKey(ExpSchema schema, Collection<Map.Entry<String, PropertyDescriptor>> dataInputs, Collection<Map.Entry<String, PropertyDescriptor>> materialInputs)
    {
        super(null);
        _schema = schema;
        _dataInputs = inputMap(dataInputs);
        _materialInputs = inputMap(materialInputs);
    }

    private Map<String, PropertyDescriptor[]> inputMap(Collection<Map.Entry<String, PropertyDescriptor>> inputs)
    {
        Map<String, PropertyDescriptor[]> ret = new LinkedHashMap();
        for (Map.Entry<String, PropertyDescriptor> entry : inputs)
        {
            PropertyDescriptor[] existing = ret.get(entry.getKey());
            if (existing == null)
            {
                ret.put(entry.getKey(), new PropertyDescriptor[] { entry.getValue() });
            }
            else
            {
                PropertyDescriptor[] newValue = new PropertyDescriptor[existing.length + 1];
                System.arraycopy(existing, 0, newValue, 0, existing.length);
                newValue[existing.length] = entry.getValue();
                ret.put(entry.getKey(), newValue);
            }
        }
        return ret;
    }

    public TableInfo getLookupTableInfo()
    {
        ExpProtocolApplicationTable ret = ExperimentService.get().createProtocolApplicationTable("l");
        SamplesSchema samplesSchema = _schema.getSamplesSchema();
        for (Map.Entry<String, PropertyDescriptor[]> entry : _dataInputs.entrySet())
        {
            ret.safeAddColumn(ret.createDataInputColumn(entry.getKey(), _schema, entry.getValue()));
        }
        for (Map.Entry<String, PropertyDescriptor[]> entry : _materialInputs.entrySet())
        {
            ExpSampleSet ss = null;
            if (entry.getValue().length == 1 && entry.getValue()[0] != null)
            {
                ss = ExperimentService.get().getSampleSet(entry.getValue()[0].getRangeURI());
            }

            if (ss == null)
            {
                ss = ExperimentService.get().lookupActiveSampleSet(_schema.getContainer());
            }
            ret.safeAddColumn(ret.createMaterialInputColumn(entry.getKey(), samplesSchema, ss, entry.getValue()));
        }
        return ret;
    }

    protected ColumnInfo getPkColumn(TableInfo table)
    {
        assert table instanceof ExpProtocolApplicationTable;
        ExpProtocolApplicationTable appTable = (ExpProtocolApplicationTable) table;
        return appTable.createColumn("~", ExpProtocolApplicationTable.Column.RowId);
    }
}
