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
package org.labkey.api.reports.model;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.view.ActionURL;

import java.util.Date;

/**
 * User: klum
 * Date: Aug 12, 2011
 * Time: 1:02:57 PM
 */
public class ViewInfo
{
    private String _name;
    private ReportIdentifier _reportId;
    private String _entityId;
    private String _query;
    private String _queryLabel;
    private String _schema;
    private String _category;
    private int _categoryDisplayOrder;
    private User _createdBy;
    private User _modifiedBy;
    private User _author;
    private Date _created;
    private Date _modified;
    private Date _refreshDate;
    private DataType _dataType;
    private String _type;
    private Boolean _editable;
    private Boolean _inherited;
    private Boolean _queryView;
    private boolean _shared;
    private String _version;

    private ActionURL _editUrl;
    private ActionURL _runUrl;
    private ActionURL _infoUrl;
    private ActionURL _detailsUrl;
    private String _runTarget;

    private String _description;
    private Container _container;
    private String _permissions;
    private String _icon;
    private ActionURL _thumbnailUrl;
    private boolean _hidden;
    private int _displayOrder;
    private Status _status = Status.None;

    public enum DataType {
        reports,
        datasets,
        queries,
    }

    public enum Status {
        None,
        Draft,
        Final,
        Locked,
        Unlocked,
    }

