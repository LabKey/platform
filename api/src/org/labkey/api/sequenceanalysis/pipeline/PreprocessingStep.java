package org.labkey.api.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.Pair;

import java.io.File;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:07 PM
 */
public interface PreprocessingStep extends PipelineStep
{
    public Output processInputFile(File inputFile, @Nullable File inputFile2, File outputDir) throws PipelineJobException;

    public static interface Output extends PipelineStepOutput
    {
        public Pair<File, File> getProcessedFastqFiles();
    }
}
