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
