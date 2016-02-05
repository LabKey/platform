/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.importer.StudyJobSupport;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Created by klum on 5/24/2014.
 */
public class SpecimenReloadTask extends PipelineJob.Task<SpecimenReloadTask.Factory>
{
    private SpecimenReloadTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @NotNull
    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        SpecimenReloadJobSupport support = job.getJobSupport(SpecimenReloadJobSupport.class);
        String specimenTransformName = support.getSpecimenTransform();
        SpecimenTransform transform = SpecimenService.get().getSpecimenTransform(specimenTransformName);

        if (transform != null)
        {
            PipeRoot root = PipelineService.get().findPipelineRoot(job.getContainer());
            if (root != null)
            {
                File archive = new File(root.getRootPath(), FileUtil.makeFileNameWithTimestamp("specimen_reload", transform.getFileType().getDefaultSuffix()));

                transform.importFromExternalSource(job, support.getExternalImportConfig(), archive);

                support.setSpecimenArchive(archive);
                return new RecordedActionSet();
            }
        }

        throw new PipelineJobException("Failed to locate a specimen transform implementation for the name: " + specimenTransformName);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(SpecimenReloadTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new SpecimenReloadTask(this, job);
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
            return "RELOAD SPECIMEN";
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
