/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
package org.labkey.api.reports.model;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;

import java.util.List;

/**
 * User: klum
 * Date: Oct 12, 2011
 * Time: 7:23:43 PM
 */
public class ViewCategory extends Entity implements Comparable
{
    private int _rowId;
    private String _label;
    private int _displayOrder;
    private Integer _parentId;

    public ViewCategory()
    {
    }

    protected ViewCategory(String label, Container c, @Nullable ViewCategory parent)
    {
        _label = label;
        setContainer(c.getId());
        if (parent != null)
            _parentId = parent.getRowId();
    }

    public boolean isNew()
    {
        return _rowId == 0;
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

    public int getDisplayOrder()
    {
        return _displayOrder;
    }

    public void setDisplayOrder(int displayOrder)
    {
        _displayOrder = displayOrder;
    }

    public boolean canEdit(Container container, User user)
    {
        return container.hasPermission(user, AdminPermission.class);
    }

    public boolean canDelete(Container container, User user)
    {
        return canEdit(container, user);
    }

    public boolean canRead(Container container, User user)
    {
        return container.hasPermission(user, ReadPermission.class);
    }

    @Nullable
    public ViewCategory getParentCategory()
    {
        if (_parentId != null)
            return ViewCategoryManager.getInstance().getCategory(getContainerId(), _parentId);
        return null;
    }

    public void setParentCategory(ViewCategory parent)
    {
        _parentId = parent != null ? parent.getRowId() : null;
    }

    @Nullable
    public Integer getParent()
    {
        return _parentId;
    }

    public void setParent(Integer id)
    {
        _parentId = id;
    }

    public List<ViewCategory> getSubcategories()
    {
        return ViewCategoryManager.getInstance().getSubcategories(this);
    }

    public JSONObject toJSON(User currentUser)
    {
        JSONObject o = new JSONObject();

        o.put("rowid", getRowId());
        o.put("label", getLabel());
        o.put("displayOrder", getDisplayOrder());


        o.put("created", getCreated());
        o.put("modified", getModified());

        User user = UserManager.getUser(getCreatedBy());
        o.put("createdBy", createDisplayValue(getCreatedBy(), user != null ? user.getDisplayName(currentUser) : getCreatedBy()));

        User modifiedBy = UserManager.getUser(getModifiedBy());
        o.put("modifiedBy", createDisplayValue(getModifiedBy(), modifiedBy != null ? modifiedBy.getDisplayName(currentUser) : getModifiedBy()));

        JSONArray subCategories = new JSONArray();
        for (ViewCategory sc : getSubcategories())
        {
            subCategories.put(sc.toJSON(currentUser));
        }
        o.put("subCategories", subCategories);

        o.put("parent", null != getParentCategory() ? getParentCategory().getRowId() : -1);

        return o;
    }

    public static ViewCategory fromJSON(Container c, JSONObject info)
    {
        ViewCategory category = new ViewCategory();

        category.setContainer(c.getId());

        Object row = info.get("rowid");
        if (row instanceof Integer)
            category.setRowId((Integer)row);

        Object label = info.get("label");
        if (label instanceof String)
            category.setLabel((String)label);

        Object displayOrder = info.get("displayOrder");
        if (displayOrder instanceof Integer)
            category.setDisplayOrder((Integer)displayOrder);

        Object parent = info.get("parent");
        if (parent instanceof Integer)
        {
            category.setParent((Integer)parent);
        }
        return category;
    }

    private JSONObject createDisplayValue(Object value, Object displayValue)
    {
        JSONObject json = new JSONObject();

        json.put("value", value);
        json.put("displayValue", displayValue);

        return json;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == null || getClass() != o.getClass()) return false;

        final ViewCategory vc = (ViewCategory) o;

        return vc.getRowId() == _rowId;
    }

    @Override
    public int hashCode()
    {
        return new HashCodeBuilder(23,61).append(super.hashCode()).append(_rowId).toHashCode();
    }

    @Override
    public int compareTo(Object o)
    {
        if (o == null || getClass() != o.getClass()) return -1;

        final ViewCategory vc = (ViewCategory) o;

        return ((Integer) getDisplayOrder()).compareTo(vc.getDisplayOrder());
    }
}

