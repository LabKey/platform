/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MethodInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;

import java.util.Arrays;
import java.util.HashSet;


public class QField extends QInternalExpr
{
    static HashSet<Class> _legalReferants = new HashSet<Class>(Arrays.asList(
            QuerySelect.SelectColumn.class, QJoin.class, QWhere.class, QOrder.class, QGroupBy.class));

    QueryRelation _table;
    String _name;
    QueryRelation.RelationColumn _column;
    private final ReferenceCount refCount = new ReferenceCount(_legalReferants);


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


    @Override
    public void addFieldRefs(Object referant)
    {
        if (0 == refCount.count())
        {
            QueryRelation.RelationColumn col = getRelationColumn();
            if (null != col)
                col.addRef(this);
        }
        refCount.increment(referant);
    }


    public void releaseFieldRefs(Object refer)
    {
        if (0 == refCount.count())
            return;
        refCount.decrement(refer);
        if (0 == refCount.count())
        {
            QueryRelation.RelationColumn col = getRelationColumn();
            if (null != col)
                col.releaseRef(this);
        }
    }


    public QueryRelation.RelationColumn getRelationColumn()
    {
        if (null == _column)
        {
            if (null == _table)
                return null;
            _column = _table.getColumn(_name);
            if (null != _column && 0 < refCount.count())
                _column.addRef(this);
        }
        return _column;
    }


    public void appendSql(SqlBuilder builder, Query query)
    {
        QueryRelation.RelationColumn col = getRelationColumn();
        if (null == col)
        {
            if (null != _table && _table.getParseErrors().size() > 0)
                return;
            String message = "Unexpected error parsing field:" + getSourceText();
            _table.getParseErrors().add(new QueryParseException(message, null, getLine(), getColumn()));
            builder.append("#ERROR: ").append(message).append("#");
            return;
        }
        builder.append(col.getValueSql());
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
        return _table.equals(qField._table);

    }


    public int hashCode()
    {
        int result;
        result = _table == null ? 0 : _table.hashCode();
        result = 29 * result + _name.hashCode();
        return result;
    }


    public @NotNull JdbcType getSqlType()
    {
        if (_column != null)
            return _column.getJdbcType();
        QueryRelation.RelationColumn col = getTable().getColumn(getName());
        if (col == null)
            return JdbcType.OTHER;
        return col.getJdbcType();
    }


    public ColumnInfo createColumnInfo(SQLTableInfo table, String alias, Query query)
    {
        ExprColumn ret = new ExprColumn(table, alias, getRelationColumn().getValueSql(), getRelationColumn().getJdbcType());
        getRelationColumn().copyColumnAttributesTo(ret);
        return ret;
    }


    public MethodInfo getMethod(SqlDialect d)
    {
        if (_table == null || !(_table instanceof QueryTable))
        {
            try
            {
                Method m = Method.resolve(d, _name.toLowerCase());
                if (null != m)
                    return m.getMethodInfo();
                return null;
            }
            catch (IllegalArgumentException iae)
            {
                return null;
            }
        }

        return _table.getMethod(FieldKey.fromString(_name).getName());
    }


    public QueryParseException fieldCheck(QNode parent, SqlDialect d)
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


    @Override
    public boolean isConstant()
    {
        return false;
    }
}
