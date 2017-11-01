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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.WorkDirFactory;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.cmd.TaskPath;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.URIUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: Jun 17, 2008
 */
public abstract class AbstractWorkDirectory implements WorkDirectory
{
    private static final Logger LOG = Logger.getLogger(AbstractWorkDirectory.class);

    protected static final String WORK_DIR_SUFFIX = ".work";
    protected static final FileType FT_WORK_DIR = new FileType(WORK_DIR_SUFFIX);
    protected static final FileType FT_COPY = new FileType(".copy");
    protected static final FileType FT_MOVE = new FileType(".move");

    protected FileAnalysisJobSupport _support;
    protected final WorkDirFactory _factory;
    protected final File _dir;
    protected final Logger _jobLog;
    protected final HashMap<File, File> _copiedInputs = new HashMap<>();

    protected CopyingResource _copyingResource;
    protected File _transferToDirOnFailure = null;

    public static abstract class AbstractFactory implements WorkDirFactory
    {
        private String _outputPermissions;

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

    public AbstractWorkDirectory(FileAnalysisJobSupport support, WorkDirFactory factory, File dir, boolean reuseExistingDirectory, Logger log) throws IOException
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
            if (!_dir.mkdirs())
                throw new IOException("Failed to create work directory " + _dir);
        }
    }

    @Override
    public void acceptFilesAsOutputs(Map<String, TaskPath> expectedOutputs, RecordedAction action) throws IOException
    {
        File[] remainingFiles = getDir().listFiles();

        if (remainingFiles != null)
        {
            try (WorkDirectory.CopyingResource lock = ensureCopyingLock())
            {
                // First handle anything that's been explicitly configured
                for (Map.Entry<String, TaskPath> entry : expectedOutputs.entrySet())
                {
                    TaskPath taskPath = entry.getValue();
                    String role = entry.getKey();
                    if (WorkDirectory.Function.output.toString().equals(role))
                    {
                        role = taskPath.getDefaultRole();
                    }
                    outputFile(taskPath, role, action);
                }

                // Slurp up any other files too
                for (File workFile : getDir().listFiles())
                {
                    File f = outputFile(workFile);
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
                    while (role.length() > 0 && !Character.isJavaIdentifierPart(role.charAt(0)))
                    {
                        role = role.substring(1);
                    }
                    if ("".equals(role))
                    {
                        role = "Output";
                    }

                    if (f.isDirectory())
                    {
                        // It's a directory, so add all of the child files instead of the directory itself 
                        Collection<File> contents = FileUtils.listFiles(f, FileFilterUtils.fileFileFilter(), FileFilterUtils.trueFileFilter());
                        for (File content : contents)
                        {
                            action.addOutput(content, role, false, true);
                        }
                    }
                    else
                    {
                        action.addOutput(f, role, false, true);
                    }
                }
            }
        }
    }

    public List<File> getWorkFiles(WorkDirectory.Function f, TaskPath tp)
    {
        if (tp == null)
            return Collections.emptyList();

        List<String> baseNames;
        if (tp.isSplitFiles())
            baseNames = _support.getSplitBaseNames();
        else
        {
            // CONSIDER: More flexable input/output file naming -- perhaps a string expression with protocol, task, job-id available.
            // CONSIDER: Or explicitly wire outputs from an upstream task as an input to this task which would make the baseName concept less important.
            String baseName = _support.getBaseName();
            if (tp.isUseProtocolNameAsBaseName())
                baseName = _support.getProtocolName();
            else if (tp.isUseFileTypeBaseName())
                baseName = _support.getBaseNameForFileType(tp.getType());

            baseNames = Collections.singletonList(baseName);
        }

        ArrayList<File> files = new ArrayList<>();
        for (String baseName : baseNames)
            files.add(newWorkFile(f, tp, baseName));
        return files;
    }

    private void outputFile(TaskPath tp, String role, RecordedAction action) throws IOException
    {
        List<File> filesWork = getWorkFiles(WorkDirectory.Function.output, tp);
        for (File fileWork : filesWork)
        {
            File fileOutput;

            // Check if the output is specifically flagged to go into a special location
            switch (tp.getOutputLocation())
            {
                case ANALYSIS_DIR:
                    fileOutput = new File(_support.getAnalysisDirectory(), fileWork.getName());
                    break;

                case DATA_DIR:
                    fileOutput = new File(_support.getDataDirectory(), fileWork.getName());
                    break;

                case PATH:
                    fileOutput = _support.findOutputFile(tp.getOutputDir(), fileWork.getName());
                    break;

                case DEFAULT:
                default:
                    fileOutput = _support.findOutputFile(fileWork.getName());
                    break;
            }

            if (fileOutput != null)
            {
                // If the output file is optional, or in a shared directory outside
                // the analysis directory for this job, and it already exists,
                // then simply discard the work file, leaving the original.

                // CONSIDER: Unfortunately, with a local work directory, this may hide files
                // that are auto-generated by the command in place.  Such files like,
                // .mzXML.inspect for msInspect will not be recorded as output.
                if (tp.isOptional() ||
                        !_support.getAnalysisDirectory().equals(fileOutput.getParentFile()))
                {
                    if (NetworkDrive.exists(fileOutput))
                    {
                        discardFile(fileWork);
                        return;
                    }
                }
            }

            if (!tp.isOptional() || fileWork.exists())
            {
                // Add it as an output if it's non-optional, or if it's optional and the file exists
                File f = outputFile(fileWork, fileOutput);
                action.addOutput(f, role, false, true);
            }
        }
    }

    public File getDir()
    {
        return _dir;
    }

    private void copyFile(File source, File target) throws IOException
    {
        NetworkDrive.ensureDrive(source.getAbsolutePath());
        NetworkDrive.ensureDrive(target.getAbsolutePath());

        try (WorkDirectory.CopyingResource lock = ensureCopyingLock())
        {
            _jobLog.info("Copying " + source + " to " + target);
            if (source.isDirectory())
            {
                FileUtils.copyDirectory(source, target);
            }
            else
            {
                FileUtils.copyFile(source, target);
            }
        }
    }

    protected File copyInputFile(File fileInput) throws IOException
    {
        File fileWork = newFile(fileInput.getName());
        return copyInputFile(fileInput, fileWork);
    }

    protected File copyInputFile(File fileInput, File fileWork) throws IOException
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

    private File getDir(Function f, String name)
    {
        if (Function.output.equals(f))
        {
            // All new output goes to the root work directory for now.
            // Output files will be moved into a final location in .outputFile().
            return _dir;
        }
        else
        {
            File file = _support.findInputFile(name);
            return file.getParentFile();
        }
    }

    public File newFile(FileType type)
    {
        return newFile(Function.output, type);
    }

    public File newFile(Function f, FileType type)
    {
        if (f == Function.input)
        {
            // that null arg to type.getName causes it to try all known filename extensions instead of just default
            return newFile(f, type.getName((File)null, _support.getBaseName()));
        }
        else if (f == Function.output)
        {
            // TODO: Issue 20143: pipeline: Custom output directory for task outputs
            return newFile(f, type.getName(_dir, _support.getBaseName()));
        }
        throw new IllegalArgumentException("input or output expected");
    }

    public File newFile(String name)
    {
        return newFile(Function.output, name);
    }

    public File newFile(Function f, String name)
    {
        File file = new File(getDir(f, name), name);

        if (Function.input.equals(f))
        {
            // See if the file has already been copied into the working directory.
            // In which case, the copied version should be used.
            File fileWork = _copiedInputs.get(file);
            if (fileWork != null)
                return fileWork;
        }

        return file;
    }

    public String getRelativePath(File fileWork) throws IOException
    {
        return FileUtil.relativize(_dir, fileWork, true);
    }

    public File outputFile(File fileWork) throws IOException
    {
        return outputFile(fileWork, fileWork.getName());
    }

    public File outputFile(File fileWork, String nameDest) throws IOException
    {
        return outputFile(fileWork, _support.findOutputFile(nameDest));
    }

    public File outputFile(File fileWork, File fileDest) throws IOException
    {
        NetworkDrive.ensureDrive(fileDest.getAbsolutePath());

        // TPP treats .xml.gz as a native format, follow suit
        if (fileWork.getName().endsWith(".gz") && !fileDest.getName().endsWith(".gz"))
        {
            fileDest = new File(fileDest.getPath()+".gz");
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
        File fileReplace = null;
        File fileCopy = null;

        try (WorkDirectory.CopyingResource lock = ensureCopyingLock())
        {
            if (fileDest.exists())
            {
                // If the destination exists, rename it out of the way while we try to
                // replace it. Rename within the same directory is always an atomic action.
                fileReplace = FT_MOVE.newFile(fileDest.getParentFile(), fileDest.getName());
                _jobLog.info("Moving " + fileDest + " to " + fileReplace);
                if (!fileDest.renameTo(fileReplace))
                {
                    throw new IOException("Failed to move file " + fileDest + " to " + fileReplace);
                }
            }
            _jobLog.info("Moving " + fileWork + " to " + fileDest);
            boolean directory = fileWork.isDirectory();
            if (fileWork.renameTo(fileDest))
                fileWork = null;
            else
            {
                // File.renameTo() is the most efficient way to move a file, but it annoyingly doesn't necessarily
                // work across different file systems.  Use a copy to a .copy file, and then an
                // atomic rename within the same directory to the destination.
                fileCopy = FT_COPY.newFile(fileDest.getParentFile(), fileDest.getName());
                if (directory)
                {
                    FileUtils.copyDirectory(fileWork, fileCopy);
                }
                else
                {
                    FileUtils.copyFile(fileWork, fileCopy);
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
                        if (fileDest.exists() && _support instanceof PipelineJob)
                        {
                            // If it's still there, make sure we don't auto-retry because this task will think it's
                            // already been run successfully if its expected outputs are on disk
                            PipelineJob job = (PipelineJob) _support;
                            job.setErrors(Math.max(1, job.getActiveTaskFactory().getAutoRetry() + 1));
                        }
                    }
                    throw new IOException("Failed to move file " + fileWork + " to " + fileDest);
                }
                fileCopy = null;
            }
            if (fileReplace != null)
            {
                File fileRemove = fileReplace;
                fileReplace = null;    // Output file is successfully in place.

                _jobLog.info("Removing " + fileRemove);
                fileRemove.delete();
            }
            if (fileWork != null)
            {
                if (directory)
                {
                    FileUtils.deleteDirectory(fileWork);
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

        _factory.setPermissions(fileDest);

        return fileDest;
    }

    public void discardFile(File fileWork) throws IOException
    {
        _jobLog.debug("discarding file: " + fileWork.getPath());
        ensureDescendant(fileWork);
        if (fileWork.exists() && !fileWork.delete())
        {
            if (fileWork.isDirectory())
            {
                FileUtils.deleteDirectory(fileWork);
            }
            if (fileWork.exists())
            {
                throw new IOException("Failed to remove file " + fileWork);
            }
        }
    }

    public void discardCopiedInputs() throws IOException
    {
        if (NetworkDrive.exists(_dir))
        {
            for (File input : _copiedInputs.values())
                discardFile(input);
            _copiedInputs.clear();
        }
    }

    public void remove(boolean success) throws IOException
    {
        discardCopiedInputs();

        if (NetworkDrive.exists(_dir))
        {
            if (!success && _transferToDirOnFailure != null)
            {
                AssayFileWriter writer = new AssayFileWriter();
                File dest = writer.findUniqueFileName(_dir.getName(), _transferToDirOnFailure);
                _jobLog.debug("after failure, moving working directory to: " + dest.getPath());

                try
                {
                    FileUtils.moveDirectory(_dir, dest);
                }
                catch (IOException e)
                {
                    _jobLog.error("failed moving working directory from : " + _dir.getPath());
                    _jobLog.error("to: " + dest.getPath());

                    throw e;
                }
            }
            else if (!_dir.delete() && success)
            {
                StringBuilder message = new StringBuilder();
                message.append("Failed to remove work directory ").append(_dir);
                File[] files = _dir.listFiles();
                if (files != null && files.length > 0)
                {
                    message.append(" unexpected files found:");
                    for (File f : files)
                        message.append("\n").append(f.getName());
                }

                throw new IOException(message.toString());
            }
        }
    }

    private void ensureDescendant(File fileWork) throws IOException
    {
        if (!URIUtil.isDescendant(_dir.toURI(), fileWork.toURI()))
            throw new IOException("The file " + fileWork + " is not a descendant of " + _dir);
    }

    /**
     * Ensures that we have a lock, if needed. The lock must be released by the caller.
     */
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

    public File newWorkFile(WorkDirectory.Function f, TaskPath tp, String baseName)
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
        public void close()
        {
            // If this is the real resource for the working directory, it can be released now
            if (_copyingResource == this)
            {
                _copyingResource = null;
            }
        }
    }

    public File getWorkingCopyForInput(File f)
    {
        return _copiedInputs.get(f);
    }
}
