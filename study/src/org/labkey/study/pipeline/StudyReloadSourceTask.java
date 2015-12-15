/*
 * Copyright (c) 2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyReloadSource;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.FileType;

import java.util.Collections;
import java.util.List;

/**
 * Created by klum on 2/9/2015.
 */
public class StudyReloadSourceTask extends PipelineJob.Task<StudyReloadSourceTask.Factory>
{
    private StudyReloadSourceTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @NotNull
    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        StudyReloadSourceJobSupport support = job.getJobSupport(StudyReloadSourceJobSupport.class);
        String studyReloadSource = support.getStudyReloadSource();
        StudyReloadSource reloadSource = StudyService.get().getStudyReloadSource(studyReloadSource);

        if (reloadSource != null)
        {
            PipeRoot root = PipelineService.get().findPipelineRoot(job.getContainer());
            if (root != null)
            {
                Study study = StudyService.get().getStudy(job.getContainer());
                reloadSource.generateReloadSource(job, study);

                return new RecordedActionSet();
            }
        }

        throw new PipelineJobException("Failed to locate a study reload source implementation for the name: " + studyReloadSource);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(StudyReloadSourceTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new StudyReloadSourceTask(this, job);
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return "GENERATE STUDY RELOAD SOURCE";
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
