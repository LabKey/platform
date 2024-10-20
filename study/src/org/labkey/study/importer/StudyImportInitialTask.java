/*
 * Copyright (c) 2009-2018 LabKey Corporation
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

import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.importer.SimpleStudyImporter;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.writer.StudySerializationRegistry;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;

import java.util.List;

/*
* User: adam
* Date: Aug 31, 2009
* Time: 9:12:22 AM
*/
public class StudyImportInitialTask
{
    private StudyImportInitialTask()
    {
    }

    private static final int DELAY_INCREMENT = 10;

    public static void doImport(PipelineJob job, StudyImportContext ctx, BindException errors, String originalFileName) throws PipelineJobException
    {
        // Construct all the SimpleStudyImporters that are designated as "Early"
        List<SimpleStudyImporter> simpleStudyImporters = StudySerializationRegistry.get().getSimpleStudyImporters().stream()
            .filter(ssi -> ssi.getTiming() == SimpleStudyImporter.Timing.Early)
            .toList();

        try
        {
            StudyDocument.Study studyXml = ctx.getXml();

            if (ctx.getContainer().isDataspace() && !studyXml.getAllowDataspace())
            {
                throw new PipelineJobException("Can't import study into a Dataspace folder.");
            }

            // verify the archiveVersion
            double currVersion = AppProps.getInstance().getSchemaVersion();
            if (studyXml.isSetArchiveVersion() && studyXml.getArchiveVersion() > currVersion)
                throw new PipelineJobException("Can't import study contents. The archive version " + studyXml.getArchiveVersion() + " is newer than the server version " + currVersion + ".");

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

            // Initialize the SimpleStudyImporters
            for (SimpleStudyImporter ssi : simpleStudyImporters)
                ssi.preHandling(ctx);

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

                if (null == StudyManager.getInstance().getStudy(ctx.getContainer()))
                    throw new IllegalStateException("Where's my study!");
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

            for (SimpleStudyImporter importer : simpleStudyImporters)
            {
                importer.process(ctx, ctx.getRoot(), errors);
                if (errors.hasErrors())
                    throwFirstErrorAsPipelineJobException(errors);
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
            for (SimpleStudyImporter importer : simpleStudyImporters)
                importer.postHandling(ctx);
        }
    }

    private static void runImporters(StudyImportContext ctx, PipelineJob job, BindException errors) throws Exception
    {
        processImporter(ctx, job, errors, new TopLevelStudyPropertiesImporter());

        // study.objective, study.personnel, and study.studyproperties tables
        new StudyPropertiesImporter().process(ctx, ctx.getRoot());

        // check for legacy QC states file in study
        processImporter(ctx, job, errors, new StudyQcStatesImporter());

        processImporter(ctx, job, errors, new VisitImporter());
        if (errors.hasErrors())
            throwFirstErrorAsPipelineJobException(errors);

        processImporter(ctx, job, errors, new TreatmentDataImporter());

        processImporter(ctx, job, errors, new AssayScheduleImporter());

        processImporter(ctx, job, errors, new DatasetDefinitionImporter());
    }

    private static void processImporter(StudyImportContext ctx, PipelineJob job, BindException errors, InternalStudyImporter importer) throws Exception
    {
        if (job != null)
            job.setStatus("IMPORT " + importer.getDataType());
        importer.process(ctx, ctx.getRoot(), errors);
    }

    private static void throwFirstErrorAsPipelineJobException(BindException errors) throws PipelineJobException
    {
        ObjectError firstError = errors.getAllErrors().get(0);
        throw new PipelineJobException("ERROR: " + firstError.getDefaultMessage());
    }
}
