/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.springframework.beans.factory.InitializingBean;

import java.util.ArrayList;
import java.util.List;

/**
 * <code>TaskPipelineRegistrar</code> is meant for use in a Spring
 * coniguration file for adding object to the <code>TaskPipelineRegistry</code>.
 *
 * @author brendanx
 */
public class TaskPipelineRegistrar implements InitializingBean
{
    private List<TaskFactorySettings> _factories =
            new ArrayList<TaskFactorySettings>();
    private List<TaskFactory> _factoryImpls =
            new ArrayList<TaskFactory>();
    private List<TaskPipelineSettings> _pipelines =
            new ArrayList<TaskPipelineSettings>();
    private List<TaskPipeline> _pipelineImpls =
            new ArrayList<TaskPipeline>();

    public List<TaskFactorySettings> getFactories()
    {
        return _factories;
    }

    public void setFactories(List<TaskFactorySettings> factories)
    {
        _factories = factories;
    }

    public List<TaskFactory> getFactoryImpls()
    {
        return _factoryImpls;
    }

    public void setFactoryImpls(List<TaskFactory> factoryImpls)
    {
        _factoryImpls = factoryImpls;
    }

    public List<TaskPipelineSettings> getPipelines()
    {
        return _pipelines;
    }

    public void setPipelines(List<TaskPipelineSettings> pipelines)
    {
        _pipelines = pipelines;
    }

    public List<TaskPipeline> getPipelineImpls()
    {
        return _pipelineImpls;
    }

    public void setPipelineImpls(List<TaskPipeline> pipelineImpls)
    {
        _pipelineImpls = pipelineImpls;
    }

    public void afterPropertiesSet() throws Exception
    {
        PipelineJobService service = PipelineJobService.get();

        // Replace non-TaskId objects in the task progressions of
        // the TaskPipelines with TaskIds.
        for (TaskPipelineSettings pipeline : _pipelines)
        {
            Object[] progression = pipeline.getTaskProgressionSpec();
            for (int i = 0; i < progression.length; i++)
            {
                Object spec = progression[i];
                if (spec instanceof TaskId)
                    continue;

                if (spec instanceof Class)
                    progression[i] = new TaskId((Class) spec);
                else if (spec instanceof TaskFactory)
                {
                    TaskFactory factory = (TaskFactory) spec;
                    _factoryImpls.add(factory);
                    progression[i] = factory.getId();
                }
                else if (spec instanceof TaskFactorySettings)
                {
                    TaskFactorySettings settings =
                            (TaskFactorySettings) spec;
                    _factories.add(settings);
                    progression[i] = settings.getId();
                }
            }
        }

        // Register and release the factories
        for (TaskFactory factory : _factoryImpls)
            service.addTaskFactory(factory);
        _factoryImpls = null;

        // Register and release the factories settings objects
        for (TaskFactorySettings settings : _factories)
            service.addTaskFactory(settings);
        _factoryImpls = null;

        // Register and release base pipeline implementations
        for (TaskPipeline pipeline : _pipelineImpls)
            service.addTaskPipeline(pipeline);
        _pipelineImpls = null;

        // Register and release the pipeline settings objects
        for (TaskPipelineSettings pipeline : _pipelines)
            service.addTaskPipeline(pipeline);
        _pipelines = null;
    }
}
