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

public class AssayExperimentListener implements ExperimentListener
{
    @Override
    public void afterExperimentDeleted(Container c, User user, ExpExperiment experiment)
    {
        DbScope.getLabKeyScope().addCommitTask(() ->
            AssayManager.get().deindexAssayBatches(List.of(experiment)),
            CommitTaskOption.POSTCOMMIT
        );
    }

    @Override
    public void afterExperimentSaved(Container c, User user, ExpExperiment experiment)
    {
        AssayManager.get().indexAssayBatch(SearchService.get().defaultTask().getQueue(c, SearchService.PRIORITY.modified), experiment);
    }

    @Override
    public void afterRunDelete(ExpProtocol protocol, ExpRun run, User user)
    {
        DbScope.getLabKeyScope().addCommitTask(() ->
            AssayManager.get().deindexAssayRuns(List.of(run)),
            CommitTaskOption.POSTCOMMIT
        );
    }

    @Override
    public void afterRunSaved(Container container, User user, ExpProtocol protocol, ExpRun run)
    {
        AssayManager.get().indexAssayRun(SearchService.get().defaultTask().getQueue(container, SearchService.PRIORITY.modified), run.getRowId());
    }
}
