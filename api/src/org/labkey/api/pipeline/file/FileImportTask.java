package org.labkey.api.pipeline.file;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.util.FileType;

import java.util.Collections;
import java.util.List;

/**
 * User: tgaluhn
 * Date: 7/26/2016
 *
 * For pipeline which imports files with no external processing. Add all selected input files to the RecordedActionSet
 * to then be imported in a XarGeneratorTask
 */
public class FileImportTask extends PipelineJob.Task<FileImportTask.Factory>
{
    private FileImportTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @NotNull
    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        RecordedActionSet records = new RecordedActionSet();
        getJob().getJobSupport(FileAnalysisJobSupport.class).getInputFiles().forEach(file -> records.add(file, "File Import"));
        return records;
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, FileImportTask.Factory>
    {
        public Factory()
        {
            super(FileImportTask.class);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new FileImportTask(this, job);
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
            return "IMPORT FILE";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
