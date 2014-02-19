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
package org.labkey.pipeline.api;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;
import org.labkey.pipeline.analysis.FileAnalysisTaskPipelineImpl;

/**
 * User: kevink
 * Date: 1/6/14
 *
 * Loads TaskPipeline from file-based modules from a /pipelines/pipeline/&lt;name&gt;.pipeline.xml file.
 * Similar to the ETL DescriptorCache's ScheduledPipelineJobDescriptor, the TaskPipeline may register some locally defined TaskFactories as well.
 */
/* package */ class TaskPipelineCacheHandler implements ModuleResourceCacheHandler<String, TaskPipeline>
{
    private static final Logger LOG = Logger.getLogger(TaskPipelineCacheHandler.class);
    private static final String PIPELINE_CONFIG_EXTENSION = ".pipeline.xml";

    static final String MODULE_PIPELINES_DIR = "pipelines";

    @Override
    public boolean isResourceFile(String filename)
    {
        return filename.endsWith(PIPELINE_CONFIG_EXTENSION) && filename.length() > PIPELINE_CONFIG_EXTENSION.length();
    }

    @Override
    public String getResourceName(Module module, String filename)
    {
        String name = filename.substring(0, filename.length() - PIPELINE_CONFIG_EXTENSION.length());
        TaskId taskId = createId(module, name);
        return taskId.toString();
    }

    private TaskId createId(Module module, String name)
    {
        return new TaskId(module.getName(), TaskId.Type.pipeline, name, 0);
    }

    private TaskId parseId(Module module, String resourceName)
    {
        try
        {
            TaskId taskId = new TaskId(resourceName);
            assert taskId.getModuleName().equals(module.getName());
            assert taskId.getType().equals(TaskId.Type.pipeline);
            return taskId;
        }
        catch (ClassNotFoundException e)
        {
            // shouldn't happen since we're not managing tasks with class names
            return null;
        }
    }

    @Override
    public String createCacheKey(Module module, String resourceName)
    {
        TaskId pipelineId = parseId(module, resourceName);
        return pipelineId.toString();
    }

    @Override
    public CacheLoader<String, TaskPipeline> getResourceLoader()
    {
        return new CacheLoader<String, TaskPipeline>()
        {
            @Override
            public TaskPipeline load(String key, @Nullable Object argument)
            {
                TaskId pipelineId;
                try
                {
                    pipelineId = TaskId.valueOf(key);
                }
                catch (ClassNotFoundException e)
                {
                    LOG.warn(e);
                    return null;
                }

                // Look for a module pipeline config file
                if (pipelineId.getNamespaceClass() == null && pipelineId.getName() != null && pipelineId.getModuleName() != null)
                {
                    Module module = ModuleLoader.getInstance().getModule(pipelineId.getModuleName());
                    String configFileName = pipelineId.getName() + PIPELINE_CONFIG_EXTENSION;

                    // Look for a "pipeline/pipelines/<name>.pipeline.xml" file
                    Path pipelineConfigPath = new Path(PipelineJobServiceImpl.MODULE_PIPELINE_DIR, MODULE_PIPELINES_DIR, configFileName);
                    Resource pipelineConfig = module.getModuleResource(pipelineConfigPath);
                    if (pipelineConfig != null && pipelineConfig.isFile())
                        return load(pipelineId, pipelineConfig);
                }

                return null;
            }

            private TaskPipeline load(TaskId taskId, Resource pipelineConfig)
            {
                try
                {
                    return FileAnalysisTaskPipelineImpl.create(taskId, pipelineConfig);
                }
                catch (IllegalArgumentException|IllegalStateException e)
                {
                    LOG.warn("Error registering '" + taskId + "' pipeline: " + e.getMessage());
                    return null;
                }
            }
        };
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
                // We aren't really removing the TaskPipeline since it only lives in this cache.
                // We are calling removeTaskPipeline so any locally defined tasks will be cleaned up.
                String resourceName = getResourceName(module, entry.toString());
                TaskId pipelineId = parseId(module, resourceName);
                PipelineJobService.get().removeTaskPipeline(pipelineId);
            }
        };
    }
}

