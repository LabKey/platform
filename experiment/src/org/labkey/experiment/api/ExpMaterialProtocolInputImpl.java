/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpMaterialProtocolInput;
import org.labkey.api.exp.api.ExpProtocolInput;
import org.labkey.api.exp.api.ExpProtocolInputCriteria;
import org.labkey.api.exp.api.ExpSampleType;
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

        ExpSampleType st = getType();
        if (st != null && !Objects.equals(st, material.getSampleType()))
            return "Sample is not from '" + st.getName() + "' SampleSet";

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
    public @Nullable ExpSampleTypeImpl getType()
    {
        Integer materialSourceId = _object.getMaterialSourceId();
        if (materialSourceId == null)
            return null;

        return SampleTypeServiceImpl.get().getSampleType(materialSourceId);
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
