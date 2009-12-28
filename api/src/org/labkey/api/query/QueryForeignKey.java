/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.api.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.StringExpression;

public class QueryForeignKey implements ForeignKey
{
    TableInfo _table;
    String _schemaName;
    String _tableName;
    String _lookupKey;
    String _displayField;
    QuerySchema _schema;

    public QueryForeignKey(QuerySchema schema, String tableName, String lookupKey, String displayField)
    {
        if (schema instanceof UserSchema)
            _schemaName = ((UserSchema)schema).getSchemaName();
        _schema = schema;
        _tableName = tableName;
        _lookupKey = lookupKey;
        _displayField = displayField;
    }

    public QueryForeignKey(TableInfo table, String lookupKey, String displayField)
    {
        _table = table;
        _tableName = table.getName();
        _lookupKey = lookupKey;
        _displayField = displayField;
    }

    public ColumnInfo createLookupColumn(ColumnInfo foreignKey, String displayField)
    {
        TableInfo lookupTable = getLookupTableInfo();
        if (null == lookupTable)
            return null;
        if (displayField == null)
        {
            displayField = _displayField;
            if (displayField == null)
            {
                displayField = lookupTable.getTitleColumn();
            }
            if (displayField == null)
                return null;
            if (displayField.equals(_lookupKey))
            {
                return foreignKey;
            }
        }
        return LookupColumn.create(foreignKey, lookupTable.getColumn(_lookupKey), lookupTable.getColumn(displayField), true);
    }

    public String getLookupContainerId()
    {
        return null;
    }

    public TableInfo getLookupTableInfo()
    {
        if (_table == null && _schema != null)
        {
            _table = _schema.getTable(_tableName);
        }
        return _table;
    }

    public String getLookupSchemaName()
    {
        return _schemaName;
    }

    public StringExpression getURL(ColumnInfo parent)
    {
        TableInfo table = getLookupTableInfo();
        if (table == null)
            return null;
        return LookupForeignKey.getDetailsURL(parent, table, _lookupKey);
    }

    public String getLookupTableName()
    {
        return _tableName;
    }

    public String getLookupColumnName()
    {
        return _lookupKey;
    }
}
