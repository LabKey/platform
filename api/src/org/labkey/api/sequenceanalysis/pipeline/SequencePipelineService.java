package org.labkey.api.sequenceanalysis.pipeline;


import org.labkey.api.pipeline.PipelineJob;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:43 PM
 */
abstract public class SequencePipelineService
{
    public static final String SEQUENCE_TOOLS_PARAM = "SEQUENCEANALYSIS_TOOLS";

    static SequencePipelineService _instance;

    public static SequencePipelineService get()
    {
        return _instance;
    }

    static public void setInstance(SequencePipelineService instance)
    {
        _instance = instance;
    }

    abstract public void registerPipelineStep(PipelineStepProvider provider);

    abstract public Set<PipelineStepProvider> getAllProviders();

    abstract public <StepType extends PipelineStep> Set<PipelineStepProvider<StepType>> getProviders(Class<? extends StepType> stepType);

    abstract public <StepType extends PipelineStep> PipelineStepProvider<StepType> getProviderByName(String name, Class<? extends StepType> stepType);

    abstract public <StepType extends PipelineStep> List<PipelineStepProvider<StepType>> getSteps(PipelineJob job, Class<StepType> stepType);

    abstract public File getExeForPackage(String packageName, String exe);
}
