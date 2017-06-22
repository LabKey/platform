/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.resource.Resource;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * User: adam
 * Date: 9/13/13
 * Time: 4:26 PM
 */
public class DescriptorCacheHandler implements ModuleResourceCacheHandler<Map<String, ScheduledPipelineJobDescriptor>>
{
    public static final String DESCRIPTOR_EXTENSION = ".xml";

    private static final TransformManager _transformManager = TransformManager.get();

    @Override
    public Map<String, ScheduledPipelineJobDescriptor> load(Stream<? extends Resource> resources, Module module)
    {
        return unmodifiable(resources
            .filter(getFilter(DESCRIPTOR_EXTENSION))
            .map(resource -> _transformManager.parseETL(resource, module))
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(TransformDescriptor::getId, Function.identity())));
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

                if (getFilenameFilter(DESCRIPTOR_EXTENSION).test(filename))
                {
                    final String configName = _transformManager.getConfigName(filename);
                    final TaskId pipelineId = new TaskId(module.getName(), TaskId.Type.pipeline, _transformManager.createConfigId(module, configName), 0);
                    PipelineJobService.get().removeTaskPipeline(pipelineId);
                }
            }
        };
    }
}
