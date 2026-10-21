/*
 * Copyright (c) 2010-2026 LabKey Corporation
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

/**
 * User: matthewb
 * Date: Dec 1, 2010
 * Time: 3:21:19 PM
 */

public class QJoin implements QJoinOrTable
{
    QJoinOrTable _left;
    QJoinOrTable _right;
    JoinType _joinType;
    QExpr _on;

    public QJoin(QJoinOrTable left, QJoinOrTable right, JoinType joinType, QExpr on)
    {
        _left = left;
        _right = right;
        _joinType = joinType;
        _on = on;
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        boolean parensLeft = _left instanceof QJoin && _joinType != JoinType.cross;
        boolean parensRight = _right instanceof QJoin && _joinType != JoinType.cross;

        if (parensLeft)
            builder.unsafeAppend("(");
        _left.appendSource(builder);
        if (parensLeft)
            builder.unsafeAppend(")");
        builder.unsafeAppend(" ");
        
        switch (_joinType)
        {
            case inner:
                builder.unsafeAppend("INNER JOIN ");
                break;
            case left:
                builder.unsafeAppend("LEFT JOIN ");
                break;
            case right:
                builder.unsafeAppend("RIGHT JOIN ");
                break;
            case outer:
                builder.unsafeAppend("OUTER JOIN ");
                break;
            case full:
                builder.unsafeAppend("FULL OUTER JOIN ");
                break;
            case cross:
                builder.unsafeAppend("CROSS JOIN ");
                break;
        }

        if (parensRight)
            builder.unsafeAppend("(");
        _right.appendSource(builder);
        if (parensRight)
            builder.unsafeAppend(")");

        if (_on != null)
        {
            builder.unsafeAppend(" ON ");
            _on.appendSource(builder);
        }
    }

    @Override
    public void appendSql(SqlBuilder sql, QuerySelect select)
    {
        _left.appendSql(sql, select);
        switch (_joinType)
        {
            case inner:
                sql.unsafeAppend("\nINNER JOIN ");
                break;
            case outer:
                sql.unsafeAppend("\nOUTER JOIN ");
                break;
            case left:
                sql.unsafeAppend("\nLEFT JOIN ");
                break;
            case right:
                sql.unsafeAppend("\nRIGHT JOIN ");
                break;
            case full:
                sql.unsafeAppend("\nFULL JOIN ");
                break;
            case cross:
                sql.unsafeAppend("\nCROSS JOIN ");
                break;
        }
        _right.appendSql(sql, select);

        if (!select.getParseErrors().isEmpty())
            return;

        if (null != _on)
        {
            sql.unsafeAppend(" ON ");
            select.resolveFields(_on, null, null).appendSql(sql, select._query);
        }
    }
}
