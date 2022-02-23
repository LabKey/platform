/*
 * Copyright (c) 2012-2018 LabKey Corporation
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
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.cloud.CloudArchiveImporterSupport;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.security.User;
import org.labkey.api.specimen.SpecimenMigrationService;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.importer.SimpleStudyImporter;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.pipeline.AbstractDatasetImportTask;
import org.labkey.study.writer.StudyArchiveDataTypes;
import org.labkey.study.writer.StudySerializationRegistry;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public static class StudyFolderImporter implements FolderImporter
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
        public void process(@Nullable PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception
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

                boolean useLocalImportDir = job != null && job.getJobSupport(CloudArchiveImporterSupport.class).useLocalImportDir(job, studyDir.getLocation());
                if (useLocalImportDir)
                {
                    Path dirPath = job.getPipeRoot().getRootNioPath().resolve(studyDir.getLocation());
                    job.getJobSupport(CloudArchiveImporterSupport.class).downloadCloudArchive(job, dirPath.resolve(studyFileName), errors);
                }

                StudyDocument studyDoc;
                XmlObject studyXml = studyDir.getXmlBean(studyFileName);
                try
                {
                    if (studyXml instanceof StudyDocument)
                    {
                        studyDoc = (StudyDocument)studyXml;
                        XmlBeansUtil.validateXmlDocument(studyDoc, studyFileName);
                    }
                    else
                        throw new ImportException("Unable to get an instance of StudyDocument from " + studyFileName);
                }
                catch (XmlValidationException e)
                {
                    throw new InvalidFileException(studyDir.getRelativePath(studyFileName), e);
                }

                StudyImportContext studyImportContext = new StudyImportContext.Builder(user,c)
                    .withDocument(studyDoc)
                    .withDataTypes(ctx.getDataTypes())
                    .withLogger(ctx.getLoggerGetter())
                    .withRoot(useLocalImportDir ? new FileSystemFile(job.getPipeRoot().getImportDirectory()) : studyDir)
                    .build();

                studyImportContext.setCreateSharedDatasets(ctx.isCreateSharedDatasets());
                studyImportContext.setFailForUndefinedVisits(ctx.isFailForUndefinedVisits());
                studyImportContext.setActivity(ctx.getActivity());

                // the initial study import task handles things like base study properties, MVIs, qcStates, visits, specimen settings, datasets definitions.
                StudyImportInitialTask.doImport(job, studyImportContext, errors, studyFileName);

                // the dataset import task handles importing the dataset data and updating the participant and participantVisit tables
                String datasetsFileName = AbstractDatasetImportTask.getDatasetsFileName(studyImportContext);
                VirtualFile datasetsDirectory = AbstractDatasetImportTask.getDatasetsDirectory(studyImportContext, studyDir);
                StudyImpl study = StudyManager.getInstance().getStudy(c);
                List<DatasetDefinition> datasets = AbstractDatasetImportTask.doImport(datasetsDirectory, datasetsFileName, job, studyImportContext, study, false);

                // import specimens, if the module is present
                if (null != SpecimenService.get())
                {
                    Path specimenFile = studyImportContext.getSpecimenArchive(studyDir);
                    if (useLocalImportDir)
                    {   //TODO this should be done from the import context getSpecimenArchive
                        specimenFile = job.getPipeRoot().getRootNioPath().relativize(specimenFile);
                        specimenFile = job.getPipeRoot().getImportDirectory().toPath().resolve(specimenFile);
                    }

                    SpecimenMigrationService.get().importSpecimenArchive(specimenFile, job, studyImportContext, false, false);
                }

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
        public Collection<PipelineJobWarning> postProcess(FolderImportContext ctx, VirtualFile root)
        {
            return Collections.emptyList();
        }

        @Override
        public @Nullable Map<String, Boolean> getChildrenDataTypes(String archiveFilePath, User user, Container container) throws ImportException, IOException
        {
            StudyImportContext sCtx = getImportContext(archiveFilePath, user, container);

            Map<String, Boolean> dataTypes = new TreeMap<>();
            for (InternalStudyImporter studyImporter : StudySerializationRegistry.get().getInternalStudyImporters())
            {
                if (studyImporter.getDataType() != null)
                    dataTypes.put(studyImporter.getDataType(), sCtx != null && studyImporter.isValidForImportArchive(sCtx, sCtx.getRoot()));
            }

            for (SimpleStudyImporter importer : StudySerializationRegistry.get().getSimpleStudyImporters())
            {
                if (importer.getDataType() != null)
                    dataTypes.put(importer.getDataType(), sCtx != null && importer.isValidForImportArchive(sCtx, sCtx.getRoot()));
            }

            // specifically add those "importers" that aren't implementers of InternalStudyImporter
            dataTypes.put(StudyArchiveDataTypes.DATASET_DATA, sCtx != null && AbstractDatasetImportTask.isValidForImportArchive(sCtx, sCtx.getRoot()));
            dataTypes.put(SpecimenMigrationService.SPECIMENS_ARCHIVE_TYPE, sCtx != null && sCtx.getSpecimenArchive(sCtx.getRoot()) != null);

            return dataTypes;
        }

        @Nullable StudyImportContext getImportContext(String archiveFilePath, User user, Container container) throws IOException
        {
            if (archiveFilePath != null)
            {
                Path archiveFile = FileUtil.stringToPath(container, archiveFilePath);
                if (Files.exists(archiveFile) && Files.isRegularFile(archiveFile))
                {
                    VirtualFile vf = new FileSystemFile(archiveFile.getParent());
                    VirtualFile studyDir = vf.getXmlBean("study.xml") != null ? vf : vf.getDir("study");
                    XmlObject studyXml = studyDir.getXmlBean("study.xml");

                    if (studyXml instanceof StudyDocument)
                        return new StudyImportContext(user, container, (StudyDocument)studyXml, null, null, studyDir);
                }
            }

            return null;
        }

        @Override
        public boolean isValidForImportArchive(FolderImportContext ctx) throws ImportException
        {
            return ctx.getDir("study") != null;
        }
    }
}
