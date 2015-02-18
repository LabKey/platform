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
