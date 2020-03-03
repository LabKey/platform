package org.labkey.study.pipeline;

import org.apache.commons.io.FileUtils;
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
import org.labkey.api.util.DateUtil;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.importer.StudyImportContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;

public class FileAnalysisSpecimenTask extends AbstractSpecimenTask<FileAnalysisSpecimenTask.Factory>
{
    public static final String MERGE_SPECIMEN = "mergeSpecimen";

    public FileAnalysisSpecimenTask(FileAnalysisSpecimenTask.Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    protected File getSpecimenFile(PipelineJob job) throws Exception
    {
        FileAnalysisJobSupport support = job.getJobSupport(FileAnalysisJobSupport.class);

        // there should only be a single file associated with this task
        assert support.getInputFiles().size() == 1;
        return support.getInputFiles().get(0);
    }

    @Override
    StudyImportContext getImportContext(PipelineJob job)
    {
        return new StudyImportContext(job.getUser(), job.getContainer(), null, new PipelineJobLoggerGetter(job));
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
        private File _tempDir;

        @Override
        public VirtualFile getSpecimenDir(PipelineJob job, StudyImportContext ctx, @Nullable File inputFile) throws IOException, ImportException, PipelineJobException
        {
            if (inputFile != null)
            {
                try (TikaInputStream is = TikaInputStream.get(new FileInputStream(inputFile)))
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
                        _tempDir = new File(inputFile.getParentFile(), tempDirName);
                        FileUtils.copyToDirectory(inputFile, _tempDir);

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
        public void afterImport(StudyImportContext ctx)
        {
            super.afterImport(ctx);

            if (_tempDir != null)
                delete(_tempDir, ctx);
        }
    }

    public static class Factory extends AbstractSpecimenTaskFactory<FileAnalysisSpecimenTask.Factory>
    {
        public Factory()
        {
            super(FileAnalysisSpecimenTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new FileAnalysisSpecimenTask(this, job);
        }
    }
}
