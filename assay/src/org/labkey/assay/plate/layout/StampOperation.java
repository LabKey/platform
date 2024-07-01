package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.assay.plate.model.ReformatOptions;

import java.util.ArrayList;
import java.util.List;

public class StampOperation implements LayoutOperation
{
    @Override
    public List<WellLayout> execute(ReformatOptions.OperationOptions options, @NotNull List<Plate> sourcePlates, PlateType targetPlateType)
    {
        List<WellLayout> result = new ArrayList<>();
        for (Plate plate : sourcePlates)
        {
            WellLayout wellLayout = new WellLayout(plate.getPlateType());
            int plateId = plate.getRowId();

            for (int r = 0; r < plate.getRows(); r++)
            {
                for (int c = 0; c < plate.getColumns(); c++)
                    wellLayout.setWell(r, c, plateId, r, c);
            }

            result.add(wellLayout);
        }

        return result;
    }
}
