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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolInput;
import org.labkey.api.exp.api.ExpProtocolInputCriteria;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.Date;

public abstract class ExpProtocolInputImpl<T extends AbstractProtocolInput, I extends ExpRunItem>
        extends ExpIdentifiableBaseImpl<T> implements ExpProtocolInput
{
    public static ExpProtocolInputImpl fromRowId(int rowId)
    {
        return ExperimentServiceImpl.get().getProtocolInput(rowId);
    }

    protected ExpProtocol _protocol;
    protected ExpProtocolInputCriteria _criteria;

    protected ExpProtocolInputImpl(T obj)
    {
        super(obj);
    }

    public int getRowId()
    {
        return _object.getRowId();
    }

    public ActionURL detailsURL()
    {
        return null;
    }

    public Date getCreated()
    {
        ExpProtocol protocol = getProtocol();
        return null == protocol ? null : protocol.getCreated();
    }

    public User getCreatedBy()
    {
        ExpProtocol protocol = getProtocol();
        return null == protocol ? null : protocol.getCreatedBy();
    }

    public User getModifiedBy()
    {
        ExpProtocol protocol = getProtocol();
        return null == protocol ? null : protocol.getModifiedBy();
    }

    public Date getModified()
    {
        ExpProtocol protocol = getProtocol();
        return null == protocol ? null : protocol.getModified();
    }

    public Container getContainer()
    {
        ExpProtocol protocol = getProtocol();
        return null == protocol ? null : protocol.getContainer();
    }

    public void setContainer(Container container)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public ExpProtocol getProtocol()
    {
        if (_protocol == null)
            _protocol = ExperimentServiceImpl.get().getExpProtocol(_object.getProtocolId());
        return _protocol;
    }

    /* package */ void setProtocol(ExpProtocol protocol)
    {
        _protocol = protocol;
    }

    @Override
    public boolean isInput()
    {
        return _object.isInput();
    }

    @Override
    public @Nullable ExpProtocolInputCriteria getCriteria()
    {
        if (_criteria != null)
            return _criteria;

        String criteriaName = _object.getCriteriaName();
        if (criteriaName == null || criteriaName.isEmpty())
            return null;

        _criteria = ExperimentServiceImpl.get().createProtocolInputCriteria(criteriaName, _object.getCriteriaConfig());
        return _criteria;
    }

    @Override
    public int getMinOccurs()
    {
        return _object.getMinOccurs();
    }

    @Override
    public @Nullable Integer getMaxOccurs()
    {
        return _object.getMaxOccurs();
    }

    @Override
    public boolean isCompatible(ExpProtocolInput other)
    {
        if (getClass() != other.getClass())
            return false;

        ExpProtocolInputImpl pi = (ExpProtocolInputImpl)other;

        // Check criteria as well
        ExpProtocolInputCriteria otherCriteria = pi.getCriteria();
        ExpProtocolInputCriteria criteria = getCriteria();
        if ((criteria == null && otherCriteria != null) || (criteria != null && otherCriteria == null))
            return false;

        if (criteria != null && !criteria.isCompatible(otherCriteria))
            return false;

        return true;
    }


    protected final TableInfo getTinfo()
    {
        return ExperimentServiceImpl.get().getTinfoProtocolInput();
    }

    @Override
    public void save(User user)
    {
        AbstractProtocolInput obj = getDataObject();
        if (obj.getProtocolId() == 0)
        {
            // The protocol input was created before the protocol was saved.  Attempt to set it now
            if (_protocol != null)
            {
                if (_protocol.getRowId() == 0)
                    throw new IllegalStateException("ExpProtocol must be saved prior to saving the protocol input");
                obj.setProtocolId(_protocol.getRowId());
            }
        }

        save(user, getTinfo(), false);
    }

    @Override
    public void delete(User user)
    {
        if (getRowId() != 0)
        {
            Table.delete(getTinfo(), getRowId());
        }
    }
}
