package org.labkey.study.pipeline;

import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyReloadSource;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.FileType;

import java.util.Collections;
import java.util.List;

/**
 * Created by klum on 2/9/2015.
 */
public class StudyReloadSourceTask extends PipelineJob.Task<StudyReloadSourceTask.Factory>
{
    private StudyReloadSourceTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        StudyReloadSourceJobSupport support = job.getJobSupport(StudyReloadSourceJobSupport.class);
        String studyReloadSource = support.getStudyReloadSource();
        StudyReloadSource reloadSource = StudyService.get().getStudyReloadSource(studyReloadSource);

        if (reloadSource != null)
        {
            PipeRoot root = PipelineService.get().findPipelineRoot(job.getContainer());
            if (root != null)
            {
                Study study = StudyService.get().getStudy(job.getContainer());
                reloadSource.generateReloadSource(job, study);

                return new RecordedActionSet();
            }
        }

        throw new PipelineJobException("Failed to locate a study reload source implementation for the name: " + studyReloadSource);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(StudyReloadSourceTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new StudyReloadSourceTask(this, job);
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return "GENERATE STUDY RELOAD SOURCE";
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
