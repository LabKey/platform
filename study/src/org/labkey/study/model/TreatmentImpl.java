package org.labkey.study.model;

import org.labkey.api.study.Treatment;

/**
 * User: cnathe
 * Date: 12/27/13
 */
public class TreatmentImpl implements Treatment
{
    private int _rowId;
    private String _label;
    private String _description;

    public TreatmentImpl()
    {
    }

    public boolean isNew()
    {
        return _rowId == 0;
    }

    public Object getPrimaryKey()
    {
        return getRowId();
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }
}
