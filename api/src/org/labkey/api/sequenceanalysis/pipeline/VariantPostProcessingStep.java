package org.labkey.api.sequenceanalysis.pipeline;

import java.io.File;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:13 PM
 */
public interface VariantPostProcessingStep extends PipelineStep
{
    public Output processVariants(File inputVCF, File refFasta);

    public static interface Output extends PipelineStepOutput
    {

    }
}
