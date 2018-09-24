package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpDataProtocolInput;
import org.labkey.api.exp.api.ExpProtocolInput;
import org.labkey.api.exp.api.ExpProtocolInputCriteria;
import org.labkey.api.security.User;

import java.util.Objects;

public class ExpDataProtocolInputImpl extends ExpProtocolInputImpl<DataProtocolInput, ExpDataImpl>
        implements ExpDataProtocolInput
{
    protected ExpDataProtocolInputImpl(DataProtocolInput obj)
    {
        super(obj);
    }

    @Override
    public String matches(@NotNull User user, @NotNull Container c, @NotNull ExpData data)
    {
        if (data == null)
            return "Data must not be null";

        ExpDataClass dc = getType();
        if (dc != null && !Objects.equals(dc, data.getDataClass()))
            return "Data is not from '" + dc.getName() + "' DataClass";

        ExpProtocolInputCriteria critera = getCriteria();
        if (critera != null)
        {
            String msg = critera.matches(this, user, getContainer(), data);
            if (msg != null)
                return msg;
        }

        // valid
        return null;
    }

    @Override
    public @Nullable ExpDataClassImpl getType()
    {
        Integer dataClassId = _object.getDataClassId();
        if (dataClassId == null)
            return null;

        return ExperimentServiceImpl.get().getDataClass(dataClassId);
    }

    @Override
    public boolean isCompatible(ExpProtocolInput other)
    {
        boolean compatible = super.isCompatible(other);
        if (!compatible)
            return false;

        if (!Objects.equals(this.getType(), ((ExpDataProtocolInputImpl)other).getType()))
            return false;

        return true;
    }
}
