/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
import org.apache.xmlbeans.XmlException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.di.TaskRefTask;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.writer.ContainerUser;
import org.labkey.di.steps.TaskRefTransformStepMeta;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 7/22/2014
 */
public abstract class TaskRefTaskImpl implements TaskRefTask
{
    protected final Map<String, String> settings = new CaseInsensitiveHashMap<>();
    protected ContainerUser containerUser;

    @Override
    public void setSettings(Map<String, String> xmlSettings) throws XmlException
    {
        StringBuilder sb = new StringBuilder();
        for (String requiredSetting : getRequiredSettings())
        {
            if (StringUtils.isBlank(xmlSettings.get(requiredSetting)))
            {
                if (sb.length() == 0)
                    sb.append(TaskRefTransformStepMeta.TASKREF_MISSING_REQUIRED_SETTING).append("\n");
                sb.append(requiredSetting).append("\n");
            }
        }
        if (sb.length() > 0)
            throw new XmlException(sb.toString());

        this.settings.putAll(xmlSettings);
    }

    @Override
    public void setContainerUser(ContainerUser containerUser)
    {
        this.containerUser = containerUser;
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return Collections.emptyList();
    }

    /**
     * Helper to turn a map of settings and outputs into a RecordedAction to be added to the RecordedActionSet return
     * of run()
     *
     */
    protected RecordedAction makeRecordedAction()
    {
        RecordedAction ra = new RecordedAction(this.getClass().getSimpleName());
        for (Map.Entry<String, String> setting : settings.entrySet())
        {
            RecordedAction.ParameterType paramType = new RecordedAction.ParameterType(setting.getKey(), PropertyType.STRING);
            ra.addParameter(paramType, setting.getValue());
        }
        return ra;
    }
}
