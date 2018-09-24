package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpMaterialProtocolInput;
import org.labkey.api.exp.api.ExpProtocolInput;
import org.labkey.api.exp.api.ExpProtocolInputCriteria;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.security.User;

import java.util.Objects;

public class ExpMaterialProtocolInputImpl extends ExpProtocolInputImpl<MaterialProtocolInput, ExpMaterialImpl>
        implements ExpMaterialProtocolInput
{
    protected ExpMaterialProtocolInputImpl(MaterialProtocolInput obj)
    {
        super(obj);
    }

    @Override
    public String matches(@NotNull User user, @NotNull Container c, @NotNull ExpMaterial material)
    {
        if (material == null)
            return "Sample must not be null";

        ExpSampleSet ss = getType();
        if (ss != null && !Objects.equals(ss, material.getSampleSet()))
            return "Sample is not from '" + ss.getName() + "' SampleSet";

        ExpProtocolInputCriteria critera = getCriteria();
        if (critera != null)
        {
            String msg = critera.matches(this, user, getContainer(), material);
            if (msg != null)
                return msg;
        }

        // valid
        return null;
    }


    @Override
    public @Nullable ExpSampleSetImpl getType()
    {
        Integer materialSourceId = _object.getMaterialSourceId();
        if (materialSourceId == null)
            return null;

        return ExperimentServiceImpl.get().getSampleSet(materialSourceId);
    }

    @Override
    public boolean isCompatible(ExpProtocolInput other)
    {
        boolean compatible = super.isCompatible(other);
        if (!compatible)
            return false;

        if (!Objects.equals(this.getType(), ((ExpMaterialProtocolInputImpl)other).getType()))
            return false;

        return true;
    }

}
