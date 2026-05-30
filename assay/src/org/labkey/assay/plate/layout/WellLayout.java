/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.PlateType;

public class WellLayout
{
    public record Well(int destinationRowIdx, int destinationColIdx, long sourcePlateId, int sourceRowIdx, int sourceColIdx, Long sourceSampleId) {}

    private final PlateType _plateType;
    private final boolean _sampleOnly;
    private final Long _targetPlateId;
    private final Long _targetTemplateId;
    private final Well[] _wells;

    public WellLayout(@NotNull PlateType plateType)
    {
        this(plateType, false, null, null);
    }

    public WellLayout(@NotNull PlateType plateType, boolean sampleOnly, @Nullable Long targetTemplateId, @Nullable Long targetPlateId)
    {
        _plateType = plateType;
        _sampleOnly = sampleOnly;
        _targetPlateId = targetPlateId;
        _targetTemplateId = targetTemplateId;
        _wells = new Well[plateType.getWellCount()];
    }

    public @Nullable Long getTargetPlateId()
    {
        return _targetPlateId;
    }

    public @Nullable Long getTargetTemplateId()
    {
        return _targetTemplateId;
    }

    private int getWellIndex(int destinationRowIdx, int destinationColIdx)
    {
        return destinationRowIdx * _plateType.getColumns() + destinationColIdx;
    }

    public @Nullable Well getWell(int destinationRowIdx, int destinationColIdx)
    {
        int index = getWellIndex(destinationRowIdx, destinationColIdx);
        if (index < _wells.length)
            return _wells[index];
        return null;
    }

    public Well[] getWells()
    {
        return _wells;
    }

    public boolean isSampleOnly()
    {
        return _sampleOnly;
    }

    public void setWell(int destinationRowIdx, int destinationColIdx, long sourcePlateId, int sourceWellRowIdx, int sourceWellColIdx)
    {
        setWell(destinationRowIdx, destinationColIdx, sourcePlateId, sourceWellRowIdx, sourceWellColIdx, null);
    }

    public void setWell(int destinationRowIdx, int destinationColIdx, long sourcePlateId, int sourceWellRowIdx, int sourceWellColIdx, Long sourceSampleId)
    {
        int index = getWellIndex(destinationRowIdx, destinationColIdx);
        _wells[index] = new Well(destinationRowIdx, destinationColIdx, sourcePlateId, sourceWellRowIdx, sourceWellColIdx, sourceSampleId);
    }

    public PlateType getPlateType()
    {
        return _plateType;
    }
}
