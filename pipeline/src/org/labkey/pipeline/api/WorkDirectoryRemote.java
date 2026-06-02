/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import org.apache.logging.log4j.LogManager;
import java.lang.ref.Cleaner;
import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.WorkDirFactory;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.URIUtil;
import org.labkey.vfs.FileLike;
import org.labkey.vfs.FileSystemLike;
import org.springframework.beans.factory.InitializingBean;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Used to copy files from (and back to) a remote file system so that they can be used directly on the local file system,
 * improving performance on high-latency and/or low-bandwidth network file systems
 *
 * @author jeckels
 */
public class WorkDirectoryRemote extends AbstractWorkDirectory
{
    private static final Logger _systemLog = LogManager.getLogger(WorkDirectoryRemote.class);

    private static final int FILE_LOCKS_DEFAULT = 5;

    private final FileLike _lockDirectory;
    private final FileLike _folderToClean;

    private static final Map<File, Lock> _locks = new HashMap<>();

    @Override
    public FileLike inputFile(FileLike fileInput, boolean forceCopy) throws IOException
    {
        return inputFile(fileInput, newFile(fileInput.getName()), forceCopy);
    }

    @Override
    public FileLike inputFile(FileLike fileInput, FileLike fileWork, boolean forceCopy) throws IOException
    {
        //can be used to prevent duplicate copy attempts
        if (fileWork.exists() && !forceCopy)
        {
            _copiedInputs.put(fileInput, fileWork);
        }

        return copyInputFile(fileInput, fileWork);
    }

    public static class Factory extends AbstractFactory implements InitializingBean
    {
        private String _lockDirectory;
        private String _tempDirectory;
        private boolean _sharedTempDirectory;
        private boolean _allowReuseExistingTempDirectory;
        private boolean _deterministicWorkingDirName;
        private boolean _cleanupOnStartup;
        private String _transferToDirOnFailure = null;

        @Override
        public void afterPropertiesSet()
        {
            if (_tempDirectory == null)
            {
                throw new IllegalStateException("tempDirectory not set - set it directly using the tempDirectory property or use the tempDirectoryEnv property to point to an environment variable");
            }
            if (_cleanupOnStartup)
            {
                FileUtil.deleteDirectoryContents(new File(_tempDirectory));
            }
        }

        @Override
        public WorkDirectory createWorkDirectory(String jobId, FileAnalysisJobSupport support, boolean useDeterministicFolderPath, Logger log) throws IOException
        {
            if (useDeterministicFolderPath)
            {
                _sharedTempDirectory = true;
                _allowReuseExistingTempDirectory = true;
                _deterministicWorkingDirName = true;
            }

            FileLike tempDir;
            FileLike tempDirBase = null;
            int attempt = 0;
            do
            {
                // We've seen very intermittent problems failing to create temp files in the past during the DRTs,
                // so try a few times before failing
                FileLike dirParent = (_tempDirectory == null ? null : FileSystemLike.wrapFile(new File(_tempDirectory)));

                // If the temp directory is shared, then create a jobId directory to be sure the
                // work directory path is unique.
                try
                {
                    if (_sharedTempDirectory)
                    {
                        if (_deterministicWorkingDirName)
                        {
                            dirParent = dirParent.resolveChild(jobId);
                            tempDirBase = dirParent;
                        }
                        else
                        {
                            dirParent = FileUtil.createTempFile(jobId, "", dirParent);
                            tempDirBase = dirParent;
                        }

                        if (_allowReuseExistingTempDirectory && dirParent.exists())
                        {
                            log.info("parent directory exists, reusing: {}", dirParent.getPath());
                        }
                        else
                        {
                            dirParent.delete();
                            FileUtil.mkdirs(dirParent);
                        }
                    }

                    String name = support.getBaseName();
                    if (name.length() > 10)
                    {
                        // Don't let the total path get too long - Windows doesn't like paths longer than 255 characters
                        // so if there's a ridiculously long file name, we don't want to duplicate its name in the
                        // directory too
                        name = StringUtilsLabKey.leftSurrogatePairFriendly(name, 9);
                    }
                    else if (name.length() < 3)
                    {
                        //File.createTempFile() does not allow prefixes <3 chars
                        name = "wd_" + name;
                    }

                    if (_deterministicWorkingDirName)
                    {
                        tempDir = dirParent.resolveChild(name + WORK_DIR_SUFFIX);
                    }
                    else
                    {
                        tempDir = FileUtil.createTempFile(name, WORK_DIR_SUFFIX, dirParent);
                    }

                    if (_allowReuseExistingTempDirectory && tempDir.exists())
                    {
                        log.info("working directory exists, reusing: {}", dirParent.getPath());
                    }
                    else
                    {
                        tempDir.delete();
                        FileUtil.mkdirs(tempDir);
                    }
                }
                catch (IOException e)
                {
                    IOException ioException = new IOException("Failed to create local working directory in the tempDirectory "
                            + dirParent + ", specified in the tempDirectory property in the pipeline configuration");
                    ioException.initCause(e);
                    _systemLog.error(ioException.getMessage(), e);
                    throw ioException;
                }
                attempt++;
            }
            while (attempt < 5 && !tempDir.isDirectory());
            if (!tempDir.isDirectory())
            {
                throw new IOException("Failed to create local working directory " + tempDir);
            }

            FileLike lockDir = (_lockDirectory == null ? null : FileSystemLike.wrapFile(new File(_lockDirectory)));
            FileLike transferToDirOnFailure = (_transferToDirOnFailure == null ? null : FileSystemLike.wrapFile(new File(_transferToDirOnFailure)));
            return new WorkDirectoryRemote(support, this, log, lockDir, tempDir, transferToDirOnFailure, _allowReuseExistingTempDirectory, tempDirBase);
        }

