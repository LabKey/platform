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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.WorkDirFactory;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.cmd.TaskPath;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.URIUtil;
import org.labkey.vfs.FileLike;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: Jun 17, 2008
 */
public abstract class AbstractWorkDirectory implements WorkDirectory
{
    private static final Logger LOG = LogManager.getLogger(AbstractWorkDirectory.class);

    protected static final String WORK_DIR_SUFFIX = ".work";
    protected static final FileType FT_WORK_DIR = new FileType(WORK_DIR_SUFFIX);
    protected static final FileType FT_COPY = new FileType(".copy");
    protected static final FileType FT_MOVE = new FileType(".move");

    protected FileAnalysisJobSupport _support;
    protected final WorkDirFactory _factory;
    protected final FileLike _dir;
    protected final Logger _jobLog;
    protected final HashMap<FileLike, FileLike> _copiedInputs = new HashMap<>();

    protected CopyingResource _copyingResource;
    protected FileLike _transferToDirOnFailure = null;

    public static abstract class AbstractFactory implements WorkDirFactory
    {
        private String _outputPermissions;

        @Override
        public void setPermissions(File outputFile) throws IOException
        {
            if (_outputPermissions != null)
            {
                Runtime.getRuntime().exec(new String[] {
                        "chmod",
                        _outputPermissions,
                        outputFile.toString()
                });
            }
        }

        /**
         * @return chmod permissions mask for Unix systems
         */
        public String getOutputPermissions()
        {
            return _outputPermissions;
        }

        /**
         * Specify a permissions mask to pass to chmod on Unix systems.  Some cluster
         * scheduling software give processing nodes very restrictive umask settings.
         *
         * @param outputPermissions chmod permissions mask (e.g. "0664")
         */
        public void setOutputPermissions(String outputPermissions)
        {
            outputPermissions = StringUtils.trimToNull(outputPermissions);
            if (System.getProperty("os.name").toLowerCase().startsWith("windows") && outputPermissions != null )
            {
                LOG.warn("outputPermissions for WorkDirectories are not supported on Windows");
            }
            else
            {
                _outputPermissions = outputPermissions;
            }
        }
    }

    public AbstractWorkDirectory(FileAnalysisJobSupport support, WorkDirFactory factory, FileLike dir, boolean reuseExistingDirectory, Logger log) throws IOException
    {
        _support = support;
        _factory = factory;
        _dir = dir;
        _jobLog = log;

        if (_dir.exists())
        {
            if (!reuseExistingDirectory)
            {
                if (!FileUtil.deleteDirectoryContents(_dir))
                    throw new IOException("Failed to clean up existing work directory " + _dir);
            }
            else
            {
                _jobLog.debug("existing work directory found, re-using");
            }
        }
        else
        {
            if (!FileUtil.mkdirs(_dir))
                throw new IOException("Failed to create work directory " + _dir);
        }
    }

    @Override
    public void acceptFilesAsOutputs(Map<String, TaskPath> expectedOutputs, RecordedAction action) throws IOException
    {
        List<FileLike> remainingFiles = getDir().getChildren();

        if (remainingFiles != null)
        {
            try (WorkDirectory.CopyingResource lock = ensureCopyingLock())
            {
                Set<FileLike> copiedFiles = new HashSet<>();
                // First handle anything that's been explicitly configured
                for (Map.Entry<String, TaskPath> entry : expectedOutputs.entrySet())
                {
                    TaskPath taskPath = entry.getValue();
                    String role = entry.getKey();
                    if (WorkDirectory.Function.output.toString().equals(role))
                    {
                        role = taskPath.getDefaultRole();
                    }
                    copiedFiles.addAll(outputFile(taskPath, role, action));
                }

                _jobLog.debug("Already copied files: {}", copiedFiles);

                // Slurp up any other files too
                List<FileLike> additionalFiles = getDir().getChildren();
                if (!additionalFiles.isEmpty())
                {
                    _jobLog.debug("Additional files: {}", Arrays.asList(additionalFiles));
                }

                for (FileLike workFile : remainingFiles)
                {
                    if (copiedFiles.contains(workFile))
                    {
                        _jobLog.debug("Skipping copy of file that was already copied as an expected output: {}", workFile);
                        int attempts = 0;
                        boolean deleted = false;
                        // Issue 40138 - large files not deleting immediately, so retry and log
                        while (workFile.exists() && attempts < 6)
                        {
                            if (attempts > 0)
                            {
                                _jobLog.debug("Attempted to discard {} but it still exists. Try #{}, delete attempt reported {}", workFile, attempts, deleted);
                                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                            }
                            attempts++;
                            deleted = workFile.delete();
                        }
                        if (workFile.exists())
                        {
                            throw new IOException("Failed to delete: " + workFile);
                        }
                    }
                    else
                    {
                        FileLike f = outputFile(workFile);
                        String role = "";
                        String baseName = _support.getBaseName();
                        if (f.getName().startsWith(baseName))
                        {
                            role = f.getName().substring(baseName.length());
                        }
                        else if (f.getName().contains("."))
                        {
                            role = f.getName().substring(f.getName().indexOf(".") + 1);
                        }
                        while (!role.isEmpty() && !Character.isJavaIdentifierPart(role.charAt(0)))
                        {
                            role = role.substring(1);
                        }
                        if (role.isEmpty())
                        {
                            role = "Output";
                        }

                        if (f.isDirectory())
                        {
                            // It's a directory, so add all of the child files instead of the directory itself
                            Collection<File> contents = FileUtils.listFiles(f.toNioPathForRead().toFile(), FileFilterUtils.fileFileFilter(), FileFilterUtils.trueFileFilter());
                            for (File content : contents)
                            {
                                action.addOutput(content, role, false, true);
                            }
                        }
                        else
                        {
                            action.addOutput(f, role, false, true);
                        }
                        copiedFiles.add(f);
                    }
                }
            }
        }
    }

