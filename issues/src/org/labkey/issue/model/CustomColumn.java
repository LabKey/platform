/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.issue.model;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;

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

    @Override
    public int hashCode()
    {
        int result;

        // intentionally not including container for comparison
        result = (_name != null ? _name.hashCode() : 0);
        result = 31 * result + (_caption != null ? _caption.hashCode() : 0);
        result = 31 * result + (_pickList ? 1 : 0);
        result = 31 * result + (_permissionClass != null ? _permissionClass.getName().hashCode() : 0);
        result = 31 * result + (_inherited ? 1 : 0);

        return result;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CustomColumn that = (CustomColumn) o;
        // intentionally not including container for comparison
        if (_name != null ? !_name.equals(that._name) : that._name != null) return false;
        if (_caption != null ? !_caption.equals(that._caption) : that._caption != null) return false;
        if (_pickList != that._pickList) return false;
        if (_permissionClass != null ? !_permissionClass.getName().equals(that._permissionClass.getName()) : that._permissionClass != null) return false;
        if (_inherited != that._inherited) return false;

        return true;
    }
}
