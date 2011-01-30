package org.labkey.api.gwt.client.model;

import java.io.Serializable;

/**
 * User: jeckels
 * Date: Jan 28, 2011
 */
public class GWTContainer implements Serializable
{
    private String _entityId;
    private int _rowId;
    private GWTContainer _parent;
    private String _name;

    /** Required for GWT serialization */
    @SuppressWarnings({"UnusedDeclaration"})
    public GWTContainer()
    {
    }

    public GWTContainer(String entityId, int rowId, GWTContainer parent, String name)
    {
        _entityId = entityId;
        _rowId = rowId;
        _parent = parent;
        _name = name;
    }

    public String getEntityId()
    {
        return _entityId;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public GWTContainer getParent()
    {
        return _parent;
    }

    public String getName()
    {
        return _name;
    }

    public String getPath()
    {
        if (_parent != null)
        {
            return _parent.getPath() + "/" + getName();
        }
        return getName();
    }

    @Override
    public String toString()
    {
        return getPath();
    }
}
