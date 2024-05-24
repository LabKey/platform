package org.labkey.api.assay;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.FileUtil;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public class AssayResultsFileWriter<ContextType extends AssayRunUploadContext<? extends AssayProvider>> extends AssayFileWriter<ContextType>
{
    private static final String FILE_INPUT_NAME = "resultsFile";

    ExpProtocol _protocol;
    ExpRun _run;
    String _pipelineJobGUID;

    public AssayResultsFileWriter(ExpProtocol protocol, @Nullable ExpRun run, @Nullable String pipelineJobGUID)
    {
        _protocol = protocol;
        _run = run;
        _pipelineJobGUID = pipelineJobGUID;
    }

    public static String getRunResultsFileDir(ExpRun run)
    {
        return "AssayId_" + run.getProtocol().getRowId() + File.separator + "RunId_" + run.getRowId();
    }

    public static String getPipelineResultsFileDir(ExpProtocol protocol, String pipelineJobGUID)
    {
        return "AssayId_" + protocol.getRowId() + File.separator + "Job_" + pipelineJobGUID;
    }

    public static Path getAssayFilesDirectoryPath(ExpRun run)
    {
        String resultsFilePath = getRunResultsFileDir(run);
        return getAssayFilesDirectoryPath(run.getContainer(), resultsFilePath);
    }

    public static Path getAssayFilesDirectoryPath(Container container, String dirName)
    {
        Path root = FileContentService.get().getFileRootPath(container, FileContentService.ContentType.assayfiles);
        return root != null ? root.resolve(dirName) : null;
    }

    public static File ensureAssayFilesDirectoryPath(Container container, String dirName) throws ExperimentException
    {
        Path dir = getAssayFilesDirectoryPath(container, dirName);
        if (null != dir && !Files.exists(dir))
        {
            try
            {
                dir = FileUtil.createDirectories(dir);
            }
            catch (IOException e)
            {
                throw new ExperimentException("Could not create directory: " + dir);
            }
        }

        if (null != dir && !FileUtil.hasCloudScheme(dir))
            return dir.toFile();
        return null;
    }

    @Override
    protected File getFileTargetDir(ContextType context) throws ExperimentException
    {
        String dir = getFileTargetDirName();
        return ensureAssayFilesDirectoryPath(context.getContainer(), dir);
    }

    private String getFileTargetDirName()
    {
        return _run != null ? getRunResultsFileDir(_run) : getPipelineResultsFileDir(_protocol, _pipelineJobGUID);
    }

    public void cleanupPostedFiles(Container container) throws ExperimentException
    {
        try
        {
            Path targetDir = getAssayFilesDirectoryPath(container, getFileTargetDirName());
            if (targetDir != null && Files.exists(targetDir) && Files.isDirectory(targetDir))
                FileUtil.deleteDir(targetDir);
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
    }

    @Override
    public String getFileName(MultipartFile file)
    {
        // since results file upload can support uploading directories of files, some browsers retain path info
        String filename = file.getOriginalFilename();
        if (null == filename)
            return null;
        int slash = Math.max(filename.lastIndexOf("/"), filename.lastIndexOf("\\"));
        filename = filename.substring(slash+1);
        return filename;
    }

    public Map<String, File> savePostedFiles(ContextType context) throws ExperimentException, IOException
    {
        // if the file results dir already exists, delete it (clean up from previous failed import)
        Container container = context.getContainer();
        cleanupPostedFiles(container);

        // if this is a background import and the files have been stashed in the pipeline results dir, move them to the assay results dir
        if (_run != null && _run.getJobId() != null)
        {
            String pipelineJobGUID = PipelineService.get().getJobGUID(context.getUser(), container, _run.getJobId());
            Path pipelineResultsDir = pipelineJobGUID != null ? getAssayFilesDirectoryPath(container, getPipelineResultsFileDir(_protocol, pipelineJobGUID)) : null;
            if (pipelineResultsDir != null && Files.exists(pipelineResultsDir))
            {
                Path targetDir = getAssayFilesDirectoryPath(container, getFileTargetDirName());
                FileUtils.moveDirectoryToDirectory(pipelineResultsDir.toFile(), targetDir.toFile(), true); // TODO will .toFile() work here for cloud?
                return Collections.emptyMap();
            }
        }

        Map<String, File> files = super.savePostedFiles(context, Collections.singleton(FILE_INPUT_NAME), true);

        // if no files were written to the targetDir, delete the empty directory
        if (files.isEmpty())
            cleanupPostedFiles(container);

        return files;
    }
}