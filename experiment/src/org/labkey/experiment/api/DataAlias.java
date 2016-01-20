package org.labkey.experiment.api;

import org.labkey.api.data.Entity;

/**
 * Created by klum on 1/19/2016.
 */
public class DataAlias extends Entity
{
    int _rowId;
    String _name;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }
}
