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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
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
    @NotNull private final File _localDirectoryFile;
    private final boolean _isTemporary;
    private final PipeRoot _pipeRoot;
    private final Path _remoteDir;
    private Path _logFile;
    private final String _baseLogFileName;
    private final String _moduleName;

    public static LocalDirectory create(@NotNull PipeRoot root, @NotNull String moduleName)
    {
        return create(root, moduleName, "dummyLogFile", root.isCloudRoot() ? "dummy" : root.getRootPath().getPath());
    }

    @Deprecated //Prefer to use a Path for workingDir -- can be local or remote, but should match with root
    public static LocalDirectory create(@NotNull PipeRoot root, @NotNull String moduleName, @NotNull String baseLogFileName, @NotNull String workingDir)
    {
        return create(root, moduleName, baseLogFileName, Path.of(workingDir));
    }

    public static LocalDirectory create(@NotNull PipeRoot root, @NotNull String moduleName, @NotNull String baseLogFileName, @NotNull Path workingDir)
    {
        return !root.isCloudRoot() ?
                new LocalDirectory(workingDir.toFile(), moduleName, baseLogFileName) :
                new LocalDirectory(root.getContainer(), moduleName, root, baseLogFileName, workingDir);
    }

    @JsonCreator
    private LocalDirectory(
            @JsonProperty("_localDirectoryFile") File localDirectoryFile,
            @JsonProperty("_isTemporary") boolean isTemporary,
            @JsonProperty("_pipeRoot") PipeRoot pipeRoot,
            @JsonProperty("_baseLogFileName") String baseLogFileName,
            @JsonProperty("_moduleName") String moduleName,
            @JsonProperty("_remoteDir") Path remoteDir)
    {
        _localDirectoryFile = localDirectoryFile;
        _isTemporary = isTemporary;
        _pipeRoot = pipeRoot;
        _remoteDir = remoteDir == null ? _pipeRoot.getRootNioPath() : remoteDir; //Using _piperoot as default for backwards compatability
        _baseLogFileName = baseLogFileName;
        _moduleName = moduleName;
    }

    // Constructor for runs and actions when pipeline root is cloud
    public LocalDirectory(Container container, String moduleName, PipeRoot pipeRoot, String basename)
    {
        this(container, moduleName, pipeRoot, basename,  pipeRoot.getRootNioPath());
    }

    public LocalDirectory(Container container, String moduleName, PipeRoot pipeRoot, String basename, Path remoteDir)
    {
        _isTemporary = true;
        _pipeRoot = pipeRoot;
        _remoteDir = remoteDir;
        _baseLogFileName = basename;
        _moduleName = moduleName;

        try
        {
            File containerDir = ensureContainerDir(container);
            _localDirectoryFile = new File(containerDir, FileUtil.makeFileNameWithTimestamp("_temp_"));

            ensureLocalDirectory();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    // Constructor when pipeline root not in cloud
    public LocalDirectory(@NotNull File localDirectory, String moduleName, String basename)
    {
        _localDirectoryFile = localDirectory;
        _isTemporary = false;
        _pipeRoot = null;
        _baseLogFileName = basename;
        _moduleName = moduleName;
        _remoteDir = null;
    }

    @NotNull
    public File getLocalDirectoryFile()
    {
        return _localDirectoryFile;
    }

    public Path determineLogFile()
    {
        // If _isTemporary, look for existing file in the parent
        _logFile = PipelineJob.FT_LOG.newFile(_localDirectoryFile, _baseLogFileName).toPath();
        if (_isTemporary && null != _remoteDir)
        {
            try
            {
                Path remoteLogFilePath = _remoteDir.resolve(_logFile.getFileName().toString());
                if (Files.exists(remoteLogFilePath))
                {
                    Files.copy(remoteLogFilePath, _logFile);
                }
                else
                {
                    Files.createFile(_logFile);
                }
            }
            catch (IOException e)
            {
                // ignore
            }
        }
        return _logFile;
    }

    public Path restore()
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

    public File copyToLocalDirectory(String url, Logger log)
    {
        if (_isTemporary && null != _pipeRoot)
        {
            // File elsewhere (on S3); make a copy a return File object for copy
            Path path = _pipeRoot.resolveToNioPathFromUrl(url);
            if (null != path)
            {
                String filename = FileUtil.getFileName(path);
                try
                {
                    File tempFile = new File(_localDirectoryFile, filename);
                    if (!Files.exists(tempFile.toPath()))
                    {
                        Files.copy(path, tempFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                        log.info("Created temp file because input is from cloud: " + FileUtil.pathToString(path));
                    }
                    return tempFile;
                }
                catch (IOException e)
                {
                    log.error("IO Error: " + e.getMessage());
                }
            }
        }
        return null;
    }

    private void ensureLocalDirectory() throws IOException
    {
        if (_isTemporary && !Files.exists(_localDirectoryFile.toPath()))
            Files.createDirectory(_localDirectoryFile.toPath());     // TODO Should we set file permissions?
    }

    // If LocalDirectory isTemporary, copies the file to the temp directory for this container.
    // That temp directory will live as long as the the LabKey container lives, or until the system temp is cleared
    @Nullable
    public static File copyToContainerDirectory(@NotNull Container container, @NotNull Path remotePath, @NotNull Logger log)
    {
        // File elsewhere (on S3); make a copy a return File object for copy

        String fileName = FileUtil.getFileName(remotePath);
        String tempFileName = FileUtil.makeFileNameWithTimestamp(FileUtil.getBaseName(fileName), FileUtil.getExtension(fileName));
        try
        {
            File containerDir = ensureContainerDir(container);
            File tempFile = new File(containerDir, tempFileName);
            if (!Files.exists(tempFile.toPath()))
            {
                log.debug("Copying file to container's temp directory: "+ FileUtil.pathToString(remotePath));
                Files.copy(remotePath, tempFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                log.debug("Copied " + Files.size(tempFile.toPath()) + " bytes.");
            }
            return tempFile;
        }
        catch (NoSuchFileException e)
        {
            // Avoid a separate round-trip just to determine if file is available, as it adds ~1 overhead per call
            log.debug("Could not find remote file: " + FileUtil.pathToString(remotePath) + ", unable to copy locally");
            return null;
        }
        catch (IOException e)
        {
            log.error("IO Error: ", e);
            return null;
        }
    }

    private static File ensureContainerDir(Container container) throws IOException
    {
        File moduleDir = getModuleLocalTempDirectory();
        if (!Files.exists(moduleDir.toPath()))
        {
            Files.createDirectory(moduleDir.toPath());
        }

        File containerDir = getContainerLocalTempDirectory(container);
        if (!Files.exists(containerDir.toPath()))
        {
            Files.createDirectory(containerDir.toPath());
        }
        return containerDir;
    }

    @Nullable
    public Path cleanUpLocalDirectory()
    {
        Path remoteLogFilePath = null;
        if (_isTemporary && Files.exists(_localDirectoryFile.toPath()))
        {
            if (null != _logFile && Files.exists(_logFile))
            {
                // Copy file back to the cloud
                remoteLogFilePath = getRemoteLogFilePath();
                if (null != remoteLogFilePath)
                {
                    try
                    {
                        Files.copy(_logFile, remoteLogFilePath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
            deleteDirectory(_localDirectoryFile.toPath());
        }

        return remoteLogFilePath;
    }

    private void deleteDirectory(Path dir)
    {
        try
        {
            FileUtil.deleteDir(dir);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static File getModuleLocalTempDirectory()
    {
        File tempDir = FileUtil.getTempDirectory();   // tomcat/temp or similar
        return new File(tempDir, FileUtil.makeLegalName(PipelineService.MODULE_NAME + "_temp"));
    }

    public static File getContainerLocalTempDirectory(Container container)
    {
        return new File(getModuleLocalTempDirectory(), FileUtil.makeLegalName(container.getName() + "_" + container.getId()));
    }

    public Path getRemoteLogFilePath()
    {
        return _remoteDir.resolve(_logFile.getFileName().toString());
    }
}
