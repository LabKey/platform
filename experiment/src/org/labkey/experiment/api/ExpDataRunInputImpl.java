/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExperimentService;

public class ExpDataRunInputImpl extends ExpRunInputImpl<DataInput> implements ExpDataRunInput
{
    static public ExpDataRunInput[] fromInputs(DataInput[] inputs)
    {
        ExpDataRunInput[] ret = new ExpDataRunInput[inputs.length];
        for (int i = 0; i < inputs.length; i ++)
        {
            ret[i] = new ExpDataRunInputImpl(inputs[i]);
        }
        return ret;
    }

    public ExpDataRunInputImpl(DataInput input)
    {
        super(input);
    }

    public ExpData getData()
    {
        return ExperimentService.get().getExpData(_input.getDataId());
    }
}