        public String getLockDirectory()
        {
            if (_lockDirectory!= null)
            {
                // Do the validation on get instead of set because we may not have the NetworkDrive
                // configuration loaded in time at startup
                File lockDir = new File(_lockDirectory);
                if (!NetworkDrive.exists(lockDir) || !lockDir.isDirectory())
                    throw new IllegalArgumentException("The lock directory " + _lockDirectory + " does not exist.");
            }
            return _lockDirectory;
        }

        public void setLockDirectory(String directoryString)
        {
            _lockDirectory = directoryString;
        }

        public String getTempDirectory()
        {
            if (_tempDirectory != null)
            {
                // Do the validation on get instead of set because we may not have the NetworkDrive
                // configuration loaded in time at startup
                File tempDir = new File(_tempDirectory);
                if (!NetworkDrive.exists(tempDir) || !tempDir.isDirectory())
                    throw new IllegalArgumentException("The temporary directory " + _tempDirectory + " does not exist.");
            }
            return _tempDirectory;
        }

        /** @param directoryString path of the directory to be used as scratch space */
        public void setTempDirectory(String directoryString)
        {
            _tempDirectory = directoryString;
        }

        public void setCleanupOnStartup(boolean cleanupOnStartup)
        {
            _cleanupOnStartup = cleanupOnStartup;
        }

        /**
         * Set to an environment variable set to the path to use for the temporary directory.
         * (e.g. some cluster schedulers initialize TMPDIR to a job specific temporary directory
         * which will be removed, if the job is cancelled)
         *
         * @param tempDirectoryVar environment variable name
         */
        public void setTempDirectoryEnv(String tempDirectoryVar)
        {
            String tempDirectory = System.getenv(tempDirectoryVar);
            if (tempDirectory == null || tempDirectory.isEmpty())
                throw new IllegalArgumentException("The environment variable " + tempDirectoryVar + " does not exist:\n" + System.getenv());
            setTempDirectory(tempDirectory);
        }

        /**
         * @return true if the root temporary directory will be shared by multiple tasks
         */
        public boolean isSharedTempDirectory()
        {
            return _sharedTempDirectory;
        }

        /**
         * Set to true, if the root temporary directory will be shared by multiple tasks.
         * This is usually not necessary on a scheduled computational cluster, where each
         * task is given a separate working environment.
         *
         * @param sharedTempDirectory true if the root temporary directory will be shared by multiple tasks
         */
        public void setSharedTempDirectory(boolean sharedTempDirectory)
        {
            _sharedTempDirectory = sharedTempDirectory;
        }

        public String getTransferToDirOnFailure()
        {
            if (_transferToDirOnFailure != null)
            {
                // Do the validation on get instead of set because we may not have the NetworkDrive
                // configuration loaded in time at startup
                File tempDir = new File(_transferToDirOnFailure);
                if (!NetworkDrive.exists(tempDir) || !tempDir.isDirectory())
                    throw new IllegalArgumentException("The directory " + _transferToDirOnFailure + " does not exist.");
            }

            return _transferToDirOnFailure;
        }

