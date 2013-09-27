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
import org.labkey.api.data.JdbcType;

import java.util.ArrayList;

public class QCase extends QExpr
{
    final boolean _switch;

    QCase(boolean switchStyle)
    {
        _switch = switchStyle;
    }
    
    public void appendSql(SqlBuilder builder, Query query)
    {
        builder.append(" CASE ");
        for (QNode child : children())
        {
            ((QExpr)child).appendSql(builder, query);
        }
        builder.append("\nEND");
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append(" CASE ");
        for (QNode child : children())
        {
            child.appendSource(builder);
        }
        builder.append("\nEND");
    }

    @Override
    public @NotNull JdbcType getSqlType()
    {
        if (!_switch)
            return getChildrenSqlType();

        // skip the first expression
        ArrayList<QNode> list = new ArrayList<>(childList());
        list.remove(0);
        return getChildrenSqlType(list);
    }

    @Override
    public boolean isConstant()
    {
        return QOperator.hasConstantChildren(this);
    }
}
