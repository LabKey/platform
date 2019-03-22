/*
 * Copyright (c) 2010-2015 LabKey Corporation
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
package org.labkey.api.data;

import org.labkey.api.util.GUID;

import java.util.List;

/**
* User: matt
* Date: Oct 23, 2010
* Time: 3:08:13 PM
*/
public class TempTableInfo extends SchemaTableInfo
{
    private final String _tempTableName;

    private TempTableTracker _ttt;

    public TempTableInfo(String name, List<ColumnInfo> cols, List<String> pk)
    {
        this(DbSchema.getTemp(), name, cols, pk);
    }

    private TempTableInfo(DbSchema schema, String name, List<ColumnInfo> cols, List<String> pk)
    {
        super(schema, DatabaseTableType.TABLE, name, name, schema.getName() + "." + name + "$" + new GUID().toStringNoDashes());

        // TODO: Do away with _tempTableName?  getSelectName() is synonymous.
        _tempTableName = getSelectName();

        for (ColumnInfo col : cols)
        {
            col.setParentTable(this);
            addColumn(col);
        }

        if (pk != null)
            setPkColumnNames(pk);
    }

    public String getTempTableName()
    {
        return _tempTableName;
    }


    /** Call this method when table is physically created */
    public void track()
    {
        // Remove the schema name and dot
        String tableName = _tempTableName.substring(getSchema().getName().length() + 1);
        _ttt = TempTableTracker.track(tableName, this);
    }

    public boolean isTracking()
    {
        return null != _ttt;
    }

    public void delete()
    {
        _ttt.delete();
    }

    public boolean verify()
    {
        try
        {
            new TableSelector(this).exists();
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
