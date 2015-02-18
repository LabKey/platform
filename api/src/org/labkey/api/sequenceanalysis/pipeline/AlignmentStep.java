package org.labkey.api.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;

import java.io.File;

/**
 * User: bimber
 * Date: 10/27/13
 * Time: 9:19 PM
 */
public interface AlignmentStep extends PipelineStep
{
    /**
     * Creates any indexes needed by this aligner if not already present.
     * @throws PipelineJobException
     */
    public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException;

    /**
     * Performs a reference guided alignment using the provided files.
     * @param inputFastq1 The forward FASTQ file
     * @param inputFastq2 The second FASTQ, if using paired end data
     * @param basename The basename to use as the output
     * @throws PipelineJobException
     */
    public AlignmentOutput performAlignment(File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename) throws PipelineJobException;

    public boolean doMergeUnalignedReads();

    public boolean doSortIndexBam();

    public static interface AlignmentOutput extends PipelineStepOutput
    {
        /**
         * If created, returns a pair of FASTQ files containing the unaligned reads
         */
        public File getUnalignedReadsFastq();

        public File getBAM();
    }

    public static interface IndexOutput extends PipelineStepOutput
    {

    }
}
