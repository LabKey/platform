package org.labkey.assay.plate.data;

import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.assay.plate.query.WellTable;

import java.util.Collections;
import java.util.Map;

public class WellData
{
    private Integer _col;
    private String _lsid;
    private Map<String, Object> _metadata;
    private String _position;
    private Integer _row;
    private Integer _rowId;
    private Integer _sampleId;
    private WellGroup.Type _type;
    private String _wellGroup;
    // NOTE: If/when adding additional properties update hasData() to include the new properties

    public WellData()
    {
    }

    public Map<String, Object> getData()
    {
        Map<String, Object> data = new CaseInsensitiveHashMap<>();
        if (_position != null)
            data.put(WellTable.WELL_LOCATION, _position);
        if (_sampleId != null)
            data.put(WellTable.Column.SampleID.name(), _sampleId);
        if (_type != null)
            data.put(WellTable.Column.Type.name(), _type.name());
        if (_wellGroup != null)
            data.put(WellTable.Column.WellGroup.name(), _wellGroup);

        for (var entry : getMetadata().entrySet())
        {
            if (entry.getValue() != null)
                data.put(entry.getKey(), entry.getValue());
        }

        return data;
    }

    public boolean hasData()
    {
        // _position is not used when determining if the well data has data
        return _sampleId != null || _type != null && _wellGroup != null || !getMetadata().isEmpty();
    }

    public boolean isReplicate()
    {
        return WellGroup.Type.REPLICATE.equals(getType());
    }

    public boolean isSample()
    {
        return WellGroup.Type.SAMPLE.equals(getType());
    }

    public Integer getCol()
    {
        return _col;
    }

    public void setCol(Integer col)
    {
        _col = col;
    }

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    public Map<String, Object> getMetadata()
    {
        return _metadata == null ? Collections.emptyMap() : _metadata;
    }

    public void setMetadata(Map<String, Object> metadata)
    {
        _metadata = metadata;
    }

    public String getPosition()
    {
        return _position;
    }

    public void setPosition(String position)
    {
        _position = position;
    }

    public Integer getRow()
    {
        return _row;
    }

    public void setRow(Integer row)
    {
        _row = row;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public Integer getSampleId()
    {
        return _sampleId;
    }

    public void setSampleId(Integer sampleId)
    {
        _sampleId = sampleId;
    }

    public WellGroup.Type getType()
    {
        return _type;
    }

    public void setType(WellGroup.Type type)
    {
        _type = type;
    }

    public String getWellGroup()
    {
        return _wellGroup;
    }

    public void setWellGroup(String wellGroup)
    {
        _wellGroup = wellGroup;
    }
}
