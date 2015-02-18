package org.labkey.api.sequenceanalysis.pipeline;

import java.util.List;

/**
 * Created by bimber on 2/9/2015.
 */
public interface ParameterizedOutputHandler extends SequenceOutputHandler
{
    public List<ToolParameterDescriptor> getParameters();
}
