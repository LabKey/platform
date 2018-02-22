package org.labkey.api.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
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
public class LocalDirectory implements Serializable
{
    @NotNull private final File _localDirectoryFile;
    private final boolean _isTemporary;
    private final PipeRoot _pipeRoot;
    private File _logFile;
    private final String _baseLogFileName;
    private final String _moduleName;

    public static LocalDirectory create (@NotNull PipeRoot root, @NotNull String moduleName)
    {
        return create(root, moduleName, "dummyLogFile", root.isCloudRoot() ? "dummy" : root.getRootPath().getPath());
    }

    public static LocalDirectory create(@NotNull PipeRoot root, @NotNull String moduleName, @NotNull String baseLogFileName, @NotNull String localDirPath)
    {
        return !root.isCloudRoot() ?
                new LocalDirectory(new File(localDirPath), moduleName, baseLogFileName) :
                new LocalDirectory(root.getContainer(), moduleName, root, baseLogFileName);
    }

    // Constructor for runs and actions when pipeline root is cloud
    public LocalDirectory(Container container, String moduleName, PipeRoot pipeRoot, String basename)
    {
        _isTemporary = true;
        _pipeRoot = pipeRoot;
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
    }

    @NotNull
    public File getLocalDirectoryFile()
    {
        return _localDirectoryFile;
    }

    public File determineLogFile()
    {
        // If _isTemporary, look for existing file in the parent
        _logFile = PipelineJob.FT_LOG.newFile(_localDirectoryFile, _baseLogFileName);
        if (_isTemporary && null != _pipeRoot)
        {
            try
            {
                Path remoteLogFilePath = _pipeRoot.resolveToNioPath(_logFile.getName());
                if (null != remoteLogFilePath && Files.exists(remoteLogFilePath))
                {
                    Files.copy(remoteLogFilePath, _logFile.toPath());
                }
                else
                {
                    Files.createFile(_logFile.toPath());
                }
            }
            catch (IOException e)
            {
                // ignore
            }
        }
        return _logFile;
    }

    public File restore()
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

    public File copyToLocalDirectory(String url, org.apache.log4j.Logger log)
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

    private File ensureContainerDir(Container container) throws IOException
    {
        File tempDir = FileUtil.getTempDirectory();   // tomcat/temp or similar
        File moduleDir = new File(tempDir, FileUtil.makeLegalName(_moduleName + "_temp"));
        if (!Files.exists(moduleDir.toPath()))
        {
            Files.createDirectory(moduleDir.toPath());
        }

        File containerDir = new File(moduleDir, FileUtil.makeLegalName(container.getName() + "_" + container.getId()));
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
            if (null != _logFile && Files.exists(_logFile.toPath()))
            {
                remoteLogFilePath = _pipeRoot.resolveToNioPath(_logFile.getName());
                if (null != remoteLogFilePath)
                {
                    try
                    {
                        Files.copy(_logFile.toPath(), remoteLogFilePath, StandardCopyOption.REPLACE_EXISTING);
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

}
