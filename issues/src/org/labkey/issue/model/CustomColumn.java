package org.labkey.issue.model;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.issue.ColumnType;

/**
 * Created by klum on 4/19/2016.
 */
public class CustomColumn
{
    private Container _container;
    private String _name;
    private String _caption;
    private boolean _pickList;
    private Class<? extends Permission> _permissionClass;
    private boolean _inherited;

    // Used via reflection by data access layer
    @SuppressWarnings({"UnusedDeclaration"})
    public CustomColumn()
    {
    }

    public CustomColumn(Container container, String name, String caption, boolean pickList, Class<? extends Permission> permissionClass)
    {
        setContainer(container);
        setName(name);
        setCaption(caption);
        setPickList(pickList);
        setPermission(permissionClass);
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        assert name.equals(name.toLowerCase());
        _name = name;
    }

    public String getCaption()
    {
        return _caption;
    }

    public void setCaption(String caption)
    {
        _caption = caption;
    }

    public boolean isPickList()
    {
        return _pickList;
    }

    public void setPickList(boolean pickList)
    {
        _pickList = pickList;
    }

    public Class<? extends Permission> getPermission()
    {
        return _permissionClass;
    }

    public void setPermission(Class<? extends Permission> permissionClass)
    {
        _permissionClass = permissionClass;
    }

    public boolean hasPermission(User user)
    {
        return _container.hasPermission(user, _permissionClass);
    }

    public void setInherited(boolean isInherited)
    {
        _inherited = isInherited;
    }

    public boolean isInherited()
    {
        return _inherited;
    }
}
