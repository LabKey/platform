/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.pipeline.api;

import org.labkey.api.pipeline.WorkDirFactory;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;

/**
 * Implementation that creates a temp directory into which files are copied to avoid writing partial (and thus mangled)
 * result files directly to their intended target in the case of failures
 *
 * @author brendanx
 */
public class WorkDirectoryLocal extends AbstractWorkDirectory
{
    public static class Factory extends AbstractFactory
    {
        public WorkDirectory createWorkDirectory(String jobId, FileAnalysisJobSupport support, boolean useDeterministicFolderPath, Logger log) throws IOException
        {
            File dir = FT_WORK_DIR.newFile(support.getAnalysisDirectory(),
                    support.getBaseName());

            return new WorkDirectoryLocal(support, this, dir, useDeterministicFolderPath, log);
        }
    }

    public WorkDirectoryLocal(FileAnalysisJobSupport support, WorkDirFactory factory, File dir, boolean reuseExistingDirectory, Logger log) throws IOException
    {
        super(support, factory, dir, reuseExistingDirectory, log);
    }

    public File inputFile(File fileInput, boolean forceCopy) throws IOException
    {
        if (!forceCopy)
            return fileInput;
        return copyInputFile(fileInput);
    }

    public File inputFile(File fileInput, File fileWork, boolean forceCopy) throws IOException
    {
        if (!forceCopy)
            return fileInput;

        return copyInputFile(fileInput, fileWork);
    }

    protected CopyingResource createCopyingLock()
    {
        return new SimpleCopyingResource();
    }
}
