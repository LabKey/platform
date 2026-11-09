/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
package org.labkey.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.AccumulatingCommitTask;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbScope.CommitTaskOption;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;

import java.util.List;
import java.util.Set;

public class AssayExperimentListener implements ExperimentListener
{
    /*
     * Both deletes below fire once per item, so a folder delete reaches them once per batch or run it holds. Collecting
     * the items and de-indexing them together keeps that to one commit task, which is also what the deindex calls want
     * -- each resolves its items to document ids and deletes them in a single search request.
     *
     * Keyed by a constant rather than by container: a document id is not container-scoped, and taking no container is
     * what lets these run after the container itself is gone.
     */

    private static class DeindexAssayBatchesTask extends AccumulatingCommitTask<String, ExpExperiment>
    {
        private DeindexAssayBatchesTask()
        {
            super(DeindexAssayBatchesTask.class.getName());
        }

        @Override
        protected void process(@NotNull String key, @NotNull Set<ExpExperiment> batches)
        {
            AssayManager.get().deindexAssayBatches(batches);
        }
    }

    private static class DeindexAssayRunsTask extends AccumulatingCommitTask<String, ExpRun>
    {
        private DeindexAssayRunsTask()
        {
            super(DeindexAssayRunsTask.class.getName());
        }

        @Override
        protected void process(@NotNull String key, @NotNull Set<ExpRun> runs)
        {
            AssayManager.get().deindexAssayRuns(runs);
        }
    }

    @Override
    public void afterExperimentDeleted(Container c, User user, ExpExperiment experiment)
    {
        new DeindexAssayBatchesTask().register(DbScope.getLabKeyScope(), List.of(experiment), CommitTaskOption.POSTCOMMIT);
    }

    @Override
    public void afterExperimentSaved(Container c, User user, ExpExperiment experiment)
    {
        AssayManager.get().indexAssayBatch(SearchService.get().defaultTask().getQueue(c, SearchService.PRIORITY.modified), experiment);
    }

    @Override
    public void afterRunDelete(ExpProtocol protocol, ExpRun run, User user)
    {
        new DeindexAssayRunsTask().register(DbScope.getLabKeyScope(), List.of(run), CommitTaskOption.POSTCOMMIT);
    }

    @Override
    public void afterRunSaved(Container container, User user, ExpProtocol protocol, ExpRun run)
    {
        AssayManager.get().indexAssayRun(SearchService.get().defaultTask().getQueue(container, SearchService.PRIORITY.modified), run.getRowId());
    }
}
