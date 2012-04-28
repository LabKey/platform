/*
 * Copyright (c) 2012 LabKey Corporation
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
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.admin.*;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.security.User;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.pipeline.StudyImportDatasetTask;
import org.labkey.study.pipeline.StudyImportSpecimenTask;
import org.labkey.study.writer.StudyWriterFactory;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;

/**
 * User: cnathe
 * Date: Apr 11, 2012
 */
public class StudyImporterFactory implements FolderImporterFactory
{
    @Override
    public FolderImporter create()
    {
        return new StudyFolderImporter();
    }

    @Override
    public boolean isFinalImporter()
    {
        return false;
    }

    public class StudyFolderImporter implements FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getDescription()                                    
        {
            return "study";
        }

        @Override
        public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            VirtualFile studyDir = ctx.getDir("study");

            if (null != studyDir)
            {
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

                StudyImportContext studyImportContext = new StudyImportContext(user, c, studyDoc, ctx.getLogger(), studyDir);

                // the initial study improt task handles things like base study properties, MVIs, qcStates, visits, datasets
                StudyImportInitialTask.doImport(job, studyImportContext, errors, studyFileName);

                // the dataset import task handles importing the dataset data and updating the participant and participantVisit tables
                File datasetsFile = StudyImportDatasetTask.getDatasetsFile(studyImportContext, studyDir);
                StudyImpl study = StudyManager.getInstance().getStudy(c);
                StudyImportDatasetTask.doImport(datasetsFile, job, study);

                // specimen import task
                File specimenFile = StudyImportSpecimenTask.getSpecimenArchive(studyImportContext, studyDir);
                StudyImportSpecimenTask.doImport(specimenFile, job, false);

                // the final study import task handles registered study importers like: cohorts, participant comments, categories, etc. 
                StudyImportFinalTask.doImport(job, studyImportContext, errors);

                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return null;
        }

        @Override
        public boolean supportsVirtualFile()
        {
            return false;
        }
    }
}