    @Override
    public List<FileLike> getWorkFiles(Function f, TaskPath tp)
    {
        if (tp == null)
            return Collections.emptyList();

        List<String> baseNames;
        if (tp.isSplitFiles())
            baseNames = _support.getSplitBaseNames();
        else
        {
            // CONSIDER: More flexible input/output file naming -- perhaps a string expression with protocol, task, job-id available.
            // CONSIDER: Or explicitly wire outputs from an upstream task as an input to this task which would make the baseName concept less important.
            String baseName = _support.getBaseName();
            if (tp.isUseProtocolNameAsBaseName())
                baseName = _support.getProtocolName();
            else if (tp.isUseFileTypeBaseName())
                baseName = _support.getBaseNameForFileType(tp.getType());

            baseNames = Collections.singletonList(baseName);
        }

        List<FileLike> files = new ArrayList<>();
        for (String baseName : baseNames)
            files.add(newWorkFile(f, tp, baseName));
        return files;
    }

    private Set<FileLike> outputFile(TaskPath tp, String role, RecordedAction action) throws IOException
    {
        Set<FileLike> result = new HashSet<>();
        List<FileLike> filesWork = getWorkFiles(WorkDirectory.Function.output, tp);
        for (FileLike fileWork : filesWork)
        {
            FileLike fileOutput = switch (tp.getOutputLocation())
            {
                case ANALYSIS_DIR -> _support.getAnalysisDirectory().resolveChild(fileWork.getName());
                case DATA_DIR -> _support.getDataDirectory().resolveChild(fileWork.getName());
                case PATH -> _support.findOutputFile(tp.getOutputDir(), fileWork.getName());
                default -> _support.findOutputFile(fileWork.getName());
            };

            // Check if the output is specifically flagged to go into a special location

            if (fileOutput != null)
            {
                // If the output file is optional, or in a shared directory outside
                // the analysis directory for this job, and it already exists,
                // then simply discard the work file, leaving the original.

                // CONSIDER: Unfortunately, with a local work directory, this may hide files
                // that are auto-generated by the command in place.  Such files will not be recorded as output.
                if (tp.isOptional() ||
                        !_support.getAnalysisDirectory().equals(fileOutput.getParent()))
                {
                    if (NetworkDrive.exists(fileOutput))
                    {
                        discardFile(fileWork);
                        break;
                    }
                }
            }

            if (!tp.isOptional() || fileWork.exists())
            {
                // Add it as an output if it's non-optional, or if it's optional and the file exists
                FileLike f = outputFile(fileWork, fileOutput);
                action.addOutput(f, role, false, true);
                result.add(fileWork);
            }
        }
        return result;
    }

    @Override
    public FileLike getDir()
    {
        return _dir;
    }

    private void copyFile(FileLike source, FileLike target) throws IOException
    {
        NetworkDrive.ensureDrive(source);
        NetworkDrive.ensureDrive(target);

        try (WorkDirectory.CopyingResource lock = ensureCopyingLock())
        {
            _jobLog.info("Copying {} to {}", source, target);
            if (source.isDirectory())
            {
                FileUtil.copyDirectory(source.toNioPathForRead(), target.toNioPathForWrite());
            }
            else
            {
                FileUtil.copyFile(source, target);
            }
        }
    }

    protected FileLike copyInputFile(FileLike fileInput) throws IOException
    {
        FileLike fileWork = newFile(fileInput.getName());
        return copyInputFile(fileInput, fileWork);
    }

