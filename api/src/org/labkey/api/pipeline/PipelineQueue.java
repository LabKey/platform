package org.labkey.api.pipeline;

import org.labkey.api.data.Container;

import java.io.IOException;

public interface PipelineQueue
{
    PipelineJobData getJobData(Container c);
    void starting(PipelineJob job, Thread thread);
    void done(PipelineJob job);
    boolean cancelJob(Container c, int jobId);
    PipelineJob findJob(Container c, String statusFile);
    void addJob(PipelineJob job) throws IOException;
    void addJob(PipelineJob job, String initialState) throws IOException;
}
