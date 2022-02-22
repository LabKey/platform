package org.labkey.specimen.pipeline;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.PipelineJobLoggerGetter;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.specimen.importer.AbstractSpecimenTask;
import org.labkey.specimen.importer.AbstractSpecimenTaskFactory;
import org.labkey.api.study.importer.SimpleStudyImportContext;
import org.labkey.api.util.DateUtil;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

// Registered by the specimen module (specimenContext.xml), so specimen module is always present when this code is invoked.
public class FileAnalysisSpecimenTask extends AbstractSpecimenTask<FileAnalysisSpecimenTask.Factory>
{
    public static final String MERGE_SPECIMEN = "mergeSpecimen";

    public FileAnalysisSpecimenTask(FileAnalysisSpecimenTask.Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    protected Path getSpecimenFile(PipelineJob job)
    {
        FileAnalysisJobSupport support = job.getJobSupport(FileAnalysisJobSupport.class);
        List<Path> paths = support.getInputFilePaths();
        // there should only be a single file associated with this task
        assert paths.size() == 1;
        return paths.get(0);
    }

    @Override
    protected SimpleStudyImportContext getImportContext(PipelineJob job)
    {
        return new SimpleStudyImportContext(job.getUser(), job.getContainer(), null, null, new PipelineJobLoggerGetter(job), null);
    }

    @Override
    protected boolean isMerge()
    {
        FileAnalysisJobSupport support = getJob().getJobSupport(FileAnalysisJobSupport.class);
        boolean isMerge = false;

        if (support.getParameters().containsKey(MERGE_SPECIMEN))
            isMerge = BooleanUtils.toBoolean(support.getParameters().get(MERGE_SPECIMEN));

        getJob().getLogger().info("Specimen merge option is set to : " + isMerge);
        return isMerge;
    }

    @Override
    public AbstractSpecimenTask.ImportHelper getImportHelper()
    {
        return new ImportHelper();
    }

    private static class ImportHelper extends DefaultImportHelper
    {
        private Path _tempDir;

        @Override
        public VirtualFile getSpecimenDir(PipelineJob job, SimpleStudyImportContext ctx, @Nullable Path inputFile) throws IOException, ImportException, PipelineJobException
        {
            if (inputFile != null)
            {
                try (TikaInputStream is = TikaInputStream.get(Files.newInputStream(inputFile)))
                {
                    // determine the type of file being imported
                    DefaultDetector detector = new DefaultDetector();
                    MediaType type = detector.detect(is, new Metadata());

                    if (MediaType.APPLICATION_ZIP.equals(type))
                    {
                        ctx.getLogger().info("Specimen archive detected");
                        return super.getSpecimenDir(job, ctx, inputFile);
                    }
                    else if (MediaType.TEXT_PLAIN.equals(type))
                    {
                        // this is a loose file that has already been extracted from the archive
                        // copy the single file to a temp directory and process it there
                        //
                        ctx.getLogger().info("Single specimen file detected, moving to a temp folder for processing.");
                        String tempDirName = DateUtil.formatDateTime(new Date(), "yyMMddHHmmssSSS");
                        _tempDir = inputFile.getParent().resolve(tempDirName);
                        Files.copy(inputFile, _tempDir);

                        return new FileSystemFile(_tempDir);
                    }
                    else
                        throw new RuntimeException("Unknown specimen file type uploaded");
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
            return null;
        }

        @Override
        public void afterImport(SimpleStudyImportContext ctx)
        {
            super.afterImport(ctx);

            if (_tempDir != null)
                delete(_tempDir, ctx);
        }
    }

    public static class Factory extends AbstractSpecimenTaskFactory<Factory>
    {
        public Factory()
        {
            super(FileAnalysisSpecimenTask.class);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new FileAnalysisSpecimenTask(this, job);
        }
    }
}
