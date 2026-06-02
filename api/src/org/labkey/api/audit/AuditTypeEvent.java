/*
 * Copyright (c) 2013-2026 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.Group;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.ExceptionUtil;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bean object to capture audit log entries. Will be used to populate the database tables via get/set methods that
 * align with column names in the corresponding provisioned table.
 */
public class AuditTypeEvent
{
    protected static final String CREATED_BY_KEY = "auditEventCreatedBy";
    protected static final String IMPERSONATED_BY_KEY = "impersonatedBy";
    protected static final String CONTAINER_KEY = "container";
    protected static final String PROJECT_KEY = "project";
    protected static final String COMMENT_KEY = "comment";
    private static final String USER_COMMENT_KEY = "userComment";

    // long type used here to allow for DbSequences to supply the rowId
    private long _rowId;
    private Integer _impersonatedBy;
    private String _comment;
    private Container _projectId;
    private Container _container;
    private String _eventType;
    private Date _created;
    private User _createdBy;
    private Date _modified;
    private User _modifiedBy;
    private String _userComment;
    private Long _transactionId;

    public AuditTypeEvent(@NotNull String eventType, @NotNull Container container, @Nullable String comment)
    {
        _eventType = eventType;
        if (container == null)
        {
            ExceptionUtil.logExceptionToMothership(null, new IllegalStateException("Audit event container is null"));
        }
        _container = container;
        _comment = comment;
        _projectId = container.getProject();
    }

    /** Important for reflection-based instantiation */
    public AuditTypeEvent()
    {
    }

    public long getRowId()
    {
        return _rowId;
    }

    public void setRowId(long rowId)
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

    public Container getProjectId()
    {
        return _projectId;
    }

    public void setProjectId(Container projectId)
    {
        _projectId = projectId;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
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

    public void setUserComment(String userComment)
    {
        _userComment = userComment;
    }

    public String getUserComment()
    {
        return _userComment;
    }

    public Long getTransactionId()
    {
        return _transactionId;
    }

    public void setTransactionId(Long transactionId)
    {
        _transactionId = transactionId;
    }

    public void setTransactionEvent(@Nullable TransactionAuditProvider.TransactionAuditEvent transactionEvent, String auditEventType)
    {
        if (transactionEvent == null)
            return;

        _transactionId = transactionEvent.getRowId();
        transactionEvent.addDetail(TransactionAuditProvider.TransactionDetail.AuditEvents, auditEventType);
    }

    protected String getContainerMessageElement(@NotNull Container container)
    {
        String value = " (" + container.getId() + ")";
        value = container.getPath() + value;
        return value;
    }

    protected String getUserMessageElement(@NotNull User user)
    {
        return user.getEmail() + " (" + user.getUserId() + ")";
    }

    protected String getUserMessageElement(int userId)
    {
        String value = " (" + userId + ")";
        User user = UserManager.getUser(userId);
        if (user != null)
            value = user.getEmail() + value;
        return value;
    }

    protected String getGroupMessageElement(@NotNull Group group)
    {
        return group.getName() + " (" + group.getUserId() + ")";
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
        Container container = getContainer();
        elements.put(CONTAINER_KEY, getContainerMessageElement(container));
        Container projectId = getProjectId();
        if (projectId != null)
            elements.put(PROJECT_KEY, getContainerMessageElement(projectId));
        if (getComment() != null)
            elements.put(COMMENT_KEY, getComment());
        if (getUserComment() != null)
            elements.put(USER_COMMENT_KEY, getUserComment());

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
