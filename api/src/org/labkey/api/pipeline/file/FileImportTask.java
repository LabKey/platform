/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.api.pipeline.file;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.cmd.FileImportTaskFactorySettings;
import org.labkey.api.util.FileType;

import java.util.Collections;
import java.util.List;

/**
 * User: tgaluhn
 * Date: 7/26/2016
 *
 * For pipeline which imports files with no external processing. Add all selected input files to the RecordedActionSet
 * to then be imported in a XarGeneratorTask
 */
public class FileImportTask extends PipelineJob.Task<FileImportTask.Factory>
{
    private static final String ROLE = "File Import";

    private FileImportTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @NotNull
    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        RecordedActionSet records = new RecordedActionSet();
        getJob().getJobSupport(FileAnalysisJobSupport.class).getInputFiles().forEach(file ->
                {
                    RecordedAction action = new RecordedAction(_factory.getProtocolActionName());
                    action.addOutput(file, _factory.getProtocolActionName(), false);
                    records.add(action);
                });
        return records;
    }

    public static class Factory extends AbstractTaskFactory<FileImportTaskFactorySettings, FileImportTask.Factory>
    {
        public Factory()
        {
            super(FileImportTask.class);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new FileImportTask(this, job);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.singletonList(getProtocolActionName());
        }

        @Override
        public String getStatusName()
        {
            return "IMPORT FILE";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }

        public String getProtocolActionName()
        {
            return getId().getName() == null ? ROLE : getId().getName();
        }
    }
}
