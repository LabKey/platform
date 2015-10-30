/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
package org.labkey.query.persist;

import org.labkey.api.data.Entity;
import org.labkey.api.util.UnexpectedException;

import java.util.Date;

/*
 * User: Karl Lum
 * Date: Jul 14, 2008
 * Time: 1:08:03 PM
 */

public class QuerySnapshotDef extends Entity implements Cloneable
{
    private int _rowId;
    private Integer _queryDefId;
    private String _name;
    private String _schema;
    private String _columns;
    private String _filter;
    private Date _lastUpdated;
    private Date _nextUpdate;
    private int _updateDelay;
    private String _queryTableName;
    private String _queryTableContainer;
    private String _participantGroups;
    private Integer _optionsId;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public Integer getQueryDefId()
    {
        return _queryDefId;
    }

    public void setQueryDefId(Integer queryDefId)
    {
        _queryDefId = queryDefId;
    }

    public String getSchema()
    {
        return _schema;
    }

    public void setSchema(String schema)
    {
        _schema = schema;
    }

    public String getColumns()
    {
        return _columns;
    }

    public void setColumns(String columns)
    {
        _columns = columns;
    }

    public String getFilter()
    {
        return _filter;
    }

    public void setFilter(String filter)
    {
        _filter = filter;
    }

    public Date getLastUpdated()
    {
        return _lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated)
    {
        _lastUpdated = lastUpdated;
    }

    public Date getNextUpdate()
    {
        return _nextUpdate;
    }

    public void setNextUpdate(Date nextUpdate)
    {
        _nextUpdate = nextUpdate;
    }

    public int getUpdateDelay()
    {
        return _updateDelay;
    }

    public void setUpdateDelay(int updateDelay)
    {
        _updateDelay = updateDelay;
    }

    public String getQueryTableName()
    {
        return _queryTableName;
    }

    public void setQueryTableName(String queryTableName)
    {
        _queryTableName = queryTableName;
    }

    public String getQueryTableContainer()
    {
        return _queryTableContainer;
    }

    public void setQueryTableContainer(String queryTableContainer)
    {
        _queryTableContainer = queryTableContainer;
    }

    public String getParticipantGroups()
    {
        return _participantGroups;
    }

    public void setParticipantGroups(String participantGroups)
    {
        _participantGroups = participantGroups;
    }

    public Integer getOptionsId()
    {
        return _optionsId;
    }

    public void setOptionsId(Integer optionsId)
    {
        _optionsId = optionsId;
    }

    public QuerySnapshotDef clone()
    {
        try
        {
            return (QuerySnapshotDef) super.clone();
        }
        catch (CloneNotSupportedException cnse)
        {
            throw UnexpectedException.wrap(cnse);
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        QuerySnapshotDef that = (QuerySnapshotDef) o;

        if (_rowId != that._rowId) return false;
        if (_updateDelay != that._updateDelay) return false;
        if (_queryDefId != null ? !_queryDefId.equals(that._queryDefId) : that._queryDefId != null) return false;
        if (_name != null ? !_name.equals(that._name) : that._name != null) return false;
        if (_schema != null ? !_schema.equals(that._schema) : that._schema != null) return false;
        if (_columns != null ? !_columns.equals(that._columns) : that._columns != null) return false;
        if (_filter != null ? !_filter.equals(that._filter) : that._filter != null) return false;
        if (_lastUpdated != null ? !_lastUpdated.equals(that._lastUpdated) : that._lastUpdated != null) return false;
        if (_nextUpdate != null ? !_nextUpdate.equals(that._nextUpdate) : that._nextUpdate != null) return false;
        if (_queryTableName != null ? !_queryTableName.equals(that._queryTableName) : that._queryTableName != null)
            return false;
        if (_queryTableContainer != null ? !_queryTableContainer.equals(that._queryTableContainer) : that._queryTableContainer != null)
            return false;
        if (_participantGroups != null ? !_participantGroups.equals(that._participantGroups) : that._participantGroups != null)
            return false;
        return !(_optionsId != null ? !_optionsId.equals(that._optionsId) : that._optionsId != null);

    }

    @Override
    public int hashCode()
    {
        int result = _rowId;
        result = 31 * result + (_queryDefId != null ? _queryDefId.hashCode() : 0);
        result = 31 * result + (_name != null ? _name.hashCode() : 0);
        result = 31 * result + (_schema != null ? _schema.hashCode() : 0);
        result = 31 * result + (_columns != null ? _columns.hashCode() : 0);
        result = 31 * result + (_filter != null ? _filter.hashCode() : 0);
        result = 31 * result + (_lastUpdated != null ? _lastUpdated.hashCode() : 0);
        result = 31 * result + (_nextUpdate != null ? _nextUpdate.hashCode() : 0);
        result = 31 * result + _updateDelay;
        result = 31 * result + (_queryTableName != null ? _queryTableName.hashCode() : 0);
        result = 31 * result + (_queryTableContainer != null ? _queryTableContainer.hashCode() : 0);
        result = 31 * result + (_participantGroups != null ? _participantGroups.hashCode() : 0);
        result = 31 * result + (_optionsId != null ? _optionsId.hashCode() : 0);
        return result;
    }
}