package org.labkey.api.view;

import org.labkey.api.data.DataRegionSelection;

import java.util.Set;

public class DataViewSelectionForm extends ViewForm
{
    protected String _dataRegionSelectionKey;
    protected Set<Long> _rowIds;

    public String getDataRegionSelectionKey()
    {
        return _dataRegionSelectionKey;
    }

    public void setDataRegionSelectionKey(String dataRegionSelectionKey)
    {
        _dataRegionSelectionKey = dataRegionSelectionKey;
    }

    public Set<Long> getRowIds()
    {
        return _rowIds;
    }

    public void setRowIds(Set<Long> rowIds)
    {
        _rowIds = rowIds;
    }

    public Set<Long> getIds(boolean clear)
    {
        return (_rowIds != null) ? _rowIds : DataRegionSelection.getSelectedIntegers(getViewContext(), getDataRegionSelectionKey(), clear);
    }
}
