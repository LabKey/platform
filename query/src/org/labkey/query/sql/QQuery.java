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

import org.labkey.query.data.Query;
import org.labkey.query.data.QueryTableInfo;
import org.labkey.api.data.Table;
import org.labkey.api.query.QueryParseException;

import java.util.List;
import java.util.LinkedList;

public class QQuery extends QExpr
{
    Query _query;

    public QQuery()
    {

    }

    public QQuery(Query query)
    {
        _query = query;
    }

    public Query getQuery()
    {
        return _query;
    }
    
    public QSelect getSelect()
    {
        QSelectFrom selectFrom = getChildOfType(QSelectFrom.class);
        if (selectFrom == null)
            return null;
        return selectFrom.getSelect();
    }
    public QFrom getFrom()
    {
        QSelectFrom selectFrom = getChildOfType(QSelectFrom.class);
        if (selectFrom == null)
            return null;
        return selectFrom.getFrom();
    }

    public QWhere getWhere()
    {
        return getChildOfType(QWhere.class);
    }

    public QGroupBy getGroupBy()
    {
        return getChildOfType(QGroupBy.class);
    }

    public QLimit getLimit()
    {
        return getChildOfType(QLimit.class);
    }

    public QOrder getOrderBy()
    {
        return getChildOfType(QOrder.class);
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append("(");
        for (QNode node : children())
        {
            node.appendSource(builder);
        }
        builder.append(")");
    }

    public void appendSql(SqlBuilder builder)
    {
        if (_query == null)
        {
            throw new IllegalStateException("Fields should have been resolved");
        }
        QueryTableInfo table = _query.getTableInfo("foo");
        if (table == null)
        {
            builder.append("'ERROR'");
            return;
        }
        builder.append("(");
        builder.append(Table.getSelectSQL(table, table.getColumns(), null, null));
        builder.append(")");
    }

    public void syntaxCheck(List<? super QueryParseException> errors)
    {
        List<QNode> children = new LinkedList<QNode>();
        children.add(this);
        while (!children.isEmpty())
        {
            QNode<QNode> child = children.remove(0);
            for (QNode grandChild : child.children())
            {
                if (grandChild instanceof QExpr)
                {
                    QueryParseException error = ((QExpr) grandChild).syntaxCheck(child);
                    if (error != null)
                    {
                        errors.add(error);
                    }
                }
                children.add(0, grandChild);
            }
        }
    }
}
