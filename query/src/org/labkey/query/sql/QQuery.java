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

import org.labkey.api.data.Table;


public class QQuery extends QExpr
{
    QuerySelect _select;

    public QQuery()
    {
		super(QNode.class);
    }

    public QQuery(QuerySelect query)
    {
        _select = query;
    }

    public QuerySelect getQuerySelect()
    {
        return _select;
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
        if (_select == null)
        {
            throw new IllegalStateException("Fields should have been resolved");
        }
        builder.append("(");
        builder.append(_select.getSql());
        builder.append(")");
    }
}
