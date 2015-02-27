package org.labkey.api.sequenceanalysis.run;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 6/18/2014
 * Time: 9:29 PM
 */
abstract public class AbstractCommandPipelineStep<Wrapper extends CommandWrapper> extends AbstractPipelineStep
{
    private Wrapper _wrapper;

    public AbstractCommandPipelineStep(PipelineStepProvider provider, PipelineContext ctx, Wrapper wrapper)
    {
        super(provider, ctx);
        _wrapper = wrapper;
    }

    public List<String> getClientCommandArgs() throws PipelineJobException
    {
        return getClientCommandArgs(" ");
    }

    public List<String> getClientCommandArgs(String separator) throws PipelineJobException
    {
        List<String> ret = new ArrayList<>();
        List<ToolParameterDescriptor> params = getProvider().getParameters();
        for (ToolParameterDescriptor desc : params)
        {
            if (desc.getCommandLineParam() != null)
            {
                ret.addAll(desc.getCommandLineParam().getArguments(separator, desc.extractValueForCommandLine(getPipelineCtx().getJob(), getProvider())));
            }
        }

        return ret;
    }

    public Wrapper getWrapper()
    {
        return _wrapper;
    }
}