        /**
         * If a directory is provided, when a remote job fails, the working directory will
         * be moved from the working location to a directory under this folder
         */
        public void setTransferToDirOnFailure(String transferToDirOnFailure)
        {
            _transferToDirOnFailure = transferToDirOnFailure;
        }

        public boolean isAllowReuseExistingTempDirectory()
        {
            return _allowReuseExistingTempDirectory;
        }

        /**
         * If true, instead of deleting an existing working directory on job startup, an existing directory will be reused.
         * This is mostly used to allow job resume, and should only be used
         */
        public void setAllowReuseExistingTempDirectory(boolean allowReuseExistingTempDirectory)
        {
            _allowReuseExistingTempDirectory = allowReuseExistingTempDirectory;
        }

        public boolean isDeterministicWorkingDirName()
        {
            return _deterministicWorkingDirName;
        }

        /**
         * If true, the working directory for each job will be named using the job' name alone (as opposed to a random temp file based on jobName)
         * This is intended to support job resume, and should be used with sharedTempDirectory=true to avoid conflicts.
         */
        public void setDeterministicWorkingDirName(boolean deterministicWorkingDirName)
        {
            _deterministicWorkingDirName = deterministicWorkingDirName;
        }
    }

    public WorkDirectoryRemote(FileAnalysisJobSupport support, WorkDirFactory factory, Logger log, FileLike lockDir, FileLike tempDir, FileLike transferToDirOnFailure, boolean reuseExistingDirectory, FileLike folderToClean) throws IOException
    {
        super(support, factory, tempDir, reuseExistingDirectory, log);

        _lockDirectory = lockDir;
        _transferToDirOnFailure = transferToDirOnFailure;
        _folderToClean = folderToClean;
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
        String line = new String(bOut.toByteArray(), StringUtilsLabKey.DEFAULT_CHARSET).trim();
        int totalLocks = FILE_LOCKS_DEFAULT;
        int currentIndex = 0;
        if (!line.isEmpty())
        {
            String[] parts = line.split(" ");
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
                totalLocks = FILE_LOCKS_DEFAULT;
        }

        if (currentIndex >= totalLocks)
        {
            currentIndex = 0;
        }
        return new MasterLockInfo(totalLocks, currentIndex);
    }

    /**
     * File system locks are fine to communicate locking between two different processes, but they don't work for
     * multiple threads inside the same VM. We need to do Java-level locking as well.
     */
    private static synchronized Lock getInMemoryLockObject(File f)
    {
        Lock result = _locks.computeIfAbsent(f, _ -> new ReentrantLock());
        return result;
    }

    @Override
    public void remove(boolean success) throws IOException
    {
        super.remove(success);

        // Issue 25166: this was a pre-existing potential bug.  If _sharedTempDirectory is true, we create a second level
        // of temp directory above the primary working dir.  this is added to make sure we clean this up.
        _jobLog.debug("inspecting remote work dir: {}", _folderToClean == null ? _dir.getPath() : _folderToClean.getPath());
        if (success && _folderToClean != null && !_dir.equals(_folderToClean))
        {
            _jobLog.debug("removing entire work dir through: {}", _folderToClean.getPath());
            _jobLog.debug("starting with: {}", _dir.getPath());
            FileLike toCheck = _dir;

            //debugging only:
            if (!URIUtil.isDescendant(_folderToClean.toURI(), toCheck.toURI()))
            {
                _jobLog.warn("not a descendant!");
            }

            while (toCheck != null && URIUtil.isDescendant(_folderToClean.toURI(), toCheck.toURI()))
            {
                if (!toCheck.exists())
                {
                    _jobLog.debug("directory does not exist: {}", toCheck.getPath());
                    toCheck = toCheck.getParent();
                    continue;
                }

                List<FileLike> children = toCheck.getChildren();
                if (children.isEmpty())
                {
                    _jobLog.debug("removing directory: {}", toCheck.getPath());
                    FileUtil.deleteDir(toCheck);
                    toCheck = toCheck.getParent();
                }
                else
                {
                    _jobLog.debug("work directory has children, will not delete: {}", toCheck.getPath());
                    _jobLog.debug("files:");
                    for (FileLike fn : children)
                    {
                        _jobLog.debug(fn);
                    }
                    break;
                }
            }
        }
    }

