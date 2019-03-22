/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.api.data.views;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * User: klum
 * Date: Apr 2, 2012
 */
public class DefaultViewInfo implements DataViewInfo
{
    private String _id;
    private String _reportId;
    private DataViewProvider.Type _dataType;
    private String _name;
    private Container _container;

    private String _type;
    private String _description;

    private User _createdBy;
    private User _modifiedBy;
    private User _author;
    private Date _created;
    private Date _modified;
    private Date _contentModified;

    private ActionURL _runUrl;
    private String _runTarget;
    private URLHelper _thumbnailUrl;
    private ActionURL _detailsUrl;

    private URLHelper _iconUrl;
    private String _defaultIconCls;
    private URLHelper _defaultThumbnailUrl;
    private String _iconCls;
    private ViewCategory _category;
    private boolean _visible = true;
    private boolean _shared = true;
    private boolean _showInDashboard;
    private boolean _readOnly;
    private String _access;
    private ActionURL _accessUrl;
    private boolean _allowCustomThumbnail = false;

    private String _schemaName;
    private String _queryName;

    private int _displayOrder;

    private List<Pair<DomainProperty, Object>> _tags = Collections.emptyList();

    public DefaultViewInfo(DataViewProvider.Type dataType, String id, String name, Container container)
    {
        _dataType = dataType;
        _id = id;
        _name = name;
        _container = container;
    }

    public String getId()
    {
        return _id;
    }

    public DataViewProvider.Type getDataType()
    {
        return _dataType;
    }

    public String getReportId()
    {
        return _reportId;
    }

    public void setReportId(String reportId)
    {
        _reportId = reportId;
    }

    public String getName()
    {
        return _name;
    }

    public Container getContainer()
    {
        return _container;
    }

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
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

    public Date getContentModified()
    {
        return _contentModified;
    }

    public void setContentModified(Date contentModified)
    {
        _contentModified = contentModified;
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

    public URLHelper getThumbnailUrl()
    {
        return _thumbnailUrl;
    }

    public void setThumbnailUrl(URLHelper thumbnailUrl)
    {
        _thumbnailUrl = thumbnailUrl;
    }

    public ActionURL getDetailsUrl()
    {
        return _detailsUrl;
    }

    public void setDetailsUrl(ActionURL detailsUrl)
    {
        _detailsUrl = detailsUrl;
    }

    public String getDefaultIconCls()
    {
        return _defaultIconCls;
    }

    public void setDefaultIconCls(String defaultIconCls)
    {
        _defaultIconCls = defaultIconCls;
    }

    public URLHelper getDefaultThumbnailUrl()
    {
        return _defaultThumbnailUrl;
    }

    public void setDefaultThumbnailUrl(URLHelper defaultThumbnailUrl)
    {
        _defaultThumbnailUrl = defaultThumbnailUrl;
    }

    public URLHelper getIconUrl()
    {
        return _iconUrl;
    }

    public void setIconUrl(URLHelper iconUrl)
    {
        _iconUrl = iconUrl;
    }

    public String getIconCls()
    {
        return _iconCls;
    }

    public void setIconCls(String iconCls)
    {
        _iconCls = iconCls;
    }

    public ViewCategory getCategory()
    {
        return _category;
    }

    public void setCategory(ViewCategory category)
    {
        _category = category;
    }

    public boolean isVisible()
    {
        return _visible;
    }

    public boolean showInDashboard()
    {
        return _showInDashboard;
    }

    public void setShowInDashboard(boolean showInDashboard)
    {
        _showInDashboard = showInDashboard;
    }

    public void setVisible(boolean visible)
    {
        _visible = visible;
    }

    public boolean isReadOnly()
    {
        return _readOnly;
    }

    public void setReadOnly(boolean readOnly)
    {
        _readOnly = readOnly;
    }

    public void setTags(List<Pair<DomainProperty, Object>> tags)
    {
        _tags = tags;
    }

    @Override
    public List<Pair<DomainProperty, Object>> getTags()
    {
        return _tags;
    }

    public User getAuthor()
    {
        if (_author != null) return _author;
        if (_createdBy != null) return _createdBy;

        return _author;
    }

    public void setAuthor(User author)
    {
        _author = author;
    }

    public String getAccess()
    {
        return _access;
    }

    public void setAccess(String access)
    {
        setAccess(access, null);
    }

    public void setAccess(String access, @Nullable ActionURL url)
    {
        _access = access;
        _accessUrl = url;
    }

    public ActionURL getAccessUrl()
    {
        return _accessUrl;
    }

    public boolean isShared()
    {
        return _shared;
    }

    public void setShared(boolean shared)
    {
        _shared = shared;
    }

    public boolean isAllowCustomThumbnail()
    {
        return _allowCustomThumbnail;
    }

    public void setAllowCustomThumbnail(boolean allowCustomThumbnail)
    {
        _allowCustomThumbnail = allowCustomThumbnail;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    public int getDisplayOrder() {return _displayOrder; }

    public void setDisplayOrder(int displayOrder) {_displayOrder = displayOrder; }
}
