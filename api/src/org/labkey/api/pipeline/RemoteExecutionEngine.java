package org.labkey.api.pipeline;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;

/**
 * Created by: jeckels
 * Date: 11/16/15
 */
public interface RemoteExecutionEngine
{
    String getType();
    void submitJob(PipelineJob job) throws PipelineJobException;
    String getStatus(String jobId) throws PipelineJobException;
    void cancelJob(String jobId) throws PipelineJobException;
}
