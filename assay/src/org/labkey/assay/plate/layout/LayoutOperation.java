package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.query.ValidationException;
import org.labkey.assay.plate.model.ReformatOptions;

import java.util.List;

public interface LayoutOperation
{
    default void validate(ReformatOptions.OperationOptions options, @NotNull List<Plate> sourcePlates, PlateType targetPlateType) throws ValidationException
    {
    }

    List<WellLayout> execute(ReformatOptions.OperationOptions options, @NotNull List<Plate> sourcePlates, PlateType targetPlateType);

    default boolean requiresOperationOptions()
    {
        return false;
    }

    default boolean requiresTargetPlateType()
    {
        return false;
    }
}
