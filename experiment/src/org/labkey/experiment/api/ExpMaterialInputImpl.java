/*
 * Copyright (c) 2006-2007 LabKey Corporation
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

import org.labkey.api.exp.api.ExpMaterialInput;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.experiment.api.MaterialInput;
import org.labkey.api.exp.OntologyManager;

public class ExpMaterialInputImpl implements ExpMaterialInput
{
    static public ExpMaterialInputImpl[] fromInputs(MaterialInput[] inputs)
    {
        ExpMaterialInputImpl[] ret = new ExpMaterialInputImpl[inputs.length];
        for (int i = 0; i < inputs.length; i ++)
        {
            ret[i] = new ExpMaterialInputImpl(inputs[i]);
        }
        return ret;
    }

    MaterialInput _input;
    public ExpMaterialInputImpl(MaterialInput input)
    {
        _input = input;
    }
    public ExpMaterial getMaterial()
    {
        return ExperimentService.get().getExpMaterial(_input.getMaterialId());
    }

    public ExpProtocolApplication getTargetApplication()
    {
        return ExperimentService.get().getExpProtocolApplication(_input.getTargetApplicationId());
    }

    public PropertyDescriptor getPropertyDescriptor()
    {
        if (_input.getPropertyId() == null)
            return null;
        return OntologyManager.getPropertyDescriptor(_input.getPropertyId());
    }
}
