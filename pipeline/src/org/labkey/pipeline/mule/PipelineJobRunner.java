/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
package org.labkey.pipeline.mule;

import org.labkey.api.data.DbScope;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.QueryService;

/**
 * <code>PipelineJobRunner</code> is a Mule object for running a <code>PipelineJob</code>
 * through as many states as possible on the current machine.  It assumes that the
 * current machine has accurate knowledge of the <code>TaskPipeline</code>.
 *
 * @author brendanx
 */
public class PipelineJobRunner
{
    public void run(PipelineJob job)
    {
        try
        {
            // Set centrally to avoid needing to set in each job. See PipelineQueueImpl for equivalent functionality
            // when running through Enterprise Pipeline
            QueryService.get().setEnvironment(job);
            job.run();
        }
        finally
        {
            QueryService.get().clearEnvironment();
            DbScope.finishedWithThread();
        }
    }
}