    protected FileLike copyInputFile(FileLike fileInput, FileLike fileWork) throws IOException
    {
        //ensure fileWork is a descendent of workDir
        if (getRelativePath(fileWork) == null)
        {
            throw new IOException("The target file must be a descendent of the work directory.  File was: " + fileWork.getPath());
        }

        copyFile(fileInput, fileWork);
        _copiedInputs.put(fileInput, fileWork);
        return fileWork;
    }

    private FileLike getDir(Function f, String name)
    {
        if (Function.output.equals(f))
        {
            // All new output goes to the root work directory for now.
            // Output files will be moved into a final location in .outputFile().
            return _dir;
        }
        else
        {
            FileLike file = _support.findInputFile(name);
            return file.getParent();
        }
    }

    @Override
    public FileLike newFile(FileType type)
    {
        // TODO: Issue 20143: pipeline: Custom output directory for task outputs
        return newFile(Function.output, type.getName(_dir, _support.getBaseName()));
    }

    @Override
    public FileLike newFile(String name)
    {
        return newFile(Function.output, name);
    }

    @Override
    public FileLike newFile(Function f, String name)
    {
        FileLike file = getDir(f, name).resolveChild(name);

        if (Function.input.equals(f))
        {
            // See if the file has already been copied into the working directory.
            // In which case, the copied version should be used.
            FileLike fileWork = _copiedInputs.get(file);
            if (fileWork != null)
                return fileWork;
        }

        return file;
    }

    @Override
    public String getRelativePath(FileLike fileWork) throws IOException
    {
        return FileUtil.relativize(_dir, fileWork, true);
    }

    @Override
    public FileLike outputFile(FileLike fileWork) throws IOException
    {
        return outputFile(fileWork, fileWork.getName());
    }

    @Override
    public FileLike outputFile(FileLike fileWork, String nameDest) throws IOException
    {
        return outputFile(fileWork, _support.findOutputFile(nameDest));
    }

    @Override
    public FileLike outputFile(FileLike fileWork, FileLike fileDest) throws IOException
    {
        NetworkDrive.ensureDrive(fileDest);

        // TPP treats .xml.gz as a native format, follow suit
        if (fileWork.getName().endsWith(".gz") && !fileDest.getName().endsWith(".gz"))
        {
            fileDest = fileDest.getParent().resolveChild(fileDest.getName() + ".gz");
        }

        if (!fileWork.exists())
        {
            // If the work file does not exist, and the destination does
            // assume the task wrote to the desired location.
            if (fileDest.exists())
                return fileDest;
            throw new FileNotFoundException("Failed to find expected output " + fileWork);
        }
        ensureDescendant(fileWork);
        FileLike fileReplace = null;
        FileLike fileCopy = null;

        try (WorkDirectory.CopyingResource lock = ensureCopyingLock())
        {
            if (fileDest.exists())
            {
                // If the destination exists, rename it out of the way while we try to
                // replace it. Rename within the same directory is always an atomic action.
                fileReplace = FT_MOVE.newFile(fileDest.getParent(), fileDest.getName());
                _jobLog.info("Moving {} to {}", fileDest, fileReplace);
                if (!fileDest.renameTo(fileReplace))
                {
                    throw new IOException("Failed to move file " + fileDest + " to " + fileReplace);
                }
            }
            _jobLog.info("Moving {} to {}", fileWork, fileDest);
            boolean directory = fileWork.isDirectory();
            if (fileWork.renameTo(fileDest))
                fileWork = null;
            else
            {
                // File.renameTo() is the most efficient way to move a file, but it annoyingly doesn't necessarily
                // work across different file systems.  Use a copy to a .copy file, and then an
                // atomic rename within the same directory to the destination.
                fileCopy = FT_COPY.newFile(fileDest.getParent(), fileDest.getName());
                if (directory)
                {
                    FileUtil.copyDirectory(fileWork, fileCopy);
                }
                else
                {
                    FileUtil.copyFile(fileWork, fileCopy);
                }
                if (!fileCopy.renameTo(fileDest))
                {
                    // We failed to copy the output file to its final location

                    if (fileDest.exists())
                    {
                        // If there's a partial file, try to clean it up 
                        fileDest.delete();

                        // TODO - change from holding a reference to FileAnalysisJobSupport to a PipelineJob directly.
                        // It's the only implementation and the extra layer of indirection doesn't help anything.
                        if (fileDest.exists() && _support instanceof PipelineJob job)
                        {
                            // If it's still there, make sure we don't auto-retry because this task will think it's
                            // already been run successfully if its expected outputs are on disk
                            job.setErrors(Math.max(1, job.getActiveTaskFactory().getAutoRetry() + 1));
                        }
                    }
                    throw new IOException("Failed to move file " + fileWork + " to " + fileDest);
                }
                fileCopy = null;
            }
            if (fileReplace != null)
            {
                FileLike fileRemove = fileReplace;
                fileReplace = null;    // Output file is successfully in place.

                _jobLog.info("Removing {}", fileRemove);
                fileRemove.delete();
            }
            if (fileWork != null)
            {
                if (directory)
                {
                    FileUtil.deleteDir(fileWork);
                }
                else if (!fileWork.delete())
                {
                    throw new IOException("Failed to remove file " + fileWork);
                }
            }
        }
        finally
        {
            if (fileCopy != null)
            {
                // Clean-up corrupted .copy file.
                fileCopy.delete();
            }
            if (fileReplace != null)
            {
                // Failed to get output file in place.  Attempt to rename original back into position.
                fileReplace.renameTo(fileDest);
            }
        }

        _factory.setPermissions(fileDest.toNioPathForWrite().toFile());

        return fileDest;
    }

