package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.List;

public class ReverseQuadrantOperation implements LayoutOperation
{
    private PlateType _targetPlateType;

    @Override
    public List<WellLayout> execute(ExecutionContext context)
    {
        Plate sourcePlate = context.sourcePlates().get(0);
        Integer plateRowId = sourcePlate.getRowId();

        List<WellLayout> layouts = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            layouts.add(new WellLayout(_targetPlateType));

        PlateType sourcePlateType = sourcePlate.getPlateType();
        int targetRows = _targetPlateType.getRows();
        int targetCols = _targetPlateType.getColumns();
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
    public void init(Container container, User user, ExecutionContext context, List<? extends PlateType> allPlateTypes) throws ValidationException
    {
        if (context.sourcePlates().size() != 1)
            throw new ValidationException("The reverse quadrant operation requires a single source plate.");

        _targetPlateType = getTargetPlateType(context.sourcePlates().get(0).getPlateType(), allPlateTypes);
    }

    private @NotNull PlateType getTargetPlateType(@NotNull PlateType sourcePlateType, List<? extends PlateType> allPlateTypes) throws ValidationException
    {
        int targetWellCount = sourcePlateType.getWellCount() / 4;

        for (PlateType plateType : allPlateTypes)
        {
            if (!plateType.isArchived() && plateType.getWellCount() == targetWellCount)
                return plateType;
        }

        throw new ValidationException(String.format("Cannot perform reverse quadrant operation on %s plates.", sourcePlateType.getDescription()));
    }
}
