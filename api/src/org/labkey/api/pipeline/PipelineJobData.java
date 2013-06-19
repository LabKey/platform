/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.api.pipeline;

import java.util.List;
import java.util.ArrayList;

public class PipelineJobData
{
    private List<PipelineJob> _running;
    private List<PipelineJob> _pending;

    public PipelineJobData()
    {
        _running = new ArrayList<>();
        _pending = new ArrayList<>();
    }

    public List<PipelineJob> getRunningJobs()
    {
        return _running;
    }

    public void addRunningJob(PipelineJob job)
    {
        _running.add(job);
    }

    public List<PipelineJob> getPendingJobs()
    {
        return _pending;
    }

    public void addPendingJob(PipelineJob job)
    {
        _pending.add(job);
    }
}