    @Override
    protected CopyingResource createCopyingLock() throws IOException
    {
        if (_lockDirectory == null)
        {
            return new SimpleCopyingResource();
        }

        _jobLog.debug("Starting to acquire lock for copying files");

        MasterLockInfo lockInfo;

        // Synchronize to prevent multiple threads from trying to lock the master file from within the same VM
        synchronized (WorkDirectoryRemote.class)
        {
            RandomAccessFile randomAccessFile = null;
            FileLock masterLock = null;

            try
            {
                File masterLockFile = _lockDirectory.resolveChild("counter").toNioPathForWrite().toFile();
                randomAccessFile = new RandomAccessFile(masterLockFile, "rw");
                FileChannel masterChannel = randomAccessFile.getChannel();
                masterLock = masterChannel.lock();

                lockInfo = parseMasterLock(randomAccessFile, masterLockFile);
                int nextIndex = (lockInfo.getCurrentLock() + 1) % lockInfo.getTotalLocks();
                rewriteMasterLock(randomAccessFile, new MasterLockInfo(lockInfo.getTotalLocks(), nextIndex));
            }
            finally
            {
                if (randomAccessFile != null) { try { randomAccessFile.close(); } catch (IOException e) {} }
                if (masterLock != null) { try { masterLock.release(); } catch (IOException e) {} }
            }
        }

        _jobLog.debug("Acquiring lock #{}", lockInfo.getCurrentLock());
        File f = _lockDirectory.resolveChild("lock" + lockInfo.getCurrentLock()).toNioPathForWrite().toFile();
        FileChannel lockChannel = new FileOutputStream(f, true).getChannel();
        FileLockCopyingResource result = new FileLockCopyingResource(lockChannel, lockInfo.getCurrentLock(), f);
        _jobLog.debug("Lock #{} acquired", lockInfo.getCurrentLock());

        return result;
    }

    private void rewriteMasterLock(RandomAccessFile masterFile, MasterLockInfo lockInfo)
        throws IOException
    {
        masterFile.seek(0);

        String output = Integer.toString(lockInfo.getCurrentLock());
        if (lockInfo.getTotalLocks() != FILE_LOCKS_DEFAULT)
            output += " " + lockInfo.getTotalLocks();
        byte[] outputBytes = output.getBytes(StringUtilsLabKey.DEFAULT_CHARSET);
        masterFile.write(outputBytes);
        masterFile.setLength(outputBytes.length);
    }

    private static class MasterLockInfo
    {
        private final int _totalLocks;
        private final int _currentLock;

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
        private final Throwable _creationStack;
        private Lock _memoryLock;

        private static final Cleaner CLEANER = Cleaner.create();

        private static class LockState implements Runnable
        {
            private FileChannel _channel;
            private FileLock _lock;
            private Lock _memoryLock;
            private final Throwable _creationStack;

            private LockState(FileChannel channel, FileLock lock, Lock memoryLock, Throwable creationStack)
            {
                _channel = channel;
                _lock = lock;
                _memoryLock = memoryLock;
                _creationStack = creationStack;
            }

            @Override
            public void run()
            {
                if (_lock != null || _memoryLock != null)
                {
                    _systemLog.error("FileLockCopyingResource was not released before it was garbage collected. Creation stack is: ", _creationStack);
                    close();
                }
            }

            private void close()
            {
                try
                {
                    if (_lock != null)
                    {
                        _lock.release();
                        _lock = null;
                    }
                    if (_channel != null)
                    {
                        _channel.close();
                        _channel = null;
                    }
                }
                catch (IOException e)
                {
                    _systemLog.error("Failed to release lock", e);
                }
                finally
                {
                    if (_memoryLock != null)
                    {
                        _memoryLock.unlock();
                        _memoryLock = null;
                    }
                }
            }
        }

        private final LockState _state;
        private final Cleaner.Cleanable _cleanable;

        public FileLockCopyingResource(FileChannel channel, int lockNumber, File f) throws IOException
        {
            _channel = channel;
            _lockNumber = lockNumber;
            _creationStack = new Throwable();

            // Lock the memory part first to eliminate multi-threaded access to the same file
            _memoryLock = getInMemoryLockObject(f);
            _memoryLock.lock();

            // Lock the file part second
            _lock = _channel.lock();

            _state = new LockState(_channel, _lock, _memoryLock, _creationStack);
            _cleanable = CLEANER.register(this, _state);
        }


        @Override
        public void close()
        {
            if (_lock != null)
            {
                _state.close();
                _cleanable.clean();
                _jobLog.debug("Lock #{} released", _lockNumber);
                _lock = null;
                _channel = null;
                super.close();
                _memoryLock = null;
            }
        }
    }
}