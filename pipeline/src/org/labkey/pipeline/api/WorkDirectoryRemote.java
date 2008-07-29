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
import org.apache.log4j.Logger;

import java.io.*;
import java.nio.channels.FileLock;
import java.nio.channels.FileChannel;

/**
 * Used to copy files from (and back to) a remote file system so that they can be used directly on the local file system 
 *
 * @author jeckels
 */
public class WorkDirectoryRemote extends AbstractWorkDirectory
{
    private static Logger _systemLog = Logger.getLogger(WorkDirectoryRemote.class);

    private static final int PERL_PIPELINE_LOCKS_DEFAULT = 5;
    
    private final File _lockDirectory;

    public File inputFile(File fileInput, boolean forceCopy) throws IOException
    {
        return copyInputFile(fileInput);
    }

    public static class Factory implements WorkDirFactory
    {
        private String _lockDirectory;
        private String _tempDirectory;

        public WorkDirectory createWorkDirectory(String jobId, FileAnalysisJobSupport support, Logger log) throws IOException
        {
            File tempDir;
            int attempt = 0;
            do
            {
                // We've seen very intermittent problems failing to create temp files in the past during the DRTs,
                // so try a few times before failing
                tempDir = File.createTempFile(support.getBaseName(), FT_WORK_DIR.getSuffix(), new File(_tempDirectory));
                tempDir.delete();
                tempDir.mkdirs();
                attempt++;
            }
            while (attempt < 5 && !tempDir.isDirectory());
            if (!tempDir.isDirectory())
            {
                throw new IOException("Failed to create local working directory " + tempDir);
            }

            return new WorkDirectoryRemote(support, tempDir, log, _lockDirectory);
        }

        public String getLockDirectory()
        {
            return _lockDirectory;
        }

        public void setLockDirectory(String lockDirectory)
        {
            if (!new File(lockDirectory).isDirectory())
                throw new IllegalArgumentException("The lock directory " + lockDirectory + " does not exist.");
            _lockDirectory = lockDirectory;
        }

        public String getTempDirectory()
        {
            return _tempDirectory;
        }

        public void setTempDirectory(String tempDirectory)
        {
            if (!new File(tempDirectory).isDirectory())
                throw new IllegalArgumentException("The temporary directory " + tempDirectory + " does not exist.");
            _tempDirectory = tempDirectory;
        }

        public void setTempDirectoryEnv(String tempDirectoryVar)
        {
            String tempDirectory = System.getenv(tempDirectoryVar);
            if (tempDirectory == null || tempDirectory.length() == 0)
                throw new IllegalArgumentException("The environment variable " + tempDirectoryVar + " does not exist:\n" + System.getenv());                
            setTempDirectory(tempDirectory);
        }
    }

    public WorkDirectoryRemote(FileAnalysisJobSupport support, File tempDir, Logger log, String lockDirectory) throws IOException
    {
        super(support, tempDir, log);
        if (lockDirectory != null)
        {
            _lockDirectory = new File(lockDirectory);
        }
        else
        {
            _lockDirectory = null;
        }
    }

    /**
     * @return a pair, where the first value is the total number of locks, and the second value is the lock index that
     * should be used next
     */
    private MasterLockInfo parseMasterLock(RandomAccessFile masterIn, File masterLockFile) throws IOException
    {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        byte[] b = new byte[128];
        int i;
        while ((i = masterIn.read(b)) != -1)
        {
            bOut.write(b, 0, i);
        }
        String line = new String(bOut.toByteArray(), "UTF-8").trim();
        if (line.length() == 0)
        {
            throw new IOException("Could not get the total number of locks from the master lock file " + masterLockFile);
        }
        String[] parts = line.split(" ");
        int totalLocks = PERL_PIPELINE_LOCKS_DEFAULT;
        int currentIndex;
        try
        {
            currentIndex = Integer.parseInt(parts[0]);
        }
        catch (NumberFormatException e)
        {
            throw new IOException("Could not parse the current lock index from the master lock file " + masterLockFile + ", the value was: " + parts[0]);
        }
        if (parts.length > 1)
        {
            try
            {
                totalLocks = Integer.parseInt(parts[1]);
            }
            catch (NumberFormatException e)
            {
                throw new IOException("Could not parse the total number of locks from the master lock file " + masterLockFile + ", the value was: " + parts[1]);
            }
        }
        if (totalLocks < 1)
            totalLocks = PERL_PIPELINE_LOCKS_DEFAULT;
        if (currentIndex >= totalLocks)
        {
            currentIndex = 0;
        }
        return new MasterLockInfo(totalLocks, currentIndex);
    }

