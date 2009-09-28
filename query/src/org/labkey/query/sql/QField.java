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

package org.labkey.query.sql;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.MethodInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.ExprColumn;

import java.sql.Types;


public class QField extends QInternalExpr
{
    QueryRelation _table;
    String _name;
    QueryRelation.RelationColumn _column;

    private QField(QNode orig)
    {
        if (null != orig)
            setLineAndColumn(orig);
    }

    public QField(QueryRelation table, String name, QNode orig)
    {
        this(orig);
        _table = table;
        _name = name;
    }

    public QField(QueryRelation.RelationColumn column, QNode orig)
    {
        this(orig);
        _table = column.getTable();
        _name = column.getAlias();
        _column = column;
    }


    public QueryRelation.RelationColumn getRelationColumn()
    {
        if (null == _column)
        {
            if (null == _table)
                return null;
            _column = _table.getColumn(_name);
        }
        return _column;
    }


    public void appendSql(SqlBuilder builder)
    {
        QueryRelation.RelationColumn col = getRelationColumn();
        if (null == col)
        {
            if (_table.getParseErrors().size() > 0)
                return;
            String message = "Unexpected error parsing field:" + getSourceText();
            _table.getParseErrors().add(new QueryParseException(message, null, getLine(), getColumn()));
            builder.append("#ERROR: " + message + "#");
            return;
        }
        builder.append(col.getValueSql(_table.getAlias()));
    }


    public QueryRelation getTable()
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
        QueryRelation.RelationColumn col = getTable().getColumn(getName());
        if (col == null)
            return Types.OTHER;
        return col.getSqlTypeInt();
    }


    public ColumnInfo createColumnInfo(SQLTableInfo table, String alias)
    {
        ExprColumn ret = new ExprColumn(table, alias,  getRelationColumn().getValueSql(_table.getAlias()),  getRelationColumn().getSqlTypeInt());
        getRelationColumn().copyColumnAttributesTo(ret);
        return ret;
    }


    public MethodInfo getMethod()
    {
        if (_table == null || !(_table instanceof QueryTable))
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

        return _table.getTableInfo().getMethod(FieldKey.fromString(_name).getName());
    }

    public QueryParseException fieldCheck(QNode parent)
    {
        if (parent instanceof QMethodCall)
            return null;
        if (getRelationColumn() == null)
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
