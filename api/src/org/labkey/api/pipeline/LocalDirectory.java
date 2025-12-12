/*
 * Copyright (c) 2018 LabKey Corporation
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.util.FileUtil;
import org.labkey.vfs.FileLike;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;

/**
 * Provides a temporary directory that can be used for
 *      (a) pipeline job logfile
 *      (b) input file (or any files)
 *      (c) logfile can be copied locally when job finishes (with error or complete)
 *      (d) directory and its contents automatically cleaned up when job finishes (with error or complete)
 * Or simply refers to a local directory where the input lives
 */
//@JsonIgnoreProperties(value={"_moduleName"})
public class LocalDirectory implements Serializable
{
    @NotNull private final FileLike _localDirectoryFile;
    private final boolean _isTemporary;
    private final PipeRoot _pipeRoot;
    private final FileLike _remoteDir;
    private FileLike _logFile;
    private final String _baseLogFileName;

    public static LocalDirectory create(@NotNull PipeRoot root)
    {
        return create(root, "dummyLogFile", root.isCloudRoot() ? root.getRootFileLike().resolveChild("dummy") : root.getRootFileLike());
    }

    public static LocalDirectory create(@NotNull PipeRoot root, @NotNull String baseLogFileName, @NotNull FileLike workingDir)
    {
        return !root.isCloudRoot() ?
                new LocalDirectory(workingDir, baseLogFileName) :
                new LocalDirectory(root.getContainer(), root, baseLogFileName);
    }

    @JsonCreator
    private LocalDirectory(
            @JsonProperty("_localDirectoryFile") FileLike localDirectoryFile,
            @JsonProperty("_isTemporary") boolean isTemporary,
            @JsonProperty("_pipeRoot") PipeRoot pipeRoot,
            @JsonProperty("_baseLogFileName") String baseLogFileName,
            @JsonProperty("_remoteDir") FileLike remoteDir)
    {
        _localDirectoryFile = localDirectoryFile;
        _isTemporary = isTemporary;
        _pipeRoot = pipeRoot;
        _remoteDir = remoteDir != null ? remoteDir : _pipeRoot == null ? null : _pipeRoot.getRootFileLike(); //Using _piperoot as default for backwards compatability
        _baseLogFileName = baseLogFileName;
    }

    // Constructor for runs and actions when pipeline root is cloud
    public LocalDirectory(Container container, PipeRoot pipeRoot, String basename)
    {
        this(container, pipeRoot, basename, null);
    }

