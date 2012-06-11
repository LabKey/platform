/*
 * Copyright (c) 2009-2011 LabKey Corporation
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

package org.labkey.study.pipeline;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/*
* This task is used to transform SampleMinded specimen files (.xlsx) into our standard specimen import format, a
* a file with extension ".specimens" which is a ZIP file.
* User: jeckels
*/

public class SampleMindedTransformTask extends PipelineJob.Task<SampleMindedTransformTask.Factory>
{
    private static final String TRANSFORM_PROTOCOL_ACTION_NAME = "SampleMindedTransform";

    private SampleMindedTransformTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        RecordedAction action = new RecordedAction(TRANSFORM_PROTOCOL_ACTION_NAME);
        File input = getJob().getJobSupport(SpecimenJobSupport.class).getInputFile();
        action.addInput(input, "SampleMindedExport");
        File output = getJob().getJobSupport(SpecimenJobSupport.class).getSpecimenArchive();
        action.addOutput(output, "SpecimenArchive", false);

        // Do the transform

        return new RecordedActionSet(action);
    }

    public static class Factory extends AbstractSpecimenTaskFactory<Factory>
    {
        public Factory()
        {
            super(SampleMindedTransformTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new SampleMindedTransformTask(this, job);
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.singletonList(TRANSFORM_PROTOCOL_ACTION_NAME);
        }

        @Override
        public boolean isParticipant(PipelineJob job) throws IOException
        {
            // Only run this task if the input is a SampleMinded export
            File input = job.getJobSupport(SpecimenJobSupport.class).getInputFile();
            return SpecimenBatch.SAMPLE_MINDED_FILE_TYPE.isType(input);
        }

        @Override
        public String getStatusName()
        {
            return "SAMPLEMINDED TRANSFORM";
        }
    }
}
