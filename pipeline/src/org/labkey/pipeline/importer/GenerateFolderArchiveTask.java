package org.labkey.pipeline.importer;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJob.TaskStatus;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.study.FolderArchiveSource;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.FileType;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class GenerateFolderArchiveTask extends PipelineJob.Task<GenerateFolderArchiveTask.Factory>
{
    private GenerateFolderArchiveTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    public @NotNull RecordedActionSet run()
    {
        PipelineJob job = getJob();
        String archiveSourceName = job.getJobSupport(FolderJobSupport.class).getFolderArchiveSourceName();
        StudyService ss = StudyService.get();

        if (null == ss)
        {
            job.setStatus(TaskStatus.error, "StudyService is not available");
        }
        else
        {
            Study study = ss.getStudy(job.getContainer());

            if (null == study)
            {
                job.setStatus(TaskStatus.error, "No study is available in this folder");
            }
            else
            {
                FolderArchiveSource folderArchiveSource = PipelineService.get().getFolderArchiveSource(archiveSourceName);

                if (null == folderArchiveSource)
                {
                    job.setStatus(TaskStatus.error, "Folder archive source named \"" + archiveSourceName + "\" is not registered");
                }
                else
                {
                    job.info("Generating folder archive");
                    folderArchiveSource.generateFolderArchive(job, study);
                    job.info("Successfully generated folder archive");
                }
            }
        }

        return new RecordedActionSet();
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(GenerateFolderArchiveTask.class);
        }

        @Override
        public PipelineJob.Task<Factory> createTask(PipelineJob job)
        {
            return new GenerateFolderArchiveTask(this, job);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        @Override
        public String getStatusName()
        {
            return "GENERATE ARCHIVE";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }

        @Override
        public boolean isParticipant(PipelineJob job) throws IOException
        {
            // I get to participate only if a folder archive source name has been provided
            return super.isParticipant(job) && job.getJobSupport(FolderJobSupport.class).getFolderArchiveSourceName() != null;
        }
    }
}
