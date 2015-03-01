/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.labkey.api.pipeline.cmd.TaskPath;
import org.labkey.api.util.FileType;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Represents a working directory in which files are made available to pipeline tasks. Typically, output files
 * are stored in this working directory and moved to their desired permanent location after the task has completed
 * successfully. Additionally, file inputs may be copied to the directory to ensure they are local, providing better
 * IO performance.
 *
 * @author brendanx
 */
public interface WorkDirectory
{
    enum Function {
        /** File is an input into the job. */
        input,
        /** File is an output of the job. */
        output,
        /** File is a relative path from the root of the {@link TaskPipeline#getDeclaringModule()}. */
        module
    }

    /**
     * @return the directory where the input files live and where the output files will end up
     */
    File getDir();

    /** Informs the WorkDirectory that a new file is being created. It is treated as a Function.output */
    File newFile(String name);

    /** Informs the WorkDirectory that a new file is being created. */
    File newFile(Function f, String name);

    /** Informs the WorkDirectory that a new file is being created. It is treated as a Function.output */
    File newFile(FileType type);

    /** Informs the WorkDirectory that a new file is being created. */
    File newFile(Function f, FileType type);

    /**
     * Indicates that a file is to be used as input. The implementation can choose whether it needs to be copied, unless
     * forceCopy is true (in which case it will always be copied to the work directory
     * @return the full path to the file where it is available for use
     */
    File inputFile(File fileInput, boolean forceCopy) throws IOException;

    /**
     * Indicates that a file is to be used as input. The implementation can choose whether it needs to be copied, unless
     * forceCopy is true (in which case it will always be copied to the work directory.  This version of the method allows the caller
     * to manually specify the destination file, which allows callers to place files into subdirectories of the work directory
     * @return the full path to the file where it is available for use
     */
    File inputFile(File fileInput, File fileWork, boolean forceCopy) throws IOException;
    
    /** @return the relative path of the file relative to the work directory itself. The file is presumed to be under the work directory. */
    String getRelativePath(File fileWork) throws IOException;

    /**
     * @return the final location for file after it's copied out of the work directory
     */
    File outputFile(File fileWork) throws IOException;

    /**
     * @return the final location for file after it's copied out of the work directory
     */
    File outputFile(File fileWork, String nameDest) throws IOException;

    /**
     * @return copies the file to the specified location
     */
    File outputFile(File fileWork, File dest) throws IOException;

    /**
     * Delete a file from the working directory
     */
    void discardFile(File fileWork) throws IOException;

    /** Deletes any inputs that were copied into this working directory */
    void discardCopiedInputs() throws IOException;

    /**
     * Associates all of the output files now in the work directory (including those that were explicitly declared as
     * expected outputs and any other files that might be present) with the RecordedAction
     */
    void acceptFilesAsOutputs(Map<String, TaskPath> expectedOutputs, RecordedAction action) throws IOException;

    /**
     * Cleans up any lingering inputs and deletes the working directory
     * @param success whether or not the task completed successfully. If so, it's fair to complain about unexpected
     * files that are still left. If not, don't add additional errors to the log.
     */
    void remove(boolean success) throws IOException;

    /**
     * Pipeline inputs are copied to the working directory.  If the passed file was already copied to the work directory, this will
     * return the local copy.
     */
    public File getWorkingCopyForInput(File f);

    /**
     * Ensures that we have a lock, if needed. The lock must be released by the caller. Locks can be configured so that
     * we do not have too many separate network file operations in place across multiple machines.
     */
    public CopyingResource ensureCopyingLock() throws IOException;

    List<File> getWorkFiles(Function f, TaskPath tp);

    File newWorkFile(Function output, TaskPath taskPath, String baseName);

    /** A lock for copying files over a network share, for convenient use with try-with-resources */
    public interface CopyingResource extends AutoCloseable
    {
        public void close();
    }
}
