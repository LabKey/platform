/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CreatedModified;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.GUID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class Job extends CreatedModified implements Identifiable
{
    protected Long _rowId;
    protected GUID _containerId;
    protected Container _container;
    protected GUID _entityId;

    protected String _name;
    protected String _id;
    protected String _description;
    protected Date _startDate;
    protected Date _dueDate;
    protected Integer _priority;
    protected Long _templateId;
    protected Integer _assignee;
    protected List<Integer> _notifyList;
    protected boolean _isTemplate;
    protected Job _template;
    protected Integer _jobCount; // only applies to templates
    protected Integer _domainId;
    protected String _lsid; // needed for attaching domain properties
    protected List<Task> _tasks; // ordered by ordinal value
    protected List<AttachmentFile> _attachments;
    protected List<Map<String, Object>> _attachmentData = new ArrayList<>(); // ?? used for template instead of attachments?
    protected List<WorkEntity> _entities;
    protected final Map<WorkEntity.EntityType, Collection<String>> _entityTypes = new HashMap<>();
    protected Map<String, ObjectProperty> _domainProperties;
    protected Map<String, Object> _domainPropertyValues;
    protected boolean _hasMedia = false;

    public Long getRowId()
    {
        return _rowId;
    }

    public void setRowId(Long rowId)
    {
        _rowId = rowId;
    }

    public GUID getContainerId()
    {
        return _containerId;
    }

    public void setContainerId(GUID containerId)
    {
        _containerId = containerId;
    }

    @JsonIgnore
    public Container getContainer()
    {
        if (_containerId == null)
            return null;
        if (_container == null)
            _container = ContainerManager.getForId(_containerId);
        return _container;
    }

    public GUID getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(GUID entityId)
    {
        _entityId = entityId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getId()
    {
        return _id;
    }

    public void setId(String id)
    {
        _id = id;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public Date getStartDate()
    {
        return _startDate;
    }

    public void setStartDate(Date startDate)
    {
        _startDate = startDate;
    }

    public Date getDueDate()
    {
        return _dueDate;
    }

    public void setDueDate(Date dueDate)
    {
        _dueDate = dueDate;
    }

    public Integer getPriority()
    {
        return _priority;
    }

    public void setPriority(Integer priority)
    {
        _priority = priority;
    }

    public Long getTemplateId()
    {
        return _templateId;
    }

    public void setTemplateId(Long templateId)
    {
        _templateId = templateId;
    }

    @JsonProperty("template")
    public abstract Job getTemplate(Container container, User user);

    public static JSONObject getAssigneeJSON(Integer assigneeId)
    {
        if (assigneeId == null)
            return null;
        User user = UserManager.getUser(assigneeId);
        if (user != null)
            return user.getUserProps();

        Group group = SecurityManager.getGroup(assigneeId);
        if (group != null)
        {
            JSONObject props = new JSONObject();
            props.put("id", group.getUserId());
            props.put("displayName", group.getName());
            return props;
        }

        return null;
    }

    @JsonProperty("assignee")
    public void setAssignee(Integer assignee)
    {
        _assignee = assignee;
    }

    public Integer getAssignee()
    {
        return _assignee;
    }

    @JsonProperty("assignee")
    public JSONObject getAssigneeJSON()
    {
        return getAssigneeJSON(_assignee);
    }

    public abstract List<Integer> getNotifyList();

    public void setNotifyList(List<Integer> notifyList)
    {
        _notifyList = notifyList;
    }

    @JsonProperty("notifyList")
    public List<JSONObject> getNotifyListJSON()
    {
        if (getNotifyList() == null)
            return null;

        return getNotifyList().stream()
                .map(Job::getAssigneeJSON)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    public boolean getIsTemplate()
    {
        return _isTemplate;
    }

    public void setIsTemplate(boolean template)
    {
        _isTemplate = template;
    }

    public Integer getJobCount()
    {
        return _jobCount;
    }

    public void setJobCount(Integer jobCount)
    {
        _jobCount = jobCount;
    }

    @Override
    public String getLSID()
    {
        return _lsid;
    }

    public void setLSID(String lsid)
    {
        _lsid = lsid;
    }

    public void setLSID(Lsid lsid)
    {
        _lsid = lsid.toString();
    }

    public Integer getDomainId()
    {
        return _domainId;
    }

    public void setDomainId(Integer domainId)
    {
        _domainId = domainId;
    }

    public abstract List<Task> getTasks();

    public void setTasks(List<Task> tasks)
    {
        _tasks = tasks;
    }

    @JsonIgnore
    public List<AttachmentFile> getAttachments()
    {
        return _attachments;
    }

    public void setAttachments(List<AttachmentFile> attachments)
    {
        _attachments = attachments;
    }

    public abstract List<WorkEntity> getEntities();

    @JsonIgnore
    public abstract @NotNull List<? extends ExpMaterial> getSamples();

    @JsonIgnore
    public abstract @NotNull List<? extends ExpData> getSources();

    public void setEntities(List<WorkEntity> entities)
    {
        _entities = entities;
    }

    @JsonProperty("containerPath")
    public String getContainerPath()
    {
        Container container = getContainer();
        return container == null ? null : container.getPath();
    }

    @JsonProperty("hasMedia")
    public boolean isHasMedia()
    {
        return _hasMedia;
    }

    public void setHasMedia(boolean hasMedia)
    {
        _hasMedia = hasMedia;
    }

    public abstract Map<String, Object> toMap();

    @JsonIgnore
    public List<Task> getSubsequentTasks(long taskId)
    {
        List<Task> orderedTasks = getTasks().stream().sorted().toList();
        int index = orderedTasks.stream().map(Task::getRowId).toList().indexOf(taskId);
        if (index == -1 || index == orderedTasks.size() - 1)
            return Collections.emptyList();
        return orderedTasks.subList(index+1, orderedTasks.size());
    }

    public Task getNextTask(long taskId)
    {
        List<Task> subsequent = getSubsequentTasks(taskId);
        if (subsequent.isEmpty())
            return null;
        return subsequent.getFirst();
    }

    public boolean isComplete()
    {
        if (getTasks().isEmpty())
            return true;
        return getTasks().stream().allMatch(Task::isCompleted);
    }

    public List<Map<String, Object>> getAttachmentData()
    {
        return _attachmentData;
    }

    public void setAttachmentData(List<Map<String, Object>> attachmentData)
    {
        _attachmentData = attachmentData;
    }

    public abstract Map<String, Object> toAuditDetailMap();

    public abstract Object getDomainProperty(PropertyDescriptor prop);

    public abstract void setProperty(User user, PropertyDescriptor pd, Object value) throws ValidationException;
}