    public LocalDirectory(Container container, PipeRoot pipeRoot, String basename, FileLike remoteDir)
    {
        _isTemporary = true;
        _pipeRoot = pipeRoot;
        _remoteDir = remoteDir != null ? remoteDir : _pipeRoot == null ? null : _pipeRoot.getRootFileLike(); //Using _piperoot as default for backwards compatability
        _baseLogFileName = basename;

        try
        {
            FileLike containerDir = ensureContainerDir(container);
            _localDirectoryFile = containerDir.resolveChild(FileUtil.makeFileNameWithTimestamp("_temp_"));

            ensureLocalDirectory();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    // Constructor when pipeline root not in cloud
    public LocalDirectory(@NotNull FileLike localDirectory, String basename)
    {
        _localDirectoryFile = localDirectory;
        _isTemporary = false;
        _pipeRoot = null;
        _baseLogFileName = basename;
        _remoteDir = null;
    }

    @NotNull
    public FileLike getLocalDirectoryFile()
    {
        return _localDirectoryFile;
    }

    public FileLike determineLogFile()
    {
        // If _isTemporary, look for existing file in the parent
        _logFile = PipelineJob.FT_LOG.newFile(_localDirectoryFile, _baseLogFileName);
        if (_isTemporary && null != _remoteDir)
        {
            try
            {
                FileLike remoteLogFilePath = _remoteDir.resolveChild(_logFile.getName());
                if (remoteLogFilePath.exists())
                {
                    FileUtil.copyFile(remoteLogFilePath, _logFile);
                }
                else
                {
                    FileUtil.createNewFile(_logFile, true);
                }
            }
            catch (IOException e)
            {
                // ignore
            }
        }
        return _logFile;
    }

    public FileLike restore()
    {
        try
        {
            ensureLocalDirectory();
            return determineLogFile();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public FileLike copyToLocalDirectory(String url, Logger log)
    {
        if (_isTemporary && null != _pipeRoot)
        {
            try
            {
            // File elsewhere (on S3); make a copy a return File object for copy
                FileLike path = _pipeRoot.resolveToFileLikeFromUrl(url);
                if (null != path)
                {
                    FileLike tempFile = _localDirectoryFile.resolveChild(path.getName());
                    if (!tempFile.exists())
                    {
                        FileUtil.copyFile(path, tempFile, StandardCopyOption.COPY_ATTRIBUTES);
                        log.info("Created temp file because input is from cloud: " + path);
                    }
                    return tempFile;
                }
            }
            catch (IOException e)
            {
                log.error("IO Error: " + e.getMessage());
            }
        }
        return null;
    }

    private void ensureLocalDirectory() throws IOException
    {
        if (_isTemporary && !_localDirectoryFile.exists())
            FileUtil.createDirectory(_localDirectoryFile);     // TODO Should we set file permissions?
    }

    // If LocalDirectory isTemporary, copies the file to the temp directory for this container.
    // That temp directory will live as long as the the LabKey container lives, or until the system temp is cleared
    @Nullable
    public static FileLike copyToContainerDirectory(@NotNull Container container, @NotNull FileLike remotePath, @NotNull Logger log)
    {
        // File elsewhere (on S3); make a copy a return File object for copy

        String fileName = remotePath.getName();
        String tempFileName = FileUtil.makeFileNameWithTimestamp(FileUtil.getBaseName(fileName), FileUtil.getExtension(fileName));
        try
        {
            FileLike containerDir = ensureContainerDir(container);
            FileLike tempFile = containerDir.resolveChild(tempFileName);
            if (!tempFile.exists())
            {
                log.debug("Copying file to container's temp directory: " + remotePath);
                FileUtil.copyFile(remotePath, tempFile, StandardCopyOption.COPY_ATTRIBUTES);
                log.debug("Copied " + tempFile.getSize() + " bytes.");
            }
            return tempFile;
        }
        catch (NoSuchFileException e)
        {
            // Avoid a separate round-trip just to determine if file is available, as it adds ~1 overhead per call
            log.debug("Could not find remote file: " + remotePath + ", unable to copy locally");
            return null;
        }
        catch (IOException e)
        {
            log.error("IO Error: ", e);
            return null;
        }
    }

    private static FileLike ensureContainerDir(Container container) throws IOException
    {
        FileLike moduleDir = getModuleLocalTempDirectory();
        if (!moduleDir.exists())
        {
            FileUtil.createDirectory(moduleDir);
        }

        FileLike containerDir = getContainerLocalTempDirectory(container);
        if (!containerDir.exists())
        {
            FileUtil.createDirectory(containerDir);
        }
        return containerDir;
    }

    @Nullable
    public FileLike cleanUpLocalDirectory()
    {
        FileLike remoteLogFilePath = null;
        if (_isTemporary && _localDirectoryFile.exists())
        {
            if (null != _logFile && _logFile.exists())
            {
                // Copy file back to the cloud
                remoteLogFilePath = getRemoteLogFile();
                if (null != remoteLogFilePath)
                {
                    try
                    {
                        FileUtil.copyFile(_logFile, remoteLogFilePath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
            FileUtil.deleteDir(_localDirectoryFile);
        }

        return remoteLogFilePath;
    }

    private static FileLike getModuleLocalTempDirectory()
    {
        FileLike tempDir = FileUtil.getTempDirectoryFileLike();   // tomcat/temp or similar
        return tempDir.resolveChild(FileUtil.makeLegalName(PipelineService.MODULE_NAME + "_temp"));
    }

    public static FileLike getContainerLocalTempDirectory(Container container)
    {
        return getModuleLocalTempDirectory().resolveChild(FileUtil.makeLegalName(container.getName() + "_" + container.getId()));
    }

    public FileLike getRemoteLogFile()
    {
        if (_remoteDir == null)
            return _logFile;
        return _remoteDir.resolveChild(_logFile.getName());
    }
}
