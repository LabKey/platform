/*
 * Copyright (c) 2018 LabKey Corporation
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

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ListReloadTask extends PipelineJob.Task<ListReloadTask.Factory>
{
    public static final String LIST_NAME_KEY = "name";
    public static final String LIST_ID_KEY = "id";

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
        assert support.getInputFilePaths().size() == 1;
        Path dataFile = support.getInputFilePaths().get(0);
        PipeRoot pr = PipelineService.get().findPipelineRoot(job.getContainer());

        if (pr != null)
        {
            try
            {
                Map<String, Pair<String, String>> inputDataMap = new CaseInsensitiveHashMap<>();
                ListImportContext context = new ListImportContext(null, false, true);
                boolean useMerge = false;

                if (params.containsKey(LIST_ID_KEY))
                    inputDataMap.put(dataFile.getFileName().toString(), new Pair<>(LIST_ID_KEY, params.get(LIST_ID_KEY)));
                else if (params.containsKey(LIST_NAME_KEY))
                    inputDataMap.put(dataFile.getFileName().toString(), new Pair<>(LIST_NAME_KEY, params.get(LIST_NAME_KEY)));

                if (params.containsKey(ListImportContext.LIST_MERGE_OPTION))
                    useMerge = Boolean.parseBoolean(params.get(ListImportContext.LIST_MERGE_OPTION));

                if (!inputDataMap.isEmpty() || useMerge)
                    context = new ListImportContext(inputDataMap, useMerge, true);

                context.setProps(params);
                ListReloadJob reloadJob = new ListReloadJob(job.getInfo(), pr, dataFile, job.getLogFilePath(), context);
                ListReloadJob unserializedJob = (ListReloadJob) PipelineJob.deserializeJob(PipelineJob.serializeJob(reloadJob, false));   // Force round trip to ensure serialization works
                unserializedJob.run();

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
