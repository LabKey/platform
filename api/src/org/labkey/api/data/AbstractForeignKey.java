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
package org.labkey.api.data;

import java.util.List;

/**
 * User: kevink
 * Date: Nov 29, 2007 5:30:26 PM
 */
public abstract class AbstractForeignKey implements ForeignKey
{
    protected String _schemaName;
    protected String _tableName;
    protected String _columnName;

    protected AbstractForeignKey()
    {
    }
    
    protected AbstractForeignKey(String tableName, String columnName)
    {
        _tableName = tableName;
        _columnName = columnName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    protected void setTableName(String name)
    {
        this._tableName = name;
    }

    public String getLookupTableName()
    {
        if (_tableName == null)
        {
            initTableAndColumnNames();
        }
        return _tableName;
    }

    protected void setColumnName(String columnName)
    {
//        if (_columnName == null)
//        {
//            initTableAndColumnNames();
//        }
        _columnName = columnName;
    }

    public String getLookupColumnName()
    {
        if (_columnName == null)
        {
            initTableAndColumnNames();
        }
        return _columnName;
    }

    private boolean _initNames = false;

    protected void initTableAndColumnNames()
    {
        if (!_initNames)
        {
            _initNames = true;
            TableInfo table = getLookupTableInfo();
            if (table != null)
            {
                if (_tableName == null)
                {
                    _tableName = table.getPublicName();
                    if (_tableName == null)
                        _tableName = table.getAliasName();
                }

                if (_columnName == null)
                {
                    List<String> pkColumns = table.getPkColumnNames();
                    if (pkColumns != null && pkColumns.size() > 0)
                        _columnName = pkColumns.get(0);
                }
            }
        }
    }
}
