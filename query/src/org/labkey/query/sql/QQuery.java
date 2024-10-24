/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.lang.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.QueryParseException;

import java.util.Collection;


public class QQuery extends QExpr
{
    QueryRelation _select;

    public QQuery()
    {
		super(QNode.class);
    }

    public QQuery(QueryRelation query)
    {
        super(QNode.class);
        _select = query;
    }

    public QueryRelation getQuerySelect()
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
        for (QNode child : children())
        {
            if (QWhere.class.isAssignableFrom(child.getClass()) && !((QWhere)child)._having)
                return (QWhere) child;
        }
        return null;

    }

    public QWhere getHaving()
    {
        for (QNode child : children())
        {
            if (QWhere.class.isAssignableFrom(child.getClass()) && ((QWhere)child)._having)
                return (QWhere) child;
        }
        return null;
    }


    public QGroupBy getGroupBy()
    {
        return getChildOfType(QGroupBy.class);
    }


    public QLimit getLimit()
    {
        return getChildOfType(QLimit.class);
    }


    public QLimit removeLimit()
    {
        return removeChildOfType(QLimit.class);
    }


    public QOrder getOrderBy()
    {
        return getChildOfType(QOrder.class);
    }


    public QOrder removeOrderBy()
    {
        return removeChildOfType(QOrder.class);
    }


    @Override
    public void appendSource(SourceBuilder builder)
    {
        builder.append("(");
        for (QNode node : children())
        {
            node.appendSource(builder);
        }
        builder.append(")");
    }

    @Override
    public void appendSql(SqlBuilder builder, Query query)
    {
        if (_select == null)
        {
            throw new IllegalStateException("Fields should have been resolved");
        }
        SQLFragment f = _select.getSql();
        if (null == f)
        {
            if (null != _select && _select.getParseErrors().size() > 0)
                return;
            String message = "Unexpected error parsing subselect: " + getSourceText();
            _select.getParseErrors().add(new QueryParseException(message, null, getLine(), getColumn()));
            builder.append("#ERROR:").append(message).append("#");
            return;
        }
        builder.append("(");
        builder.append(f);
        builder.append(")");
    }


    @Override
    public boolean isConstant()
    {
        return false;
    }


    @Override
    public void addFieldRefs(Object referant)
    {
        /* We could now traverse the nested QueryRelation, chasing down references.
         * Instead, we just addRef() all "upward" references in QuerySelect.getField()
         */
    }

    @Override
    public Collection<AbstractQueryRelation.RelationColumn> gatherInvolvedSelectColumns(Collection<AbstractQueryRelation.RelationColumn> collect)
    {
        var relation = getQuerySelect();
        AbstractQueryRelation.RelationColumn col = relation.getFirstColumn();
        if (null != col)
            col.gatherInvolvedSelectColumns(collect);
        return collect;
    }

    @Override
    public @NotNull JdbcType getJdbcType()
    {
        var relation = getQuerySelect();
        if (relation instanceof QueryLookupWrapper qlw)
            relation = qlw.getSource();
        AbstractQueryRelation.RelationColumn col = relation.getFirstColumn();
        if (null != col)
            return col.getJdbcType();
        return super.getJdbcType();
    }
}
