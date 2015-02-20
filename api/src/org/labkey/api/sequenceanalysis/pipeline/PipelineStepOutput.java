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
import org.labkey.api.util.Pair;

import java.io.File;
import java.util.List;

/**
 * Provides information about the outputs of a pipeline step.  This does not act on these files,
 * but will provide the pipeline with information used to track outputs and cleanup intermediate files
 *
 * User: bimber
 * Date: 6/21/2014
 * Time: 7:47 AM
 */
public interface PipelineStepOutput
{
    /**
     * Returns a list of pairs giving additional input files and role of this file.  Note: inputs are usually set upfront, so this will only include
     * any non-standard inputs created during the course of this step
     */
    public List<Pair<File, String>> getInputs();

    /**
     * Returns a list of pairs giving the output file and role of this output
     */
    public List<Pair<File, String>> getOutputs();

    /**
     * Returns a list of pairs giving the output file and role of this output
     */
    public List<File> getOutputsOfRole(String role);

    /**
     * Returns a list of intermediate files created during this step.  Intermediate files are files
     * that are deemed non-essential by this step.  If the pipeline has selected deleteIntermediaFiles=true,
     * these files will be deleted during the cleanup step.
     */
    public List<File> getIntermediateFiles();

    /**
     * Returns a list of deferred delete intermediate files created during this step.  These are similar to the files
     * tagged as intermediate files, except that the delete step does not run until the very end of the pipeline.
     * This allows earlier steps to create products that are needed by later steps (such as aligner-specific indexes),
     * but still delete these files at the end of the process.
     */
    public List<File> getDeferredDeleteIntermediateFiles();

    public List<SequenceOutput> getSequenceOutputs();

    public static class SequenceOutput
    {
        private File _file;
        private String _label;
        private String _category;
        private Integer _readsetId;
        private Integer _analysisId;
        private Integer _genomeId;

        public SequenceOutput(File file, String label, String category, @Nullable Integer readsetId, @Nullable Integer analysisId, @Nullable Integer genomeId)
        {
            _file = file;
            _label = label;
            _category = category;
            _readsetId = readsetId;
            _analysisId = analysisId;
            _genomeId = genomeId;
        }

        public File getFile()
        {
            return _file;
        }

        public String getLabel()
        {
            return _label;
        }

        public String getCategory()
        {
            return _category;
        }

        public Integer getReadsetId()
        {
            return _readsetId;
        }

        public Integer getAnalysisId()
        {
            return _analysisId;
        }

        public Integer getGenomeId()
        {
            return _genomeId;
        }
    }
}
