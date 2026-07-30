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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.CreatedModified;
import org.labkey.api.util.GUID;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Task extends CreatedModified implements Comparable<Task>
{
    protected Long _rowId;
    protected GUID _containerId;
    protected Container _container;
    protected GUID _entityId;

    protected String _name;

    protected String _description;
    protected String _entityFilter;
    protected String _inputEntityType;
    protected Integer _status;
    protected Date _startDate;
    protected Date _endDate;
    protected Date _dueDate;
    protected int _ordinal;
    protected Integer _assignee;
    protected Long _jobId;
    protected Job _job;
    protected List<Action> _actions = null;

    public Long getRowId()
    {
        return _rowId;
    }

    public void setRowId(Long rowId)
    {
        _rowId = rowId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getEntityFilter()
    {
        return _entityFilter;
    }

    public void setEntityFilter(String entityFilter)
    {
        _entityFilter = entityFilter;
    }

    public String getInputEntityType()
    {
        return _inputEntityType;
    }

    public void setInputEntityType(String inputEntityType)
    {
        _inputEntityType = inputEntityType;
    }

    @JsonProperty("assignee")
    public JSONObject getAssigneeJSON()
    {
        return Job.getAssigneeJSON(_assignee);
    }

    public Integer getAssignee()
    {
        return _assignee;
    }

    @JsonProperty("assignee")
    public void setAssignee(Integer assignee)
    {
        _assignee = assignee;
    }

    public Integer getStatus()
    {
        return _status;
    }

    public void setStatus(Integer status)
    {
        _status = status;
    }

    public int getOrdinal()
    {
        return _ordinal;
    }

    public void setOrdinal(int ordinal)
    {
        _ordinal = ordinal;
    }

    public Date getStartDate()
    {
        return _startDate;
    }

    public void setStartDate(Date startDate)
    {
        _startDate = startDate;
    }

    public Date getEndDate()
    {
        return _endDate;
    }

    public void setEndDate(Date endDate)
    {
        _endDate = endDate;
    }

    public Date getDueDate()
    {
        return _dueDate;
    }

    public void setDueDate(Date dueDate)
    {
        _dueDate = dueDate;
    }

    public GUID getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(GUID entityId)
    {
        _entityId = entityId;
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
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public abstract boolean isCompleted();

    public abstract boolean isActive();

    public abstract boolean isPending();

    public abstract List<Action> getActions();

    @JsonIgnore
    public Map<Long, Action> getActionsByRowId()
    {
        Map<Long, Action> map = new HashMap<>();
        for (Action action : getActions())
            map.put(action.getRowId(), action);
        return map;
    }

    public void setActions(List<Action> actions)
    {
        _actions = actions;
    }

    public Long getJobId()
    {
        return _jobId;
    }

    public void setJobId(Long jobId)
    {
        _jobId = jobId;
    }

    public abstract Job getJob();

    public abstract Map<String, Object> toMap();

    // Determine if the template task with existing jobs can be updated to the new task definition
    // Only entityFilter field is allowed to be changed for a referenced template task
    public boolean canUpdateUsedTemplateTask(Task task)
    {
        if (this == task) return true;
        if (task == null || getClass() != task.getClass()) return false;

        if (!Objects.equals(_name, task._name) ||
                !Objects.equals(_description, task._description))
            return false;

        List<Action> existingActions = this.getActions();
        List<Action> newActions = task.getActions();
        if (existingActions.size() != newActions.size())
            return false;

        for (int i = 0; i < existingActions.size(); i++)
        {
            Action existingAction = existingActions.get(i);
            Action newAction = newActions.get(i);
            if (!existingAction.equals(newAction))
            {
                return false;
            }
        }

        return true;
    }

    @JsonIgnore
    public boolean hasUpdatableAssaysAction()
    {
        return getActions().stream().anyMatch(action -> action.getType() == WorkflowService.ActionType.AssayImport && action.getIsUpdatable());
    }

    public Map<String, Object> toAuditDetailMap()
    {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rowId", getRowId());
        map.put("name", getName());
        map.put("description", getDescription());
        map.put("entityFilter", getEntityFilter());
        map.put("status", getStatus());
        if (getStartDate() != null)
            map.put("startDate", getStartDate());
        if (getEndDate() != null)
            map.put("endDate", getEndDate());
        if (getDueDate() != null)
            map.put("dueDate", getDueDate());
        if (getEntityId() != null)
            map.put("entityId", getEntityId().toString());
        if (getInputEntityType() != null)
            map.put("inputEntityType", getInputEntityType());
        map.put("ordinal", getOrdinal());
        int actionIndex = 1;
        for (Action action : getActions())
        {
            Map<String, Object> actionMap = action.toAuditDetailMap();
            for (Map.Entry<String, Object> entry : actionMap.entrySet())
                map.put("action" + actionIndex + "." + entry.getKey(), entry.getValue());
            actionIndex++;
        }
        return map;
    }

    @Override
    public int compareTo(@NotNull Task o)
    {
        return Integer.compare(getOrdinal(), o.getOrdinal());
    }

}
