package org.labkey.assay.plate.layout;

import org.labkey.api.assay.plate.Plate;

import java.util.ArrayList;
import java.util.List;

public class StampOperation implements LayoutOperation
{
    @Override
    public List<WellLayout> execute(ExecutionContext context)
    {
        List<WellLayout> result = new ArrayList<>();
        for (Plate plate : context.sourcePlates())
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

    @Override
    public boolean produceEmptyPlates()
    {
        return true;
    }
}
