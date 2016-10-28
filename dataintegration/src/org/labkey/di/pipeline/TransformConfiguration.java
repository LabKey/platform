/*
 * Copyright (c) 2013-2016 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.util.DateUtil;

import java.util.Date;
import java.util.Map;

/**
 * User: matthewb
 * Date: 2013-03-13
 * Time: 4:52 PM
 *
 * Persistence object that goes with the dataintegration.transformconfiguration table
 *
 */
public class TransformConfiguration extends Entity
{
    int rowId = -1;
    String transformId;
    boolean enabled = false;
    boolean verboseLogging = false;
    Date lastChecked = null;
    String lastStatus = null;
    Date lastCompletion = null;
    Integer lastJobId = null;
    Integer lastCompletionJobId = null;
    Integer lastRunId = null;

    JSONObject jsonState;


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

    public String getLastStatus()
    {
        return lastStatus;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLastStatus(String lastStatus)
    {
        this.lastStatus = lastStatus;
    }

    public Date getLastCompletion()
    {
        return lastCompletion;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLastCompletion(Date lastCompletion)
    {
        this.lastCompletion = lastCompletion;
    }

    public Integer getLastJobId()
    {
        return lastJobId;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLastJobId(Integer lastJobId)
    {
        this.lastJobId = lastJobId;
    }

    public Integer getLastCompletionJobId()
    {
        return lastCompletionJobId;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLastCompletionJobId(Integer lastCompletionJobId)
    {
        this.lastCompletionJobId = lastCompletionJobId;
    }

    public Integer getLastRunId()
    {
        return lastRunId;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLastRunId(Integer lastRunId)
    {
        this.lastRunId = lastRunId;
    }

    public String getLastStatusUrl()
    {
        if (null == getLastJobId())
        {
            if (null == getLastRunId())
                return "";
            return TransformManager.get().getRunDetailsLink(lookupContainer(), getLastRunId(), getLastStatus());
        }

        return TransformManager.get().getJobDetailsLink(lookupContainer(), getLastJobId(), getLastStatus(), false);
    }

    public String getLastCompletionUrl()
    {
        if (null == getLastCompletion())
            return null;
        String formattedCompletion = DateUtil.formatDateTime(lookupContainer(), getLastCompletion());
        return TransformManager.get().getJobDetailsLink(lookupContainer(), getLastCompletionJobId(), formattedCompletion, false);
    }

    public String getLastCheckedString()
    {
        if (null == getLastChecked())
            return null;
        return DateUtil.formatDateTime(lookupContainer(), getLastChecked());
    }

    public String getLastTransformRunLog()
    {
        if (null == getLastRunId())
            return null;
        String transformRunLog = TransformManager.get().getTransformRunlog(lookupContainer(), getLastRunId());
        // Don't display the full stack trace on the configuration admin page; still available by clicking through to the run from the ERROR link
        transformRunLog = StringUtils.trim(StringUtils.substringBefore(transformRunLog, "\tat"));
        return transformRunLog;
    }

    public String getTransformState()
    {
        return null==jsonState ? "{}" : jsonState.toString();
    }


    public void setTransformState(String stringState)
    {
        if (null == stringState)
            this.jsonState = new JSONObject();
        else
            this.jsonState = new JSONObject(stringState);
    }

    public JSONObject getJsonState()
    {
        if (null == jsonState)
            jsonState = new JSONObject();
        return jsonState;
    }

    public void setJsonState(JSONObject jsonState)
    {
        this.jsonState = jsonState;
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
        map.put("state", getJsonState());
        map.put("lastJobId", getLastJobId());
        map.put("lastStatus", getLastStatus());
        map.put("lastCompletionJobId", getLastCompletionJobId());
        map.put("lastCompletion", getLastCompletion());
        map.put("lastRunId", getLastCompletion());
        return map;
    }
}
