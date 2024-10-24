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
package org.labkey.pipeline.importer;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporterImpl;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.cloud.CloudArchiveImporterSupport;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.DirectoryNotDeletedException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileType;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/*
* User: cnathe
* Date: Jan 19, 2012
*/
public class FolderImportTask extends PipelineJob.Task<FolderImportTask.Factory>
{
    private static final String FOLDER_XML = "folder.xml";

    private FolderImportTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        VirtualFile vf;
        FolderImportContext importContext;
        PipelineJob job = getJob();

        if (hasExpandedCloudRoot(job) && CloudStoreService.get() != null)
        {
            Path importRoot = CloudStoreService.get().downloadExpandedArchive(job);
            job.getJobSupport(CloudArchiveImporterSupport.class).updateWorkingRoot(importRoot);
        }

        boolean isFileAnalysisJob = FileAnalysisJobSupport.class.isInstance(job); // File watcher triggered job
        if (isFileAnalysisJob)
        {
            FileAnalysisJobSupport support = job.getJobSupport(FileAnalysisJobSupport.class);
            ImportOptions options = new ImportOptions(job.getContainerId(), job.getUser().getUserId());
            options.setAnalysisDir(support.getDataDirectory().toPath());

            job = new FolderImportJob(job.getContainer(), job.getUser(), null, support.findInputPath(FOLDER_XML), FOLDER_XML, job.getPipeRoot(), options);
            job.setStatus(PipelineJob.TaskStatus.running.toString(), "Starting folder import job", true);

            importContext = ((FolderImportJob) job).getImportContext();
            vf = new FileSystemFile(support.getDataDirectory());
        }
        /* Standard Pipeline triggered job */
        else
        {
            FolderJobSupport support = job.getJobSupport(FolderJobSupport.class);
            vf = support.getRoot();
            importContext = support.getImportContext();
        }

        try
        {
            // verify the archiveVersion
            FolderDocument.Folder folderXml = importContext.getXml();
            double currVersion = AppProps.getInstance().getSchemaVersion();
            if (folderXml.isSetArchiveVersion() && folderXml.getArchiveVersion() > currVersion)
                throw new PipelineJobException("Can't import folder archive. The archive version " + folderXml.getArchiveVersion() + " is newer than the server version " + currVersion + ".");

            FolderImporterImpl importer = new FolderImporterImpl(job);

            if (HttpView.hasCurrentView())
            {
                importer.process(job, importContext, vf);
            }
            else
            {
                //Build a fake ViewContext so we can run trigger scripts
                try (ViewContext.StackResetter ignored = ViewContext.pushMockViewContext(job.getUser(), job.getContainer(), job.getActionURL()))
                {
                    importer.process(job, importContext, vf);
                }
            }

            Collection<PipelineJobWarning> warnings = importer.postProcess(importContext, vf);
            //TODO: capture warnings in the pipeline job and make a distinction between success & success with warnings
            //  for now, just fail the job if there were any warnings. The warnings will
            //  have already been written to the log
            if (!warnings.isEmpty())
                job.error("Warnings were generated by the folder importers!");

            // todo: if importing into multiple folders from a single template source folder then we dont want to delete the import directory until done with all the imports
            //   maybe be best to just create a temporary, but real zip file and pass that around... but where to put the zip file itself?
            if (job.getErrors() == 0) {
                job.getPipeRoot().deleteImportDirectory(job.getLogger());
                if (isFileAnalysisJob) {
                    String message = "File analysis-based folder import job complete";
                    job.setStatus(PipelineJob.TaskStatus.complete.toString(), message, true);
                }
            }
        }
        catch (CancelledException e)
        {
            throw e;
        }
        catch (PipelineJobException | DirectoryNotDeletedException e)
        {
            job.error(e.getMessage(), e);
        }
        catch (Exception e)
        {
            throw new PipelineJobException(e);
        }

        return new RecordedActionSet();
    }

    private boolean hasExpandedCloudRoot(PipelineJob job)
    {
        if (!(job instanceof CloudArchiveImporterSupport))
            return false;

        CloudArchiveImporterSupport support = job.getJobSupport(CloudArchiveImporterSupport.class);
        return job.getPipeRoot().isCloudRoot() && !support.getOriginalFilename().endsWith("zip"); // Zip archives can be streamed
    }


    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(FolderImportTask.class);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new FolderImportTask(this, job);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        @Override
        public String getStatusName()
        {
            return "IMPORT FOLDER";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
