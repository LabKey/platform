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
package org.labkey.pipeline.importer;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJob.TaskStatus;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.study.FolderArchiveSource;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.FileType;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class GenerateFolderArchiveTask extends PipelineJob.Task<GenerateFolderArchiveTask.Factory>
{
    private GenerateFolderArchiveTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    public @NotNull RecordedActionSet run()
    {
        PipelineJob job = getJob();
        String archiveSourceName = job.getJobSupport(FolderJobSupport.class).getFolderArchiveSourceName();
        StudyService ss = StudyService.get();

        if (null == ss)
        {
            job.setStatus(TaskStatus.error, "StudyService is not available");
        }
        else
        {
            Study study = ss.getStudy(job.getContainer());

            if (null == study)
            {
                job.setStatus(TaskStatus.error, "No study is available in this folder");
            }
            else
            {
                FolderArchiveSource folderArchiveSource = PipelineService.get().getFolderArchiveSource(archiveSourceName);

                if (null == folderArchiveSource)
                {
                    job.setStatus(TaskStatus.error, "Folder archive source named \"" + archiveSourceName + "\" is not registered");
                }
                else
                {
                    job.info("Generating folder archive");
                    folderArchiveSource.generateFolderArchive(job, study);
                    job.info("Successfully generated folder archive");
                }
            }
        }

        return new RecordedActionSet();
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(GenerateFolderArchiveTask.class);
        }

        @Override
        public PipelineJob.Task<Factory> createTask(PipelineJob job)
        {
            return new GenerateFolderArchiveTask(this, job);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        @Override
        public String getStatusName()
        {
            return "GENERATE ARCHIVE";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }

        @Override
        public boolean isParticipant(PipelineJob job) throws IOException
        {
            // I get to participate only if a folder archive source name has been provided
            return super.isParticipant(job) && job.getJobSupport(FolderJobSupport.class).getFolderArchiveSourceName() != null;
        }
    }
}
