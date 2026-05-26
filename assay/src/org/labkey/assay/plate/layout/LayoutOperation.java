package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.data.WellData;
import org.labkey.assay.plate.model.ReformatOptions;

import java.util.Collection;
import java.util.List;

public interface LayoutOperation
{
    List<WellLayout> execute(ExecutionContext context) throws ValidationException;

    default void init(Container container, User user, ExecutionContext context) throws ValidationException
    {
    }

    default boolean produceEmptyPlates()
    {
        return false;
    }

    default boolean requiresSourcePlates()
    {
        return true;
    }

    default boolean requiresTargetPlateType()
    {
        return false;
    }

    default boolean requiresTargetTemplate()
    {
        return false;
    }

    default boolean supportsFillExistingWells()
    {
        return false;
    }

    default boolean supportsFillPlatesOnly()
    {
        return false;
    }

    record ExecutionContext(
        Container container,
        User user,
        ReformatOptions options,
        List<? extends PlateType> allPlateTypes,
        PlateType targetPlateType,
        List<Plate> sourcePlates,
        Plate targetTemplate,
        List<? extends Plate> targetPlates,
        List<PlateManager.PlateData> targetPlateData,
        Collection<Long> sampleIds,
        WellData.Cache wellDataCache
    )
    {
        public @Nullable PlateType resolvePlateType(Long plateTypeRowId)
        {
            if (allPlateTypes == null || allPlateTypes.isEmpty())
                return null;

            return allPlateTypes.stream().filter(plateType -> plateType.getRowId().equals(plateTypeRowId)).findFirst().orElse(null);
        }
    }
}
