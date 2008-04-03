package org.labkey.biotrue.datamodel;

public class Entity
{
    int _rowId;
    int _serverId;
    int _parentId;
    String _biotrue_id;
    String _biotrue_type;
    String _biotrue_name;
    String _physicalName;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int id)
    {
        _rowId = id;
    }

    public int getServerId()
    {
        return _serverId;
    }

    public void setServerId(int id)
    {
        _serverId = id;
    }

    public int getParentId()
    {
        return _parentId;
    }

    public void setParentId(int id)
    {
        _parentId = id;
    }

    public String getBioTrue_Id()
    {
        return _biotrue_id;
    }

    public void setBioTrue_Id(String name)
    {
        _biotrue_id = name;
    }

    public String getBioTrue_Type()
    {
        return _biotrue_type;
    }

    public void setBioTrue_Type(String ent)
    {
        _biotrue_type = ent;
    }

    public String getBioTrue_Name()
    {
        return _biotrue_name;
    }

    public void setBioTrue_Name(String name)
    {
        _biotrue_name = name;
    }

    public String getPhysicalName()
    {
        return _physicalName;
    }

    public void setPhysicalName(String name)
    {
        _physicalName = name;
    }
}
