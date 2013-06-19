/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
package org.labkey.study.model;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.ParticipantCategory;
import org.labkey.api.study.permissions.SharedParticipantGroupPermission;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

/**
 * User: klum
 * Date: Jun 8, 2011
 * Time: 2:49:27 PM
 */

/**
 * Represents a category of participants in related groups.
 */
public class ParticipantCategoryImpl extends Entity implements ParticipantCategory
{
    private int _rowId;
    private boolean _shared;
    private String _label;
    private String _type;
    private boolean _autoUpdate;

    // properties used when the type is a query
    private String _queryName;
    private String _schemaName;
    private String _viewName;

    // properties used when the type is a cohort (column in a dataset)
    private int _datasetId;
    private String _groupProperty;

    private ParticipantGroup[] _groups = new ParticipantGroup[0];

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

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        if (ParticipantCategory.Type.valueOf(type) == null)
            throw new IllegalArgumentException("Invalid ParticipantCategory type");
        
        _type = type;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getViewName()
    {
        return _viewName;
    }

    public void setViewName(String viewName)
    {
        _viewName = viewName;
    }

    public boolean isShared()
    {
        return _shared;
    }

    public void setShared(boolean shared)
    {
        _shared = shared;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public boolean isAutoUpdate()
    {
        return _autoUpdate;
    }

    public void setAutoUpdate(boolean autoUpdate)
    {
        _autoUpdate = autoUpdate;
    }

    public int getDatasetId()
    {
        return _datasetId;
    }

    public void setDatasetId(int datasetId)
    {
        _datasetId = datasetId;
    }

    public String getGroupProperty()
    {
        return _groupProperty;
    }

    public void setGroupProperty(String groupProperty)
    {
        _groupProperty = groupProperty;
    }

    public ParticipantGroup[] getGroups()
    {
        return _groups;
    }

    @Override
    public String[] getGroupNames()
    {
        String[] groupNames = new String[_groups.length];
        int i = 0;

        for (ParticipantGroup group : _groups)
            groupNames[i++] = group.getLabel();

        return groupNames;
    }

    public void setGroups(ParticipantGroup[] groups)
    {
        _groups = groups;
    }

    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        ViewContext context = HttpView.currentContext();
        User currentUser = context != null ? context.getUser() : null;

        json.put("rowId", getRowId());
        json.put("shared", isShared());
        json.put("label", getLabel());
        json.put("type", getType());
        json.put("autoUpdate", isAutoUpdate());
        json.put("created", getCreated());
        json.put("modified", getModified());

        if (context != null)
        {
            json.put("canEdit", canEdit(context.getContainer(), currentUser));
            json.put("canDelete", canDelete(context.getContainer(), currentUser));
        }
        User user = UserManager.getUser(getCreatedBy());
        json.put("createdBy", createDisplayValue(getCreatedBy(), user != null ? user.getDisplayName(currentUser) : getCreatedBy()));

        User modifiedBy = UserManager.getUser(getModifiedBy());
        json.put("modifiedBy", createDisplayValue(getModifiedBy(), modifiedBy != null ? modifiedBy.getDisplayName(currentUser) : getModifiedBy()));

        if (ParticipantCategory.Type.query.equals(ParticipantCategory.Type.valueOf(getType())))
        {
            json.put("queryName", getQueryName());
            json.put("schemaName", getSchemaName());
            json.put("viewName", getViewName());
        }

        if (ParticipantCategory.Type.cohort.equals(ParticipantCategory.Type.valueOf(getType())))
        {
            json.put("datasetId", getDatasetId());
            json.put("groupProperty", getGroupProperty());
        }

        // special case simple group list for now
        JSONArray ptids = new JSONArray();
        if (_groups.length == 1)
        {
            for (String ptid : _groups[0].getParticipantIds())
            {
                ptids.put(ptid);
            }
        }
        json.put("participantIds", ptids);

        return json;
    }

    public void fromJSON(JSONObject json)
    {
        if (json.has("rowId"))
            setRowId(json.getInt("rowId"));
        if (json.has("shared"))
            setShared(json.getBoolean("shared"));
        if (json.has("label"))
            setLabel(json.getString("label"));
        if (json.has("type"))
            setType(json.getString("type"));
        if (json.has("autoUpdate"))
            setShared(json.getBoolean("autoUpdate"));

        if (json.has("queryName"))
            setLabel(json.getString("queryName"));
        if (json.has("schemaName"))
            setLabel(json.getString("schemaName"));
        if (json.has("viewName"))
            setLabel(json.getString("viewName"));

        if (json.has("datasetId"))
            setDatasetId(json.getInt("datasetId"));
        if (json.has("groupProperty"))
            setGroupProperty(json.getString("groupProperty"));
    }

    private JSONObject createDisplayValue(Object value, Object displayValue)
    {
        JSONObject json = new JSONObject();

        json.put("value", value);
        json.put("displayValue", displayValue);

        return json;
    }

    public void copySpecialFields(ParticipantCategoryImpl copy)
    {
        if (getEntityId() == null)
            setEntityId(copy.getEntityId());
        if (getCreatedBy() == 0)
            setCreatedBy(copy.getCreatedBy());
        if (getCreated() == null)
            setCreated(copy.getCreated());
        if (getContainerId() == null)
            setContainer(copy.getContainerId());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParticipantCategoryImpl that = (ParticipantCategoryImpl) o;

        if (_rowId != that._rowId) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        return _rowId;
    }

    public boolean canEdit(Container container, User user)
    {
        if (isShared())
            return container.hasPermission(user, SharedParticipantGroupPermission.class) || 
                    container.hasPermission(user, AdminPermission.class);
        else
        {
            if (isNew())
                return true;
            else
            {
                User owner = UserManager.getUser(getCreatedBy());
                return (owner != null && !owner.isGuest()) ? owner.equals(user) : false;
            }
        }
    }

    public boolean canDelete(Container container, User user)
    {
        return canEdit(container, user);
    }
    
    public boolean canRead(User user)
    {
        if (!isShared())
        {
            if (isNew())
                return true;
            else
            {
                // issue 16645 : don't show participant groups that may have been created by guests, which was possible
                // before this bug was fixed. When admins have the ability to update and delete private groups we can
                // make guest created groups visible again.
                User owner = UserManager.getUser(getCreatedBy());
                return (owner != null && !owner.isGuest()) ? owner.equals(user) : false;
            }
        }
        return true;
    }
}
