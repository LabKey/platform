/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

package org.labkey.study.importer;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.util.FileType;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.writer.StudySerializationRegistryImpl;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/*
* User: adam
* Date: Aug 31, 2009
* Time: 9:12:22 AM
*/
public class StudyImportFinalTask extends PipelineJob.Task<StudyImportFinalTask.Factory>
{
    private StudyImportFinalTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        StudyJobSupport support = job.getJobSupport(StudyJobSupport.class);

        doImport(job, support.getImportContext(), support.getSpringErrors());

        return new RecordedActionSet();
    }

    public static void doImport(PipelineJob job, StudyImportContext ctx, BindException errors) throws PipelineJobException
    {
        try
        {
            Collection<InternalStudyImporter> internalImporters = new LinkedList<>();

            // Dataset and Specimen upload jobs delete "unused" participants, so we need to defer setting participant
            // cohorts until the end of upload.
            internalImporters.add(new CohortImporter());

            // Can't assign visits or datasets to cohorts until the cohorts are created
            internalImporters.add(new VisitCohortAssigner());
            internalImporters.add(new DatasetCohortAssigner());

            // Can't setup specimen request actors until the locations have been created
            internalImporters.add(new SpecimenSettingsImporter());

            internalImporters.add(new ParticipantCommentImporter());
            internalImporters.add(new ParticipantGroupImporter());
            internalImporters.add(new ProtocolDocumentImporter());
            internalImporters.add(new ViewCategoryImporter());
            internalImporters.add(new StudyViewsImporter());

            // TreatmentVisitMap needs to import after cohort info is loaded (issue 19947)
            internalImporters.add(new TreatmentVisitMapImporter());

            VirtualFile vf = ctx.getRoot();
            for (InternalStudyImporter importer : internalImporters)
            {
                if (job != null)
                    job.setStatus("IMPORT " + importer.getDescription());

                importer.process(ctx, vf, errors);
            }

            // the registered study importers only need to be called in the Import Study case (not for Import Folder)
            if (job != null && (job instanceof StudyImporter))
            {
                Collection<FolderImporter> externalStudyImporters = StudySerializationRegistryImpl.get().getRegisteredStudyImporters();
                for (FolderImporter importer : externalStudyImporters)
                {
                    if (ctx.isDataTypeSelected(importer.getDataType()))
                        importer.process(job, ctx, vf);
                }

                List<PipelineJobWarning> warnings = new ArrayList<>();
                for (FolderImporter importer : externalStudyImporters)
                {
                    if (ctx.isDataTypeSelected(importer.getDataType()))
                    {
                        job.setStatus("POST-PROCESS " + importer.getDescription());
                        Collection<PipelineJobWarning> importerWarnings = importer.postProcess(ctx, vf);
                        warnings.addAll(importerWarnings);
                    }
                }
                //TODO: capture warnings in the pipeline job and make a distinction between success & success with warnings
                //for now, just fail the job if there were any warnings. The warnings will
                //have already been written to the log
                if (!warnings.isEmpty())
                    job.error("Warnings were generated by the study importers!");
            }
        }
        catch (Exception e)
        {
            throw new PipelineJobException(e);
        }
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(StudyImportFinalTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new StudyImportFinalTask(this, job);
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
            return "STUDY IMPORT";    // TODO: RELOAD?
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}