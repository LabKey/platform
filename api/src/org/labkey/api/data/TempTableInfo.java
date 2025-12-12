/*
 * Copyright (c) 2010-2019 LabKey Corporation
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

public class TempTableInfo extends SchemaTableInfo
{
    private TempTableTracker _ttt;

    public TempTableInfo(String name, List<ColumnInfo> cols, List<String> pk)
    {
        this(DbSchema.getTemp(), name, cols, pk);
    }

    public TempTableInfo(DbSchema schema, String name, List<ColumnInfo> cols, List<String> pk)
    {
        super(schema, DatabaseTableType.TABLE, name, name,
                new SQLFragment().appendIdentifier(schema.getName()).append(".").appendIdentifier(name + "$" + new GUID().toStringNoDashes()));

        // make sure TempTableTracker is initialized _before_ caller executes CREATE TABLE
        TempTableTracker.init();

        for (var col : cols)
        {
            ((BaseColumnInfo)col).setParentTable(this);
            addColumn(((BaseColumnInfo)col));
        }

        if (pk != null)
            setPkColumnNames(pk);
    }

    public String getTempTableName()
    {
        return getSelectName();
    }

    /** Call this method when table is physically created */
    public void track()
    {
        // Remove the schema name and dot
        String tableName = getTempTableName().substring(getSchema().getName().length() + 1);
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
