/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

package org.labkey.specimen.pipeline;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.specimen.importer.AbstractSpecimenTask;
import org.labkey.specimen.importer.AbstractSpecimenTaskFactory;

/*
* User: adam
* Date: Sep 1, 2009
* Time: 4:37:31 PM
*/

// This task is used to import specimen archives directly via the pipeline ui. SpecimenBatch is the associated pipeline job.
// Registered by the specimen module (specimenContext.xml), so specimen module is always present when this code is invoked.
public class StandaloneSpecimenTask extends AbstractSpecimenTask<StandaloneSpecimenTask.Factory>
{
    private StandaloneSpecimenTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSpecimenTaskFactory<Factory>
    {
        public Factory()
        {
            super(StandaloneSpecimenTask.class);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new StandaloneSpecimenTask(this, job);
        }
    }
}
