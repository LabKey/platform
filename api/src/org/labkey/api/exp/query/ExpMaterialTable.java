/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.exp.api.ExpSampleType;

public interface ExpMaterialTable extends ExpTable<ExpMaterialTable.Column>, UpdateableTableInfo
{
    enum Column
    {
        Alias,
        AliquotCount,
        AliquotUnit,
        AliquotVolume,
        AliquotedFromLSID,
        AvailableAliquotCount,
        AvailableAliquotVolume,
        Created,
        CreatedBy,
        Description,
        Flag,
        Folder,
        Inputs,
        IsAliquot,
        LSID,
        MaterialExpDate,
        MaterialSourceId,
        Modified,
        ModifiedBy,
        Name,
        Outputs,
        Properties,
        Property,
        QueryableInputs,
        RawAmount,
        RawUnits,
        RootMaterialRowId,
        RowId,
        Run,
        RunApplication,
        RunApplicationOutput,
        SampleSet,
        SampleState,
        SourceApplicationInput,
        SourceProtocolApplication,
        SourceProtocolLSID,
        StoredAmount,
        Units,
    }

    void populate(@Nullable ExpSampleType st);
}
