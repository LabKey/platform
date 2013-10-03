/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
 * <code>WorkDirectory</code>
 *
 * @author brendanx
 */
public interface WorkDirectory
{
    enum Function { input, output }

    /**
     * @return the directory where the input files live and where the output files will end up
     */
    File getDir();

    File newFile(String name);

    File newFile(Function f, String name);

    File newFile(FileType type);

    File newFile(Function f, FileType type);

    File inputFile(File fileInput, boolean forceCopy) throws IOException;

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
     * Delete a file
     * @throws IOException
     */
    void discardFile(File fileWork) throws IOException;

    /** Deletes any inputs that were copied into this working directory */
    void discardCopiedInputs() throws IOException;

    void acceptFilesAsOutputs(Map<String, TaskPath> expectedOutputs, RecordedAction action) throws IOException;

    /**
     * Cleans up any lingering inputs and deletes the working directory
     * @param success whether or not the task completed successfully. If so, it's fair to complain about unexpected
     * files that are still left. If not, don't add additional errors to the log.
     */
    void remove(boolean success) throws IOException;

    /**
     * Ensures that we have a lock, if needed. The lock must be released by the caller.
     */
    public CopyingResource ensureCopyingLock() throws IOException;

    List<File> getWorkFiles(Function f, TaskPath tp);

    File newWorkFile(Function output, TaskPath taskPath, String baseName);

    public interface CopyingResource extends AutoCloseable
    {
        public void close();
    }
}
