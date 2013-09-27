/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.antlr.runtime.tree.CommonTree;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.JdbcType;
import org.labkey.api.query.FieldKey;

/**
 * User: matthewb
 * Date: 2012-10-17
 * Time: 1:31 PM
 */
public class QIfDefined extends QExpr
{
    boolean isDefined = true;

    QIfDefined(CommonTree node)
    {
        super(QFieldKey.class);
        from(node);
    }

    @Override
    public FieldKey getFieldKey()
    {
        return ((QExpr)getFirstChild()).getFieldKey();
    }

    @Override
    public void appendSql(SqlBuilder builder, Query query)
    {
        if (isDefined)
            ((QExpr)getFirstChild()).appendSql(builder, query);
        else
            builder.append("NULL");
    }

    @Override
    public boolean isConstant()
    {
        return ((QExpr)getFirstChild()).isConstant();
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        builder.append("IFDEFINED(");
        getFirstChild().appendSource(builder);
        builder.append(")");
    }

    @Override
    protected boolean isValidChild(QNode n)
    {
        return n instanceof QField || n instanceof QIdentifier || n instanceof QDot;
    }

    @NotNull
    @Override
    public JdbcType getSqlType()
    {
        if (isDefined)
            return ((QExpr)getFirstChild()).getSqlType();
        else
            return JdbcType.OTHER;
    }
}
