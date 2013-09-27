/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

    public void appendSource(SourceBuilder builder)
    {
        boolean parensLeft = _left instanceof QJoin && _joinType != JoinType.cross;
        boolean parensRight = _right instanceof QJoin && _joinType != JoinType.cross;

        if (parensLeft)
            builder.append("(");
        _left.appendSource(builder);
        if (parensLeft)
            builder.append(")");
        builder.append(" ");
        
        switch (_joinType)
        {
            case inner:
                builder.append("INNER JOIN ");
                break;
            case left:
                builder.append("LEFT JOIN ");
                break;
            case right:
                builder.append("RIGHT JOIN ");
                break;
            case outer:
                builder.append("OUTER JOIN ");
                break;
            case full:
                builder.append("FULL OUTER JOIN ");
                break;
            case cross:
                builder.append("CROSS JOIN ");
                break;
        }

        if (parensRight)
            builder.append("(");
        _right.appendSource(builder);
        if (parensRight)
            builder.append(")");

        if (_on != null)
        {
            builder.append(" ON ");
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
                sql.append("\nINNER JOIN ");
                break;
            case outer:
                sql.append("\nOUTER JOIN ");
                break;
            case left:
                sql.append("\nLEFT JOIN ");
                break;
            case right:
                sql.append("\nRIGHT JOIN ");
                break;
            case full:
                sql.append("\nFULL JOIN ");
                break;
            case cross:
                sql.append("\nCROSS JOIN ");
                break;
        }
        _right.appendSql(sql, select);

        if (select.getParseErrors().size() > 0)
            return;

        if (null != _on)
        {
            sql.append(" ON ");
            select.resolveFields(_on, null, null).appendSql(sql, select._query);
        }
    }
}
