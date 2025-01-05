package org.labkey.assay.plate.layout;

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
    private Map<Integer, WellLayout.Well> _sampleWells;

    public ArrayOperation(@NotNull Layout layout)
    {
        _layout = layout;
    }

    @Override
    public List<WellLayout> execute(ExecutionContext context) throws ValidationException
    {
        int sampleIndex = 0;
        List<WellLayout> layouts = new ArrayList<>();
        Map<Pair<WellGroup.Type, String>, Integer> groupSampleMap = new HashMap<>();

        List<Integer> sampleIds = new ArrayList<>();
        for (Map.Entry<Integer, WellLayout.Well> entry : _sampleWells.entrySet())
            sampleIds.add(entry.getKey());

        List<WellLayout> targetLayouts = new ArrayList<>();

        // TODO: Document how this is handled.
        // We look back at plates when populating existing which effectively means we inherit grouped samples and plate
        // them on subsequent new plates so that the rules are upheld. This may not be what users are expecting.
        // Does this need to be a choice? It's difficult to grasp.
        if (context.options().isFillExistingWells() && context.targetPlates() != null)
        {
            for (Plate plate : context.targetPlates())
            {
                populateGroupSampleMap(context, plate, groupSampleMap);
                targetLayouts.add(new WellLayout(plate.getPlateType(), false, null, plate.getRowId()));
            }
        }

        // Remove samples that are already plated in this plate set?
        // sampleIds.removeAll(groupSampleMap.values());

        // Plate all samples
        while (sampleIndex < sampleIds.size())
        {
            WellLayout wellLayout = getNextWellLayout(context, targetLayouts, layouts.size());
            if (wellLayout == null)
                throw new ValidationException(String.format("Only %d of %d samples could be plated with this configuration.", sampleIndex, sampleIds.size()));

            Pair<Integer, WellLayout> result;

            if (wellLayout.getTargetPlateId() != null)
            {
                result = executeTargetPlateLayout(context, wellLayout, sampleIds, groupSampleMap, sampleIndex);

                // The counter may not advance here and that is OK since the plate sample/replicate wells may be full.
                if (result.first == sampleIndex)
                    continue;
            }
            else if (wellLayout.getTargetTemplateId() != null)
            {
                result = executeTemplateLayout(context, wellLayout, sampleIds, groupSampleMap, sampleIndex);

                // The counter did not advance for this well layout meaning we did not plate any additional samples.
                if (result.first == sampleIndex)
                    throw new ValidationException(String.format("There are %d selected samples and only %d unique sample regions are available in template \"%s\".", sampleIds.size(), sampleIndex, context.targetTemplate().getName()));
            }
            else
                result = executeRowColumnLayout(wellLayout, sampleIds, sampleIndex);

            layouts.add(result.second);
            sampleIndex = result.first;
        }

        // Layout any further plates that have been requested (if any)
        List<PlateManager.PlateData> plateData = context.targetPlateData();
        if (plateData != null && plateData.size() > layouts.size())
        {
            while (layouts.size() < plateData.size())
            {
                WellLayout wellLayout = getPlateDataWellLayout(context, layouts.size());
                if (wellLayout == null)
                    throw new ValidationException(String.format("Failed to resolve plate at index %d.", layouts.size()));

                if (wellLayout.getTargetTemplateId() != null)
                {
                    Pair<Integer, WellLayout> result = executeTemplateLayout(context, wellLayout, sampleIds, groupSampleMap, sampleIndex);
                    layouts.add(result.second);
                }
                else
                    layouts.add(wellLayout);
            }
        }

        return layouts;
    }

    private @Nullable WellLayout getNextWellLayout(ExecutionContext context, List<WellLayout> targetLayouts, int numLayouts)
    {
        WellLayout layout;
        if (!targetLayouts.isEmpty())
            return targetLayouts.remove(0);

        if (context.targetPlateData() != null && !context.targetPlateData().isEmpty())
            layout = getPlateDataWellLayout(context, numLayouts);
        else if (context.targetTemplate() != null)
            layout = new WellLayout(context.targetTemplate().getPlateType(), false, context.targetTemplate().getRowId(), null);
        else
            layout = new WellLayout(context.targetPlateType(), true, null, null);

        return layout;
    }

    private @Nullable WellLayout getPlateDataWellLayout(ExecutionContext context, int plateIndex)
    {
        List<PlateManager.PlateData> plateData = context.targetPlateData();

        if (plateData != null && plateData.size() > plateIndex)
        {
            PlateManager.PlateData targetPlateData = plateData.get(plateIndex);
            if (targetPlateData.plateType() != null && targetPlateData.plateType() > 0)
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
        }

        return null;
    }

    private Pair<Integer, WellLayout> executeRowColumnLayout(WellLayout target, List<Integer> sampleIds, int sampleIndex)
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
            WellLayout.Well sourceWell = _sampleWells.get(sampleIds.get(i));
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
        List<Integer> sampleIds,
        Map<Pair<WellGroup.Type, String>, Integer> groupSampleMap,
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
                Integer wellSampleId = wellData.getSampleId();
                if (wellSampleId == null)
                {
                    boolean isSampleWell = wellData.isSample();
                    boolean isReplicateWell = wellData.isReplicate();
                    boolean isSampleOrReplicate = isSampleWell || isReplicateWell;

                    Pair<WellGroup.Type, String> groupKey = null;
                    if (isSampleOrReplicate && wellData.getWellGroup() != null)
                    {
                        WellGroup.Type type = isSampleWell ? WellGroup.Type.SAMPLE : WellGroup.Type.REPLICATE;
                        groupKey = Pair.of(type, wellData.getWellGroup());
                    }

                    if (sampleIndex >= sampleIds.size())
                    {
                        // Fill remaining group wells
                        if (isSampleOrReplicate && groupKey != null && groupSampleMap.containsKey(groupKey))
                        {
                            Integer sampleId = groupSampleMap.get(groupKey);
                            WellLayout.Well sourceWell = _sampleWells.get(sampleId);
                            target.setWell(wellData.getRow(), wellData.getCol(), sourceWell.sourcePlateId(), sourceWell.sourceRowIdx(), sourceWell.sourceColIdx(), sampleId);
                        }
                    }
                    else if (isSampleOrReplicate)
                    {
                        Integer sampleId = sampleIds.get(sampleIndex);

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

                        WellLayout.Well sourceWell = _sampleWells.get(sampleId);
                        target.setWell(wellData.getRow(), wellData.getCol(), sourceWell.sourcePlateId(), sourceWell.sourceRowIdx(), sourceWell.sourceColIdx(), sampleId);
                    }
                    else if (wellData.getType() == null)
                    {
                        Integer sampleId = sampleIds.get(sampleIndex);
                        sampleIndex++;

                        WellLayout.Well sourceWell = _sampleWells.get(sampleId);
                        target.setWell(wellData.getRow(), wellData.getCol(), sourceWell.sourcePlateId(), sourceWell.sourceRowIdx(), sourceWell.sourceColIdx(), sampleId);
                    }
                }
            }
        }

        return Pair.of(sampleIndex, target);
    }

    private Pair<Integer, WellLayout> executeTemplateLayout(
        ExecutionContext context,
        WellLayout target,
        List<Integer> sampleIds,
        Map<Pair<WellGroup.Type, String>, Integer> groupSampleMap,
        int sampleIndex
    )
    {
        for (WellData wellData : context.wellDataCache().getData(target.getTargetTemplateId(), false, false))
        {
            boolean isSampleWell = wellData.isSample();
            boolean isReplicateWell = wellData.isReplicate();
            boolean isSampleOrReplicate = isSampleWell || isReplicateWell;

            Pair<WellGroup.Type, String> groupKey = null;
            if (isSampleOrReplicate && wellData.getWellGroup() != null)
            {
                WellGroup.Type type = isSampleWell ? WellGroup.Type.SAMPLE : WellGroup.Type.REPLICATE;
                groupKey = Pair.of(type, wellData.getWellGroup());
            }

            if (sampleIndex >= sampleIds.size())
            {
                // Fill remaining group wells
                if (isSampleOrReplicate && groupKey != null && groupSampleMap.containsKey(groupKey))
                {
                    Integer sampleId = groupSampleMap.get(groupKey);
                    WellLayout.Well sourceWell = _sampleWells.get(sampleId);
                    target.setWell(wellData.getRow(), wellData.getCol(), sourceWell.sourcePlateId(), sourceWell.sourceRowIdx(), sourceWell.sourceColIdx(), sampleId);
                }
            }
            else if (isSampleOrReplicate)
            {
                Integer sampleId = sampleIds.get(sampleIndex);

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

                WellLayout.Well sourceWell = _sampleWells.get(sampleId);
                target.setWell(wellData.getRow(), wellData.getCol(), sourceWell.sourcePlateId(), sourceWell.sourceRowIdx(), sourceWell.sourceColIdx(), sampleId);
            }
        }

        return Pair.of(sampleIndex, target);
    }

    private void populateGroupSampleMap(ExecutionContext context, Plate plate, Map<Pair<WellGroup.Type, String>, Integer> groupSampleMap)
    {
        for (WellData wellData : context.wellDataCache().getData(plate.getRowId(), true, false))
        {
            Integer sampleId = wellData.getSampleId();
            if (sampleId == null)
                continue;

            boolean isSampleWell = wellData.isSample();
            boolean isReplicateWell = wellData.isReplicate();
            boolean isSampleOrReplicate = isSampleWell || isReplicateWell;

            if (isSampleOrReplicate && wellData.getWellGroup() != null)
            {
                WellGroup.Type type = isSampleWell ? WellGroup.Type.SAMPLE : WellGroup.Type.REPLICATE;
                groupSampleMap.put(Pair.of(type, wellData.getWellGroup()), sampleId);
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

    private Map<Integer, WellLayout.Well> generateSampleWellsFromSampleIds(Collection<Integer> sampleIds)
    {
        LinkedHashMap<Integer, WellLayout.Well> sampleWells = new LinkedHashMap<>();

        for (Integer sampleId : sampleIds)
        {
            if (!sampleWells.containsKey(sampleId))
                sampleWells.put(sampleId, new WellLayout.Well(-1, -1, -1, -1, -1, sampleId));
        }

        return sampleWells;
    }

    private Map<Integer, WellLayout.Well> generateSampleWellsFromSourcePlates(ExecutionContext context)
    {
        LinkedHashMap<Integer, WellLayout.Well> sampleWells = new LinkedHashMap<>();

        for (Plate sourcePlate : context.sourcePlates())
        {
            int sourceRowId = sourcePlate.getRowId();

            for (WellData wellData : context.wellDataCache().getData(sourceRowId, true, false))
            {
                Integer wellSampleId = wellData.getSampleId();
                if (wellSampleId != null && !sampleWells.containsKey(wellSampleId) && (wellData.isSample() || wellData.isReplicate()))
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
}
