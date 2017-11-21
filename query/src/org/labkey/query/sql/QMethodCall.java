/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.data.*;

import java.util.List;
import java.util.ArrayList;
import java.util.ListIterator;


public class QMethodCall extends QExpr
{
    public QMethodCall()
    {

    }

    public void appendSql(SqlBuilder builder, Query query)
    {
        MethodInfo method = getMethod(builder.getDialect());
        if (method == null)
        {
            builder.appendStringLiteral("Unrecognized method " + getField().getFieldKey());
            return;
        }
        List<SQLFragment> arguments = new ArrayList<>();
        for (QNode n : getLastChild().children())
        {
			QExpr expr = (QExpr)n;
            SQLFragment sqlf = expr.getSqlFragment(builder.getDialect(), query);
            if (null == sqlf)
            {
                QueryParseException qpe = new QueryParseException("Unexpected error parsing query near method " + getFirstChild().getTokenText(), null, expr.getLine(), expr.getColumn());
                if (null == query)
                    throw qpe;
                if (query.getParseErrors().isEmpty())
                    query.getParseErrors().add(qpe);
                return;
            }
            arguments.add(sqlf);
        }
        QNode first = getFirstChild();

        if (first instanceof QField && null != ((QField)first).getTable())
        {
            // table.method()
            builder.append(method.getSQL(((QField)first).getRelationColumn().getTable().getAlias(), builder.getDbSchema(), arguments.toArray(new SQLFragment[arguments.size()])));
        }
        else if (method instanceof AbstractQueryMethodInfo)
        {
            // method that supports query parameter
            builder.append(((AbstractQueryMethodInfo)method).getSQL(query, builder.getDialect(), arguments.toArray(new SQLFragment[arguments.size()])));
        }
        else
        {
            // regular method info
            builder.append(method.getSQL(builder.getDialect(), arguments.toArray(new SQLFragment[arguments.size()])));
        }
    }

    public ColumnInfo createColumnInfo(SQLTableInfo table, String alias, Query query)
    {
        MethodInfo method = getMethod(table.getSqlDialect());
        if (method == null)
        {
            return super.createColumnInfo(table, alias, query);
        }
        List<ColumnInfo> arguments = new ArrayList<>();

        for (ListIterator<QNode> it = getLastChild().childList().listIterator(); it.hasNext();)
        {
            QExpr expr = (QExpr)it.next();
            arguments.add(expr.createColumnInfo(table, "arg" + it.previousIndex(), query));
        }
        return method.createColumnInfo(table, arguments.toArray(new ColumnInfo[arguments.size()]), alias);
    }

    public MethodInfo getMethod(SqlDialect d)
    {
        return getField().getMethod(d);
    }

    public void appendSource(SourceBuilder builder)
    {
        getFirstChild().appendSource(builder);
        builder.append("(");
        builder.pushPrefix("");

        for (QNode n : getLastChild().children())
        {
			QExpr child = (QExpr)n;
            child.appendSource(builder);
            builder.nextPrefix(",");
        }
        builder.popPrefix();
        builder.append(")");
    }

    public QField getField()
    {
        if (!(getFirstChild() instanceof QField))
        {
            throw new QueryException("Fields should have been resolved");
        }
        return (QField) getFirstChild();
    }

    public QueryParseException fieldCheck(QNode parent, SqlDialect d)
    {
        if (getMethod(d) == null)
        {
            return new QueryParseException("Unknown method " + getField().getName(), null, getLine(), getColumn());
        }
        return null;
    }

    @Override
    public boolean isConstant()
    {
        // UNDONE MethodInfo.isPure()
        return false;
    }

    @Override @NotNull
    public JdbcType getSqlType()
    {
        MethodInfo method = getMethod(null);        // UNDONE: passthrough queries won't work unless we pass in dialect here
        if (method == null)
            return JdbcType.OTHER;

        if (method instanceof Method.ConvertInfo)
        {
            return ((Method.ConvertInfo)method).getTypeFromArgs(this.getLastChild());
        }

        List<QNode> children = getLastChild().childList();
        int len = children.size();
        JdbcType[] args = new JdbcType[len];
        for (int i=0 ; i<len ; i++)
            args[i] = ((QExpr)children.get(i)).getSqlType();
        return method.getJdbcType(args);
    }
}
