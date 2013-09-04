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
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.ParticipantCategory;
import org.labkey.api.study.permissions.SharedParticipantGroupPermission;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.List;

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
    private int _ownerId = OWNER_SHARED;
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

    public ParticipantCategoryImpl()
    {
    }

    public ParticipantCategoryImpl(ParticipantCategoryImpl cat)
    {
        this.copy(cat);
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
        return _ownerId == OWNER_SHARED;
    }

    public int getOwnerId()
    {
        return _ownerId;
    }

    public void setOwnerId(int owner)
    {
        _ownerId = owner;
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
        if (json.has("ownerId"))
            setOwnerId(json.getInt("ownerId"));
        if (json.has("label"))
            setLabel(json.getString("label"));
        if (json.has("type"))
            setType(json.getString("type"));

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

    public void copy(ParticipantCategoryImpl copy)
    {
        copySpecialFields(copy);

        _rowId = copy._rowId;
        _ownerId = copy._ownerId;
        _label = copy._label;
        _type = copy._type;
        _autoUpdate = copy._autoUpdate;
        _queryName = copy._queryName;
        _schemaName = copy._schemaName;
        _viewName = copy._viewName;
        _datasetId = copy._datasetId;
        _groupProperty = copy._groupProperty;
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
        return canEdit(container, user, new ArrayList<ValidationError>());
    }

    public boolean canEdit(Container container, User user, List<ValidationError> errors)
    {
        if (isShared())
        {
            if (!container.hasPermission(user, SharedParticipantGroupPermission.class) && !container.hasPermission(user, AdminPermission.class))
                errors.add(new SimpleValidationError("You must be in the Editor role or an Admin to create a shared participant category"));
        }
        else
        {
            if (isNew())
                return true;
            else
            {
                User owner = UserManager.getUser(getCreatedBy());
                boolean allowed = (owner != null && !owner.isGuest()) ? owner.equals(user) : false;

                if (!allowed)
                    errors.add(new SimpleValidationError("You must be the owner to unshare this participant category"));
            }
        }
        return errors.isEmpty();
    }

    public boolean canDelete(Container container, User user)
    {
        return canDelete(container, user, new ArrayList<ValidationError>());
    }
    
    public boolean canDelete(Container container, User user, List<ValidationError> errors)
    {
        if (isShared())
        {
            if (!container.hasPermission(user, SharedParticipantGroupPermission.class) && !container.hasPermission(user, AdminPermission.class))
                errors.add(new SimpleValidationError("You must be in the Editor role or an Admin to delete a shared participant category"));
        }
        else
        {
            if (isNew())
                return true;
            else
            {
                User owner = UserManager.getUser(getCreatedBy());
                boolean allowed = (owner != null && !owner.isGuest()) ? owner.equals(user) : false;

                if (!allowed)
                    errors.add(new SimpleValidationError("You must be the owner to delete this participant category"));
            }
        }
        return errors.isEmpty();
    }

    public boolean canRead(Container c, User user)
    {
        return canRead(c, user, new ArrayList<ValidationError>());
    }

    public boolean canRead(Container c, User user, List<ValidationError> errors)
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
                boolean allowed = (owner != null && !owner.isGuest()) ? owner.equals(user) : false;

                if (!allowed)
                {
                    errors.add(new SimpleValidationError("You don't have permission to read this private participant category"));
                    return false;
                }
            }
        }
        return true;
    }
}
