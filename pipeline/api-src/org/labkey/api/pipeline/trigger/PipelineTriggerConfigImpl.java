package org.labkey.api.pipeline.trigger;

import org.json.JSONObject;
import org.labkey.api.data.Entity;
import org.labkey.api.security.User;

import java.util.Date;
import java.util.Map;

public abstract class PipelineTriggerConfigImpl extends Entity implements PipelineTriggerConfig, Cloneable
{
    private final int _rowId;
    private final String _name;
    private final String _description;
    private final String _type;
    private final boolean _enabled;
    private final Map<String, Object> _configuration;
    private final String _pipelineId;
    private final Date _lastChecked;

    protected PipelineTriggerConfigImpl(int rowId, String containerId, Date created, int createdBy,
            Date modified, int modifiedBy, String name, String description, String type, boolean enabled,
            Map<String, Object> configuration, String pipelineId, Date lastChecked)
    {
        _rowId = rowId;
        _name = name;
        _description = description;
        _type = type;
        _enabled = enabled;
        _configuration = configuration;
        _pipelineId = pipelineId;
        _lastChecked = lastChecked;

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

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    @Override
    public String getType()
    {
        return _type;
    }

    @Override
    public PipelineTriggerType getPipelineTriggerType()
    {
        return PipelineTriggerRegistry.get().getTypeByName(_type);
    }

    @Override
    public Date getLastChecked()
    {
        return _lastChecked;
    }

    @Override
    public boolean isEnabled()
    {
        return _enabled;
    }

    @Override
    public String getConfiguration()
    {
        return new JSONObject(_configuration).toString();
    }

    @Override
    public String getPipelineId()
    {
        return _pipelineId;
    }
}
