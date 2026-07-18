/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.query.FieldKey;

import java.util.Set;

public interface ExpMaterialTable extends ExpTable<ExpMaterialTable.Column>, UpdateableTableInfo
{
    enum Column
    {
        Alias,
        AliquotCount,
        AliquotUnit,
        AliquotVolume(true),
        AliquotedFromLSID,
        AvailableAliquotCount,
        AvailableAliquotVolume(true),
        CpasType, // database table only
        Created,
        CreatedBy,
        Description,
        Flag,
        Folder,
        Inputs,
        IsAliquot,
        IsPlated,
        LastIndexed,
        LSID,
        MaterialExpDate,
        MaterialSourceId,
        Modified,
        ModifiedBy,
        Name,
        ObjectId, // database table only
        Outputs,
        Properties,
        Property,
        QueryableInputs,
        RawAliquotUnit(false, "Raw Aliquot Unit"),
        RawAliquotVolume(false, "Raw Aliquot Total Amount"),
        RawAvailableAliquotVolume(false, "Raw Available Aliquot Amount"),
        RawAmount(true),
        RawUnits,
        RootMaterialRowId,
        RowId,
        Run,
        RunId, // database table only
        RunApplication,
        RunApplicationOutput,
        SampleColor,
        SampleSet,
        SampleState,
        SourceApplicationId, // database table only
        SourceApplicationInput,
        SourceProtocolApplication,
        SourceProtocolLSID,
        StoredAmount(true, "Amount"),
        Units;

        private boolean _hasUnit = false;
        private final String _label;

        Column()
        {
            _label = ColumnInfo.labelFromName(name());
        }

        Column(boolean hasUnit)
        {
            this();
            _hasUnit = hasUnit;
        }

        Column(boolean hasUnit, String label)
        {
            _hasUnit = hasUnit;
            _label = label;
        }

        public FieldKey fieldKey()
        {
            return FieldKey.fromParts(name());
        }

        public boolean hasUnit()
        {
            return _hasUnit;
        }

        public String label()
        {
            return _label;
        }

        public Set<String> namesAndLabels()
        {
            Set<String> values = new CaseInsensitiveHashSet();

            values.add(this.name());
            values.add(this.label());
            values.add(this.label().replaceAll("\\s", ""));
            return values;
        }
    }

    default void setSupportTableRules(boolean supportTableRules)
    {
        throw new UnsupportedOperationException();
    }
}
