package org.labkey.api.assay.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.util.FileType;

import java.util.Collections;
import java.util.List;

/**
 * A tiny task definition that allows configuration of the execution location of this work. It defers back to
 * AssayUploadPipelineJob to do the real work.
 */
public class AssayUploadPipelineTask extends PipelineJob.Task<AssayUploadPipelineTask.Factory>
{
    public AssayUploadPipelineTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    public @NotNull RecordedActionSet run() throws PipelineJobException
    {
        ((AssayUploadPipelineJob<?>)getJob()).doWork();
        return new RecordedActionSet();
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        private String _executionLocation;

        public Factory()
        {
            super(AssayUploadPipelineTask.class);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public void setExecutionLocation(String executionLocation)
        {
            _executionLocation = executionLocation;
        }

        @Override
        public String getExecutionLocation()
        {
            return _executionLocation == null ? super.getExecutionLocation() : _executionLocation;
        }

        @Override
        public String getStatusName()
        {
            return "Assay upload";
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        @Override
        public AssayUploadPipelineTask createTask(PipelineJob job)
        {
            return new AssayUploadPipelineTask(this, job);
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
