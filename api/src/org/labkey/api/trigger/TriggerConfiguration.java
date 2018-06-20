package org.labkey.api.trigger;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TriggerConfiguration
{
    private Integer _rowId;
    private String _name;
    private String _description;
    private String _type;
    private String _pipelineTask;
    private String _username;
    private String _assayProvider;
    private String _location;
    private String _filePattern;
    private int _quiet;
    private String _copy;
    private String _parameterFunction;
    private boolean _enabled;
    private boolean _recursive;
    protected List<String> _customParamKey = new ArrayList<>();
    protected List<String> _customParamValue = new ArrayList<>();

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
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

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }

    public String getPipelineTask()
    {
        return _pipelineTask;
    }

    public void setPipelineTask(String pipelineTask)
    {
        _pipelineTask = pipelineTask;
    }

    public String getUsername()
    {
        return _username;
    }

    public void setUsername(String username)
    {
        _username = username;
    }

    public String getAssayProvider()
    {
        return _assayProvider;
    }

    public void setAssayProvider(String assayProvider)
    {
        _assayProvider = assayProvider;
    }

    public String getLocation()
    {
        return _location;
    }

    public void setLocation(String location)
    {
        _location = location;
    }

    public String getFilePattern()
    {
        return _filePattern;
    }

    public void setFilePattern(String filePattern)
    {
        _filePattern = filePattern;
    }

    public int getQuiet()
    {
        return _quiet;
    }

    public void setQuiet(int quiet)
    {
        _quiet = quiet;
    }

    public String getCopy()
    {
        return _copy;
    }

    public void setCopy(String copy)
    {
        _copy = copy;
    }

    public String getParameterFunction()
    {
        return _parameterFunction;
    }

    public void setParameterFunction(String parameterFunction)
    {
        _parameterFunction = parameterFunction;
    }

    public boolean isEnabled()
    {
        return _enabled;
    }

    public void setEnabled(boolean enabled)
    {
        _enabled = enabled;
    }

    public boolean isRecursive()
    {
        return _recursive;
    }

    public void setRecursive(boolean recursive)
    {
        _recursive = recursive;
    }

    public List<String> getCustomParamKey()
    {
        return _customParamKey;
    }

    public void setCustomParamKey(List<String> customParamKey)
    {
        _customParamKey = customParamKey;
    }

    public List<String> getCustomParamValue()
    {
        return _customParamValue;
    }

    public void setCustomParamValue(List<String> customParamValue)
    {
        _customParamValue = customParamValue;
    }

    public String getConfigurationJSON()
    {
        JSONObject json = new JSONObject();

        addIfNotNull(json, "name", getName());
        addIfNotNull(json, "description", getDescription());
        addIfNotNull(json, "location", getLocation());
        addIfNotNull(json, "filePattern", getFilePattern());
        addIfNotNull(json, "quiet", getQuiet() * 1000);
        addIfNotNull(json, "copy", getCopy());
        addIfNotNull(json, "parameterFunction", getParameterFunction());
        addIfNotNull(json, "enabled", isEnabled());
        addIfNotNull(json, "recursive", isRecursive());

        JSONObject params = new JSONObject();
        params.put("pipeline, username", getUsername());
        if (!StringUtils.isEmpty(getAssayProvider()))
            params.put("pipeline, assay provider", getAssayProvider());

        json.put("parameters", params);

        return json.toString();
    }

    private void addIfNotNull(JSONObject json, String key, Object value)
    {
        if (value != null)
            json.put(key, value);
    }

    public String getCustomConfigurationJSON()
    {
        assert _customParamKey.size() == _customParamValue.size();
        JSONObject json = new JSONObject();
        int i=0;

        for (String key : _customParamKey)
        {
            json.put(key, _customParamValue.get(i++));
        }
        return json.toString();
    }

    public Map<String, Object> getRow()
    {
        Map<String, Object> row = new HashMap<>();
        row.put("RowId", getRowId());
        row.put("Name", getName());
        row.put("Description", getDescription());
        row.put("Type", getType());
        row.put("PipelineId", getPipelineTask());
        row.put("Enabled", isEnabled());
        row.put("Configuration", getConfigurationJSON());
        row.put("CustomConfiguration", getCustomConfigurationJSON());

        return row;
    }
}
