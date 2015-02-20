/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.sequenceanalysis.pipeline;

import org.labkey.api.pipeline.PipelineJobException;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:50 PM
 */
abstract public class AbstractPipelineStep implements PipelineStep
{
    private PipelineContext _ctx;
    private PipelineStepProvider _provider;

    public AbstractPipelineStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        _ctx = ctx;
        _provider = provider;
    }

    public PipelineContext getPipelineCtx()
    {
        return _ctx;
    }

    public PipelineStepProvider getProvider()
    {
        return _provider;
    }

    protected <ParamType> ParamType extractParamValue(String paramName, Class<ParamType> clazz) throws PipelineJobException
    {
        return getProvider().getParameterByName(paramName).extractValue(getPipelineCtx().getJob(), getProvider(), clazz);
    }
}
