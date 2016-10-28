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
package org.labkey.api.pipeline;

import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SpringModule;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.ArrayList;
import java.util.List;

/**
 * <code>TaskPipelineRegistrar</code> is meant for use in a Spring
 * configuration file for adding object to the {@link org.labkey.api.pipeline.TaskPipelineRegistrar}.
 *
 * @author brendanx
 */
public class TaskPipelineRegistrar implements InitializingBean, ApplicationContextAware
{
    private List<TaskFactorySettings> _factories =
            new ArrayList<>();
    private List<TaskFactory> _factoryImpls =
            new ArrayList<>();
    private List<TaskPipelineSettings> _pipelines =
            new ArrayList<>();
    private List<TaskPipeline> _pipelineImpls =
            new ArrayList<>();
    private Module _declaringModule;

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

    public void addSubFactories(List factories)
    {
        List<TaskFactorySettings> listSettings = new ArrayList<>();
        for (Object spec : factories)
        {
            if (spec instanceof TaskFactorySettings.Provider)
            {
                TaskFactorySettings.Provider provider =
                        (TaskFactorySettings.Provider) spec;
                for (TaskFactorySettings settings : provider.getSettings())
                {
                    if (!_factories.contains(settings) && !listSettings.contains(settings))
                        listSettings.add(settings);
                }
            }
        }

        // Make sure the sub-factories get added first
        listSettings.addAll(_factories);
        _factories = listSettings;
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
                    if (!_factories.contains(settings))
                        _factories.add(settings);
                    progression[i] = settings.getId();
                }
            }
        }

        addSubFactories(_factoryImpls);
        addSubFactories(_factories);

        // Register and release the factories
        for (TaskFactory factory : _factoryImpls)
        {
            factory.setDeclaringModule(_declaringModule);
            service.addTaskFactory(factory);
        }
        _factoryImpls = null;

        // Register and release the factories settings objects
        for (TaskFactorySettings settings : _factories)
        {
            settings.setDeclaringModule(_declaringModule);
            service.addTaskFactory(settings);
        }
        _factories = null;

        // Register and release base pipeline implementations
        for (TaskPipeline pipeline : _pipelineImpls)
        {
            pipeline.setDeclaringModule(_declaringModule);
            service.addTaskPipeline(pipeline);
        }
        _pipelineImpls = null;

        // Register and release the pipeline settings objects
        for (TaskPipelineSettings pipeline : _pipelines)
        {
            pipeline.setDeclaringModule(_declaringModule);
            service.addTaskPipeline(pipeline);
        }
        _pipelines = null;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException
    {
        try
        {
            _declaringModule = applicationContext.getBean("moduleBean", SpringModule.class);
        }
        catch (NoSuchBeanDefinitionException x)
        {
            String name = applicationContext.getDisplayName();
            if (name.contains(" "))
            {
                name = name.substring(0, name.indexOf(" "));
            }
            _declaringModule = ModuleLoader.getInstance().getModule(name);
        }
    }
}
