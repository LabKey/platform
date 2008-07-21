/*
 * Copyright (c) 2008 LabKey Corporation
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
import org.labkey.api.data.CacheKey;
import org.labkey.api.data.Container;
import org.labkey.api.util.UnexpectedException;/*
 * User: Karl Lum
 * Date: Jul 14, 2008
 * Time: 1:08:03 PM
 */

public class QuerySnapshotDef extends Entity implements Cloneable
{
    public enum Column
    {
        rowid,
        schema,
        queryDefId,
    }

    static public class Key extends CacheKey<QuerySnapshotDef, Column>
    {
        public Key(Container container)
        {
            super(QueryManager.get().getTableInfoQuerySnapshotDef(), QuerySnapshotDef.class, container);
        }

        public void setSchema(String schema)
        {
            addCondition(Column.schema, schema);
        }

        public void setId(int id)
        {
            addCondition(Column.rowid, id);
        }

        public void setQueryDefId(int id)
        {
            addCondition(Column.queryDefId, id);
        }
    }
    private int _rowId;
    private Integer _queryDefId;
    private String _name;
    private String _schema;
    private String _columns;
    private String _filter;

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
}