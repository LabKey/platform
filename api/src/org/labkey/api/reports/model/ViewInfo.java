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

import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.view.ActionURL;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Aug 12, 2011
 * Time: 1:02:57 PM
 */
public class ViewInfo
{
    private String _name;
    private ReportIdentifier _reportId;
    private String _query;
    private String _schema;
    private String _category;
    private User _createdBy;
    private User _modifiedBy;
    private Date _created;
    private Date _modified;
    private String _type;
    private Boolean _editable;
    private Boolean _inherited;
    private Boolean _queryView;
    private String _version;

    private ActionURL _editUrl;
    private ActionURL _runUrl;

    private String _description;
    private Container _container;
    private String _permissions;
    private String _icon;

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

    public String getQuery()
    {
        return _query;
    }

    public void setQuery(String query)
    {
        _query = query;
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

    public String getCategory()
    {
        return _category;
    }

    public void setCategory(String category)
    {
        _category = category;
    }

    public Boolean getQueryView()
    {
        return _queryView;
    }

    public void setQueryView(Boolean queryView)
    {
        _queryView = queryView;
    }

    public JSONObject toJSON(User user)
    {
        JSONObject o = new JSONObject();

        o.put("name", getName());
        o.put("type", getType());

        if (getEditable() != null)
            o.put("editable", getEditable());
        if (getInherited() != null)
            o.put("inherited", getInherited());
        if (getQueryView() != null)
            o.put("queryView", getQueryView());

        if (getReportId() != null)
            o.put("reportId", getReportId().toString());
        if (getQuery() != null)
            o.put("query", getQuery());
        if (getSchema() != null)
            o.put("schema", getSchema());

        if (getCreatedBy() != null)
            o.put("createdBy", getCreatedBy().getDisplayName(user));
        if (getModifiedBy() != null)
            o.put("modifiedBy", getModifiedBy().getDisplayName(user));

        if (getCreated() != null)
            o.put("created", DateUtil.formatDate(getCreated()));
        if (getModified() != null)
            o.put("modified", DateUtil.formatDate(getModified()));

        if (getVersion() != null)
            o.put("version", getVersion());

        if (getEditUrl() != null)
            o.put("editUrl", getEditUrl().getLocalURIString());
        if (getRunUrl() != null)
            o.put("runUrl", getRunUrl().getLocalURIString());

        if (getDescription() != null)
            o.put("description", getDescription());
        if (getCategory() != null)
            o.put("category", getCategory());
        
        if (getContainer() != null)
            o.put("container", getContainer().getPath());

        if (getPermissions() != null)
            o.put("permissions", getPermissions());
        if (getIcon() != null)
            o.put("icon", getIcon());

        return o;
    }
}
