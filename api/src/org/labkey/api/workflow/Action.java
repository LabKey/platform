package org.labkey.api.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.CreatedModified;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.util.GUID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class Action extends CreatedModified
{
    public static final String ASSAY_TYPES_KEY = "assayTypes";
    public static final String NUM_PER_PARENT_KEY = "numPerParent";
    protected Long _rowId;
    protected int _ordinal;
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

    public int getOrdinal()
    {
        return _ordinal;
    }

    public void setOrdinal(int ordinal)
    {
        _ordinal = ordinal;
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

    @JsonIgnore
    public List<String> validateInputParameters(int ordinal)
    {
        String prefix = "Action #" + ordinal + ": ";
        if (_type == WorkflowService.ActionType.AssayImport)
        {
            if (_inputParameters != null && _inputParameters.has(ASSAY_TYPES_KEY))
            {
                try
                {
                    JSONArray assayTypes = _inputParameters.getJSONArray(ASSAY_TYPES_KEY);
                    // When assay types are updatable, there may be none provided. Usually that means the inputParameters will
                    // be empty or null, but it also works if the assay types array is empty.
                    if (assayTypes.isEmpty())
                        return Collections.emptyList();

                    Set<Object> invalidAssayIds = new HashSet<>();
                    assayTypes.toList().forEach(assayId ->
                    {
                        try
                        {
                            int protocolId = (assayId instanceof String) ? Integer.valueOf((String) assayId) : (Integer) assayId;
                            ExpProtocol assay = ExperimentService.get().getExpProtocol(protocolId);
                            if (null == assay)
                                invalidAssayIds.add(protocolId);
                        }
                        catch (Exception e)
                        {
                            invalidAssayIds.add(assayId);
                        }
                    });
                    if (!invalidAssayIds.isEmpty())
                        return List.of(prefix + "invalid assay IDs " + invalidAssayIds + ".");
                }
                catch (Exception e)
                {
                    return List.of(prefix + ASSAY_TYPES_KEY + " must be an array.");
                }
            }

        }
        else if (_type == WorkflowService.ActionType.AliquotSamples)
        {
            if (_inputParameters == null || !_inputParameters.has(NUM_PER_PARENT_KEY))
                return List.of(prefix + NUM_PER_PARENT_KEY + " is required for action of type " + _type + ".");
            else
            {
                try {
                    int numPerParent = _inputParameters.getInt(NUM_PER_PARENT_KEY);
                    if (numPerParent < 0)
                        return List.of(prefix + NUM_PER_PARENT_KEY + " cannot be negative.");
                }
                catch (Exception e) {
                    return List.of(prefix + NUM_PER_PARENT_KEY + " must be an integer.");
                }
            }
        }
        else
        {
            if (_inputParameters == null || _inputParameters.isEmpty())
                return List.of(prefix + "data about sample types and sample counts per parent is required for action of type " + _type + ".");
            if (_type == WorkflowService.ActionType.PoolSamples && _inputParameters.length() > 1)
                return List.of(prefix + "only one sample type can be specified for action of type " + _type + ".");
            SampleTypeService sampleTypeService = SampleTypeService.get();
            Set<String> invalidSampleTypeIds = new HashSet<>();
            List<Object> invalidCounts = new ArrayList<>();

            _inputParameters.keys().forEachRemaining(id -> {
                try
                {
                    if (sampleTypeService.getSampleType(Long.valueOf(id)) == null)
                        invalidSampleTypeIds.add(id);
                }
                catch (NumberFormatException e)
                {
                    invalidSampleTypeIds.add(id);
                }
                Object countObj = _inputParameters.get(id);
                if ((countObj instanceof String))
                    try
                    {
                        if (Integer.parseInt((String) countObj) < 0)
                            invalidCounts.add(countObj);
                    }
                    catch (NumberFormatException e)
                    {
                        invalidCounts.add(countObj);
                    }
                else if (countObj instanceof Integer)
                {
                    if (((Integer) countObj) < 0)
                        invalidCounts.add(countObj);
                }
                else
                    invalidCounts.add(countObj);
            });
            List<String> messages = new ArrayList<>();
            if (!invalidSampleTypeIds.isEmpty())
                messages.add(prefix + "invalid sample type IDs " + invalidSampleTypeIds + ".");
            if (!invalidCounts.isEmpty())
                messages.add(prefix + "invalid sample count values " + invalidCounts + ".");
            return messages;
        }
        return Collections.emptyList();
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
                _ordinal == action._ordinal &&
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
        map.put("ordinal", _ordinal);
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
