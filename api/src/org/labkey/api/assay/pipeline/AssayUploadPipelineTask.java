/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.api.assay.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.util.FileType;

import java.util.Collections;
import java.util.List;

/**
 * A tiny task definition that allows configuration of the execution location of this work. It defers back to
 * AssayUploadPipelineJob to do the real work.
 */
public class AssayUploadPipelineTask extends PipelineJob.Task<AssayUploadPipelineTask.Factory>
{
    public AssayUploadPipelineTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    public @NotNull RecordedActionSet run()
    {
        ((AssayUploadPipelineJob<?>)getJob()).doWork();
        return new RecordedActionSet();
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        private String _executionLocation;

        public Factory()
        {
            super(AssayUploadPipelineTask.class);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public void setExecutionLocation(String executionLocation)
        {
            _executionLocation = executionLocation;
        }

        @Override
        public String getExecutionLocation()
        {
            return _executionLocation == null ? super.getExecutionLocation() : _executionLocation;
        }

        @Override
        public String getStatusName()
        {
            return "Assay upload";
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        @Override
        public AssayUploadPipelineTask createTask(PipelineJob job)
        {
            return new AssayUploadPipelineTask(this, job);
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
