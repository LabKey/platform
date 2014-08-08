/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.di.TaskrefTask;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.writer.ContainerUser;

import java.util.Map;

/**
 * User: tgaluhn
 * Date: 7/22/2014
 */
public abstract class TaskrefTaskImpl implements TaskrefTask
{

    protected Map<String, String> settings = new CaseInsensitiveHashMap<>();
    protected ContainerUser containerUser;
    protected Logger logger;

    @Override
    public void setSettings(Map<String, String> settings)
    {
        this.settings = settings;
    }

    @Override
    public void setContainerUser(ContainerUser containerUser)
    {
        this.containerUser = containerUser;
    }

    @Override
    public void setLogger(Logger logger)
    {
        this.logger = logger;
    }

    /**
     * Helper to turn a map of settings and outputs into a RecordedAction to be added to the RecordedActionSet return
     * of run()
     *
     */
    protected RecordedAction makeRecordedAction()
    {
        RecordedAction ra = new RecordedAction(this.getClass().getSimpleName());
        for (Map.Entry<String,String> setting : settings.entrySet())
        {
            RecordedAction.ParameterType paramType = new RecordedAction.ParameterType(setting.getKey(), "terms.labkey.org#" + setting.getKey().replaceAll("\\s",""), PropertyType.STRING);
            ra.addParameter(paramType, setting.getValue());
        }
        return ra;
    }
}
