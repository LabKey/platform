/*
 * Copyright (c) 2024-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.List;

public class QuadrantOperation implements LayoutOperation
{
    private PlateType _sourcePlateType;
    private PlateType _targetPlateType;

    @Override
    public List<WellLayout> execute(ExecutionContext context)
    {
        List<WellLayout> layouts = new ArrayList<>();
        WellLayout target = null;

        for (int i = 0; i < context.sourcePlates().size(); i++)
        {
            if (target == null)
                target = new WellLayout(_targetPlateType);

            Plate sourcePlate = context.sourcePlates().get(i);
            Long plateRowId = sourcePlate.getRowId();

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
    public void init(Container container, User user, ExecutionContext context) throws ValidationException
    {
        _sourcePlateType = getSourcePlateType(context.sourcePlates());
        _targetPlateType = getTargetPlateType(_sourcePlateType, context.allPlateTypes());
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
