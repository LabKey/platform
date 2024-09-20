package org.labkey.assay.plate.layout;

import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.assay.plate.data.WellData;
import org.labkey.assay.plate.model.ReformatOptions;

import java.util.List;

public interface LayoutOperation
{
    List<WellLayout> execute(ExecutionContext context) throws ValidationException;

    default void init(Container container, User user, ExecutionContext context, List<? extends PlateType> allPlateTypes) throws ValidationException
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

    default boolean requiresTargetTemplate()
    {
        return false;
    }

    record ExecutionContext(
        ReformatOptions options,
        List<Plate> sourcePlates,
        PlateType targetPlateType,
        Plate targetTemplate,
        List<WellData> targetTemplateWellData
    ) {};
}
