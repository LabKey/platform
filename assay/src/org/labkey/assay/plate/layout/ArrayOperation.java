package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.assay.plate.Well;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.Pair;
import org.labkey.assay.plate.data.WellData;
import org.labkey.assay.plate.model.ReformatOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

public class ArrayOperation implements LayoutOperation
{
    public enum Layout
    {
        Column,
        Row,
        Template
    }

    private final Layout _layout;

    public ArrayOperation(@NotNull Layout layout)
    {
        _layout = layout;
    }

    @Override
    public List<WellLayout> execute(
        ReformatOptions options,
        @NotNull List<Plate> sourcePlates,
        PlateType targetPlateType,
        Plate targetTemplate,
        List<WellData> targetTemplateWellData
    ) throws ValidationException
    {
        Map<Integer, WellLayout.Well> sampleWells = getSampleWellsFromSourcePlates(sourcePlates);
        if (sampleWells.isEmpty())
            return emptyList();

        if (Layout.Column.equals(_layout) || Layout.Row.equals(_layout))
            return executeRowColumnLayout(sampleWells, targetPlateType);
        else if (Layout.Template.equals(_layout))
            return executeTemplateLayout(sampleWells, targetTemplate, targetTemplateWellData);

        throw new UnsupportedOperationException(String.format("The layout \"%s\" is not supported.", _layout));
    }

    private List<WellLayout> executeRowColumnLayout(Map<Integer, WellLayout.Well> sampleWells, PlateType targetPlateType)
    {
        List<WellLayout> layouts = new ArrayList<>();
        WellLayout target = null;
        boolean isColumnLayout = Layout.Column.equals(_layout);

        int targetCols = targetPlateType.getColumns();
        int targetRows = targetPlateType.getRows();

        int targetColIdx = 0;
        int targetRowIdx = 0;

        for (Map.Entry<Integer, WellLayout.Well> entry : sampleWells.entrySet())
        {
            if (target == null)
                target = new WellLayout(targetPlateType, true, null);

            WellLayout.Well sourceWell = entry.getValue();
            target.setWell(targetRowIdx, targetColIdx, sourceWell.sourcePlateId(), sourceWell.sourceRowIdx(), sourceWell.sourceColIdx());

            if (isColumnLayout)
            {
                targetRowIdx++;
                if (targetRowIdx == targetRows)
                {
                    targetRowIdx = 0;
                    targetColIdx++;

                    if (targetColIdx == targetCols)
                    {
                        layouts.add(target);
                        target = null;
                        targetColIdx = 0;
                    }
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
                    {
                        layouts.add(target);
                        target = null;
                        targetRowIdx = 0;
                    }
                }
            }
        }

        if (target != null)
            layouts.add(target);

        return layouts;
    }

    private List<WellLayout> executeTemplateLayout(Map<Integer, WellLayout.Well> sampleWells, Plate targetTemplate, List<WellData> targetTemplateWellData) throws ValidationException
    {
        int counter = 0;
        List<WellLayout> layouts = new ArrayList<>();
        Map<Pair<WellGroup.Type, String>, Integer> groupSampleMap = new HashMap<>();

        List<Integer> sampleIds = new ArrayList<>();
        for (Map.Entry<Integer, WellLayout.Well> entry : sampleWells.entrySet())
            sampleIds.add(entry.getKey());

        while (counter < sampleIds.size())
        {
            int startCounter = counter;
            WellLayout layout = new WellLayout(targetTemplate.getPlateType(), false, targetTemplate.getRowId());

            for (WellData wellData : targetTemplateWellData)
            {
                boolean isSampleWell = WellGroup.Type.SAMPLE.equals(wellData.getType());
                boolean isReplicateWell = WellGroup.Type.REPLICATE.equals(wellData.getType());
                boolean isSampleOrReplicate = isSampleWell || isReplicateWell;

                Pair<WellGroup.Type, String> groupKey = null;
                if (isSampleOrReplicate && wellData.getWellGroup() != null)
                {
                    WellGroup.Type type = isSampleWell ? WellGroup.Type.SAMPLE : WellGroup.Type.REPLICATE;
                    groupKey = Pair.of(type, wellData.getWellGroup());
                }

                if (counter >= sampleIds.size())
                {
                    // Fill remaining group wells
                    if (isSampleOrReplicate && groupKey != null && groupSampleMap.containsKey(groupKey))
                    {
                        Integer sampleId = groupSampleMap.get(groupKey);
                        WellLayout.Well sourceWell = sampleWells.get(sampleId);
                        layout.setWell(wellData.getRow(), wellData.getCol(), sourceWell.sourcePlateId(), sourceWell.sourceRowIdx(), sourceWell.sourceColIdx(), sampleId);
                    }
                }
                else if (isSampleOrReplicate)
                {
                    Integer sampleId = sampleIds.get(counter);

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
                            counter++;
                        }
                    }
                    else
                    {
                        counter++;
                    }

                    WellLayout.Well sourceWell = sampleWells.get(sampleId);
                    layout.setWell(wellData.getRow(), wellData.getCol(), sourceWell.sourcePlateId(), sourceWell.sourceRowIdx(), sourceWell.sourceColIdx(), sampleId);
                }
            }

            // The counter did not advance for this well layout meaning we did not plate any additional samples.
            if (startCounter == counter)
                throw new ValidationException(String.format("There are %d selected samples and only %d unique sample regions are available in \"%s\".", sampleIds.size(), counter, targetTemplate.getName()));

            layouts.add(layout);
        }

        return layouts;
    }

    private Map<Integer, WellLayout.Well> getSampleWellsFromSourcePlates(@NotNull List<Plate> sourcePlates)
    {
        LinkedHashMap<Integer, WellLayout.Well> sampleWells = new LinkedHashMap<>();

        for (Plate sourcePlate : sourcePlates)
        {
            int sourceRowId = sourcePlate.getRowId();
            PlateType sourcePlateType = sourcePlate.getPlateType();

            for (int r = 0; r < sourcePlateType.getRows(); r++)
            {
                for (int c = 0; c < sourcePlateType.getColumns(); c++)
                {
                    // TODO: May need to be more discerning regarding controls v samples
                    Well well = sourcePlate.getWell(r, c);
                    Integer wellSampleId = well.getSampleId();
                    if (wellSampleId == null)
                        continue;

                    if (!sampleWells.containsKey(wellSampleId))
                        sampleWells.put(wellSampleId, new WellLayout.Well(-1, -1, sourceRowId, r, c, null));
                }
            }
        }

        return sampleWells;
    }

    @Override
    public boolean requiresTargetPlateType()
    {
        return Layout.Column.equals(_layout) || Layout.Row.equals(_layout);
    }

    @Override
    public boolean requiresTargetTemplate()
    {
        return Layout.Template.equals(_layout);
    }
}