    public ViewInfo(String name, String type)
    {
        _name = name;
        _type = type;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public ReportIdentifier getReportId()
    {
        return _reportId;
    }

    public void setReportId(ReportIdentifier reportId)
    {
        _reportId = reportId;
    }

    public String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(String entityId)
    {
        _entityId = entityId;
    }

    public String getQuery()
    {
        return _query;
    }

    public void setQuery(String query)
    {
        _query = query;
    }

    public String getQueryLabel()
    {
        return _queryLabel != null ? _queryLabel : _query;
    }

    public void setQueryLabel(String queryLabel)
    {
        _queryLabel = queryLabel;
    }

    public String getSchema()
    {
        return _schema;
    }

    public void setSchema(String schema)
    {
        _schema = schema;
    }

    public User getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(User createdBy)
    {
        _createdBy = createdBy;
    }

    public User getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(User modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public User getAuthor()
    {
        return _author != null ? _author : _createdBy;
    }

    public void setAuthor(User author)
    {
        _author = author;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }

    public Boolean getEditable()
    {
        return _editable;
    }

    public void setEditable(Boolean editable)
    {
        _editable = editable;
    }

    public Boolean getInherited()
    {
        return _inherited;
    }

    public void setInherited(Boolean inherited)
    {
        _inherited = inherited;
    }

    public String getVersion()
    {
        return _version;
    }

    public void setVersion(String version)
    {
        _version = version;
    }

    public ActionURL getEditUrl()
    {
        return _editUrl;
    }

    public void setEditUrl(ActionURL editUrl)
    {
        _editUrl = editUrl;
    }

    public ActionURL getRunUrl()
    {
        return _runUrl;
    }

    public void setRunUrl(ActionURL runUrl)
    {
        _runUrl = runUrl;
    }

    public String getRunTarget()
    {
        return _runTarget;
    }

    public void setRunTarget(String target)
    {
        _runTarget = target;
    }

    public ActionURL getDetailsUrl()
    {
        return _detailsUrl;
    }

    public void setDetailsUrl(ActionURL detailsUrl)
    {
        _detailsUrl = detailsUrl;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public String getPermissions()
    {
        return _permissions;
    }

    public void setPermissions(String permissions)
    {
        _permissions = permissions;
    }

    public String getIcon()
    {
        return _icon;
    }

    public void setIcon(String icon)
    {
        _icon = icon;
    }

    public ActionURL getThumbnailUrl()
    {
        return _thumbnailUrl;
    }

    public void setThumbnailUrl(ActionURL thumbnailUrl)
    {
        _thumbnailUrl = thumbnailUrl;
    }

    public String getCategory()
    {
        return _category;
    }

    public void setCategory(String category)
    {
        _category = category;
    }

    public int getCategoryDisplayOrder()
    {
        return _categoryDisplayOrder;
    }

    public void setCategoryDisplayOrder(int categoryDisplayOrder)
    {
        _categoryDisplayOrder = categoryDisplayOrder;
    }

    public Boolean getQueryView()
    {
        return _queryView;
    }

    public void setQueryView(Boolean queryView)
    {
        _queryView = queryView;
    }

    public boolean isHidden()
    {
        return _hidden;
    }

    public void setHidden(boolean hidden)
    {
        _hidden = hidden;
    }

    public int getDisplayOrder()
    {
        return _displayOrder;
    }

    public void setDisplayOrder(int displayOrder)
    {
        _displayOrder = displayOrder;
    }

    public DataType getDataType()
    {
        return _dataType;
    }

    public void setDataType(DataType dataType)
    {
        _dataType = dataType;
    }

    public ActionURL getInfoUrl()
    {
        return _infoUrl;
    }

    public void setInfoUrl(ActionURL infoUrl)
    {
        _infoUrl = infoUrl;
    }

    public Status getStatus()
    {
        return _status;
    }

    public void setStatus(Status status)
    {
        _status = status;
    }

    public Date getRefreshDate()
    {
        return _refreshDate;
    }

    public void setRefreshDate(Date refreshDate)
    {
        _refreshDate = refreshDate;
    }

    public boolean isShared()
    {
        return _shared;
    }

    public void setShared(boolean shared)
    {
        _shared = shared;
    }

    public JSONObject toJSON(User user)
    {
        return toJSON(user, null);
    }

    public JSONObject toJSON(User user, String dateFormat)
    {
        JSONObject o = new JSONObject();

        if(dateFormat == null)
        {
            dateFormat = DateUtil.getStandardDateFormatString();
        }

        o.put("name", getName());
        o.put("type", getType());
        o.put("dataType", getDataType());
        o.put("hidden", isHidden());
        o.put("shared", isShared());
        o.put("displayOrder", getDisplayOrder());
        o.put("status", getStatus().name());

        if (getEditable() != null)
            o.put("editable", getEditable());
        if (getInherited() != null)
            o.put("inherited", getInherited());
        if (getQueryView() != null)
            o.put("queryView", getQueryView());

        if (getEntityId() != null)
            o.put("entityId", getEntityId().toString());
        if (getReportId() != null)
            o.put("reportId", getReportId().toString());
        if (getQuery() != null)
            o.put("query", getQuery());
        if (getQueryLabel() != null)
            o.put("queryLabel", getQueryLabel());
        if (getSchema() != null)
            o.put("schema", getSchema());

        if (getCreatedBy() != null)
        {
            o.put("createdBy", getCreatedBy().getDisplayName(user));
            // temporary, refactor in 12.1
            o.put("createdByUserId", getCreatedBy().getUserId());
        }
        if (getModifiedBy() != null)
            o.put("modifiedBy", getModifiedBy().getDisplayName(user));

        o.put("author", createUserObject(getAuthor(), user));

        if (getCreated() != null)
            o.put("created", DateUtil.formatDateTime(getCreated(), dateFormat));
        if (getModified() != null)
            o.put("modified", DateUtil.formatDateTime(getModified(), dateFormat));
        if (getRefreshDate() != null)
            o.put("refreshDate", DateUtil.formatDateTime(getRefreshDate(), dateFormat));
        
        if (getVersion() != null)
            o.put("version", getVersion());

        if (getEditUrl() != null)
            o.put("editUrl", getEditUrl().getLocalURIString());
        if (getRunUrl() != null)
            o.put("runUrl", getRunUrl().getLocalURIString());
        if (getRunTarget() != null)
            o.put("runTarget", getRunTarget());
        if (getInfoUrl() != null)
            o.put("infoUrl", getInfoUrl().getLocalURIString());
        if (getDetailsUrl() != null)
            o.put("detailsUrl", getDetailsUrl().getLocalURIString());

        if (getDescription() != null)
            o.put("description", getDescription());
        if (getCategory() != null)
        {
            o.put("category", getCategory());
            o.put("categoryDisplayOrder", getCategoryDisplayOrder());
        }
        
        if (getContainer() != null)
            o.put("container", getContainer().getPath());

        if (getPermissions() != null)
            o.put("permissions", getPermissions());
        if (getIcon() != null)
            o.put("icon", getIcon());
        if (getThumbnailUrl() != null)
            o.put("thumbnail", getThumbnailUrl().getLocalURIString());

        return o;
    }

    private JSONObject createUserObject(User user, User currentUser)
    {
        JSONObject json = new JSONObject();

        json.put("userId", user != null ? user.getUserId() : "");
        json.put("displayName", user != null ? user.getDisplayName(currentUser) : "");

        return json;
    }
}
