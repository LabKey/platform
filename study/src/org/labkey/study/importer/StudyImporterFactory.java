/*
 * Copyright (c) 2012-2015 LabKey Corporation
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

import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.admin.*;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.security.User;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.pipeline.StudyImportDatasetTask;
import org.labkey.study.pipeline.StudyImportSpecimenTask;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * User: cnathe
 * Date: Apr 11, 2012
 */
public class StudyImporterFactory extends AbstractFolderImportFactory
{
    @Override
    public FolderImporter create()
    {
        return new StudyFolderImporter();
    }

    @Override
    public int getPriority()
    {
        return 60;
    }

    public class StudyFolderImporter implements FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getSelectionText()
        {
            return FolderWriterNames.STUDY;
        }

        @Override
        public String getDescription()                                    
        {
            return getSelectionText().toLowerCase();
        }

        @Override
        public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            if (!ctx.isDataTypeSelected(getSelectionText()))
                return;

            VirtualFile studyDir = ctx.getDir("study");

            if (null != studyDir)
            {
                if (job != null)
                    job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());

                String studyFileName = "study.xml";
                Container c = ctx.getContainer();
                User user = ctx.getUser();
                BindException errors = new NullSafeBindException(c, "import");

                StudyDocument studyDoc;
                XmlObject studyXml = studyDir.getXmlBean(studyFileName);
                try
                {
                    if (studyXml instanceof StudyDocument)
                    {
                        studyDoc = (StudyDocument)studyXml;
                        XmlBeansUtil.validateXmlDocument(studyDoc);
                    }
                    else
                        throw new ImportException("Unable to get an instance of StudyDocument from " + studyFileName);
                }
                catch (XmlValidationException e)
                {
                    throw new InvalidFileException(studyDir.getRelativePath(studyFileName), e);
                }

                StudyImportContext studyImportContext = new StudyImportContext(user, c, studyDoc, ctx.getDataTypes(), ctx.getLoggerGetter(), studyDir);
                studyImportContext.setCreateSharedDatasets(ctx.isCreateSharedDatasets());

                // the initial study import task handles things like base study properties, MVIs, qcStates, visits, specimen settings, datasets definitions.
                StudyImportInitialTask.doImport(job, studyImportContext, errors, studyFileName);

                // the dataset import task handles importing the dataset data and updating the participant and participantVisit tables
                String datasetsFileName = StudyImportDatasetTask.getDatasetsFileName(studyImportContext);
                VirtualFile datasetsDirectory = StudyImportDatasetTask.getDatasetsDirectory(studyImportContext, studyDir);
                StudyImpl study = StudyManager.getInstance().getStudy(c);
                StudyImportDatasetTask.doImport(datasetsDirectory, datasetsFileName, job, studyImportContext, study);

                // specimen import task
                File specimenFile = studyImportContext.getSpecimenArchive(studyDir);
                StudyImportSpecimenTask.doImport(specimenFile, job, studyImportContext, false);

                // the final study import task handles registered study importers like: cohorts, participant comments, categories, etc.
                StudyImportFinalTask.doImport(job, studyImportContext, errors);

                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        @NotNull
        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return Collections.emptyList();
        }
    }
}
