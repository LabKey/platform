/*
 * Copyright (c) 2015-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.pipeline.mule.test;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractRemoteExecutionEngineConfig;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.pipeline.api.PipelineStatusFileImpl;
import org.labkey.pipeline.api.PipelineStatusManager;

import java.util.Collection;

/**
 * Created by: jeckels
 * Date: 11/18/15
 */
public class DummyRemoteExecutionEngine implements RemoteExecutionEngine<DummyRemoteExecutionEngine.DummyConfig>
{
    public static final String TYPE = "Dummy!!!";
    @NotNull
    @Override
    public String getType()
    {
        return TYPE;
    }

    private int _submitCount = 0;
    private int _statusCount = 0;
    private int _cancelCount = 0;

    @Override
    public void submitJob(@NotNull PipelineJob job) throws PipelineJobException
    {
        _submitCount++;
    }

    @Override
    public void updateStatusForJobs(@NotNull Collection<String> jobIds) throws PipelineJobException
    {
        for (Object jobId : jobIds)
        {
            _statusCount++;

            PipelineStatusFileImpl sf = (PipelineStatusFileImpl)PipelineService.get().getStatusFile((String)jobId);
            if (sf != null)
            {
                sf.setStatus("UNKNOWN");
                PipelineStatusManager.updateStatusFile(sf);
            }
        }
    }

    @Override
    public void cancelJob(@NotNull String jobId) throws PipelineJobException
    {
        _cancelCount++;
        PipelineStatusFileImpl file = PipelineStatusManager.getJobStatusFile(jobId);
        if (file != null)
        {
            file.setStatus(PipelineJob.TaskStatus.cancelled.toString());
            PipelineStatusManager.updateStatusFile(file);
        }
    }

    public int getSubmitCount()
    {
        return _submitCount;
    }

    public int getStatusCount()
    {
        return _statusCount;
    }

    public int getCancelCount()
    {
        return _cancelCount;
    }

    @Override
    @NotNull
    public DummyConfig getConfig()
    {
        return new DummyConfig();
    }

    public static class DummyConfig extends AbstractRemoteExecutionEngineConfig
    {
        public static final String LOCATION = "dummylocation";

        public DummyConfig()
        {
            super(TYPE, LOCATION);
        }
    }

}
