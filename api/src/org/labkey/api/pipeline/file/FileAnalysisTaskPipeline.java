/*
 * Copyright (c) 2008 LabKey Software Foundation
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

import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.util.FileType;

import java.io.File;

/**
 * <code>FileAnalysisTaskPipeline</code>
 */
public interface FileAnalysisTaskPipeline extends TaskPipeline
{
    /**
     * Returns a description of this pipeline to be displayed in the
     * user interface for initiating a job that will run the pipeline.
     *
     * @return a description of this pipeline
     */
    String getDescription();

    /**
     * Returns the name of the protocol factory for this pipeline, which
     * will be used as the root directory name for all analyses of this type
     * and the directory name of the saved system protocol XML files.
     *
     * @return the name of the protocol factory
     */
    String getProtocolFactoryName();

    /**
     * Returns the full list of acceptable file types that can be used to
     * start this pipeline.
     *
     * @return array containing acceptable initial file types
     */
    FileType[] getInitialFileTypes();

    /**
     * Returns true if a given file is of a type that can be used to start
     * this pipeline.
     *  
     * @param f the file to test
     * @return true if the file can be used to start the pipeline
     */
    boolean isInitialType(File f);

    /**
     * Returns a specific instance of an input file, pipeline root directory,
     * the analysis directory and the file name.  For finding input files before
     * the pipeline job has been created.
     *
     * @param dirRoot pipeline root directory
     * @param dirAnalysis analysis directory
     * @param name file name to find
     * @return an instance of an input file, or null if failed to locate
     */
    File findInputFile(File dirRoot, File dirAnalysis, String name);
    
    /**
     * Returns a specific instance of an input file, given the pipeline job
     * context in which this pipeline is being run and the file name.
     * 
     * @param support the job support context in which the pipeline is running
     * @param name the name of the file for which the path is required
     * @return an instance of an input file that may be used in a task, or null
     */
    File findInputFile(FileAnalysisJobSupport support, String name);

    /**
     * Returns a specific instance of an output file, given the pipeline job
     * context in which this pipeline is being run and the file name.
     *
     * @param support the job support context in which the pipeline is running
     * @param name the name of the file for which the path is required
     * @return an instance of an output file that may be used in a task
     */
    File findOutputFile(FileAnalysisJobSupport support, String name);

    /**
     * Returns support for generating a XAR file to describe this task
     * pipeline.
     *
     * @return a support interface for generating XAR files
     */
    FileAnalysisXarGeneratorSupport getXarGeneratorSupport();
}
