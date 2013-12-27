package org.labkey.study.model;

import org.labkey.api.study.Product;

/**
 * User: cnathe
 * Date: 12/26/13
 */
public class ProductImpl implements Product
{
    private int _rowId;
    private String _label;
    private String _role;
    private String _type;

    public ProductImpl()
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

    public String getRole()
    {
        return _role;
    }

    public void setRole(String role)
    {
        _role = role;
    }

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }
}
