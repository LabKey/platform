/*
 * Copyright (c) 2015-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.pipeline.file.PathMapperImpl;

import java.util.Collections;
import java.util.Set;

/**
 * Created by: jeckels
 * Date: 11/18/15
 */
public class AbstractRemoteExecutionEngineConfig implements PipelineJobService.RemoteExecutionEngineConfig
{
    @NotNull
    private final String _type;
    @NotNull
    private final String _location;
    @NotNull
    private final Set<String> _queues;
    @NotNull
    private final PathMapper _mapper;

    protected AbstractRemoteExecutionEngineConfig(@NotNull String type, @NotNull String location)
    {
        this(type, location, Collections.emptySet(), new PathMapperImpl());
    }

    protected AbstractRemoteExecutionEngineConfig(@NotNull String type, @NotNull String location, @NotNull Set<String> queues, @NotNull PathMapper mapper)
    {
        _type = type;
        _location = location;
        _queues = Collections.unmodifiableSet(queues);
        _mapper = mapper;
    }

    @NotNull
    @Override
    public String getLocation()
    {
        return _location;
    }

    @NotNull
    @Override
    public String getType()
    {
        return _type;
    }

    @NotNull
    @Override
    public PathMapper getPathMapper()
    {
        return _mapper;
    }

}
