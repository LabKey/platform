package org.labkey.api.exp.api;

/**
 * Created by Nick Arnold on 2/12/2016.
 */
public class ExpLineageOptions
{
    private int _rowId;
    private String _lsid;
    private int _depth;
    private boolean _parents = true;
    private boolean _children = true;

    public int getDepth()
    {
        return _depth;
    }

    public void setDepth(int depth)
    {
        _depth = depth;
    }

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    public String getLSID()
    {
        return getLsid();
    }

    public void setLSID(String lsid)
    {
        setLsid(lsid);
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public boolean isParents()
    {
        return _parents;
    }

    public void setParents(boolean parents)
    {
        _parents = parents;
    }

    public boolean isChildren()
    {
        return _children;
    }

    public void setChildren(boolean children)
    {
        _children = children;
    }
}
