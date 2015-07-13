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
package org.labkey.di.steps;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.di.pipeline.TaskRefTaskImpl;

import java.util.Collections;
import java.util.List;

/**
 * User: tgaluhn
 * Date: 7/25/2014
 */
public class TestTaskRefTask extends TaskRefTaskImpl
{
    private enum Setting
    {
        setting1,
        sleep
    }

    @Override
    public RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException
    {
        settings.put(Setting.setting1.name(), "test");
        job.getLogger().info("Log from test task");
        if (settings.get(Setting.sleep.name()) != null)
        {
            int sleepSeconds = Integer.parseInt(settings.get(Setting.sleep.name()));
            job.getLogger().info("Sleeping ETL task for " + sleepSeconds + " seconds");
            try
            {
                Thread.sleep(sleepSeconds * 1000);
            }
            catch (InterruptedException e) {/* */}
        }
        return new RecordedActionSet(makeRecordedAction());
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return Collections.singletonList(Setting.setting1.name());
    }
}
