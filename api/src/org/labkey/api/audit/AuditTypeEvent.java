/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.audit;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bean object to capture audit log entries. Will be used to populate the database tables via get/set methods that
 * align with column names in the corresponding provisioned table.
 * User: klum
 * Date: 7/12/13
 */
public class AuditTypeEvent
{
    protected static final String CREATED_BY_KEY = "auditEventCreatedBy";
    protected static final String IMPERSONATED_BY_KEY = "impersonatedBy";
    protected static final String CONTAINER_KEY = "container";
    protected static final String PROJECT_KEY = "project";
    protected static final String COMMENT_KEY = "comment";

    private int _rowId;
    private Integer _impersonatedBy;
    private String _comment;
    private String _projectId;
    private String _container;
    private String _eventType;
    private Date _created;
    private User _createdBy;
    private Date _modified;
    private User _modifiedBy;

    public AuditTypeEvent(String eventType, Container container, String comment)
    {
        this(eventType, container.getId(), comment);
    }

    public AuditTypeEvent(String eventType, String container, String comment)
    {
        _eventType = eventType;
        _container = container;
        _comment = comment;
    }

    public AuditTypeEvent(){}

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public Integer getImpersonatedBy()
    {
        return _impersonatedBy;
    }

    public void setImpersonatedBy(Integer impersonatedBy)
    {
        _impersonatedBy = impersonatedBy;
    }

    public String getComment()
    {
        return _comment;
    }

    public void setComment(String comment)
    {
        _comment = comment;
    }

    public String getProjectId()
    {
        return _projectId;
    }

    public void setProjectId(String projectId)
    {
        _projectId = projectId;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public String getEventType()
    {
        return _eventType;
    }

    public void setEventType(String eventType)
    {
        _eventType = eventType;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public User getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(User createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public User getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(User modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    protected String getContainerMessageElement(@NotNull String containerId)
    {
        String value = " (" + containerId + ")";
        Container container = ContainerManager.getForId(containerId);
        if (container != null)
            value = container.getPath() + value;
        return value;
    }

    protected String getUserMessageElement(@NotNull Integer userId)
    {
        String value = " (" + userId + ")";
        User user = UserManager.getUser(userId);
        if (user != null)
            value = user.getEmail() + value;
        return value;
    }

    protected String getGroupMessageElement(@NotNull Integer groupId)
    {
        String value = " (" + groupId + ")";
        Group group = SecurityManager.getGroup(groupId);
        if (group != null)
            value = group.getName() + value;
        return value;
    }

    public Map<String, Object> getAuditLogMessageElements()
    {
        Map<String, Object> elements = new LinkedHashMap<>();
        User createdBy = getCreatedBy();
        if (createdBy != null)
        {
            String message = createdBy.getEmail() != null ? createdBy.getEmail() : "";
            message += " (" + createdBy.getUserId() + ")";
            elements.put(CREATED_BY_KEY, message);
        }
        Integer impersonatorId = getImpersonatedBy();
        if (impersonatorId != null)
            elements.put(IMPERSONATED_BY_KEY, getUserMessageElement(impersonatorId));
        String containerId = getContainer();
        if (containerId != null)
            elements.put(CONTAINER_KEY, getContainerMessageElement(containerId));
        String projectId = getProjectId();
        if (projectId != null)
            elements.put(PROJECT_KEY, getContainerMessageElement(projectId));
        if (getComment() != null)
            elements.put(COMMENT_KEY, getComment());

        return elements;
    }

    public String getAuditLogMessage()
    {
        StringBuilder builder = new StringBuilder();
        builder.append(getEventType()).append(" - ");


        Map<String, Object> messageElements = getAuditLogMessageElements();
        for (String key : messageElements.keySet())
        {
            builder.append(key).append(": ").append(messageElements.get(key)).append(" | ");
        }
        return builder.toString();
    }
}
