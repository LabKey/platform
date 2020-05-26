/*
 * Copyright (c) 2005-2018 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.exp;

import org.labkey.api.data.Container;

import java.io.Serializable;
import java.util.Objects;

/**
 * Utility base class for implementations of {@link Identifiable}
 * User: migra
 * Date: Jun 14, 2005
 */
public class IdentifiableBase implements Identifiable, Serializable
{
    private String _lsid;
    private String _name;
    // some entities copy the exp.object.objectid value
    private Integer objectId;
    protected Container container;

    public IdentifiableBase()
    {
    }

    public IdentifiableBase(String lsid)
    {
        _lsid = lsid;
    }

    public IdentifiableBase(OntologyObject oo)
    {
        _lsid = oo.getObjectURI();
        objectId = oo.getObjectId();
        container = oo.getContainer();
    }

    @Override
    public String getLSID()
    {
        return _lsid;
    }

    public void setLSID(String lsid)
    {
        _lsid = lsid;
    }

    public void setName(String name)
    {
        _name = name;
    }

    @Override
    public String getName()
    {
        if (null == _name && null != getLSID())
            _name = new Lsid(getLSID()).getObjectId();

        return _name;
    }

    public Integer getObjectId()
    {
        return objectId;
    }

    public void setObjectId(Integer objectId)
    {
        if (this.objectId != null && !this.objectId.equals(objectId))
            throw new IllegalStateException("can't change objectId");
        this.objectId = objectId;
    }

    @Override
    public Container getContainer()
    {
        return container;
    }

    // for Table layer
    public void setContainer(Container container)
    {
        this.container = container;
    }


    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdentifiableBase that = (IdentifiableBase) o;
        return _lsid.equals(that._lsid);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(_lsid);
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + ": " + getLSID();
    }
}
