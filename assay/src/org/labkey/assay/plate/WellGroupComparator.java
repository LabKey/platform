/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
package org.labkey.assay.plate;

import org.labkey.api.assay.plate.Position;

import java.util.Comparator;

public class WellGroupComparator implements Comparator<WellGroupImpl>
{
    @Override
    public int compare(WellGroupImpl first, WellGroupImpl second)
    {
        Position firstPos = first.getTopLeft();
        Position secondPos = second.getTopLeft();
        if (firstPos == null && secondPos == null)
            return 0;
        if (firstPos == null)
            return -1;
        if (secondPos == null)
            return 1;
        int comp = firstPos.getColumn() - secondPos.getColumn();
        if (comp == 0)
            comp = firstPos.getRow() - secondPos.getRow();
        return comp;
    }
}
