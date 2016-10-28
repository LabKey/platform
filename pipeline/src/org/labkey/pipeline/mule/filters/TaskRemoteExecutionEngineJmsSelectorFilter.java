/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
package org.labkey.pipeline.mule.filters;

import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.pipeline.mule.test.DummyRemoteExecutionEngine;

import java.util.List;

/**
 * <code>TaskJmsSelectorFilter</code> builds and applies a JMS selector for
 * all registered <code>TaskFactory</code> objects for all configured RemoteExecutionEngineConfigs.
 *
 * @author brendanx
 */
public class TaskRemoteExecutionEngineJmsSelectorFilter extends TaskJmsSelectorFilter
{
    public TaskRemoteExecutionEngineJmsSelectorFilter()
    {
        List<? extends RemoteExecutionEngine<?>> allEngines = PipelineJobService.get().getRemoteExecutionEngines();

        for (RemoteExecutionEngine engine : allEngines)
        {
            _locations.add(engine.getConfig().getLocation());
        }

        // For unit testing purposes. See PipelineJobServiceImpl.TestCase.testDummySubmit()
        _locations.add(DummyRemoteExecutionEngine.DummyConfig.LOCATION);
    }
}