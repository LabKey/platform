/*
 * Copyright (c) 2013-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ParameterDescription;
import org.labkey.api.di.ScheduledPipelineJobContext;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.writer.ContainerUser;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: 3/13/13
 */
public class TransformJobContext extends ScheduledPipelineJobContext implements ContainerUser, Serializable
{
    private String _transformId;
    private int _version;
    private PipelineJob _pipelineJob;
    private Map<ParameterDescription, Object> _params =  new LinkedHashMap<>();
    private Pair<Object, Object> _incrementalWindow = null;

    // No-args constructor to support de-serialization in Java 7
    @SuppressWarnings({"UnusedDeclaration"})
    public TransformJobContext()
    {
    }

    public TransformJobContext(ScheduledPipelineJobDescriptor descriptor, Container container, User user, Map<ParameterDescription, Object> params)
    {
        super(descriptor, container, user);
        _transformId = descriptor.getId();
        _version = descriptor.getVersion();
        if (null != params)
            _params.putAll(params);
    }

    public String getTransformId()
    {
        return _transformId;
    }

    public int getTransformVersion()
    {
        return _version;
    }

    public @Nullable PipelineJob getPipelineJob()
    {
        return _pipelineJob;
    }

    public void setPipelineJob(@NotNull PipelineJob job)
    {
        if (_locked)
            throw new IllegalStateException("Context is read-only");
        if (null != _pipelineJob && _pipelineJob != job)
            throw new IllegalStateException("Context is already associated with a pipeline job");
        _pipelineJob = job;
    }

    @Override
    public TransformJobContext clone()
    {
        return (TransformJobContext)super.clone();
    }

    public Map<ParameterDescription, Object> getParams()
    {
        return _params;
    }

    /** An explicit set of min/max incremental filter values, overriding any persisted values from previous runs.
     *  Setting these values in the UI is only available in dev mode, and is intended to be used for testing
     *  purposes only.
     */
    public Pair<Object, Object> getIncrementalWindow()
    {
        return _incrementalWindow;
    }

    public void setIncrementalWindow(Pair<Object, Object> incrementalWindow)
    {
        _incrementalWindow = incrementalWindow;
    }
}
