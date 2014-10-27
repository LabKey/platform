/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

package org.labkey.api.exp.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpProtocol;

public interface ExpExperimentTable extends ExpTable<ExpExperimentTable.Column>
{
    void addExperimentMembershipColumn(ExpRun run);

    /** @param protocol if not null, the protocol for which the run group must be a batch. If null, then only include non-batches */
    void setBatchProtocol(@Nullable ExpProtocol protocol);

    enum Column
    {
        RowId,
        LSID,
        Name,
        Hypothesis,
        Contact,
        ExperimentDescriptionURL,
        Comments,
        Created,
        CreatedBy,
        Modified,
        ModifiedBy,
        RunCount,
        Folder,
        BatchProtocolId,
    }

    ColumnInfo createRunCountColumn(String alias, ExpProtocol parentProtocol, ExpProtocol childProtocol);
}
