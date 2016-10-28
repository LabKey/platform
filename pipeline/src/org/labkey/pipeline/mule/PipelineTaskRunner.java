/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;

import java.io.IOException;

/**
 * <code>PipelineTaskRunner</code> is a Mule object for running the active
 * task on a <code>PipelineJob</code>.
 *
 * @author brendanx
 */
public class PipelineTaskRunner
{
    public void run(PipelineJob job)
    {
        try
        {
            job.runActiveTask();
        }
        catch (IOException | PipelineJobException e)
        {
            job.error(e.getMessage(), e);
        }
        catch (CancelledException ignored)
        {
            // Don't need to do anything, job is already marked as being cancelled in the database
        }
    }
}