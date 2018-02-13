package org.labkey.study.importer;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StudyReloadTask extends PipelineJob.Task<StudyReloadTask.Factory>
{

    private StudyReloadTask(StudyReloadTask.Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @NotNull
    @Override
    public RecordedActionSet run()
    {
        PipelineJob job = getJob();
        FileAnalysisJobSupport support = job.getJobSupport(FileAnalysisJobSupport.class);
        Map<String, String> params = support.getParameters();
        StudyReload.ReloadTask reloadTask = new StudyReload.ReloadTask();
        String containerId = getJob().getContainer().getId();

        try
        {
            ImportOptions options = new ImportOptions(containerId, job.getUser().getUserId());
            StudyReload.ReloadStatus status = reloadTask.attemptTriggeredReload(options, "a configured study reload filewatcher");
            job.setStatus(status.getMessage());
        }
        catch (ImportException ie)
        {
            Container c = ContainerManager.getForId(containerId);
            String message = null != c ? " in folder " + c.getPath() : "";

            getJob().getLogger().error("Study reload failed" + message, ie);
        }
        catch (Throwable t)
        {
            ExceptionUtil.logExceptionToMothership(null, t);
            getJob().getLogger().error("Study reload failed" + t.getMessage());
        }

        return new RecordedActionSet();
    }


    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(StudyReloadTask.class);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new StudyReloadTask(this, job);
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
            return "RELOAD STUDY";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}

