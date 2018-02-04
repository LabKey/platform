package org.labkey.api.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URIUtil;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
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
    private final String _parentDataFileUrl;
    private final PipeRoot _pipeRoot;
    private File _logFile;
    private final String _basename;

    public LocalDirectory(Container container, PipeRoot pipeRoot, String dataFileUrl, String basename)
    {
        File tempDirectory = FileUtil.getTempDirectory();   // tomcat/temp or similar
        String workingDirectoryName = FileUtil.makeFileNameWithTimestamp("_temp_" + container.getId());
        _localDirectoryFile = new File(tempDirectory, workingDirectoryName);
        _isTemporary = true;
        _pipeRoot = pipeRoot;
        _basename = basename;

        URI uri = URIUtil.getParentURI(null, FileUtil.createUri(dataFileUrl));
        _parentDataFileUrl = null != uri ? FileUtil.uriToString(uri) : null;

        ensureLocalDirectory();
    }

    public LocalDirectory(@NotNull File localDirectory, String basename)
    {
        _localDirectoryFile = localDirectory;
        _isTemporary = false;
        _pipeRoot = null;
        _basename = basename;
        _parentDataFileUrl = null;
    }

    @NotNull
    public File getLocalDirectoryFile()
    {
        return _localDirectoryFile;
    }

    public File determineLogFile()
    {
        // If _isTemporary, look for existing file in the parent
        _logFile = PipelineJob.FT_LOG.newFile(_localDirectoryFile, _basename);
        if (_isTemporary && null != _pipeRoot && null != _parentDataFileUrl)
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
        ensureLocalDirectory();
        return determineLogFile();
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
                    Files.copy(path, tempFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                    log.info("Created temp file because input is from cloud: " + FileUtil.pathToString(path));
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

    private void ensureLocalDirectory()
    {
        if (_isTemporary)
            try
            {
                if (!Files.exists(_localDirectoryFile.toPath()))
                    Files.createDirectory(_localDirectoryFile.toPath());     // TODO Should we set file permissions?
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
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
