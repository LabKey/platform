package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.query.ValidationException;
import org.labkey.assay.plate.model.ReformatOptions;

import java.util.ArrayList;
import java.util.List;

public class CompressOperation implements LayoutOperation
{
    private PlateType _sourcePlateType;

    @Override
    public void validateOptions(ReformatOptions.OperationOptions options, @NotNull List<Plate> sourcePlates, PlateType targetPlateType) throws ValidationException
    {
        if (!ReformatOptions.FillStrategy.quadrant.equals(options.getFillStrategy()))
            throw new ValidationException("Quadrant stamping is the only fill strategy supported by this operation.");

        for (Plate plate : sourcePlates)
        {
            if (_sourcePlateType == null)
                _sourcePlateType = plate.getPlateType();
            else if (!_sourcePlateType.equals(plate.getPlateType()))
                throw new ValidationException("Source plate type mismatch. All source plates must be of the same type.");
        }

        if (_sourcePlateType == null)
            throw new ValidationException("Source plate type missing. Unable to determine source plate type.");

        if (_sourcePlateType.getWellCount() * 4 != targetPlateType.getWellCount())
            throw new ValidationException("Quadrant stamping only supports target plates types with exactly 4x the number of wells.");
    }

    @Override
    public List<WellLayout> execute(@NotNull ReformatOptions.OperationOptions options, @NotNull List<Plate> sourcePlates, PlateType targetPlateType)
    {
        List<WellLayout> layouts = new ArrayList<>();
        WellLayout target = null;

        for (int i = 0; i < sourcePlates.size(); i++)
        {
            if (target == null)
                target = new WellLayout(targetPlateType);

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
    public boolean requiresTargetPlateType()
    {
        return true;
    }
}
