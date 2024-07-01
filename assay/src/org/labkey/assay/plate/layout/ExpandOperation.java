package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.assay.plate.model.ReformatOptions;

import java.util.List;

public class ExpandOperation implements LayoutOperation
{
    @Override
    public List<WellLayout> execute(@NotNull ReformatOptions.OperationOptions options, @NotNull List<Plate> sourcePlates, PlateType targetPlateType)
    {
        return List.of();
    }
}