    @Override
    public void discardFile(FileLike fileWork) throws IOException
    {
        _jobLog.debug("discarding file: {}", fileWork.getPath());
        ensureDescendant(fileWork);
        int attempts = 0;
        // Issue 40138 - large files not deleting immediately, so retry and log
        while (fileWork.exists() && attempts < 6)
        {
            attempts++;
            boolean deleted = fileWork.delete();

            if (fileWork.isDirectory())
            {
                FileUtil.deleteDir(fileWork);
            }

            if (fileWork.exists())
            {
                _jobLog.debug("Attempted to discard {} but it still exists. Try #{}, delete attempt reported {}", fileWork, attempts, deleted);
                // Wait five seconds
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }
        }

        if (fileWork.exists())
        {
            throw new IOException("Failed to remove file " + fileWork);
        }
    }

    @Override
    public void discardCopiedInputs() throws IOException
    {
        if (NetworkDrive.exists(_dir))
        {
            for (FileLike input : _copiedInputs.values())
                discardFile(input);
            _copiedInputs.clear();
        }
    }

    @Override
    public void remove(boolean success) throws IOException
    {
        discardCopiedInputs();

        if (NetworkDrive.exists(_dir))
        {
            if (!success && _transferToDirOnFailure != null)
            {
                FileLike dest = FileUtil.findUniqueFileName(_dir.getName(), _transferToDirOnFailure);
                _jobLog.debug("after failure, moving working directory to: {}", dest.getPath());

                try
                {
                    FileUtils.moveDirectory(_dir.toNioPathForRead().toFile(), dest.toNioPathForRead().toFile());
                }
                catch (IOException e)
                {
                    _jobLog.error("failed moving working directory from : {}", _dir.getPath());
                    _jobLog.error("to: {}", dest.getPath());

                    throw e;
                }
            }
            else if (!_dir.delete() && success)
            {
                StringBuilder message = new StringBuilder();
                message.append("Failed to remove work directory ").append(_dir);
                List<FileLike> files = _dir.getChildren();
                if (!files.isEmpty())
                {
                    message.append(" unexpected files found:");
                    for (FileLike f : files)
                        message.append("\n").append(f.getName());
                }

                throw new IOException(message.toString());
            }
        }
    }

    private void ensureDescendant(FileLike fileWork) throws IOException
    {
        if (!URIUtil.isDescendant(_dir.toURI(), fileWork.toURI()))
            throw new IOException("The file " + fileWork + " is not a descendant of " + _dir);
    }

    /**
     * Ensures that we have a lock, if needed. The lock must be released by the caller.
     */
    @Override
    public CopyingResource ensureCopyingLock() throws IOException
    {
        if (_copyingResource != null)
        {
            // Hand out a dummy. There's already a lock established, so rely on the place that created it to release it
            return new SimpleCopyingResource();
        }
        _copyingResource = createCopyingLock();
        return _copyingResource;
    }

    @Override
    public FileLike newWorkFile(Function f, TaskPath tp, String baseName)
    {
        if (tp == null)
            return null;

        FileType type = tp.getType();
        if (type != null)
            return newFile(f, type.findInputFile(_support, baseName).getName());

        return newFile(f, tp.getName());
    }

    /**
     * Creates an actual lock resource. Used internally by the WorkDirectory - callers should use ensureCopyingLock() instead
     */
    protected abstract CopyingResource createCopyingLock() throws IOException;

    public class SimpleCopyingResource implements CopyingResource
    {
        @Override
        public void close()
        {
            // If this is the real resource for the working directory, it can be released now
            if (_copyingResource == this)
            {
                _copyingResource = null;
            }
        }
    }

    @Override
    public FileLike getWorkingCopyForInput(FileLike f)
    {
        return _copiedInputs.get(f);
    }
}
