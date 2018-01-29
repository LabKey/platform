/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
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
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.pipeline.StudyImportDatasetTask;
import org.labkey.study.pipeline.StudyImportSpecimenTask;
import org.labkey.study.writer.StudySerializationRegistryImpl;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        public String getDataType()
        {
            return FolderArchiveDataTypes.STUDY;
        }

        @Override
        public String getDescription()                                    
        {
            return getDataType().toLowerCase();
        }

        @Override
        public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            if (!ctx.isDataTypeSelected(getDataType()))
                return;

            if (isValidForImportArchive(ctx))
            {
                VirtualFile studyDir = ctx.getDir("study");

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
                studyImportContext.setFailForUndefinedVisits(ctx.isFailForUndefinedVisits());
                studyImportContext.setActivity(ctx.getActivity());

                // the initial study import task handles things like base study properties, MVIs, qcStates, visits, specimen settings, datasets definitions.
                StudyImportInitialTask.doImport(job, studyImportContext, errors, studyFileName);

                // the dataset import task handles importing the dataset data and updating the participant and participantVisit tables
                String datasetsFileName = StudyImportDatasetTask.getDatasetsFileName(studyImportContext);
                VirtualFile datasetsDirectory = StudyImportDatasetTask.getDatasetsDirectory(studyImportContext, studyDir);
                StudyImpl study = StudyManager.getInstance().getStudy(c);
                List<DatasetDefinition> datasets = StudyImportDatasetTask.doImport(datasetsDirectory, datasetsFileName, job, studyImportContext, study, false);

                // specimen import task
                File specimenFile = studyImportContext.getSpecimenArchive(studyDir);
                StudyImportSpecimenTask.doImport(specimenFile, job, studyImportContext, false, false);

                ctx.getLogger().info("Updating study-wide subject/visit information...");
                StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user, datasets, null, null, true, ctx.getLogger());
                ctx.getLogger().info("Subject/visit update complete.");

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

        @Nullable
        @Override
        public Map<String, Boolean> getChildrenDataTypes(ImportContext ctx) throws ImportException
        {
            StudyImportContext sCtx = null;
            if (ctx instanceof StudyImportContext)
                sCtx = (StudyImportContext)ctx;

            Map<String, Boolean> dataTypes = new TreeMap<>();
            for (InternalStudyImporter studyImporter : StudySerializationRegistryImpl.get().getInternalStudyImporters())
            {
                if (studyImporter.getDataType() != null)
                    dataTypes.put(studyImporter.getDataType(), sCtx != null && studyImporter.isValidForImportArchive(sCtx, sCtx.getRoot()));
            }

            // specifically add those "importers" that aren't implementers of InternalStudyImporter
            dataTypes.put(StudyImportDatasetTask.getType(), sCtx != null && StudyImportDatasetTask.isValidForImportArchive(sCtx, sCtx.getRoot()));
            dataTypes.put(StudyImportSpecimenTask.getType(), sCtx != null && sCtx.getSpecimenArchive(sCtx.getRoot()) != null);

            return dataTypes;
        }

        @Override
        public ImportContext getImporterSpecificImportContext(String archiveFilePath, User user, Container container) throws IOException
        {
            if (archiveFilePath != null)
            {
                    File archiveFile = new File(archiveFilePath);
                    if (archiveFile.exists() && archiveFile.isFile())
                    {
                        VirtualFile vf = new FileSystemFile(archiveFile.getParentFile());
                        VirtualFile studyDir = vf.getXmlBean("study.xml") != null ? vf : vf.getDir("study");
                        XmlObject studyXml = studyDir.getXmlBean("study.xml");

                        if (studyXml instanceof StudyDocument)
                            return new StudyImportContext(user, container, (StudyDocument)studyXml, null, null, studyDir);
                    }
            }

            return null;
        }

        @Override
        public boolean isValidForImportArchive(ImportContext<FolderDocument.Folder> ctx) throws ImportException
        {
            return ctx.getDir("study") != null;
        }
    }
}
