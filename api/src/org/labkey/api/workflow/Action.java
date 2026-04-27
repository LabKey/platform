package org.labkey.api.workflow;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.data.CreatedModified;
import org.labkey.api.util.GUID;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class Action extends CreatedModified
{
    public static final String ASSAY_TYPES_KEY = "assayTypes";
    protected Long _rowId;
    protected GUID _containerId;
    protected String _name;
    protected boolean _isUpdatable = false;
    protected Long _taskId;
    protected WorkflowService.ActionType _type;
    protected JSONObject _inputParameters;


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

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public boolean getIsUpdatable()
    {
        return _isUpdatable;
    }

    public void setIsUpdatable(boolean updatable)
    {
        _isUpdatable = updatable;
    }

    public Long getTaskId()
    {
        return _taskId;
    }

    public void setTaskId(Long taskId)
    {
        _taskId = taskId;
    }

    public WorkflowService.ActionType getType()
    {
        return _type;
    }

    public void setType(WorkflowService.ActionType type)
    {
        _type = type;
    }

    public JSONObject getInputParameters()
    {
        return _inputParameters;
    }

    public void setInputParameters(JSONObject inputParameters)
    {
        _inputParameters = inputParameters;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Action action = (Action) o;

        // GitHub Issue 799: Workflow Automation: Attempting to add a sample filter on an existing template errors
        // Migration script generated action.name, but they are currently not used. Allow name to be changed to null or empty string.
        if (!java.util.Objects.equals(_name, action._name) && !StringUtils.isEmpty(action.getName()))
            return false;

        return _isUpdatable == action._isUpdatable &&
                java.util.Objects.equals(_rowId, action._rowId) &&
                java.util.Objects.equals(_taskId, action._taskId) &&
                java.util.Objects.equals(_type, action._type) &&
                java.util.Objects.equals(
                        _inputParameters == null ? null : _inputParameters.toString(),
                        action._inputParameters == null ? null : action._inputParameters.toString()
                );
    }

    public Map<String, Object> toAuditDetailMap()
    {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rowId", _rowId);
        map.put("name", _name);
        map.put("isUpdatable", _isUpdatable);
        map.put("taskId", _taskId);
        if (_type != null)
            map.put("type", _type.name());
        if (_inputParameters != null)
            map.put("inputParameters", _inputParameters.toString());
        return map;
    }

    public abstract Task getTask();
}
