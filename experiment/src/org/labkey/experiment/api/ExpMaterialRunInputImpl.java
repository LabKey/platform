/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.experiment.api;

import org.labkey.api.exp.api.ExpMaterialRunInput;

import java.util.ArrayList;
import java.util.List;

public class ExpMaterialRunInputImpl extends ExpRunInputImpl<MaterialInput> implements ExpMaterialRunInput
{
    static public List<ExpMaterialRunInputImpl> fromInputs(List<MaterialInput> inputs)
    {
        List<ExpMaterialRunInputImpl> ret = new ArrayList<>(inputs.size());
        for (MaterialInput input : inputs)
        {
            ret.add(new ExpMaterialRunInputImpl(input));
        }
        return ret;
    }

    public ExpMaterialRunInputImpl(MaterialInput input)
    {
        super(input);
    }
    
    public ExpMaterialImpl getMaterial()
    {
        return ExperimentServiceImpl.get().getExpMaterial(_input.getMaterialId());
    }
}
