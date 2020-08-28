/*
 * Copyright (c) 2018 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.api.ExpObject;

public class DataProtocolInput extends AbstractProtocolInput
{
    protected Integer _dataClassId;

    public Integer getDataClassId()
    {
        return _dataClassId;
    }

    public void setDataClassId(Integer dataClassId)
    {
        _dataClassId = dataClassId;
    }

    @Override
    public String getObjectType()
    {
        return ExpDataImpl.DEFAULT_CPAS_TYPE;
    }

    @Override
    public @Nullable ExpDataProtocolInputImpl getExpObject()
    {
        return new ExpDataProtocolInputImpl(this);
    }
}
