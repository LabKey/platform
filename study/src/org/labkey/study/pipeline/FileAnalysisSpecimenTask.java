package org.labkey.study.pipeline;

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.PipelineJobLoggerGetter;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.FileUtil;
import org.labkey.study.importer.StudyImportContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileAnalysisSpecimenTask extends AbstractSpecimenTask<FileAnalysisSpecimenTask.Factory>
{
    public FileAnalysisSpecimenTask(FileAnalysisSpecimenTask.Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    protected File getSpecimenArchive(PipelineJob job) throws Exception
    {
        FileAnalysisJobSupport support = job.getJobSupport(FileAnalysisJobSupport.class);

        // there should only be a single file associated with this task
        assert support.getInputFiles().size() == 1;

        File file = support.getInputFiles().get(0);

        // try to detect the file type
        try (TikaInputStream is = TikaInputStream.get(new FileInputStream(file)))
        {
            DefaultDetector detector = new DefaultDetector();
            MediaType type = detector.detect(is, new Metadata());

            boolean isText = MediaType.TEXT_PLAIN.equals(type);
            boolean isZip = MediaType.APPLICATION_ZIP.equals(type);

            type.getSubtype();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        return file;
    }

    @Override
    StudyImportContext getImportContext(PipelineJob job)
    {
        return new StudyImportContext(job.getUser(), job.getContainer(), null, new PipelineJobLoggerGetter(job));
    }

    @Override
    protected boolean isMerge()
    {
        // TODO : wire through the custom parameter
        return false;
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
