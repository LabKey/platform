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

public class QWhen extends QExpr
{
    public void appendSql(SqlBuilder builder, Query query)
    {
        builder.append("\nWHEN(");
        ((QExpr)getFirstChild()).appendSql(builder, query);
        builder.append(")THEN(");
        ((QExpr)getLastChild()).appendSql(builder, query);
        builder.append(")");
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append("\nWHEN(");
        getFirstChild().appendSource(builder);
        builder.append(")THEN(");
        getLastChild().appendSource(builder);
        builder.append(")");
    }

    @Override @NotNull
    public JdbcType getSqlType()
    {
        QNode valueChild = getLastChild();
        if (valueChild instanceof QExpr)
        {
            return ((QExpr)valueChild).getSqlType();
        }
        return super.getSqlType();
    }

    @Override
    public boolean isConstant()
    {
        return QOperator.hasConstantChildren(this);
    }
}
