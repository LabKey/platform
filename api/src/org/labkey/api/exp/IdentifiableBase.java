/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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

import java.io.Serializable;

/**
 * Utility base class for implementations of {@link Identifiable}
 * User: migra
 * Date: Jun 14, 2005
 */
public class IdentifiableBase implements Identifiable, Serializable
{
    private String _lsid;
    private String _name;

    public IdentifiableBase()
    {
    }

    public IdentifiableBase(String lsid)
    {
        this();
        this._lsid = lsid;
    }

    public String getLSID()
    {
        return _lsid;
    }

    public void setLSID(String lsid)
    {
        this._lsid = lsid;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getName()
    {
        if (null == _name && null != getLSID())
            _name = new Lsid(getLSID()).getObjectId();

        return _name;
    }
}
