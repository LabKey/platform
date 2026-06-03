/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.assay.plate.model;

import org.labkey.assay.plate.PlateManager;

import java.util.ArrayList;
import java.util.List;

public class CreatePlateSetOptions extends ReformatOptions.TargetPlateSet
{
    private ReformatOptions.ReformatOperation _operation;
    private List<PlateManager.PlateData> _plates = new ArrayList<>();
    private String _selectionKey;

    public ReformatOptions.ReformatOperation getOperation()
    {
        return _operation;
    }

    public void setOperation(ReformatOptions.ReformatOperation operation)
    {
        _operation = operation;
    }

    public List<PlateManager.PlateData> getPlates()
    {
        return _plates;
    }

    public void setPlates(List<PlateManager.PlateData> plates)
    {
        _plates = plates;
    }

    public String getSelectionKey()
    {
        return _selectionKey;
    }

    public void setSelectionKey(String selectionKey)
    {
        _selectionKey = selectionKey;
    }
}
