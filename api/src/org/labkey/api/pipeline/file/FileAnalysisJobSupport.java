/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.pipeline.file;

import org.labkey.api.exp.pipeline.XarTemplateSubstitutionId;
import org.labkey.api.exp.pipeline.XarImportTaskId;
import org.labkey.api.pipeline.ParamParser;

import java.io.File;
import java.util.Map;

/**
 * <code>FileAnalysisJobSupport</code>
 *
 * @author brendanx
 */
public interface FileAnalysisJobSupport
        extends XarTemplateSubstitutionId.JobSupport
{
    /**
     * @return the directory of the pipeline-root running this job.
     */
    File getRootDir();

    /**
     * @return the base name for the full set of files.
     */
    String getJoinedBaseName();

    /**
     * @return base name of the original input file.
     */
    String getBaseName();

    /**
     * @return the directory in which the original input file resides.
     */
    File getDataDirectory();

    /**
     * @return the directory where the input files reside, and where the
     *      final analysis should end up.
     */
    File getAnalysisDirectory();

    /**
     * Returns a file for use as input in the pipeline, given its name.
     * This allows the task definitions to name files they require as input,
     * and the pipeline definition to specify where those files should come from.
     */
    File findInputFile(String name);

    /**
     * Returns a file for use as output in the pipeline, given its name. 
     * This allows the task definitions to name files they create as output,
     * and the pipeline definition to specify where those files should end up.
     */
    File findOutputFile(String name);

    /**
     * @return a parameter parser object for writing parameters to a file.
     */
    ParamParser createParamParser();

    /**
     * @return name-value map of the BioML parameters.
     */
    Map<String, String> getParameters();

    /**
     * @return the parameters input file used to drive the pipeline.
     */
    File getParametersFile();

    /**
     * @return an array of all input files analyzed.
     */
    File[] getInputFiles();
}
