package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.query.ValidationException;
import org.labkey.assay.plate.model.ReformatOptions;

import java.util.ArrayList;
import java.util.List;

public class ColumnCompressionOperation implements LayoutOperation
{
    @Override
    public List<WellLayout> execute(ReformatOptions options, @NotNull List<Plate> sourcePlates, PlateType targetPlateType)
    {
        List<WellLayout> layouts = new ArrayList<>();
        WellLayout target = null;

        int targetCols = targetPlateType.getColumns();
        int targetRows = targetPlateType.getRows();

        int targetColIdx = 0;
        int targetRowIdx = 0;

        for (Plate sourcePlate : sourcePlates)
        {
            int sourceRowId = sourcePlate.getRowId();
            PlateType sourcePlateType = sourcePlate.getPlateType();

            for (int r = 0; r < sourcePlateType.getRows(); r++)
            {
                for (int c = 0; c < sourcePlateType.getColumns(); c++)
                {
                    if (target == null)
                        target = new WellLayout(targetPlateType);

                    target.setWell(targetRowIdx, targetColIdx, sourceRowId, r, c);

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
            }
        }

        if (target != null)
            layouts.add(target);

        return layouts;
    }

    @Override
    public boolean requiresTargetPlateType()
    {
        return true;
    }

    @Override
    public void validate(ReformatOptions options, @NotNull List<Plate> sourcePlates, PlateType targetPlateType) throws ValidationException
    {
    }
}
