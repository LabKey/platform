/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.pipeline.*;
import org.labkey.api.util.FileType;
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

    public RecordedActionSet run() throws PipelineJobException
    {
        try
        {
            PipelineJob job = getJob();
            StudyJobSupport support = job.getJobSupport(StudyJobSupport.class);
            ImportContext ctx = support.getImportContext();
            StudyDocument.Study studyXml = ctx.getStudyXml();
            StudyImpl study = support.getStudy(true);

            // Create the study if it doesn't exist... otherwise, modify the existing properties
            if (null == study)
            {
                job.info("Creating study");

                // Create study
                StudyController.StudyPropertiesForm studyForm = new StudyController.StudyPropertiesForm();

                if (studyXml.isSetLabel())
                    studyForm.setLabel(studyXml.getLabel());

                if (studyXml.isSetDateBased())
                    studyForm.setDateBased(studyXml.getDateBased());

                if (studyXml.isSetStartDate())
                    studyForm.setStartDate(studyXml.getStartDate().getTime());

                if (studyXml.isSetSecurityType())
                    studyForm.setSecurityType(SecurityType.valueOf(studyXml.getSecurityType().toString()));

                StudyController.createStudy(support.getStudy(true), ctx.getContainer(), ctx.getUser(), studyForm);
            }
            else
            {
                job.info("Loading top-level study properties");

                // TODO: Change these props and save only if values have changed
                study = study.createMutable();

                if (studyXml.isSetLabel())
                    study.setLabel(studyXml.getLabel());

                if (studyXml.isSetDateBased())
                    study.setDateBased(studyXml.getDateBased());

                if (studyXml.isSetStartDate())
                    study.setStartDate(studyXml.getStartDate().getTime());

                if (studyXml.isSetSecurityType())
                    study.setSecurityType(SecurityType.valueOf(studyXml.getSecurityType().toString()));

                StudyManager.getInstance().updateStudy(ctx.getUser(), study);
            }

            new MissingValueImporter().process(ctx);
            new QcStatesImporter().process(support.getStudy(), ctx);

            if (!new VisitImporter().process(support.getStudy(), ctx, support.getRoot(), support.getSpringErrors()))
                throwFirstErrorAsPiplineJobException(support.getSpringErrors());

            if (!new DatasetImporter().process(support.getStudy(), ctx, support.getRoot(), support.getSpringErrors()))
                throwFirstErrorAsPiplineJobException(support.getSpringErrors());
        }
        catch (Throwable t)
        {
            throw new PipelineJobException(t);
        }

        return new RecordedActionSet();
    }

    private static void throwFirstErrorAsPiplineJobException(BindException errors) throws PipelineJobException
    {
        ObjectError firstError = (ObjectError)errors.getAllErrors().get(0);
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

        public FileType[] getInputTypes()
        {
            return new FileType[0];
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