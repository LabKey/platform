package org.labkey.api.sequenceanalysis.pipeline;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.ReadsetModel;

import java.io.File;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:10 PM
 */
public interface BamProcessingStep extends PipelineStep
{
    public BamProcessingStep.Output processBam(ReadsetModel rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException;

    public static interface Output extends PipelineStepOutput
    {
        /**
         * Returns the processed BAM
         */
        public File getBAM();
    }
}
