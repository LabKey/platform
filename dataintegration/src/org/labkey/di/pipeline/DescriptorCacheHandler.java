/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;

/**
 * User: adam
 * Date: 9/13/13
 * Time: 4:26 PM
 */
public class DescriptorCacheHandler implements ModuleResourceCacheHandler<ScheduledPipelineJobDescriptor>
{
    private static final TransformManager _transformManager = TransformManager.get();

    static final String DIR_NAME = "etls";

    @Override
    public String createCacheKey(Module module, String resourceName)
    {
        return _transformManager.createConfigId(module, resourceName);
    }

    @Override
    public boolean isResourceFile(String filename)
    {
        return _transformManager.isConfigFile(filename);
    }

    @Override
    public String getResourceName(Module module, String filename)
    {
        assert isResourceFile(filename) : "Configuration filename \"" + filename + "\" does not end with .xml";
        return FileUtil.getBaseName(filename);
    }

    @Override
    public CacheLoader<String, ScheduledPipelineJobDescriptor> getResourceLoader()
    {
        return DESCRIPTOR_LOADER;
    }

    private static final CacheLoader<String, ScheduledPipelineJobDescriptor> DESCRIPTOR_LOADER = new CacheLoader<String, ScheduledPipelineJobDescriptor>()
    {
        @Override
        public ScheduledPipelineJobDescriptor load(String configId, @Nullable Object argument)
        {
            Pair<Module, String> pair = _transformManager.parseConfigId(configId);
            Module module = pair.first;
            String configName = pair.second;

            Path configPath = new Path(DIR_NAME, configName + ".xml");
            Resource config = pair.first.getModuleResolver().lookup(configPath);

            if (config != null && config.isFile())
                return _transformManager.parseETL(config, module);
            else
                return null;
        }
    };


    @Nullable
    @Override
    public FileSystemDirectoryListener createChainedDirectoryListener(Module module)
    {
        return new EtlDirectoryListener(module);
    }

    private class EtlDirectoryListener implements FileSystemDirectoryListener
    {
        private final Module _module;

        public EtlDirectoryListener(Module module)
        {
            _module = module;
        }

        @Override
        public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
        {
        }

        @Override
        public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            removeDescriptor(entry);
        }

        @Override
        public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            removeDescriptor(entry);
        }

        @Override
        public void overflow()
        {
            // TODO: Should clear all the registered pipelines, but no current method to retrieve list of all config names across all modules.
        }

        // TODO: Ideally, we'd take a lighterweight approach, since this will be called many times for each descriptor file change
        private void removeDescriptor(java.nio.file.Path entry)
        {
            String filename = entry.toString();

            final String configName = _transformManager.getConfigName(filename);
            final TaskId pipelineId = new TaskId(_module.getName(), TaskId.Type.pipeline, _transformManager.createConfigId(_module, configName),0);
            PipelineJobService.get().removeTaskPipeline(pipelineId);
        }
    }
}
