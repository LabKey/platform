package org.labkey.pipeline.mule.test;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;

/**
 * Created by: jeckels
 * Date: 12/14/15
 */
public class DummyTask extends PipelineJob.Task
{
    public DummyTask(PipelineJob job)
    {
        super(null, job);
    }

    @NotNull
    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        return new RecordedActionSet();
    }
}
