package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.query.ValidationException;
import org.labkey.assay.plate.model.ReformatOptions;

import java.util.ArrayList;
import java.util.List;

public class ReverseQuadrantOperation implements LayoutOperation
{
    @Override
    public List<WellLayout> execute(ReformatOptions options, @NotNull List<Plate> sourcePlates, PlateType targetPlateType)
    {
        Plate sourcePlate = sourcePlates.get(0);
        Integer plateRowId = sourcePlate.getRowId();

        List<WellLayout> layouts = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            layouts.add(new WellLayout(targetPlateType));

        PlateType sourcePlateType = sourcePlate.getPlateType();
        int targetRows = targetPlateType.getRows();
        int targetCols = targetPlateType.getColumns();
        int lastTargetCol = targetCols - 1;
        int lastTargetRow = targetRows - 1;

        for (int r = 0; r < sourcePlateType.getRows(); r++)
        {
            for (int c = 0; c < sourcePlateType.getColumns(); c++)
            {
                int quadrant = 0;
                int colOffset = 0;
                int rowOffset = 0;

                if (r > lastTargetRow)
                {
                    if (c > lastTargetCol)
                    {
                        quadrant = 3;
                        colOffset = targetCols;
                    }
                    else
                        quadrant = 2;

                    rowOffset = targetRows;
                }
                else if (c > lastTargetCol)
                {
                    quadrant = 1;
                    colOffset = targetCols;
                }

                layouts.get(quadrant).setWell(r - rowOffset, c - colOffset, plateRowId, r, c);
            }
        }

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
        if (sourcePlates.size() != 1)
            throw new ValidationException("The reverse quadrant operation requires a single source plate.");

        if (targetPlateType.getWellCount() * 4 != sourcePlates.get(0).getPlateType().getWellCount())
            throw new ValidationException("The reverse quadrant operation only supports target plates types with exactly 1/4 the number of wells.");
    }
}
