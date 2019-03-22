/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleSet;

public interface ExpQCFlagTable extends ExpTable<ExpQCFlagTable.Column>
{
    enum Column
    {
        RowId,
        Run,
        FlagType,
        Description,
        Comment,
        Enabled,
        Created,
        CreatedBy,
        Modified,
        ModifiedBy,
        IntKey1,
        IntKey2,
        Key1,
        Key2
    }

    public void setAssayProtocol(ExpProtocol protocol);
}
