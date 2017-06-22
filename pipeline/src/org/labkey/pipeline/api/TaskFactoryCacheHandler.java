/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.pipeline.api;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.pipeline.xml.TaskDocument;
import org.labkey.pipeline.xml.TaskType;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * User: kevink
 * Date: 1/6/14
 */
/* package */ class TaskFactoryCacheHandler implements ModuleResourceCacheHandler<Map<TaskId, TaskFactory>>
{
    private static final Logger LOG = Logger.getLogger(TaskFactoryCacheHandler.class);
    private static final String TASK_CONFIG_EXTENSION = ".task.xml";

    @Override
    public Map<TaskId, TaskFactory> load(Stream<? extends Resource> resources, Module module)
    {
        return unmodifiable(resources
            .filter(getFilter(TASK_CONFIG_EXTENSION))
            .map(resource -> loadTaskConfig(module, resource))
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(TaskFactory::getId, Function.identity())));
    }

    private @Nullable TaskFactory loadTaskConfig(Module module, Resource resource)
    {
        TaskId taskId = createTaskId(module, resource.getName());

        if (null == taskId)
            return null;

        try
        {
            return create(taskId, resource);
        }
        catch (IllegalArgumentException|IllegalStateException e)
        {
            LOG.warn("Error registering '" + taskId + "' task: " + e.getMessage());
            return null;
        }
    }

    private @Nullable TaskId createTaskId(Module module, String filename)
    {
        if (!filename.endsWith(TASK_CONFIG_EXTENSION))
            return null;

        String name = filename.substring(0, filename.length() - TASK_CONFIG_EXTENSION.length());
        return new TaskId(module.getName(), TaskId.Type.task, name, 0);
    }

    private TaskFactory create(TaskId taskId, Resource taskConfig)
    {
        if (taskId.getName() == null)
            throw new IllegalArgumentException("Task factory must by named");

        if (taskId.getType() != TaskId.Type.task)
            throw new IllegalArgumentException("Task factory must by of type 'task'");

        TaskDocument doc;
        try
        {
            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
            doc = TaskDocument.Factory.parse(taskConfig.getInputStream(), options);
            XmlBeansUtil.validateXmlDocument(doc, "Task factory config '" + taskConfig.getPath() + "'");
        }
        catch (XmlValidationException e)
        {
            LOG.error(e);
            return null;
        }
        catch (XmlException |IOException e)
        {
            LOG.error("Error loading task factory '" + taskConfig.getPath() + "':\n" + e.getMessage());
            return null;
        }

        TaskType xtask = doc.getTask();
        if (xtask == null)
            throw new IllegalArgumentException("task element required");

        if (!taskId.getName().equals(xtask.getName()))
            throw new IllegalArgumentException(String.format("Task factory config must have the name '%s'", taskId.getName()));

        Path taskDir = taskConfig.getPath().getParent();
        return create(taskId, xtask, taskDir);
    }

    private TaskFactory create(TaskId taskId, TaskType xtask, Path taskDir)
    {
        return PipelineJobServiceImpl.get().createTaskFactory(taskId, xtask, taskDir);
    }
}
