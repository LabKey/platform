/*
 * Copyright (c) 2006-2007 LabKey Corporation
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

package org.labkey.query.sql;

import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.MethodInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.AliasedColumn;

import java.sql.Types;

import org.labkey.query.data.SQLTableInfo;

public class QField extends QInternalExpr
{
    TableInfo _table;
    String _name;
    ColumnInfo _column;

    private QField(QNode orig)
    {
        setLineAndColumn(orig);
    }

    public QField(TableInfo table, String name, QNode orig)
    {
        this(orig);
        _table = table;
        if (table == null)
        {
            table = table;
        }
        _name = name;
    }

    public QField(ColumnInfo column, QNode orig)
    {
        this(orig);
        if (column == null)
            return;
        _table = column.getParentTable();
        _name = column.getName();
        _column = column;
    }

    public ColumnInfo getColumnInfo()
    {
        if (_column != null)
            return _column;
        if (_table == null)
            return null;
        _column = _table.getColumn(_name);
        return _column;
    }

    public void appendSql(SqlBuilder builder)
    {
        ColumnInfo col = getColumnInfo();
        if (col == null)
        {
            builder.append("~~error~~");
            return;
        }
        builder.append(getColumnInfo().getValueSql());
    }

    public TableInfo getTable()
    {
        return _table;
    }

    public String getName()
    {
        return _name;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final QField qField = (QField) o;

        if (!_name.equals(qField._name)) return false;
        if (!_table.equals(qField._table)) return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = _table == null ? 0 : _table.hashCode();
        result = 29 * result + _name.hashCode();
        return result;
    }

    public int getSqlType()
    {
        if (_column != null)
            return _column.getSqlTypeInt();
        ColumnInfo col = getTable().getColumn(getName());
        if (col == null)
            return Types.OTHER;
        return col.getSqlTypeInt();
    }

    public ColumnInfo createColumnInfo(SQLTableInfo table, String alias)
    {
        ColumnInfo baseColumn = getColumnInfo();
        if (baseColumn == null)
            return null;
        AliasedColumn ret = new AliasedColumn(alias, baseColumn);
        ret.setAlias(alias);
        return ret;
    }

    public MethodInfo getMethod()
    {
        if (_table == null)
        {
            try
            {
                return Method.valueOf(_name.toLowerCase()).getMethodInfo();
            }
            catch (IllegalArgumentException iae)
            {
                return null; 
            }
        }

        return _table.getMethod(FieldKey.fromString(_name).getName());
    }

    public QueryParseException fieldCheck(QNode parent)
    {
        if (parent instanceof QMethodCall)
            return null;
        if (getColumnInfo() == null)
        {
            if (_table == null)
            {
                return new QueryParseException("Field name " + getName() + " must be qualified with a table name.", null, getLine(), getColumn());
            }
            return new QueryParseException("Field name " + getName() + " could not be found.", null, getLine(), getColumn());
        }
        return null;
    }
}
