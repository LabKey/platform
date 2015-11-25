package org.labkey.pipeline.mule;

import org.labkey.api.pipeline.AbstractRemoteExecutionEngineConfig;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RemoteExecutionEngine;

/**
 * Created by: jeckels
 * Date: 11/18/15
 */
public class DummyRemoteExecutionEngine implements RemoteExecutionEngine
{
    public static final String TYPE = "Dummy!!!";
    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public void submitJob(PipelineJob job) throws PipelineJobException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getStatus(String jobId) throws PipelineJobException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cancelJob(String jobId) throws PipelineJobException
    {
        throw new UnsupportedOperationException();
    }

    public static class DummyConfig extends AbstractRemoteExecutionEngineConfig
    {
        public DummyConfig()
        {
            super(TYPE, "Nowhere");
        }
    }

}
