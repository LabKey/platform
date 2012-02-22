package org.labkey.pipeline.importer;

import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.FileType;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;

import java.util.Collections;
import java.util.List;


/**
 * User: cnathe
 * Date: Feb 22, 2012
 */
public class FolderImportInitialTask extends PipelineJob.Task<FolderImportInitialTask.Factory>
{
    private FolderImportInitialTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        FolderJobSupport support = job.getJobSupport(FolderJobSupport.class);
        VirtualFile vf = new FileSystemFile(support.getRoot());

        try
        {
            job.info("Loading folder from " + support.getOriginalFilename());
            job.info("Loading folder settings"); // currently only MVIs
            StudyService.get().getMissingValueImporter().process(support.getImportContext(), vf);
        }
        catch (Exception e)
        {
            throw new PipelineJobException(e);
        }

        return new RecordedActionSet();
    }


    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(FolderImportInitialTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new FolderImportInitialTask(this, job);
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
            return "LOAD FOLDER SETTINGS";
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
