/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.labkey.api.exp.api.ExpDataProtocolInput;
import org.labkey.api.exp.api.ExpDataRunInput;

import java.util.ArrayList;
import java.util.List;

public class ExpDataRunInputImpl extends ExpRunInputImpl<DataInput> implements ExpDataRunInput
{
    static public List<ExpDataRunInputImpl> fromInputs(List<DataInput> inputs)
    {
        List<ExpDataRunInputImpl> ret = new ArrayList<>(inputs.size());
        for (DataInput input : inputs)
        {
            ret.add(new ExpDataRunInputImpl(input));
        }
        return ret;
    }

    // For serialization
    protected ExpDataRunInputImpl() {}

    public ExpDataRunInputImpl(DataInput input)
    {
        super(input);
    }

    @Override
    public ExpDataImpl getData()
    {
        return ExperimentServiceImpl.get().getExpData(_input.getDataId());
    }

    @Override
    public ExpDataProtocolInput getProtocolInput()
    {
        Integer protocolInputId = _input.getProtocolInputId();
        if (protocolInputId == null)
            return null;

        return ExperimentServiceImpl.get().getDataProtocolInput(protocolInputId);
    }

}
