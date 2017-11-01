/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.pipeline.trigger;

import org.json.JSONObject;
import org.labkey.api.data.Entity;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public abstract class PipelineTriggerConfigImpl extends Entity implements PipelineTriggerConfig, Cloneable
{
    private int _rowId;
    private String _name;
    private String _description;
    private String _type;
    private boolean _enabled;
    private Map<String, Object> _configMap;
    private String _pipelineId;
    private Date _lastChecked;

    protected PipelineTriggerConfigImpl(int rowId, String containerId, Date created, int createdBy,
            Date modified, int modifiedBy, String name, String description, String type, boolean enabled,
            Map<String, Object> configuration, String pipelineId, Date lastChecked)
    {
        _rowId = rowId;
        _name = name;
        _description = description;
        _type = type;
        _enabled = enabled;
        _pipelineId = pipelineId;
        _lastChecked = lastChecked;
        setConfiguration(configuration);

        setContainer(containerId);
        if (created != null)
            setCreated(created);
        setCreatedBy(createdBy);
        if (modified != null)
            setModified(modified);
        setModifiedBy(modifiedBy);
    }

    @Override
    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    @Override
    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }

    @Override
    public Date getLastChecked()
    {
        return _lastChecked;
    }

    public void setLastChecked(Date lastChecked)
    {
        _lastChecked = lastChecked;
    }

    @Override
    public boolean isEnabled()
    {
        return _enabled;
    }

    public void setEnabled(boolean enabled)
    {
        _enabled = enabled;
    }

    @Override
    public String getConfiguration()
    {
        return new JSONObject(_configMap).toString();
    }

    public void setConfiguration(String configuration)
    {
        if (StringUtils.isEmpty(configuration))
            _configMap = new HashMap<>();
        else
            _configMap = new JSONObject(configuration);
    }

    public void setConfiguration(Map<String, Object> configMap)
    {
        _configMap = configMap;
    }

    public Map<String, Object> getConfigMap()
    {
        return _configMap;
    }

    /**
     * Get "parameters" object from the config
     */
    public Map<String, Object> getParameterMap()
    {
        Map<String, Object> parameterMap = (Map)getConfigMap().get("parameters");
        if (parameterMap == null)
        {
            parameterMap = new HashMap<>();
            // todo: this is causing an java.lang.UnsupportedOperationException because sometimes _configMap is a java.util.Collections$UnmodifiableMap
            // not sure of consequence of not setting this 'parameters' key here, doesn't appear to currently cause any problem to leave it unset.
            //_configMap.put("parameters", parameterMap);
        }

        return parameterMap;
    }

    @Override
    public String getPipelineId()
    {
        return _pipelineId;
    }

    public void setPipelineId(String pipelineId)
    {
        _pipelineId = pipelineId;
    }

    @Override
    public PipelineTriggerType getPipelineTriggerType()
    {
        return PipelineTriggerRegistry.get().getTypeByName(_type);
    }

    @Override
    public void start()
    {
        PipelineTriggerType type = getPipelineTriggerType();
        if (type != null)
            type.start(this);
    }

    @Override
    public void stop()
    {
        PipelineTriggerType type = getPipelineTriggerType();
        if (type != null)
            type.stop(this);
    }
}
