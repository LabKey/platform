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
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.FileType;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;

import java.util.Collections;
import java.util.List;

/*
* User: adam
* Date: Aug 31, 2009
* Time: 9:12:22 AM
*/
public class StudyImportInitialTask extends PipelineJob.Task<StudyImportInitialTask.Factory>
{
    private StudyImportInitialTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    private static final int DELAY_INCREMENT = 10;

    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        StudyJobSupport support = job.getJobSupport(StudyJobSupport.class);
        StudyImportContext ctx = support.getImportContext();

        doImport(job, ctx, support.getSpringErrors(), support.getOriginalFilename());

        return new RecordedActionSet();
    }

    public static void doImport(PipelineJob job, StudyImportContext ctx, BindException errors, String originalFileName) throws PipelineJobException
    {
        SpecimenTablesTemplate previousTablesTemplate = null;
        try
        {
            StudyDocument.Study studyXml = ctx.getXml();

            if (ctx.getContainer().isDataspace() && !studyXml.getAllowDataspace())
            {
                throw new PipelineJobException("Can't import study into a Dataspace folder.");
            }

            // verify the archiveVersion
            double currVersion = ModuleLoader.getInstance().getCoreModule().getVersion();
            if (studyXml.isSetArchiveVersion() && studyXml.getArchiveVersion() > currVersion)
                throw new PipelineJobException("Can't import study archive. The archive version " + studyXml.getArchiveVersion() + " is newer than the server version " + currVersion + ".");

            // Check if a delay has been requested for testing purposes, to make it easier to cancel the job in a reliable way
            if (studyXml.isSetImportDelay() && studyXml.getImportDelay() > 0)
            {
                for (int i = 0; i < studyXml.getImportDelay(); i = i + DELAY_INCREMENT)
                {
                    if (job != null)
                        job.setStatus("Delaying import, waited " + i + " out of "+ studyXml.getImportDelay() + " second delay");
                    try
                    {
                        Thread.sleep(1000 * DELAY_INCREMENT);
                    }
                    catch (InterruptedException ignored) {}
                }
            }
            
            boolean hasSpecimenSchemasToImport = SpecimenSchemaImporter.containsSchemasToImport(ctx);

            if (hasSpecimenSchemasToImport)
            {
                // if specimen schemas will be imported, don't create optional specimen table fields
                previousTablesTemplate = StudySchema.getInstance().setSpecimenTablesTemplates(new SpecimenSchemaImporter.ImportTemplate());
            }

            // Create the study if it doesn't exist... otherwise, modify the existing properties
            StudyImpl study = StudyManager.getInstance().getStudy(ctx.getContainer());
            if (null == study)
            {
                ctx.getLogger().info("Loading study from " + originalFileName);
                ctx.getLogger().info("Creating study");

                // Create study
                StudyController.StudyPropertiesForm studyForm = new StudyController.StudyPropertiesForm();

                if (studyXml.isSetLabel())
                    studyForm.setLabel(studyXml.getLabel());

                if (studyXml.isSetTimepointType())
                    studyForm.setTimepointType(TimepointType.valueOf(studyXml.getTimepointType().toString()));
                else if (studyXml.isSetDateBased())
                    studyForm.setTimepointType(studyXml.getDateBased() ? TimepointType.DATE : TimepointType.VISIT);

                if (studyXml.isSetSecurityType())
                    studyForm.setSecurityType(SecurityType.valueOf(studyXml.getSecurityType().toString()));

                StudyController.createStudy(null, ctx.getContainer(), ctx.getUser(), studyForm);
            }
            else
            {
                ctx.getLogger().info("Reloading study from " + originalFileName);

                TimepointType timepointType = study.getTimepointType();
                if (studyXml.isSetTimepointType())
                    timepointType = TimepointType.valueOf(studyXml.getTimepointType().toString());
                else if (studyXml.isSetDateBased())
                    timepointType = studyXml.getDateBased() ? TimepointType.DATE : TimepointType.VISIT;

                if (study.getTimepointType() != timepointType)
                    throw new PipelineJobException("Can't change timepoint style from '" + study.getTimepointType() + "' to '" + timepointType + "' when reloading an existing study.");
            }

            runImporters(ctx, job, errors);

            if (errors.hasErrors())
                throwFirstErrorAsPipelineJobException(errors);

            if (hasSpecimenSchemasToImport)
            {
                VirtualFile specimenDir = SpecimenSchemaImporter.getSpecimenFolder(ctx);
                if (null != specimenDir)
                {
                    new SpecimenSchemaImporter().process(ctx, specimenDir, errors);
                    if (errors.hasErrors())
                        throwFirstErrorAsPipelineJobException(errors);
                }
            }
        }
        catch (CancelledException e)
        {
            // Let this through without wrapping
            throw e;
        }
        catch (Throwable t)
        {
            throw new PipelineJobException(t) {};
        }
        finally
        {
            if (previousTablesTemplate != null)
                StudySchema.getInstance().setSpecimenTablesTemplates(previousTablesTemplate);
        }
    }


    private static void runImporters(StudyImportContext ctx, PipelineJob job, BindException errors) throws Exception
    {
        VirtualFile vf = ctx.getRoot();

        TopLevelStudyPropertiesImporter importer1 = new TopLevelStudyPropertiesImporter();
        job.setStatus("IMPORT " + importer1.getDataType());
        importer1.process(ctx, vf, errors);
        // study.objective, study.personnel, and study.studyproperties tables
        new StudyPropertiesImporter().process(ctx, vf, errors);

        new MissingValueImporterFactory().create().process(job, ctx, vf);

        QcStatesImporter importer3 = new QcStatesImporter();
        job.setStatus("IMPORT " + importer3.getDataType());
        importer3.process(ctx, vf, errors);

        VisitImporter importer4 = new VisitImporter();
        job.setStatus("IMPORT " + importer4.getDataType());
        importer4.process(ctx, vf, errors);
        if (errors.hasErrors())
            throwFirstErrorAsPipelineJobException(errors);

        TreatmentDataImporter importer5 = new TreatmentDataImporter();
        job.setStatus("IMPORT " + importer5.getDataType());
        importer5.process(ctx, vf, errors);

        AssayScheduleImporter importer6 = new AssayScheduleImporter();
        job.setStatus("IMPORT " + importer6.getDataType());
        importer6.process(ctx, vf, errors);

        DatasetDefinitionImporter importer7 = new DatasetDefinitionImporter();
        job.setStatus("IMPORT " + importer7.getDataType());
        importer7.process(ctx, vf, errors);
    }

    private static void throwFirstErrorAsPipelineJobException(BindException errors) throws PipelineJobException
    {
        ObjectError firstError = errors.getAllErrors().get(0);
        throw new PipelineJobException("ERROR: " + firstError.getDefaultMessage());
    }


    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(StudyImportInitialTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new StudyImportInitialTask(this, job);
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
            return "LOAD STUDY";
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
