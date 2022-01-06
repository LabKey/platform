/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.core.admin;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.files.FileContentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PairSerializer;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.core.CoreModule;
import org.labkey.core.admin.AdminController.MigrateFilesOption;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CopyFileRootPipelineJob extends PipelineJob
{
    @JsonSerialize(contentUsing = PairSerializer.class)
    private final List<Pair<Container, String>> _sourceInfos;
    private final MigrateFilesOption _migrateFilesOption;

    @JsonCreator
    protected CopyFileRootPipelineJob(
            @JsonProperty("_sourceInfos") List<Pair<Container, String>> sourceInfos,
            @JsonProperty("_migrateFilesOption") MigrateFilesOption migrateFilesOption)
    {
        super();
        _sourceInfos = sourceInfos;
        _migrateFilesOption = migrateFilesOption;
    }

    CopyFileRootPipelineJob(Container container, User user, @NotNull List<Pair<Container, String>> sourceInfos, PipeRoot pipeRoot, MigrateFilesOption migrateFilesOption)
    {
        super("FileRootCopy", new ViewBackgroundInfo(container, user, null), pipeRoot);
        _sourceInfos = sourceInfos;
        _migrateFilesOption = migrateFilesOption;


        String baseLogFileName = FileUtil.makeFileNameWithTimestamp(
                FileUtil.getBaseName("copy_directory_fileroot_change", 1).replace(" ", "_"));

        setupLocalDirectoryAndJobLog(pipeRoot, CoreModule.CORE_MODULE_NAME, baseLogFileName);
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
        info(getDescription() + " started");
        long startTime = System.currentTimeMillis();
        setStatus(TaskStatus.running);
        TaskStatus status = TaskStatus.complete;
        info("Migration option: " + _migrateFilesOption.description());

        String foldersMessage = "Containers:\n\t" + _sourceInfos.stream().map(Pair::getKey).map(Container::getPath).collect(Collectors.joining(",\n\t"));
        info(foldersMessage);

        try
        {
            boolean sourceIsLocalFileSystem = true;
            FileContentService fileContentService = FileContentService.get();
            if (null != fileContentService)
            {
                for (Pair<Container, String> sourceInfo : _sourceInfos)
                {
                    Container container = sourceInfo.first;
                    Path destFileRootDir = fileContentService.getFileRootPath(container, FileContentService.ContentType.files);
                    if (null != destFileRootDir)
                    {
                        Path sourceDir = FileUtil.stringToPath(sourceInfo.first, sourceInfo.second);
                        sourceIsLocalFileSystem = sourceIsLocalFileSystem && !FileUtil.hasCloudScheme(sourceDir);
                        TaskStatus oneFolderStatus = copyOneFolder(container, sourceDir, destFileRootDir, startTime);
                        status = updateIfError(status, oneFolderStatus);   // Remember there was an error, but continue with other folders
                    }
                }
            }
            else
            {
                getLogger().warn("FileContentService not found");
                status = TaskStatus.error;
            }

            if (MigrateFilesOption.move.equals(_migrateFilesOption) && sourceIsLocalFileSystem && !TaskStatus.error.equals(status))
            {
                //  TODO: Clean up LabKey created directories -- do this only if source was "default based on...." and we need to do it even if "Don't copy" option was selected and there were no files.
            }

            long elapsed = System.currentTimeMillis() - startTime;
            info("Elapsed time " + elapsed / 1000 + " seconds");
            info("Job complete");
            setStatus(status);
        }
        catch (CancelledException e)
        {
            setActiveTaskStatus(TaskStatus.cancelled);  // Need to set because cleaning upo LocalDirectory can getActiveTaskStatus
        }
        catch (Exception e)
        {
            // Something not caught below
            error("Unexpected error; ending job.", e);
            setStatus(TaskStatus.error);
        }
        finally
        {
            finallyCleanUpLocalDirectory();
        }
    }

    private TaskStatus copyOneFolder(Container container, Path sourceDir, Path destDir, long startTime)
    {
        TaskStatus status = TaskStatus.complete;

        if (null == sourceDir || null == destDir)
        {
            error("Source or destination is null");
            status = TaskStatus.error;
        }
        else
        {
            String sourceStr = FileUtil.pathToString(sourceDir);
            String destStr = FileUtil.pathToString(destDir);
            info("Container: " + container.getPath());
            info("Source: " + (null != sourceStr ? sourceStr : "null"));
            info("Destination: " + (null != destStr ? destStr : "null"));

            if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir))
            {
                warn("Source does not exist or is not directory: " + FileUtil.pathToString(sourceDir));
            }
            else if (Files.exists(destDir) && !Files.isDirectory(destDir))          // Dest doesn't have to exist
            {
                getLogger().warn("Destination exists and is not directory: " + FileUtil.pathToString(destDir));
                status = TaskStatus.error;
            }
            else
            {
                try
                {
                    // Count files and sum sizes
                    Pair<Integer, Long> stats = new Pair<>(0, 0L);
                    status = updateIfError(status, getStats(sourceDir, stats));
                    info("Source directory has " + stats.first + " files (" + stats.second + " total bytes)");

                    setStatus(TaskStatus.running, "Copying files");
                    info("Copying directory '" + FileUtil.pathToString(sourceDir) + "'");
                    List<Long> lastLogTime = new ArrayList<>();
                    lastLogTime.add(startTime);
                    status = updateIfError(status, copyFiles(sourceDir, destDir, lastLogTime, stats, new Pair<>(stats.first, stats.second)));
                    info("Done copying");

                    FileContentService fileContentService = FileContentService.get();
                    if (null != fileContentService)
                    {
                        info("Informing file listeners of copy/move");
                        fileContentService.fireFileMoveEvent(sourceDir, destDir, getUser(), getContainer());
                    }
                    else
                    {
                        getLogger().warn("FileContentService not available to call fireFileMoveEvent");
                        status = TaskStatus.error;
                    }

                    if (MigrateFilesOption.move.equals(_migrateFilesOption) && !TaskStatus.error.equals(status))
                    {
                        setStatus(TaskStatus.running, "Deleting files");
                        info("Deleting source directory");
                        deleteFiles(sourceDir);
                        info("Done deleting source directory");
                    }
                    info("");
                }
                catch (IllegalArgumentException e)
                {
                    getLogger().warn("Error processing folder", e);
                    status = TaskStatus.error;
                }
                catch (UncheckedIOException e)
                {
                    // Specific error should already have been logged
                    status = TaskStatus.error;
                }
            }
        }
        return status;
    }

    private TaskStatus getStats(Path dirPath, final Pair<Integer, Long> stats)
    {
        setStatus(TaskStatus.running, "Getting stats");
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dirPath))
        {
            for (Path path : dirStream)
            {
                try
                {
                    if (Files.isDirectory(path))
                    {
                        if (TaskStatus.error.equals(getStats(path, stats)))
                            return TaskStatus.error;
                    }
                    else
                    {
                        stats.first += 1;
                        stats.second += Files.size(path);
                    }
                }
                catch (IllegalArgumentException | IOException e)
                {
                    getLogger().warn("Error getting source directory stats", e);
                    return TaskStatus.error;
                }
            }
        }
        catch (IllegalArgumentException | IOException e)
        {
            getLogger().warn("Error getting directory stream for source directory", e);
            return TaskStatus.error;
        }
        return TaskStatus.complete;
    }

    private TaskStatus copyFiles(Path sourceDir, Path destDir, List<Long> lastStatTime, final Pair<Integer, Long> stats, final Pair<Integer, Long> origStats)
    {
        if (!Files.exists(destDir))
        {
            try
            {
                Files.createDirectory(destDir);
            }
            catch (IllegalArgumentException | IOException e)
            {
                getLogger().warn("Error creating destination directory destination directory", e);
                return TaskStatus.error;
            }
        }

        TaskStatus status = TaskStatus.complete;
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(sourceDir))
        {
            for (Path sourceChild : dirStream)
            {
                try
                {
                    String pathString = FileUtil.getAbsolutePath(sourceChild);
                    Path destChild = destDir.resolve(FileUtil.getFileName(sourceChild));
                    if (Files.isDirectory(sourceChild))
                    {
                        if (FileUtil.hasCloudScheme(destDir) && StringUtils.contains(pathString, "%"))
                        {
                            getLogger().warn("Cannot copy directory '" + pathString + "' to cloud, because its name contains '%'.");
                            status = TaskStatus.error;
                        }
                        else
                        {
                            setStatus(TaskStatus.running, "Copying files");
                            info("Copying directory '" + pathString + "'");
                            status = updateIfError(status, copyFiles(sourceChild, destChild, lastStatTime, stats, origStats));
                        }
                    }
                    else
                    {
                        setStatus(TaskStatus.running, "Copying files");
                        info("Copying file  '" + pathString + "'");

                        long sourceSize = Files.size(sourceChild);
                        boolean retainExisting = false;

                        try
                        {
                            if (sourceSize == Files.size(destChild))
                            {
                                retainExisting = true;
                                int retryCount = 0;
                                boolean modifiedUpdated = false;
                                while (!modifiedUpdated)
                                {
                                    try
                                    {
                                        Files.setLastModifiedTime(destChild, Files.getLastModifiedTime(sourceChild));
                                        info("Retained existing file '" + pathString + "'");
                                        modifiedUpdated = true;
                                    }
                                    catch (FileNotFoundException | NoSuchFileException e)
                                    {
                                        // S3 backed storage is not immediately consistent after the PUT. Try a few times
                                        // before declaring failure
                                        if (retryCount++ > 5)
                                        {
                                            throw e;
                                        }
                                        warn("Failed trying to update last modified for '" + pathString + "', will retry");
                                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                                    }
                                }
                            }
                        }
                        catch (NoSuchFileException ignored) {}

                        if (!retainExisting)
                        {
                            Files.copy(sourceChild, destChild, StandardCopyOption.REPLACE_EXISTING);
                            boolean modifiedUpdated = false;
                            int retryCount = 0;
                            while (!modifiedUpdated)
                            {
                                try
                                {
                                    Files.setLastModifiedTime(destChild, Files.getLastModifiedTime(sourceChild));
                                    info("Copy complete '" + pathString + "'");
                                    modifiedUpdated = true;
                                }
                                catch (FileNotFoundException | NoSuchFileException e)
                                {
                                    // S3 backed storage is not immediately consistent after the PUT. Try a few times
                                    // before declaring failure
                                    if (retryCount++ > 5)
                                    {
                                        throw e;
                                    }
                                    warn("Failed trying to update last modified for '" + pathString + "', will retry");
                                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                                }
                            }
                        }

                        stats.first -= 1;
                        stats.second -= sourceSize;
                        logStatTime(lastStatTime, stats, origStats);
                    }
                    setStatus(TaskStatus.running, "Done copying '" + pathString + "'");
                }
                catch (IllegalArgumentException | IOException e)
                {
                    getLogger().warn("Copy error", e);
                    status = TaskStatus.error;
                }
            }
        }
        catch (IllegalArgumentException | IOException e)
        {
            getLogger().warn("Error getting directory stream for source directory", e);
            return TaskStatus.error;
        }
        return status;
    }

    private boolean matchesCopyFailureSimulatorRegex(String propertyName, String fileName)
    {
        Module module = ModuleLoader.getInstance().getCoreModule();
        ModuleProperty mp = module.getModuleProperties().get(propertyName);
        String s =  mp.getEffectiveValue(getContainer());
        if (s == null)
            return false;
        Pattern p = Pattern.compile(s);
        return p.matcher(fileName).matches();
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
                    getLogger().warn("Error deleting file '" + FileUtil.pathToString(path) + "'", e);
                    throw new UncheckedIOException(e);
                }
            });
        }
        catch (IOException e)
        {
            getLogger().warn("Error getting directory stream for destination directory", e);
            throw new UncheckedIOException(e);
        }
        try
        {
            Files.deleteIfExists(dirPath);
        }
        catch (IOException e)
        {
            getLogger().warn("Error deleting directory '" + FileUtil.pathToString(dirPath) + "'", e);
            throw new UncheckedIOException(e);
        }
    }

    private TaskStatus updateIfError(TaskStatus current, TaskStatus update)
    {
        return TaskStatus.error.equals(update) ? update : current;
    }

    @Override
    public List<String> compareJobs(PipelineJob job2)
    {
        List<String> errors = super.compareJobs(job2);
        if (job2 instanceof CopyFileRootPipelineJob)
        {
            CopyFileRootPipelineJob copyJob2 = (CopyFileRootPipelineJob)job2;
            if (!this._migrateFilesOption.equals(copyJob2._migrateFilesOption))
                errors.add("_migrateFilesOption");
            if (this._sourceInfos.size() != copyJob2._sourceInfos.size())
                errors.add("_sourceInfos size");
        }
        else
        {
            errors.add("Expected job2 to be CopyFileRootPipelineJob");
        }
        return errors;
    }

    public static class TestCase extends PipelineJob.TestSerialization
    {
        private static Logger LOG = LogManager.getLogger(CopyFileRootPipelineJob.class);

        @Test
        public void testSerialize()
        {
            User user = TestContext.get().getUser();
            Container container = ContainerManager.getRoot();
            PipeRoot pipeRoot = PipelineService.get().getPipelineRootSetting(container);
            CopyFileRootPipelineJob job = new CopyFileRootPipelineJob(container, user, Collections.emptyList(), pipeRoot, AdminController.MigrateFilesOption.leave);
            job.getActionSet().add(new RecordedAction("foo"));
            testSerialize(job, LOG);
        }
    }
}
