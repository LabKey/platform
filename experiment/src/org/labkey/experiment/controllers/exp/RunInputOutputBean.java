/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.experiment.controllers.exp;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;

import java.util.Map;

/**
 * User: jeckels
* Date: Dec 18, 2007
*/
public class RunInputOutputBean
{
    private final Map<ExpMaterial, String> _materials;
    private final Map<ExpData, String> _datas;

    public RunInputOutputBean(@NotNull Map<ExpMaterial, String> materials, @NotNull Map<ExpData, String> datas)
    {
        _materials = materials;
        _datas = datas;
    }

    @NotNull
    public Map<ExpMaterial, String> getMaterials()
    {
        return _materials;
    }

    @NotNull
    public Map<ExpData, String> getDatas()
    {
        return _datas;
    }
}
