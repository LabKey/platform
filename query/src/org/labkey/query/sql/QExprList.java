/*
 * Copyright (c) 2006-2007 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;

public class QExprList extends QExpr
{
    public QExprList()
    {

    }

    public QExprList(QExpr... children)
    {
        for (QExpr child : children)
        {
            appendChild(child);
        }
    }
    public void appendSql(SqlBuilder builder)
    {
        builder.append("(");
        builder.pushPrefix("");
        for (QExpr child : children())
        {
            child.appendSql(builder);
            builder.nextPrefix(",");
        }
        builder.popPrefix();
        builder.append(")");
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append("(");
        builder.pushPrefix("");
        for (QExpr child : children())
        {
            child.appendSource(builder);
            builder.nextPrefix(",");
        }
        builder.popPrefix();
        builder.append(")");
    }

    public String getValueString()
    {
        StringBuilder ret = new StringBuilder("(");
        String strComma = "";
        for (QExpr child : children())
        {
            String strChild = child.getValueString();
            if (StringUtils.isEmpty(strChild))
                return null;
            ret.append(strComma);
            strComma = ",";
            ret.append(strChild);
        }
        ret.append(")");
        return ret.toString();
    }
}
