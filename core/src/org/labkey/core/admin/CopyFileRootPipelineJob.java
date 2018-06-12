package org.labkey.core.admin;

import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.LocalDirectory;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.core.admin.AdminController.ProjectSettingsForm.MigrateFilesOption;
import org.labkey.core.CoreModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class CopyFileRootPipelineJob extends PipelineJob
{
    private final String _sourceDir;
    private final String _destDir;
    private final MigrateFilesOption _migrateFilesOption;
    CopyFileRootPipelineJob(ViewBackgroundInfo info, PipeRoot pipeRoot, Path sourceDir, Path destDir, MigrateFilesOption migrateFilesOption)
    {
        super(null, info, pipeRoot);
        _sourceDir = FileUtil.pathToString(sourceDir);
        _destDir = FileUtil.pathToString(destDir);
        _migrateFilesOption = migrateFilesOption;


        String baseLogFileName = FileUtil.makeFileNameWithTimestamp(
                FileUtil.getBaseName("copy_directory_fileroot_change", 1).replace(" ", "_"));

        LocalDirectory localDirectory = LocalDirectory.create(pipeRoot, CoreModule.CORE_MODULE_NAME, baseLogFileName,
                !pipeRoot.isCloudRoot() ? pipeRoot.getRootPath().getPath() : FileUtil.getTempDirectory().getPath());
        setLocalDirectory(localDirectory);
        setLogFile(localDirectory.determineLogFile());
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "Copy Files for File Root Change";
    }

    @Override
    public void run()
    {
        try
        {
            info(getDescription() + " started");
            long startTime = System.currentTimeMillis();
            setStatus(TaskStatus.running);
            TaskStatus finalStatus = TaskStatus.complete;
            info("Migration option: " + _migrateFilesOption.description());
            info("Source: " + _sourceDir);
            info("Destination: " + _destDir);

            Path sourceDir = FileUtil.stringToPath(getContainer(), _sourceDir);
            Path destDir = FileUtil.stringToPath(getContainer(), _destDir);
            if (null == sourceDir || null == destDir)
            {
                error("Source or destination is null");
                finalStatus = TaskStatus.error;
            }
            else if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir))     // Source must exist
            {
                error("Source not directory: " + FileUtil.pathToString(sourceDir));
                finalStatus = TaskStatus.error;
            }
            else if (Files.exists(destDir) && !Files.isDirectory(destDir))          // Dest doesn't have to exist
            {
                error("Destination not directory: " + FileUtil.pathToString(destDir));
                finalStatus = TaskStatus.error;
            }
            else
            {
                try
                {
                    // Count files and sum sizes
                    Pair<Integer, Long> stats = new Pair<>(new Integer(0), new Long(0));
                    getStats(sourceDir, stats);
                    info("Source directory has " + stats.first + " files (" + stats.second + " total bytes)");

                    setStatus(TaskStatus.running, "Copying files");
                    info("Copying directory '" + FileUtil.pathToString(sourceDir) + "'");
                    List<Long> lastLogTime = new ArrayList<>();
                    lastLogTime.add(startTime);
                    copyFiles(sourceDir, destDir, lastLogTime, stats, new Pair<>(stats.first, stats.second));
                    info("Done copying");

                    FileContentService fileContentService = FileContentService.get();
                    if (null != fileContentService)
                    {
                        info("Informing file listeners of copy/move");
                        fileContentService.fireFileMoveEvent(sourceDir, destDir, getUser(), getContainer());
                    }
                    else
                        error("FileContentService not available to call fireFileMoveEvent");

                    if (MigrateFilesOption.move.equals(_migrateFilesOption))
                    {
                        setStatus(TaskStatus.running, "Deleting files");
                        info("Deleting source directory");
                        deleteFiles(sourceDir);
                        info("Done deleting source directory");
                    }
                }
                catch (UncheckedIOException e)
                {
                    // Specific error should already have been logged
                    finalStatus = TaskStatus.error;
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            info("Elapsed time " + elapsed / 1000 + " seconds");
            info("Job complete");
            setStatus(finalStatus);
        }
        finally
        {
            finallyCleanUpLocalDirectory();
        }
    }

    private void getStats(Path dirPath, final Pair<Integer, Long> stats)
    {
        setStatus(TaskStatus.running, "Getting stats");
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dirPath))
        {
            dirStream.forEach(path -> {
                try
                {
                    if (Files.isDirectory(path))
                    {
                        getStats(path, stats);
                    }
                    else
                    {
                        stats.first += 1;
                        stats.second += Files.size(path);
                    }
                }
                catch (IOException e)
                {
                    error("Error getting source directory stats", e);
                    throw new UncheckedIOException(e);
                }
            });
        }
        catch (IOException e)
        {
            error("Error getting directory stream for '" + FileUtil.pathToString(dirPath) + "'", e);
            throw new UncheckedIOException(e);
        }
    }

    private void copyFiles(Path sourceDir, Path destDir, List<Long> lastStatTime, final Pair<Integer, Long> stats, final Pair<Integer, Long> origStats)
    {
        if (!Files.exists(destDir))
        {
            try
            {
                Files.createDirectory(destDir);
            }
            catch (IOException e)
            {
                error("Error creating destination directory '" + FileUtil.pathToString(destDir) + "'", e);
                throw new UncheckedIOException(e);
            }
        }

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(sourceDir))
        {
            dirStream.forEach(sourceChild -> {
                String pathString = FileUtil.pathToString(sourceChild);
                try
                {
                    Path destChild = destDir.resolve(FileUtil.getFileName(sourceChild));
                    if (Files.isDirectory(sourceChild))
                    {
                        setStatus(TaskStatus.running, "Copying files");
                        info("Copying directory '" + pathString + "'");
                        copyFiles(sourceChild, destChild, lastStatTime, stats, origStats);
                    }
                    else
                    {
                        setStatus(TaskStatus.running, "Copying files");
                        info("Copying file '" + pathString + "'");
                        Files.copy(sourceChild, destChild, StandardCopyOption.REPLACE_EXISTING);
                        stats.first -= 1;
                        stats.second -= Files.size(sourceChild);
                        logStatTime(lastStatTime, stats, origStats);
                    }
                }
                catch (IOException e)
                {
                    error("Copy error", e);
                    throw new UncheckedIOException(e);
                }
                setStatus(TaskStatus.running, "Done copying '" + pathString + "'");
            });
        }
        catch (IOException e)
        {
            error("Error getting directory stream for '" + FileUtil.pathToString(sourceDir) + "'", e);
            throw new UncheckedIOException(e);
        }
    }

    private void logStatTime(List<Long> lastStatTime, final Pair<Integer, Long> stats, final Pair<Integer, Long> origStats)
    {
        Long currentTime = System.currentTimeMillis();
        if (currentTime - lastStatTime.get(0) > 1000*60)
        {
            info((origStats.first - stats.first) + " out of " + origStats.first + " files copied (" +
                    (origStats.second - stats.second) + " out of " + origStats.second + " bytes)");
            lastStatTime.set(0, currentTime);
        }
    }

    private void deleteFiles(Path dirPath)
    {
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dirPath))
        {
            dirStream.forEach(path -> {
                try
                {
                    if (Files.isDirectory(path))
                    {
                        setStatus(TaskStatus.running, "Deleting files");
                        deleteFiles(path);
                    }
                    else if (Files.isRegularFile(path))
                    {
                        setStatus(TaskStatus.running, "Deleting files");
                        Files.deleteIfExists(path);
                    }
                }
                catch (IOException e)
                {
                    error("Error deleting file '" + FileUtil.pathToString(path) + "'", e);
                    throw new UncheckedIOException(e);
                }
            });
        }
        catch (IOException e)
        {
            error("Error getting directory stream for '" + FileUtil.pathToString(dirPath) + "'", e);
            throw new UncheckedIOException(e);
        }
        try
        {
            Files.deleteIfExists(dirPath);
        }
        catch (IOException e)
        {
            error("Error deleting directory '" + FileUtil.pathToString(dirPath) + "'", e);
            throw new UncheckedIOException(e);
        }
    }
}
