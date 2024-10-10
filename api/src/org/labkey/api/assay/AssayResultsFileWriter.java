package org.labkey.api.assay;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.FileUtil;
import org.labkey.vfs.FileLike;
import org.labkey.vfs.FileSystemLike;
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

    public static String getRunResultsFileDir(ExpProtocol protocol, ExpRun run)
    {
        return "AssayId_" + protocol.getRowId() + File.separator + "RunId_" + run.getRowId();
    }

    public static String getPipelineResultsFileDir(ExpProtocol protocol, String pipelineJobGUID)
    {
        return "AssayId_" + protocol.getRowId() + File.separator + "Job_" + pipelineJobGUID;
    }

    public static Path getAssayFilesDirectoryPath(ExpRun run)
    {
        String resultsFilePath = getRunResultsFileDir(run.getProtocol(), run);
        return getAssayFilesDirectoryPath(run.getContainer(), resultsFilePath);
    }

    public static Path getAssayFilesDirectoryPath(Container container, String dirName)
    {
        Path root = FileContentService.get().getFileRootPath(container, FileContentService.ContentType.assayfiles);
        return root != null ? root.resolve(dirName) : null;
    }

    public static FileLike ensureAssayFilesDirectoryPath(Container container, String dirName) throws ExperimentException
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
            return FileSystemLike.wrapFile(dir);
        return null;
    }

    // since results file upload can support uploading directories of files, some browsers retain path information
    // so we strip off the path and just return the file name
    public static String getFileNameWithoutPath(String originalName)
    {
        if (null == originalName)
            return null;
        int slash = Math.max(originalName.lastIndexOf("/"), originalName.lastIndexOf("\\"));
        return originalName.substring(slash+1);
    }

    @Override
    protected FileLike getFileTargetDir(ContextType context) throws ExperimentException
    {
        String dir = getFileTargetDirName();
        return ensureAssayFilesDirectoryPath(context.getContainer(), dir);
    }

    private String getFileTargetDirName()
    {
        return _run != null ? getRunResultsFileDir(_protocol, _run) : getPipelineResultsFileDir(_protocol, _pipelineJobGUID);
    }

    public void cleanupPostedFiles(Container container, boolean deleteIfEmptyOnly) throws ExperimentException
    {
        try
        {
            Path targetDir = getAssayFilesDirectoryPath(container, getFileTargetDirName());
            if (targetDir != null && Files.exists(targetDir) && Files.isDirectory(targetDir))
            {
                if (!deleteIfEmptyOnly || FileUtils.isEmptyDirectory(targetDir.toFile())) // TODO refactor to support cloud-based storage which won't have a toFile()
                    FileUtil.deleteDir(targetDir);
            }
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
    }

    @Override
    public String getFileName(MultipartFile file)
    {
        String filename = file.getOriginalFilename();
        return getFileNameWithoutPath(filename);
    }

    public Map<String, FileLike> savePostedFiles(ContextType context) throws ExperimentException, IOException
    {
        // if the file results dir already exists, delete it (clean up from previous failed import)
        Container container = context.getContainer();
        cleanupPostedFiles(container, false);

        // if this is a background import and the files have been stashed in the pipeline results dir, move them to the assay results dir
        if (_run != null && _run.getJobId() != null)
        {
            String pipelineJobGUID = PipelineService.get().getJobGUID(context.getUser(), container, _run.getJobId());
            Path pipelineResultsDir = pipelineJobGUID != null ? getAssayFilesDirectoryPath(container, getPipelineResultsFileDir(_protocol, pipelineJobGUID)) : null;
            if (pipelineResultsDir != null && Files.exists(pipelineResultsDir))
            {
                Path targetDir = getAssayFilesDirectoryPath(container, getFileTargetDirName());
                FileUtils.moveDirectory(pipelineResultsDir.toFile(), targetDir.toFile()); // TODO refactor to support cloud-based storage which won't have a toFile()
                return Collections.emptyMap();
            }
        }

        Map<String, FileLike> files = super.savePostedFiles(context, Collections.singleton(FILE_INPUT_NAME), true, true);

        // if no files were written to the targetDir, delete the empty directory
        if (files.isEmpty())
            cleanupPostedFiles(container, true);

        return files;
    }

    public static class TestCase extends Assert
    {
        private Mockery _context;
        private ExpRun _run;
        private ExpProtocol _protocol;

        public TestCase()
        {
            _context = new Mockery();
            _context.setImposteriser(ClassImposteriser.INSTANCE);

            _protocol = _context.mock(ExpProtocol.class);
            _context.checking(new Expectations() {{
                allowing(_protocol).getRowId();
                will(returnValue(123));
            }});

            _run = _context.mock(ExpRun.class);
            _context.checking(new Expectations() {{
                allowing(_run).getProtocol();
                will(returnValue(_protocol));
            }});
            _context.checking(new Expectations() {{
                allowing(_run).getRowId();
                will(returnValue(456));
            }});
        }

        @Test
        public void testGetRunResultsFileDir()
        {
            String dir = getRunResultsFileDir(_protocol, _run);
            String[] tokens = dir.contains("/") ? dir.split("/") : dir.split("\\\\");
            assertEquals("AssayId_123", tokens[0]);
            assertEquals("RunId_456", tokens[1]);
        }

        @Test
        public void testGetPipelineResultsFileDir()
        {
            String dir = getPipelineResultsFileDir(_protocol, "789");
            String[] tokens = dir.contains("/") ? dir.split("/") : dir.split("\\\\");
            assertEquals("AssayId_123", tokens[0]);
            assertEquals("Job_789", tokens[1]);
        }

        @Test
        public void testGetFileNameWithoutPath()
        {
            assertNull(getFileNameWithoutPath(null));
            assertEquals("file.txt", getFileNameWithoutPath("C:\\path\\to\\file.txt"));
            assertEquals("file.txt", getFileNameWithoutPath("C:/path/to/file.txt"));
            assertEquals("file.txt", getFileNameWithoutPath("file.txt"));
        }
    }
}