    protected CopyingResource createCopyingLock() throws IOException
    {
        if (_lockDirectory == null)
        {
            return new SimpleCopyingResource();
        }

        _log.info("Starting to acquire lock for copying files");

        synchronized (WorkDirectoryRemote.class)
        {
            RandomAccessFile randomAccessFile = null;
            FileLock masterLock = null;

            try
            {
                File masterLockFile = new File(_lockDirectory, "counter");
                randomAccessFile = new RandomAccessFile(masterLockFile, "rw");
                FileChannel masterChannel = randomAccessFile.getChannel();
                masterLock = masterChannel.lock();

                MasterLockInfo lockInfo = parseMasterLock(randomAccessFile, masterLockFile);
                int nextIndex = (lockInfo.getCurrentLock() + 1) % lockInfo.getTotalLocks();
                rewriteMasterLock(randomAccessFile, new MasterLockInfo(lockInfo.getTotalLocks(), nextIndex));
                
                _log.info("Acquiring lock #" + lockInfo.getCurrentLock());
                File f = new File(_lockDirectory, "lock" + lockInfo.getCurrentLock());
                FileChannel lockChannel = new FileOutputStream(f, true).getChannel();
                FileLockCopyingResource result = new FileLockCopyingResource(lockChannel, lockInfo.getCurrentLock());
                _log.info("Lock #" + lockInfo.getCurrentLock() + " acquired");
                
                return result;
            }
            finally
            {
                if (randomAccessFile != null) { try { randomAccessFile.close(); } catch (IOException e) {} }
                if (masterLock != null) { try { masterLock.release(); } catch (IOException e) {} }
            }
        }
    }

    private void rewriteMasterLock(RandomAccessFile masterFile, MasterLockInfo lockInfo)
        throws IOException
    {
        masterFile.seek(0);

        String output = Integer.toString(lockInfo.getCurrentLock());
        if (lockInfo.getTotalLocks() != PERL_PIPELINE_LOCKS_DEFAULT)
            output += " " + Integer.toString(lockInfo.getTotalLocks());
        byte[] outputBytes = output.getBytes("UTF-8");
        masterFile.write(outputBytes);
        masterFile.setLength(outputBytes.length);
    }

    private static class MasterLockInfo
    {
        private int _totalLocks;
        private int _currentLock;

        private MasterLockInfo(int totalLocks, int currentLock)
        {
            assert totalLocks > 0 : "Total locks must be greater than 0.";
            
            _totalLocks = totalLocks;
            _currentLock = currentLock;
        }

        public int getTotalLocks()
        {
            return _totalLocks;
        }

        public int getCurrentLock()
        {
            return _currentLock;
        }
    }

    public class FileLockCopyingResource extends SimpleCopyingResource
    {
        private FileChannel _channel;
        private final int _lockNumber;
        private FileLock _lock;
        private Throwable _creationStack;

        public FileLockCopyingResource(FileChannel channel, int lockNumber) throws IOException
        {
            _channel = channel;
            _lockNumber = lockNumber;
            _lock = _channel.lock();
            _creationStack = new Throwable();
        }

        protected void finalize() throws Throwable
        {
            super.finalize();
            if (_lock != null)
            {
                _systemLog.error("FileLockCopyingResource was not released before it was garbage collected. Creation stack is: ", _creationStack);
            }
            release();
        }

        public void release()
        {
            if (_lock != null)
            {
                try { _lock.release(); } catch (IOException e) {}
                try { _channel.close(); } catch (IOException e) {}
                _log.info("Lock #" + _lockNumber + " released");
                _lock = null;
                _channel = null;
                super.release();
            }
        }
    }
}