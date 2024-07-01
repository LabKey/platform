package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.query.ValidationException;
import org.labkey.assay.plate.model.ReformatOptions;

import java.util.ArrayList;
import java.util.List;

public class ExpandOperation implements LayoutOperation
{
    @Override
    public List<WellLayout> execute(ReformatOptions.OperationOptions options, @NotNull List<Plate> sourcePlates, PlateType targetPlateType)
    {
        ReformatOptions.FillStrategy fillStrategy = options.getFillStrategy();

        if (ReformatOptions.FillStrategy.reverseQuadrant == fillStrategy)
            return reverseQuadrant(sourcePlates.get(0), targetPlateType);

        throw new UnsupportedOperationException(String.format("ExpandOperation does not support the \"%s\" fill strategy.", fillStrategy));
    }

    private List<WellLayout> reverseQuadrant(@NotNull Plate sourcePlate, PlateType targetPlateType)
    {
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
    public boolean requiresOperationOptions()
    {
        return true;
    }

    @Override
    public boolean requiresTargetPlateType()
    {
        return true;
    }

    @Override
    public void validate(ReformatOptions.OperationOptions options, @NotNull List<Plate> sourcePlates, PlateType targetPlateType) throws ValidationException
    {
        if (!ReformatOptions.FillStrategy.reverseQuadrant.equals(options.getFillStrategy()))
            throw new ValidationException("Reverse quadrant stamping is the only fill strategy supported by this operation.");

        if (sourcePlates.size() != 1)
            throw new ValidationException("Quadrant stamping requires a single source plate.");

        if (targetPlateType.getWellCount() * 4 != sourcePlates.get(0).getPlateType().getWellCount())
            throw new ValidationException("Quadrant stamping only supports target plates types with exactly 1/4 the number of wells.");
    }
}
