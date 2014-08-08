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
package org.labkey.di.steps;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.di.pipeline.TaskRefTaskImpl;

import java.util.Arrays;
import java.util.List;

/**
 * User: tgaluhn
 * Date: 7/25/2014
 */
public class TestTaskRefTask extends TaskRefTaskImpl
{
    private static final String SETTING_1 = "setting1";

    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        settings.put(SETTING_1, "test");
        logger.info("Log from test task");
        return new RecordedActionSet(makeRecordedAction());
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return Arrays.asList(SETTING_1);
    }
}
