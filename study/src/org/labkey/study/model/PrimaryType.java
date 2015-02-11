/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
package org.labkey.study.model;

import org.labkey.api.data.Container;/*
 * User: brittp
 * Date: Dec 18, 2008
 * Time: 12:48:03 PM
 */

import java.util.Map;

public class PrimaryType extends AbstractStudyCachable<PrimaryType>
{
    private long _rowId; // serial NOT NULL,
    private Container _container; // entityid NOT NULL,
    private String _primaryTypeLDMSCode; // character varying(5),
    private String _primaryTypeLabwareCode; // character varying(5),
    private String _primaryType; // character varying(100),
    private int _externalId; // integer NOT NULL default 0

    public PrimaryType(Container container, Map<String, Object> map)
    {
        super();
        _rowId = (int) map.get(PrimaryTypeDomainKind.ROWID);
        _container = container;
        _primaryTypeLDMSCode = (String) map.get(PrimaryTypeDomainKind.PRIMARYLDMSCODE);
        _primaryTypeLabwareCode = (String) map.get(PrimaryTypeDomainKind.PRIMARYLABWARECODE);
        _primaryType = (String) map.get(PrimaryTypeDomainKind.PRIMARYTYPE);
        _externalId = (int) map.get(PrimaryTypeDomainKind.EXTERNALID);
    }

    public Object getPrimaryKey()
    {
        return _rowId;
    }

    public long getRowId()
    {
        return _rowId;
    }

    public void setRowId(long rowId)
    {
        _rowId = rowId;
    }

    public int getExternalId()
    {
        return _externalId;
    }

    public void setExternalId(int externalId)
    {
        _externalId = externalId;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public String getPrimaryTypeLDMSCode()
    {
        return _primaryTypeLDMSCode;
    }

    public void setPrimaryTypeLDMSCode(String primaryTypeLDMSCode)
    {
        _primaryTypeLDMSCode = primaryTypeLDMSCode;
    }

    public String getPrimaryTypeLabwareCode()
    {
        return _primaryTypeLabwareCode;
    }

    public void setPrimaryTypeLabwareCode(String primaryTypeLabwareCode)
    {
        _primaryTypeLabwareCode = primaryTypeLabwareCode;
    }

    public String getPrimaryType()
    {
        return _primaryType;
    }

    public void setPrimaryType(String primaryType)
    {
        _primaryType = primaryType;
    }
}