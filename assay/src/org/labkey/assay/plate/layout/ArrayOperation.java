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

        // Plate all samples
        while (sampleIndex < sampleIds.size())
        {
            WellLayout wellLayout = getNextWellLayout(context, layouts.size());
            Pair<Integer, WellLayout> result;

            if (wellLayout.getTargetTemplateId() != null)
            {
                result = executeTemplateLayout(context, wellLayout, sampleIds, groupSampleMap, sampleIndex);

                // The counter did not advance for this well layout meaning we did not plate any additional samples.
                if (result.first == sampleIndex)
                    throw new ValidationException(String.format("There are %d selected samples and only %d unique sample regions are available in \"%s\".", sampleIds.size(), sampleIndex, context.targetTemplate().getName()));
            }
            else
                result = executeRowColumnLayout(wellLayout, sampleIds, sampleIndex);

            layouts.add(result.second);
            sampleIndex = result.first;
        }

        // Layout any further plates that have been requested (if any)
        List<PlateManager.PlateData> plateData = context.plateData();
        if (plateData != null && plateData.size() > layouts.size())
        {
            while (layouts.size() < plateData.size())
            {
                WellLayout wellLayout = getNextWellLayout(context, layouts.size());

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

    private @NotNull WellLayout getNextWellLayout(ExecutionContext context, int numLayouts)
    {
        WellLayout layout = getPlateDataWellLayout(context, Math.max(0, numLayouts - 1));

        if (layout == null)
        {
            if (context.targetTemplate() != null)
                layout = new WellLayout(context.targetTemplate().getPlateType(), false, context.targetTemplate().getRowId());
            else
                layout = new WellLayout(context.targetPlateType(), true, null);
        }

        return layout;
    }

    private @Nullable WellLayout getPlateDataWellLayout(ExecutionContext context, int plateIndex)
    {
        List<PlateManager.PlateData> plateData = context.plateData();

        if (plateData != null && plateData.size() > plateIndex)
        {
            PlateManager.PlateData targetPlateData = plateData.get(plateIndex);
            if (targetPlateData.plateType() != null && targetPlateData.plateType() > 0)
            {
                PlateType targetPlateDataType = context.resolvePlateType(targetPlateData.plateType());
                if (targetPlateDataType != null)
                {
                    if (targetPlateData.templateId() != null)
                        return new WellLayout(targetPlateDataType, false, targetPlateData.templateId());
                    else
                        return new WellLayout(targetPlateDataType, true, null);
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

    private Pair<Integer, WellLayout> executeTemplateLayout(
        ExecutionContext context,
        WellLayout target,
        List<Integer> sampleIds,
        Map<Pair<WellGroup.Type, String>, Integer> groupSampleMap,
        int sampleIndex
    )
    {
        for (WellData wellData : context.getWellData(target.getTargetTemplateId(), false, false))
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

            for (WellData wellData : context.getWellData(sourceRowId, true, false))
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
}
