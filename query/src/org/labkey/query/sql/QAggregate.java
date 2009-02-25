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

import org.labkey.api.data.ColumnInfo;

import java.sql.Types;
import java.util.List;


public class QAggregate extends QExpr
{
    public void appendSql(SqlBuilder builder)
    {
        String function = getTokenText();
        if (function.equalsIgnoreCase("stddev"))
        {
            function = builder.getDialect().getStdDevFunction();
        }
        builder.append(" " + function + "(");
        for (QNode child : children())
        {
            ((QExpr)child).appendSql(builder);
        }
        builder.append(")");
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append(" " + getTokenText() + "(");
        for (QNode child : children())
        {
            child.appendSource(builder);
        }
        builder.append(")");
    }

    public int getSqlType()
    {
        if ("count".equalsIgnoreCase(getTokenText()))
        {
            return Types.INTEGER;
        }
		if (getFirstChild() != null)
			return ((QExpr)getFirstChild()).getSqlType();
        return Types.OTHER;
    }

    public boolean isAggregate()
    {
        return true;
    }

    public ColumnInfo createColumnInfo(SQLTableInfo table, String alias)
    {
        ColumnInfo ret = super.createColumnInfo(table, alias);
        if ("max".equalsIgnoreCase(getTokenText()) || "min".equalsIgnoreCase(getTokenText()))
        {
            List<QNode> children = childList();
            if (children.size() == 1 && children.get(0) instanceof QField)
            {
                QField field = (QField) children.get(0);
                ColumnInfo columnInfo = field.getColumnInfo();
                ret.copyAttributesFrom(columnInfo);
                ret.setCaption(null);
            }
        }
        return ret;
    }
}
