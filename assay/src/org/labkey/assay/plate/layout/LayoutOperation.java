package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.query.ValidationException;
import org.labkey.assay.plate.model.ReformatOptions;

import java.util.List;

public interface LayoutOperation
{
    List<WellLayout> execute(ReformatOptions options, @NotNull List<Plate> sourcePlates, PlateType targetPlateType);

    default void init(ReformatOptions options, @NotNull List<Plate> sourcePlates, PlateType targetPlateType, List<? extends PlateType> allPlateTypes) throws ValidationException
    {
    }

    default boolean produceEmptyPlates()
    {
        return false;
    }

    default boolean requiresTargetPlateType()
    {
        return false;
    }
}
