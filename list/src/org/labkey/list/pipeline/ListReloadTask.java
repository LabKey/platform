package org.labkey.list.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Pair;
import org.labkey.list.model.ListImportContext;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ListReloadTask extends PipelineJob.Task<ListReloadTask.Factory>
{
    public static final String LIST_NAME_KEY = "name";
    public static final String LIST_ID_KEY = "id";
    public static final String LIST_MERGE_OPTION = "mergeData";

    public ListReloadTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @NotNull
    @Override
    public RecordedActionSet run()
    {
        PipelineJob job = getJob();
        FileAnalysisJobSupport support = job.getJobSupport(FileAnalysisJobSupport.class);

        Map<String, String> params = support.getParameters();

        if (params.containsKey(LIST_ID_KEY) && params.containsKey(LIST_NAME_KEY))
        {
            job.error("List ID and name cannot be specified at the same time.");
            return new RecordedActionSet();
        }

        // guaranteed to only have a single file
        assert support.getInputFiles().size() == 1;
        File dataFile = support.getInputFiles().get(0);
        PipeRoot pr = PipelineService.get().findPipelineRoot(job.getContainer());

        if (pr != null)
        {
            try
            {
                Map<String, Pair<String, String>> inputDataMap = new CaseInsensitiveHashMap<>();
                ListImportContext context = new ListImportContext(null);
                boolean useMerge = false;

                if (params.containsKey(LIST_ID_KEY))
                    inputDataMap.put(dataFile.getName(), new Pair<>(LIST_ID_KEY, params.get(LIST_ID_KEY)));
                else if (params.containsKey(LIST_NAME_KEY))
                    inputDataMap.put(dataFile.getName(), new Pair<>(LIST_NAME_KEY, params.get(LIST_NAME_KEY)));

                if (params.containsKey(LIST_MERGE_OPTION))
                    useMerge = Boolean.parseBoolean(params.get(LIST_MERGE_OPTION));

                if (!inputDataMap.isEmpty() || useMerge)
                    context = new ListImportContext(inputDataMap, useMerge);


                ListReloadJob reloadJob = new ListReloadJob(job.getInfo(), pr, dataFile, job.getLogFile(), context);
                reloadJob.run();

                if (reloadJob.getErrors() > 0)
                {
                    job.error("Failed to reload list data.");
                    job.setStatus(PipelineJob.TaskStatus.error);
                }
            }
            catch (Exception e)
            {
                job.error("List Reload failed: ", e);
            }
        }
        else
        {
            job.error("Cannot locate pipeline root from job's container");
        }

        return new RecordedActionSet();
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(ListReloadTask.class);
        }

        @Override
        public String getStatusName()
        {
            return "LIST RELOAD";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new ListReloadTask(this, job);
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }
    }
}
