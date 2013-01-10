/*
 * Copyright (c) 2011 LabKey Corporation
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Oct 12, 2011
 * Time: 7:23:43 PM
 */
public class ViewCategory extends Entity
{
    private int _rowId;
    private String _label;
    private int _displayOrder;
    private transient WeakReference<ViewCategory> _parent;

    public ViewCategory()
    {
        this(null, 0, 0, null);
    }

    protected ViewCategory(String label, int rowId, int displayOrder, ViewCategory parent)
    {
        _label = label;
        _rowId = rowId;
        _displayOrder = displayOrder;
        _parent = new WeakReference<ViewCategory>(parent);
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
        return user.isAdministrator();
    }

    public boolean canDelete(Container container, User user)
    {
        return canEdit(container, user);
    }

    public boolean canRead(Container container, User user)
    {
        return container.hasPermission(user, ReadPermission.class);
    }

    public ViewCategory getParent()
    {
        ViewCategory parent = _parent == null ? null : _parent.get();
        return parent;
    }

    public void setParent(ViewCategory parent)
    {
        _parent = new WeakReference<ViewCategory>(parent);
    }

    public List<ViewCategory> getSubcategories()
    {
        return ViewCategoryManager.getInstance().getSubCategories(this);
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

        return o;
    }

    public static ViewCategory fromJSON(JSONObject info)
    {
        ViewCategory category = new ViewCategory();

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
            ViewCategory parentCategory = ViewCategoryManager.getInstance().getCategory((Integer)parent);
            if (parentCategory != null)
                category.setParent(parentCategory);
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
}

