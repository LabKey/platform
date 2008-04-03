/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.sample;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.util.ResultSetUtil;

/**
 * Bean Class for for SamplePreparation.
 *
 * @author <a href="http://boss.bekk.no/boss/middlegen/"/>Middlegen</a>
 */
public class SamplePreparation
{

    private java.lang.String _ts = null;
    private int _createdBy = 0;
    private java.sql.Timestamp _created = null;
    private int _modifiedBy = 0;
    private java.sql.Timestamp _modified = null;
    private int _preparationId = 0;
    private java.lang.String _name = null;
    private java.lang.String _description = null;

    public SamplePreparation()
    {
    }

    public java.lang.String getTs()
    {
        return _ts;
    }

    public void setTs(java.lang.String ts)
    {
        _ts = ts;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }

    public java.sql.Timestamp getCreated()
    {
        return _created;
    }

    public void setCreated(java.sql.Timestamp created)
    {
        _created = created;
    }

    public int getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(int modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public java.sql.Timestamp getModified()
    {
        return _modified;
    }

    public void setModified(java.sql.Timestamp modified)
    {
        _modified = modified;
    }

    public int getPreparationId()
    {
        return _preparationId;
    }

    public void setPreparationId(int preparationId)
    {
        _preparationId = preparationId;
    }

    public java.lang.String getName()
    {
        return _name;
    }

    public void setName(java.lang.String name)
    {
        _name = name;
    }

    public java.lang.String getDescription()
    {
        return _description;
    }

    public void setDescription(java.lang.String description)
    {
        _description = description;
    }


}
