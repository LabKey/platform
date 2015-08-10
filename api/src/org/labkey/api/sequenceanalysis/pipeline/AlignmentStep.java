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

    public boolean doAddReadGroups();

    public boolean doSortIndexBam();

    public boolean supportsGzipFastqs();

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
