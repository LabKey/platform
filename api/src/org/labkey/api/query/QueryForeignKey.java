/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.util.StringExpressionFactory;

import java.util.Collections;

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

    public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
    {
        TableInfo table = getLookupTableInfo();
        if (table == null)
            return null;
        return table.getDetailsURL(Collections.singletonMap(_lookupKey, parent));
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
