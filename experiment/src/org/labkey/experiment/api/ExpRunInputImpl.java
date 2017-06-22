/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRunInput;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;

import java.util.Date;

/**
 * User: jeckels
 * Date: Oct 31, 2008
 */
public abstract class ExpRunInputImpl<InputType extends AbstractRunInput> extends ExpObjectImpl implements ExpRunInput
{
    protected InputType _input;

    public ExpRunInputImpl(InputType input)
    {
        _input = input;
    }

    public ExpProtocolApplication getTargetApplication()
    {
        return ExperimentService.get().getExpProtocolApplication(_input.getTargetApplicationId());
    }

    public String getRole()
    {
        return _input.getRole();
    }

    @Override
    public final String getLSID()
    {
        return _input.getLSID();
    }

    @Override
    public final void setLSID(String lsid)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void setLSID(Lsid lsid)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public final String getName()
    {
        return _input.getName();
    }

    @Override
    public final void setName(String name)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int getRowId()
    {
        return 0;
    }

    @Override
    public @Nullable URLHelper detailsURL()
    {
        return null;
    }

    @Override
    public final Container getContainer()
    {
        return getTargetApplication().getContainer();
    }

    @Override
    public final void setContainer(Container container)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public final User getCreatedBy()
    {
        return getTargetApplication().getCreatedBy();
    }

    @Override
    public final Date getCreated()
    {
        return getTargetApplication().getCreated();
    }

    @Override
    public final User getModifiedBy()
    {
        return getTargetApplication().getModifiedBy();
    }

    @Override
    public final Date getModified()
    {
        return getTargetApplication().getModified();
    }

    @Override
    public final void save(User user)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void delete(User user)
    {
        throw new UnsupportedOperationException();
    }
}
