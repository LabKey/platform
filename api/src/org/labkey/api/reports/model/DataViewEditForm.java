/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.reports.report.ReportIdentifier;

import java.util.Date;

/**
* User: klum
* Date: Mar 16, 2012
*/
public class DataViewEditForm extends ReturnUrlForm
{
    ReportIdentifier _reportId;
    String _viewName;
    String _entityId;
    Integer _category;
    String _description;
    boolean _hidden;
    ViewInfo.DataType _dataType;
    int _author;
    ViewInfo.Status _status;
    Date _refreshDate;
    Date _modifiedDate;
    private Boolean _shared;
    private Boolean _canChangeSharing;

    public String getViewName()
    {
        return _viewName;
    }

    public void setViewName(String viewName)
    {
        _viewName = viewName;
    }

    public Date getRefreshDate()
    {
        return _refreshDate;
    }

    public void setRefreshDate(Date refreshDate)
    {
        _refreshDate = refreshDate;
    }

    public Date getModifiedDate()
    {
        return _modifiedDate;
    }

    public void setModifiedDate(Date modifiedDate)
    {
        _modifiedDate = modifiedDate;
    }

    public ReportIdentifier getReportId()
    {
        return _reportId;
    }

    public void setReportId(ReportIdentifier reportId)
    {
        _reportId = reportId;
    }

    public Integer getCategory()
    {
        return _category;
    }

    public void setCategory(Integer category)
    {
        _category = category;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public boolean isHidden()
    {
        return _hidden;
    }

    public void setHidden(boolean hidden)
    {
        _hidden = hidden;
    }

    public ViewInfo.DataType getDataType()
    {
        return _dataType;
    }

    public void setDataType(ViewInfo.DataType dataType)
    {
        _dataType = dataType;
    }

    public String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(String entityId)
    {
        _entityId = entityId;
    }

    public int getAuthor()
    {
        return _author;
    }

    public void setAuthor(int author)
    {
        _author = author;
    }

    public ViewInfo.Status getStatus()
    {
        return _status;
    }

    public void setStatus(ViewInfo.Status status)
    {
        _status = status;
    }

    public Boolean getShared()
    {
        return _shared;
    }

    public void setShared(Boolean shared)
    {
        _shared = shared;
    }

    public boolean isUpdate()
    {
        return null != _reportId;
    }

    public Boolean getCanChangeSharing()
    {
        return _canChangeSharing;
    }

    public void setCanChangeSharing(Boolean canChangeSharing)
    {
        _canChangeSharing = canChangeSharing;
    }
}
