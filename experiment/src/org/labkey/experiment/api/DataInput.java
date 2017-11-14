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

import org.labkey.api.exp.LsidType;
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.settings.AppProps;

import static org.labkey.api.util.PageFlowUtil.encode;

/**
 * Bean class for the exp.datainput table.
 */
public class DataInput extends AbstractRunInput
{
    /*package*/static final String NAMESPACE = LsidType.DataInput.name();

    /*package*/static String lsidPrefix()
    {
        return "urn:lsid:" + encode(AppProps.getInstance().getDefaultLsidAuthority()) + ":" + NAMESPACE + ":";
    }

    /*package*/ static String lsid(int inputKey, int targetApplicationId)
    {
        return AbstractRunInput.lsid(NAMESPACE, inputKey, targetApplicationId);
    }

    private int _dataId;

    public DataInput()
    {
        super(ExpDataRunInput.DEFAULT_ROLE);
    }

    @Override
    public String getLSID()
    {
        return lsid(getInputKey(), getTargetApplicationId());
    }

    public int getDataId()
    {
        return _dataId;
    }

    public void setDataId(int id)
    {
        _dataId = id;
    }

    @Override
    protected int getInputKey()
    {
        return _dataId;
    }
}
