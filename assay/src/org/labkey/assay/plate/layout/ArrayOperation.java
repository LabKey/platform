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

import org.apache.commons.collections4.MapUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.data.WellData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ArrayOperation implements LayoutOperation
{
    public enum Layout
    {
        Column,
        Row,
        Template
    }

    private final Layout _layout;
    private Map<Long, WellLayout.Well> _sampleWells;

    public ArrayOperation(@NotNull Layout layout)
    {
        _layout = layout;
    }

    @Override
    public List<WellLayout> execute(ExecutionContext context) throws ValidationException
    {
        int sampleIndex = 0;
        List<WellLayout> layouts = new ArrayList<>();
        Map<Pair<WellGroup.Type, String>, Long> groupSampleMap = new HashMap<>();
        Map<Long, WellLayout.Well> sampleWells = new LinkedHashMap<>(_sampleWells);

        List<Long> sampleIds = new ArrayList<>();
        for (Map.Entry<Long, WellLayout.Well> entry : sampleWells.entrySet())
            sampleIds.add(entry.getKey());

        List<WellLayout> targetLayouts = new ArrayList<>();

        // We look back at plates when populating existing which effectively means we inherit grouped samples and plate
        // them on subsequent new plates so that the rules are upheld. This may not be what users are expecting.
        // Does this need to be a choice? It's difficult to grasp.
        if (context.options().isFillExistingWells() && context.targetPlates() != null)
        {
            Map<Long, Long> plateRunCounts = PlateManager.get().getPlateRunCounts(context.container(), context.user(), context.targetPlates());

            for (Plate plate : context.targetPlates())
            {
                populateGroupSampleMap(context, plate, groupSampleMap, sampleWells);

                if (plateRunCounts.get(plate.getRowId()) == 0)
                    targetLayouts.add(new WellLayout(plate.getPlateType(), false, null, plate.getRowId()));
            }
        }

        List<PlateManager.PlateData> targetPlateData = new ArrayList<>(context.targetPlateData());
        boolean isFillPlatesOnly = context.options().isFillPlatesOnly();
        int initialSampleCount = sampleIds.size();

        // Plate all samples
        while (sampleIndex < sampleIds.size())
        {
            // If isFillPlatesOnly is true, then require that those target plate configurations are enough to plate all
            // the samples. Otherwise, generate additional plates.
            if (isFillPlatesOnly && targetPlateData.isEmpty() && targetLayouts.isEmpty())
                throw new ValidationException(String.format("%s%d of %d samples could be plated with this configuration.", sampleIndex == 0 ? "" : "Only ", sampleIndex, initialSampleCount));

            WellLayout wellLayout = getNextWellLayout(context, targetLayouts, targetPlateData);
            if (wellLayout == null)
                throw new ValidationException(String.format("%s%d of %d samples could be plated with this configuration.", sampleIndex == 0 ? "" : "Only ", sampleIndex, sampleIds.size()));

            Pair<Integer, WellLayout> result;

            if (wellLayout.getTargetPlateId() != null)
            {
                result = executeTargetPlateLayout(context, wellLayout, sampleIds, groupSampleMap, sampleWells, sampleIndex);

                // The counter may not advance here and that is OK since the plate sample/replicate wells may be full.
                if (result.first == sampleIndex)
                    continue;
            }
            else
            {
                if (wellLayout.getTargetTemplateId() != null)
                {
                    result = executeTemplateLayout(context, wellLayout, sampleIds, groupSampleMap, sampleWells, sampleIndex);

                    // The counter did not advance for this well layout meaning we did not plate any additional samples.
                    if (result.first == sampleIndex)
                        throw new ValidationException(String.format("There are %d selected samples and only %d unique sample regions are available in template \"%s\".", sampleIds.size(), sampleIndex, context.targetTemplate().getName()));
                }
                else
                {
                    result = executeRowColumnLayout(wellLayout, sampleWells, sampleIds, sampleIndex);
                }
            }

            layouts.add(result.second);
            sampleIndex = result.first;
        }

        // Layout any further plates that have been requested (if any)
        while (!targetPlateData.isEmpty())
        {
            WellLayout wellLayout = getPlateDataWellLayout(context, targetPlateData);

            if (wellLayout != null)
            {
                if (wellLayout.getTargetTemplateId() != null)
                {
                    Pair<Integer, WellLayout> result = executeTemplateLayout(context, wellLayout, sampleIds, groupSampleMap, sampleWells, sampleIndex);
                    layouts.add(result.second);
                }
                else
                    layouts.add(wellLayout);
            }
        }

        return layouts;
    }

    private static @Nullable WellLayout getNextWellLayout(
        ExecutionContext context,
        List<WellLayout> targetLayouts,
        List<PlateManager.PlateData> targetPlateData
    )
    {
        WellLayout layout;
        if (!targetLayouts.isEmpty())
            return targetLayouts.removeFirst();

        if (targetPlateData != null && !targetPlateData.isEmpty())
            layout = getPlateDataWellLayout(context, targetPlateData);
        else if (context.targetTemplate() != null)
            layout = new WellLayout(context.targetTemplate().getPlateType(), false, context.targetTemplate().getRowId(), null);
        else
            layout = new WellLayout(context.targetPlateType(), true, null, null);

        return layout;
    }

    private static @Nullable WellLayout getPlateDataWellLayout(ExecutionContext context, @NotNull List<PlateManager.PlateData> plateData)
    {
        if (plateData.isEmpty())
            return null;

        PlateManager.PlateData targetPlateData = plateData.removeFirst();
        if (targetPlateData != null && targetPlateData.plateType() != null && targetPlateData.plateType() > 0)
        {
            PlateType targetPlateDataType = context.resolvePlateType(targetPlateData.plateType());
            if (targetPlateDataType != null)
            {
                if (targetPlateData.templateId() != null)
                    return new WellLayout(targetPlateDataType, false, targetPlateData.templateId(), null);
                else
                    return new WellLayout(targetPlateDataType, true, null, null);
            }
        }

        return null;
    }

    private Pair<Integer, WellLayout> executeRowColumnLayout(WellLayout target, Map<Long, WellLayout.Well> sampleWells, List<Long> sampleIds, int sampleIndex)
    {
        PlateType targetPlateType = target.getPlateType();
        boolean isColumnLayout = Layout.Column.equals(_layout);
        int targetCols = targetPlateType.getColumns();
        int targetRows = targetPlateType.getRows();
        int targetColIdx = 0;
        int targetRowIdx = 0;
        int sampleCounter = 0;

        for (int i = sampleIndex; i < sampleIds.size(); i++)
        {
            WellLayout.Well sourceWell = sampleWells.get(sampleIds.get(i));
            target.setWell(targetRowIdx, targetColIdx, sourceWell.sourcePlateId(), sourceWell.sourceRowIdx(), sourceWell.sourceColIdx(), sourceWell.sourceSampleId());
            sampleCounter++;

            if (isColumnLayout)
            {
                targetRowIdx++;
                if (targetRowIdx == targetRows)
                {
                    targetRowIdx = 0;
                    targetColIdx++;

                    if (targetColIdx == targetCols)
                        break;
                }
            }
            else
            {
                targetColIdx++;
                if (targetColIdx == targetCols)
                {
                    targetColIdx = 0;
                    targetRowIdx++;

                    if (targetRowIdx == targetRows)
                        break;
                }
            }
        }

        return Pair.of(sampleIndex + sampleCounter, target);
    }

    private Pair<Integer, WellLayout> executeTargetPlateLayout(
        ExecutionContext context,
        WellLayout target,
        List<Long> sampleIds,
        Map<Pair<WellGroup.Type, String>, Long> groupSampleMap,
        Map<Long, WellLayout.Well> sampleWells,
        int sampleIndex
    )
    {
        List<WellData> plateWellData = context.wellDataCache().getData(target.getTargetPlateId(), true, false);
        PlateType targetPlateType = target.getPlateType();
        boolean isColumnLayout = Layout.Column.equals(_layout);
        int columnCount = targetPlateType.getColumns();
        int rowCount = targetPlateType.getRows();

        for (int outerIdx = 0; outerIdx < (isColumnLayout ? columnCount : rowCount); outerIdx++)
        {
            for (int innerIdx = 0; innerIdx < (isColumnLayout ? rowCount : columnCount); innerIdx++)
            {
                int rowIdx = isColumnLayout ? innerIdx : outerIdx;
                int colIdx = isColumnLayout ? outerIdx : innerIdx;
                int wellIdx = rowIdx * columnCount + colIdx;
                WellData wellData = plateWellData.get(wellIdx);
                Long wellSampleId = wellData.getSampleId();
                if (wellSampleId == null)
                {
                    boolean isSampleOrReplicate = wellData.isSampleOrReplicate();
                    Pair<WellGroup.Type, String> groupKey = wellData.getGroupKey();

                    if (sampleIndex >= sampleIds.size())
                    {
                        // Fill remaining group wells
                        if (isSampleOrReplicate && groupKey != null && groupSampleMap.containsKey(groupKey))
                        {
                            Long sampleId = MapUtils.getLong(groupSampleMap,groupKey);
                            WellLayout.Well sourceWell = sampleWells.get(sampleId);
                            target.setWell(wellData.getRow(), wellData.getCol(), sourceWell.sourcePlateId(), sourceWell.sourceRowIdx(), sourceWell.sourceColIdx(), sampleId);
                        }
                    }
                    else if (isSampleOrReplicate)
                    {
                        Long sampleId = sampleIds.get(sampleIndex);

                        if (groupKey != null)
                        {
                            if (groupSampleMap.containsKey(groupKey))
                            {
                                // Do not increment counter as this reuses the same sample within a group
                                sampleId = groupSampleMap.get(groupKey);
                            }
                            else
                            {
                                groupSampleMap.put(groupKey, sampleId);
                                sampleIndex++;
                            }
                        }
                        else
                        {
                            sampleIndex++;
                        }

                        WellLayout.Well sourceWell = sampleWells.get(sampleId);
                        target.setWell(wellData.getRow(), wellData.getCol(), sourceWell.sourcePlateId(), sourceWell.sourceRowIdx(), sourceWell.sourceColIdx(), sampleId);
                    }
                    else if (wellData.getType() == null)
                    {
                        Long sampleId = sampleIds.get(sampleIndex);
                        sampleIndex++;

                        WellLayout.Well sourceWell = sampleWells.get(sampleId);
                        target.setWell(wellData.getRow(), wellData.getCol(), sourceWell.sourcePlateId(), sourceWell.sourceRowIdx(), sourceWell.sourceColIdx(), sampleId);
                    }
                }
            }
        }

        return Pair.of(sampleIndex, target);
    }

    private static Pair<Integer, WellLayout> executeTemplateLayout(
        ExecutionContext context,
        WellLayout target,
        List<Long> sampleIds,
        Map<Pair<WellGroup.Type, String>, Long> groupSampleMap,
        Map<Long, WellLayout.Well> sampleWells,
        int sampleIndex
    )
    {
        for (WellData wellData : context.wellDataCache().getData(target.getTargetTemplateId(), false, false))
        {
            boolean isSampleOrReplicate = wellData.isSampleOrReplicate();
            Pair<WellGroup.Type, String> groupKey = wellData.getGroupKey();

            if (sampleIndex >= sampleIds.size())
            {
                // Fill remaining group wells
                if (isSampleOrReplicate && groupKey != null && groupSampleMap.containsKey(groupKey))
                {
                    Long sampleId = groupSampleMap.get(groupKey);
                    WellLayout.Well sourceWell = sampleWells.get(sampleId);
                    target.setWell(wellData.getRow(), wellData.getCol(), sourceWell.sourcePlateId(), sourceWell.sourceRowIdx(), sourceWell.sourceColIdx(), sampleId);
                }
            }
            else if (isSampleOrReplicate)
            {
                Long sampleId = sampleIds.get(sampleIndex);

                if (groupKey != null)
                {
                    if (groupSampleMap.containsKey(groupKey))
                    {
                        // Do not increment counter as this reuses the same sample within a group
                        sampleId = groupSampleMap.get(groupKey);
                    }
                    else
                    {
                        groupSampleMap.put(groupKey, sampleId);
                        sampleIndex++;
                    }
                }
                else
                {
                    sampleIndex++;
                }

                WellLayout.Well sourceWell = sampleWells.get(sampleId);
                target.setWell(wellData.getRow(), wellData.getCol(), sourceWell.sourcePlateId(), sourceWell.sourceRowIdx(), sourceWell.sourceColIdx(), sampleId);
            }
        }

        return Pair.of(sampleIndex, target);
    }

    private static void populateGroupSampleMap(
        ExecutionContext context,
        Plate plate,
        Map<Pair<WellGroup.Type, String>, Long> groupSampleMap,
        Map<Long, WellLayout.Well> sampleWells
    )
    {
        for (WellData wellData : context.wellDataCache().getData(plate.getRowId(), true, false))
        {
            Long sampleId = wellData.getSampleId();
            if (sampleId == null)
                continue;

            Pair<WellGroup.Type, String> groupKey = wellData.getGroupKey();
            if (groupKey != null)
            {
                groupSampleMap.put(groupKey, sampleId);
                sampleWells.putIfAbsent(sampleId, new WellLayout.Well(-1, -1, plate.getRowId(), wellData.getRow(), wellData.getCol(), sampleId));
            }
        }
    }

    @Override
    public void init(Container container, User user, ExecutionContext context) throws ValidationException
    {
        if (!context.sourcePlates().isEmpty())
            _sampleWells = generateSampleWellsFromSourcePlates(context);
        else if (context.sampleIds() != null && !context.sampleIds().isEmpty())
            _sampleWells = generateSampleWellsFromSampleIds(context.sampleIds());
        else
            throw new ValidationException("Invalid configuration. Either source plates or source samples must be provided.");
    }

    private Map<Long, WellLayout.Well> generateSampleWellsFromSampleIds(Collection<Long> sampleIds)
    {
        LinkedHashMap<Long, WellLayout.Well> sampleWells = new LinkedHashMap<>();

        for (Long sampleId : sampleIds)
        {
            if (!sampleWells.containsKey(sampleId))
                sampleWells.put(sampleId, new WellLayout.Well(-1, -1, -1, -1, -1, sampleId));
        }

        return sampleWells;
    }

    private Map<Long, WellLayout.Well> generateSampleWellsFromSourcePlates(ExecutionContext context)
    {
        LinkedHashMap<Long, WellLayout.Well> sampleWells = new LinkedHashMap<>();

        for (Plate sourcePlate : context.sourcePlates())
        {
            long sourceRowId = sourcePlate.getRowId();

            for (WellData wellData : context.wellDataCache().getData(sourceRowId, true, false))
            {
                Long wellSampleId = wellData.getSampleId();
                if (wellSampleId != null && !sampleWells.containsKey(wellSampleId) && wellData.isSampleOrReplicate())
                {
                    sampleWells.put(wellSampleId, new WellLayout.Well(-1, -1, sourceRowId, wellData.getRow(), wellData.getCol(), null));
                }
            }
        }

        return sampleWells;
    }

    @Override
    public boolean produceEmptyPlates()
    {
        return true;
    }

    @Override
    public boolean requiresSourcePlates()
    {
        return false;
    }

    @Override
    public boolean requiresTargetTemplate()
    {
        return Layout.Template.equals(_layout);
    }

    @Override
    public boolean supportsFillExistingWells()
    {
        return true;
    }

    @Override
    public boolean supportsFillPlatesOnly()
    {
        return true;
    }
}
