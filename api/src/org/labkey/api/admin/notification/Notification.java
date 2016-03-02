/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.admin.notification;

import org.labkey.api.data.Transient;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;

import java.util.Date;

/**
 * User: cnathe
 * Date: 9/14/2015
 */
public class Notification
{
    private int _rowId;
    private GUID _container;
    private int _userId;
    private String _objectId;
    private String _type;
    private Date _readOn;
    private String _actionLinkText;
    private String _actionLinkURL;
    private String _content;
    private String _contentType;

    public Notification()
    {}

    public Notification(String objectId, String type, User user)
    {
        _objectId = objectId;
        _type = type;
        _userId = user.getUserId();
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getUserId()
    {
        return _userId;
    }

    public void setUserId(int userId)
    {
        _userId = userId;
    }

    public String getObjectId()
    {
        return _objectId;
    }

    public void setObjectId(String objectId)
    {
        _objectId = objectId;
    }

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }

    public GUID getContainer()
    {
        return _container;
    }

    public void setContainer(GUID container)
    {
        _container = container;
    }

    @Deprecated
    public void setDescription(String description)
    {
        _content = description;
        _contentType = "text/plain";
    }

    public Date getReadOn()
    {
        return _readOn;
    }

    public void setReadOn(Date readOn)
    {
        _readOn = readOn;
    }

    public String getActionLinkText()
    {
        return _actionLinkText;
    }

    public void setActionLinkText(String actionLinkText)
    {
        _actionLinkText = actionLinkText;
    }

    public String getActionLinkURL()
    {
        return _actionLinkURL;
    }

    public void setActionLinkURL(String actionLinkURL)
    {
        _actionLinkURL = actionLinkURL;
    }

    public String getContent()
    {
        return _content;
    }

    public void setContent(String content)
    {
        _content = content;
    }

    public String getContentType()
    {
        return _contentType;
    }

    public void setContentType(String contentType)
    {
        _contentType = contentType;
    }

    public void setContent(String content, String contentType)
    {
        setContent(content);
        setContentType(contentType);
    }

    @Transient
    public String getHtmlContent()
    {
        String content = getContent();
        if ("text/html".equals(getContentType()))
            return content;
        else
            return PageFlowUtil.filter(content, true, true);
    }
}
