/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
 * Bean Class for for SampleType.
 *
 * @author <a href="http://boss.bekk.no/boss/middlegen/"/>Middlegen</a>
 */
public class SampleType
{

    private int _sampleTypeId = 0;
    private java.lang.String _sampleType = null;
    private java.sql.Timestamp _created = null;
    private boolean _lymphoid = false;
    private int _selectTiss = 0;

    public SampleType()
    {
    }

    public int getSampleTypeId()
    {
        return _sampleTypeId;
    }

    public void setSampleTypeId(int sampleTypeId)
    {
        _sampleTypeId = sampleTypeId;
    }

    public java.lang.String getSampleType()
    {
        return _sampleType;
    }

    public void setSampleType(java.lang.String sampleType)
    {
        _sampleType = sampleType;
    }

    public java.sql.Timestamp getCreated()
    {
        return _created;
    }

    public void setCreated(java.sql.Timestamp created)
    {
        _created = created;
    }

    public boolean getLymphoid()
    {
        return _lymphoid;
    }

    public void setLymphoid(boolean lymphoid)
    {
        _lymphoid = lymphoid;
    }

    public int getSelectTiss()
    {
        return _selectTiss;
    }

    public void setSelectTiss(int selectTiss)
    {
        _selectTiss = selectTiss;
    }


}
