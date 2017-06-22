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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.resource.Resource;
import org.labkey.pipeline.analysis.FileAnalysisTaskPipelineImpl;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * User: kevink
 * Date: 1/6/14
 *
 * Loads TaskPipeline from file-based modules from a /pipelines/pipeline/&lt;name&gt;.pipeline.xml file.
 * Similar to the ETL DescriptorCache's ScheduledPipelineJobDescriptor, the TaskPipeline may register some locally defined TaskFactories as well.
 */
/* package */ class TaskPipelineCacheHandler implements ModuleResourceCacheHandler<Map<TaskId, TaskPipeline>>
{
    private static final Logger LOG = Logger.getLogger(TaskPipelineCacheHandler.class);
    private static final String PIPELINE_CONFIG_EXTENSION = ".pipeline.xml";

    @Override
    public Map<TaskId, TaskPipeline> load(Stream<? extends Resource> resources, Module module)
    {
        return unmodifiable(resources
            .filter(getFilter(PIPELINE_CONFIG_EXTENSION))
            .map(resource -> loadPipelineConfig(module, resource))
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(TaskPipeline::getId, Function.identity())));
    }

    private @Nullable TaskId createPipelineId(Module module, String filename)
    {
        if (!filename.endsWith(PIPELINE_CONFIG_EXTENSION))
            return null;

        String name = filename.substring(0, filename.length() - PIPELINE_CONFIG_EXTENSION.length());
        return new TaskId(module.getName(), TaskId.Type.pipeline, name, 0);
    }

    private @Nullable TaskPipeline loadPipelineConfig(Module module, Resource resource)
    {
        TaskId taskId = createPipelineId(module, resource.getName());

        if (null == taskId)
            return null;

        try
        {
            // TODO: Should not pass in taskId (read that from the resource!)
            return FileAnalysisTaskPipelineImpl.create(module, resource, taskId);
        }
        catch (IllegalArgumentException|IllegalStateException e)
        {
            LOG.warn("Error registering '" + taskId + "' pipeline: " + e.getMessage());
            return null;
        }
    }

    @Nullable
    @Override
    public FileSystemDirectoryListener createChainedDirectoryListener(final Module module)
    {
        return new FileSystemDirectoryListener()
        {
            @Override
            public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
            {
            }

            @Override
            public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
            {
                removeResource(entry);
            }

            @Override
            public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
            {
                removeResource(entry);
            }

            @Override
            public void overflow()
            {
            }

            private void removeResource(java.nio.file.Path entry)
            {
                String name = entry.toString();

                if (getFilenameFilter(PIPELINE_CONFIG_EXTENSION).test(name))
                {
                    // We aren't really removing the TaskPipeline since it only lives in this cache.
                    // We are calling removeTaskPipeline so any locally defined tasks will be cleaned up.
                    TaskId pipelineId = createPipelineId(module, name);

                    if (null != pipelineId)
                        PipelineJobService.get().removeTaskPipeline(pipelineId);
                }
            }
        };
    }
}

