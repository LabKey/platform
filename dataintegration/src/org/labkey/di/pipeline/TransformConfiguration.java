package org.labkey.di.pipeline;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.di.api.ScheduledPipelineJobDescriptor;

import java.util.Date;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2013-03-13
 * Time: 4:52 PM
 */
public class TransformConfiguration extends Entity
{
    int rowId = -1;
    String descriptionId;
    boolean enabled = false;
    boolean verboseLogging = false;
    Date lastChecked = null;

    public TransformConfiguration()
    {
    }

    public TransformConfiguration(ScheduledPipelineJobDescriptor etl, Container container)
    {
        setDescriptionId(etl.getId());
        setContainer(container.getId());
    }

    public int getRowId()
    {
        return rowId;
    }


    @SuppressWarnings("UnusedDeclaration")
    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public String getDescriptionId()
    {
        return descriptionId;
    }

    public void setDescriptionId(String descriptionId)
    {
        this.descriptionId = descriptionId;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public boolean isVerboseLogging()
    {
        return verboseLogging;
    }

    public void setVerboseLogging(boolean verboseLogging)
    {
        this.verboseLogging = verboseLogging;
    }

    public Date getLastChecked()
    {
        return lastChecked;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLastChecked(Date lastChecked)
    {
        this.lastChecked = lastChecked;
    }

    public Map<String, Object> toJSON(@Nullable Map<String,Object> map)
    {
        if (null == map)
            map = new JSONObject();
        map.put("rowId", getRowId());
        map.put("descriptionId", getDescriptionId());
        map.put("enabled", isEnabled());
        map.put("verboseLogging", isVerboseLogging());
        map.put("lastChecked", getLastChecked());
        return map;
    }
}
