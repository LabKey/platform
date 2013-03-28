/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.di.pipeline;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;

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
    String transformId;
    boolean enabled = false;
    boolean verboseLogging = false;
    Date lastChecked = null;

    public TransformConfiguration()
    {
    }

    public TransformConfiguration(ScheduledPipelineJobDescriptor etl, Container container)
    {
        setTransformId(etl.getId());
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

    // corresponds to ScheduledPipelineJobDescriptor.getId()
    public String getTransformId()
    {
        return transformId;
    }

    public void setTransformId(String descriptionId)
    {
        this.transformId = descriptionId;
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
        map.put("descriptionId", getTransformId());
        map.put("enabled", isEnabled());
        map.put("verboseLogging", isVerboseLogging());
        map.put("lastChecked", getLastChecked());
        return map;
    }
}
