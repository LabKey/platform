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
package org.labkey.pipeline.api;

import org.labkey.api.pipeline.WorkDirFactory;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileLock;

/**
 * Used to copy files from (and back to) a remote file system so that they can be used directly on the local file system 
 *
 * @author jeckels
 */
public class WorkDirectoryRemote extends AbstractWorkDirectory
{
    private File _lockDirectory;
    private File _masterLockFile;

    public static class Factory implements WorkDirFactory
    {
        public WorkDirectory createWorkDirectory(String jobId, FileAnalysisJobSupport support) throws IOException
        {
            File tempDir;
            int attempt = 0;
            do
            {
                // We've seen very intermittent problems failing to create temp files in the past during the DRTs,
                // so try a few times before failing
                tempDir = File.createTempFile(support.getBaseName(), FT_WORK_DIR.getSuffix());
                tempDir.delete();
                tempDir.mkdirs();
                attempt++;
            }
            while (attempt < 5 && !tempDir.isDirectory());
            if (!tempDir.isDirectory())
            {
                throw new IOException("Failed to create local working directory");
            }

            return new WorkDirectoryRemote(support, tempDir);
        }
    }

    public WorkDirectoryRemote(FileAnalysisJobSupport support, File tempDir) throws IOException
    {
        super(support, tempDir);
    }

    protected CopyingLock acquireCopyingLock()
    {
//        _masterLockFile
        return null;
    }

    public class LockingCopyingLock implements CopyingLock
    {
        private FileLock _lock;

        public LockingCopyingLock(FileLock lock)
        {
            _lock = lock;
        }

        public void release() throws IOException
        {
            _lock.release();
        }
    }
}