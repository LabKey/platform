/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.IdentifiableBase;
import org.labkey.api.exp.Lsid;
import org.labkey.api.security.User;

/**
 * User: jeckels
 * Date: Sep 26, 2007
 */
public abstract class ExpIdentifiableBaseImpl<Type extends IdentifiableBase> extends ExpObjectImpl
{
    protected Type _object;
    
    public ExpIdentifiableBaseImpl(Type object)
    {
        _object = object;
    }

    public String getLSID()
    {
        return _object.getLSID();
    }

    public Type getDataObject()
    {
        return _object;
    }

    public void setLSID(Lsid lsid)
    {
        ensureUnlocked();
        setLSID(lsid == null ? null : lsid.toString());
    }

    public void setLSID(String lsid)
    {
        ensureUnlocked();
        _object.setLSID(lsid);
    }

    public String getName()
    {
        return _object.getName();
    }

    public void setName(String name)
    {
        ensureUnlocked();
        _object.setName(name);
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ExpIdentifiableBaseImpl that = (ExpIdentifiableBaseImpl) o;

        return !(_object.getLSID() != null ? !_object.getLSID().equals(that._object.getLSID()) : that._object.getLSID() != null);
    }

    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + (_object.getLSID() != null ? _object.getLSID().hashCode() : 0);
        return result;
    }

    protected void save(User user, TableInfo table)
    {
        if (getRowId() == 0)
        {
            _object = Table.insert(user, table, _object);
        }
        else
        {
            _object = Table.update(user, table, _object, getRowId());
        }
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + ": " + _object.getName() + " - " + _object.getLSID();
    }
}
