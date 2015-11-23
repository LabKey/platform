package org.labkey.api.gwt.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * User: kevink
 * Date: 11/9/15
 */
public class GWTIndex implements IsSerializable, Serializable
{
    private List<String> _columnNames;
    private boolean _unique;

    public GWTIndex() { }

    public GWTIndex(List<String> columnNames, boolean unique)
    {
        _columnNames = columnNames;
        _unique = unique;
    }

    public GWTIndex(GWTIndex other)
    {
        setColumnNames(new ArrayList<String>(other.getColumnNames()));
        setUnique(other.isUnique());
    }

    public GWTIndex copy()
    {
        return new GWTIndex(this);
    }


    public List<String> getColumnNames()
    {
        return _columnNames;
    }

    public void setColumnNames(List<String> columnNames)
    {
        _columnNames = columnNames;
    }

    public boolean isUnique()
    {
        return _unique;
    }

    public void setUnique(boolean unique)
    {
        _unique = unique;
    }
}
