/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.labkey.api.exp.api;

import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;

public class DataColor extends Entity
{
    private int _rowId;
    private String _label;
    private String _color; // hex value
    private boolean _archived;

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

    public String getColor()
    {
        return _color;
    }

    public void setColor(String color)
    {
        _color = color;
    }

    public boolean isArchived()
    {
        return _archived;
    }

    public void setArchived(boolean archived)
    {
        _archived = archived;
    }

    public Container getContainer()
    {
        return lookupContainer();
    }
}
