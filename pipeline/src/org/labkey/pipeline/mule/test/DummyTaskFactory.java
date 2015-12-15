package org.labkey.pipeline.mule.test;

import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskFactorySettings;
import org.labkey.api.util.FileType;

import java.util.Collections;
import java.util.List;

/**
 * Created by: jeckels
 * Date: 12/14/15
 */
public class DummyTaskFactory extends AbstractTaskFactory
{
    public DummyTaskFactory()
    {
        super(DummyTaskFactory.class);
        setLocation(DummyRemoteExecutionEngine.DummyConfig.LOCATION);
    }

    @Override
    public PipelineJob.Task createTask(PipelineJob job)
    {
        return new DummyTask(job);
    }

    @Override
    public TaskFactory cloneAndConfigure(TaskFactorySettings settings) throws CloneNotSupportedException
    {
        return this;
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
        return "DUMMY";
    }

    @Override
    public boolean isJobComplete(PipelineJob job)
    {
        return false;
    }
}
