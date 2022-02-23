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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class QExprList extends QExpr implements SupportsAnnotations
{
    public QExprList()
    {

    }

    public QExprList(Class validChildrenClass)
    {
        super(validChildrenClass);
    }

    @Override
    public void appendSql(SqlBuilder builder, Query query)
    {
        appendSql(builder, query, true);
    }

    protected void appendSql(SqlBuilder builder, Query query, boolean parens)
    {
        if (parens)
            builder.append("(");
        builder.pushPrefix("");
        for (QNode child : children())
        {
            ((QExpr)child).appendSql(builder, query);
            builder.nextPrefix(",");
        }
        builder.popPrefix();
        if (parens)
            builder.append(")");
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        appendSource(builder, true);
    }

    protected void appendSource(SourceBuilder builder, boolean parens)
    {
        if (parens)
            builder.append("(");
        builder.pushPrefix("");
        for (QNode child : children())
        {
            child.appendSource(builder);
            builder.nextPrefix(",");
        }
        builder.popPrefix();
        if (parens)
            builder.append(")");
    }

    @Override
    public String getValueString()
    {
        StringBuilder ret = new StringBuilder("(");
        String strComma = "";
        for (QNode child : children())
        {
            String strChild = ((QExpr)child).getValueString();
            if (StringUtils.isEmpty(strChild))
                return null;
            ret.append(strComma);
            strComma = ",";
            ret.append(strChild);
        }
        ret.append(")");
        return ret.toString();
    }

    @Override
    public boolean isConstant()
    {
        return QOperator.hasConstantChildren(this);
    }



    Map<String,Object> _annotations = null;

    @Override
    public void setAnnotations(Map<String, Object> a)
    {
        _annotations = a;
    }

    @Override
    public @NotNull Map<String, Object> getAnnotations()
    {
        return null==_annotations ? Map.of() : _annotations;
    }
}
