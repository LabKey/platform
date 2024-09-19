package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.query.ValidationException;
import org.labkey.assay.plate.data.WellData;
import org.labkey.assay.plate.model.ReformatOptions;

import java.util.ArrayList;
import java.util.List;

public class QuadrantOperation implements LayoutOperation
{
    private PlateType _sourcePlateType;
    private PlateType _targetPlateType;

    @Override
    public List<WellLayout> execute(ReformatOptions options, @NotNull List<Plate> sourcePlates, PlateType targetPlateType, Plate targetTemplate, List<WellData> targetTemplateWellData)
    {
        List<WellLayout> layouts = new ArrayList<>();
        WellLayout target = null;

        for (int i = 0; i < sourcePlates.size(); i++)
        {
            if (target == null)
                target = new WellLayout(_targetPlateType);

            Plate sourcePlate = sourcePlates.get(i);
            Integer plateRowId = sourcePlate.getRowId();

            int quadrant = i % 4;
            int rowOffset = 0;
            int colOffset = 0;

            if (quadrant == 1)
                colOffset = _sourcePlateType.getColumns();
            else if (quadrant == 2)
                rowOffset = _sourcePlateType.getRows();
            else if (quadrant == 3)
            {
                rowOffset = _sourcePlateType.getRows();
                colOffset = _sourcePlateType.getColumns();
            }

            for (int r = 0; r < _sourcePlateType.getRows(); r++)
            {
                for (int c = 0; c < _sourcePlateType.getColumns(); c++)
                    target.setWell(r + rowOffset, c + colOffset, plateRowId, r, c);
            }

            if ((i + 1) % 4 == 0)
            {
                layouts.add(target);
                target = null;
            }
        }

        if (target != null)
            layouts.add(target);

        return layouts;
    }

    @Override
    public void init(ReformatOptions options, @NotNull List<Plate> sourcePlates, PlateType targetPlateType, Plate targetTemplate, List<? extends PlateType> allPlateTypes) throws ValidationException
    {
        _sourcePlateType = getSourcePlateType(sourcePlates);
        _targetPlateType = getTargetPlateType(_sourcePlateType, allPlateTypes);
    }

    private @NotNull PlateType getSourcePlateType(@NotNull List<Plate> sourcePlates) throws ValidationException
    {
        PlateType sourcePlateType = null;

        for (Plate plate : sourcePlates)
        {
            if (sourcePlateType == null)
                sourcePlateType = plate.getPlateType();
            else if (!sourcePlateType.equals(plate.getPlateType()))
                throw new ValidationException("Source plate type mismatch. All source plates must be of the same type.");
        }

        if (sourcePlateType == null)
            throw new ValidationException("Source plate type missing. Unable to determine source plate type.");

        return sourcePlateType;
    }

    private @NotNull PlateType getTargetPlateType(@NotNull PlateType sourcePlateType, List<? extends PlateType> allPlateTypes) throws ValidationException
    {
        int targetWellCount = sourcePlateType.getWellCount() * 4;

        for (PlateType plateType : allPlateTypes)
        {
            if (!plateType.isArchived() && plateType.getWellCount() == targetWellCount)
                return plateType;
        }

        throw new ValidationException(String.format("Cannot perform quadrant operation on %s plates.", sourcePlateType.getDescription()));
    }
}
