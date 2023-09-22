package org.labkey.api.pipeline;

public interface PipelineMXBean
{
    /** @return count of jobs in the queue, either running or waiting */
    int getPipelineQueueSize();
}
