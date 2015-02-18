package org.labkey.api.sequenceanalysis.pipeline;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:08 PM
 *
 * This describes one step in the pipeline, which is divided into the steps below.  Subclasses of this interface
 * define methods specific to that type of PipelineStep.  Each step is typically a wrapper around a command line tool or script.
 * Supported step types are enumerated in PipelineStep.StepType
 */
public interface PipelineStep
{
    public PipelineContext getPipelineCtx();

    public PipelineStepProvider getProvider();

    public enum StepType
    {
        fastqProcessing(PreprocessingStep.class),
        referenceLibraryCreation(ReferenceLibraryStep.class),
        alignment(AlignmentStep.class),
        bamPostProcessing(BamProcessingStep.class),
        variantCalling(VariantCallingStep.class),
        variantPostProcessing(VariantPostProcessingStep.class),
        analysis(AnalysisStep.class);

        private Class<? extends PipelineStep> _stepClass;

        StepType(Class<? extends PipelineStep> clazz)
        {
            _stepClass = clazz;
        }

        public Class<? extends PipelineStep> getStepClass()
        {
            return _stepClass;
        }

        public static StepType getStepType(Class<? extends PipelineStep> step)
        {
            for (StepType t : values())
            {
                if (t.getStepClass().isAssignableFrom(step))
                {
                    return t;
                }
            }

            throw new IllegalArgumentException("Unable to find matching type for class: " + step.getName());
        }
    }
}
