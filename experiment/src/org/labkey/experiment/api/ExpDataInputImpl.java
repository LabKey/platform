package org.labkey.experiment.api;

import org.labkey.api.exp.api.ExpDataInput;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.experiment.api.DataInput;
import org.labkey.api.exp.OntologyManager;

public class ExpDataInputImpl implements ExpDataInput
{
    private DataInput _input;
    static public ExpDataInput[] fromInputs(DataInput[] inputs)
    {
        ExpDataInput[] ret = new ExpDataInput[inputs.length];
        for (int i = 0; i < inputs.length; i ++)
        {
            ret[i] = new ExpDataInputImpl(inputs[i]);
        }
        return ret;
    }

    public ExpDataInputImpl(DataInput input)
    {
        _input = input;
    }
    public ExpData getData()
    {
        return ExperimentService.get().getExpData(_input.getDataId());
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
