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

import java.util.ArrayList;
import java.util.List;

public class CompressionOperation implements LayoutOperation
{
    public enum Layout
    {
        Column,
        Row
    }

    private final Layout _layout;

    public CompressionOperation(@NotNull Layout layout)
    {
        _layout = layout;
    }

    @Override
    public List<WellLayout> execute(ExecutionContext context)
    {
        List<WellLayout> layouts = new ArrayList<>();
        WellLayout target = null;
        boolean isColumnLayout = Layout.Column.equals(_layout);

        int targetCols = context.targetPlateType().getColumns();
        int targetRows = context.targetPlateType().getRows();

        int targetColIdx = 0;
        int targetRowIdx = 0;

        for (Plate sourcePlate : context.sourcePlates())
        {
            long sourceRowId = sourcePlate.getRowId();
            PlateType sourcePlateType = sourcePlate.getPlateType();

            for (int r = 0; r < sourcePlateType.getRows(); r++)
            {
                for (int c = 0; c < sourcePlateType.getColumns(); c++)
                {
                    if (target == null)
                        target = new WellLayout(context.targetPlateType());

                    if (sourcePlate.getWell(r, c).getSampleId() == null)
                        continue;

                    target.setWell(targetRowIdx, targetColIdx, sourceRowId, r, c);

                    if (isColumnLayout)
                    {
                        targetRowIdx++;
                        if (targetRowIdx == targetRows)
                        {
                            targetRowIdx = 0;
                            targetColIdx++;

                            if (targetColIdx == targetCols)
                            {
                                layouts.add(target);
                                target = null;
                                targetColIdx = 0;
                            }
                        }
                    }
                    else
                    {
                        targetColIdx++;
                        if (targetColIdx == targetCols)
                        {
                            targetColIdx = 0;
                            targetRowIdx++;

                            if (targetRowIdx == targetRows)
                            {
                                layouts.add(target);
                                target = null;
                                targetRowIdx = 0;
                            }
                        }
                    }
                }
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
