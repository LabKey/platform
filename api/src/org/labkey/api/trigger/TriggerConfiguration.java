/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.trigger;

import org.apache.commons.lang3.StringUtils;
import org.json.old.JSONObject;
import org.labkey.api.data.Entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TriggerConfiguration extends Entity
{
    private Integer _rowId;
    private String _name;
    private String _description;
    private String _type;
    private String _pipelineId;
    private String _username;
    private String _assayProvider;
    private String _location;
    private String _filePattern;
    private int _quiet;
    private String _copy;
    private String _move;
    private String _parameterFunction;
    private boolean _enabled;
    private boolean _recursive;
    protected List<String> _customParamKey = new ArrayList<>();
    protected List<String> _customParamValue = new ArrayList<>();
    private Date _lastChecked;
    private String _configuration;
    private String _customConfiguration;

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

    public String getPipelineId()
    {
        return _pipelineId;
    }

    public void setPipelineId(String pipelineId)
    {
        _pipelineId = pipelineId;
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

    public String getMove()
    {
        return _move;
    }

    public void setMove(String move)
    {
        _move = move;
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

    private void addIfNotNull(JSONObject json, String key, Object value)
    {
        if (value != null)
            json.put(key, value);
    }

    public Date getLastChecked()
    {
        return _lastChecked;
    }

    public void setLastChecked(Date lastChecked)
    {
        _lastChecked = lastChecked;
    }

    /**
     * flush the cached configuration
     */
    public void resetConfiguration()
    {
        _configuration = null;
    }

    public String getConfiguration()
    {
        if (_configuration == null)
        {
            JSONObject json = new JSONObject();

            addIfNotNull(json, "name", getName());
            addIfNotNull(json, "description", getDescription());
            addIfNotNull(json, "location", getLocation());
            addIfNotNull(json, "filePattern", getFilePattern());
            addIfNotNull(json, "quiet", getQuiet() * 1000);
            addIfNotNull(json, "copy", getCopy());
            addIfNotNull(json, "move", getMove());
            addIfNotNull(json, "parameterFunction", getParameterFunction());
            addIfNotNull(json, "enabled", isEnabled());
            addIfNotNull(json, "recursive", isRecursive());

            JSONObject params = new JSONObject();
            params.put("pipeline, username", getUsername());
            if (!StringUtils.isEmpty(getAssayProvider()))
                params.put("pipeline, assay provider", getAssayProvider());

            json.put("parameters", params);

            _configuration = json.toString();
        }
        return _configuration;
    }

    public void setConfiguration(String configuration)
    {
        // parse the JSON object
        JSONObject json = new JSONObject(configuration);

        if (json.has("name"))
            setName(json.getString("name"));
        if (json.has("description"))
            setDescription(json.getString("description"));
        if (json.has("location"))
            setLocation(json.getString("location"));
        if (json.has("filePattern"))
            setFilePattern(json.getString("filePattern"));
        if (json.has("quiet"))
            setQuiet(json.getInt("quiet"));
        if (json.has("copy"))
            setCopy(json.getString("copy"));
        if (json.has("move"))
            setMove(json.getString("move"));
        if (json.has("parameterFunction"))
            setParameterFunction(json.getString("parameterFunction"));
        if (json.has("enabled"))
            setEnabled(json.getBoolean("enabled"));
        if (json.has("recursive"))
            setRecursive(json.getBoolean("recursive"));

        if (json.has("parameters"))
        {
            JSONObject parameters = json.getJSONObject("parameters");

            if (parameters.has("pipeline, username"))
                setUsername(parameters.getString("pipeline, username"));
            if (parameters.has("pipeline, assay provider"))
                setAssayProvider(parameters.getString("pipeline, assay provider"));
        }
        _configuration = configuration;
    }

    public String getCustomConfiguration()
    {
        if (_customConfiguration == null)
        {
            assert _customParamKey.size() == _customParamValue.size();
            JSONObject json = new JSONObject();
            int i=0;

            for (String key : _customParamKey)
            {
                json.put(key, _customParamValue.get(i++));
            }
            _customConfiguration = json.toString();
        }
        return _customConfiguration;
    }

    public void setCustomConfiguration(String customConfiguration)
    {
        // parse the JSON object
        JSONObject json = new JSONObject(customConfiguration);
        List<String> keys = new ArrayList<>();
        List<String> values = new ArrayList<>();

        json.forEach((key, value) -> {
            keys.add(key);
            values.add(String.valueOf(value));
        });

        if (!keys.isEmpty())
        {
            setCustomParamKey(keys);
            setCustomParamValue(values);
        }
        _customConfiguration = customConfiguration;
    }
}
