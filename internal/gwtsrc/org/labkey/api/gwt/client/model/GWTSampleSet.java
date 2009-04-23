/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

package org.labkey.api.gwt.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.io.Serializable;
import java.util.List;

/**
 * User: jeckels
 * Date: Feb 8, 2008
 */
public class GWTSampleSet implements Serializable, IsSerializable
{
    private String _lsid;
    private String _name;
    private int _rowId;

    private List<String> _columnNames;

    public GWTSampleSet() {}

    public GWTSampleSet(String name, String lsid)
    {
        _name = name;
        _lsid = lsid;
    }

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public List<String> getColumnNames()
    {
        return _columnNames;
    }

    public void setColumnNames(List<String> columnNames)
    {
        _columnNames = columnNames;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof GWTSampleSet)) return false;

        GWTSampleSet that = (GWTSampleSet) o;

        return !(_lsid != null ? !_lsid.equals(that._lsid) : that._lsid != null);
    }

    public int hashCode()
    {
        return (_lsid != null ? _lsid.hashCode() : 0);
    }
}
