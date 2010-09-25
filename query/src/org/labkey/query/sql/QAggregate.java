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
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.MultiValuedDisplayColumn;

import java.sql.Types;
import java.util.List;

public class QAggregate extends QExpr
{
    public static final String STDDEV = "stddev";
    public static final String COUNT = "count";
    public static final String GROUP_CONCAT = "group_concat";
    private boolean _distinct;

    public QAggregate()
    {
        super(QNode.class);
    }

    public void appendSql(SqlBuilder builder)
    {
        String function = getTokenText();
        if (GROUP_CONCAT.equalsIgnoreCase(function))
        {
            SqlBuilder nestedBuilder = new SqlBuilder(builder.getDialect());
            for (QNode child : children())
            {
                ((QExpr)child).appendSql(nestedBuilder);
            }
            builder.append(builder.getDialect().getGroupConcatAggregateFunction(nestedBuilder, _distinct, true));
        }
        else
        {
            if (STDDEV.equalsIgnoreCase(function))
            {
                function = builder.getDialect().getStdDevFunction();
            }
            builder.append(" " + function + "(");
            if (_distinct)
            {
                builder.append("DISTINCT ");
            }
            for (QNode child : children())
            {
                ((QExpr)child).appendSql(builder);
            }
            builder.append(")");
        }
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append(" " + getTokenText() + "(");
        if (_distinct)
        {
            builder.append("DISTINCT ");
        }
        for (QNode child : children())
        {
            child.appendSource(builder);
        }
        builder.append(")");
    }

    public int getSqlType()
    {
        if (COUNT.equalsIgnoreCase(getTokenText()))
        {
            return Types.INTEGER;
        }
        if (GROUP_CONCAT.equalsIgnoreCase(getTokenText()))
        {
            return Types.VARCHAR;
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
                field.getRelationColumn().copyColumnAttributesTo(ret);
                ret.setLabel(null);
            }
        }
        if (GROUP_CONCAT.equalsIgnoreCase(getTokenText()))
        {
            final DisplayColumnFactory originalFactory = ret.getDisplayColumnFactory();
            ret.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                @Override
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new MultiValuedDisplayColumn(originalFactory.createRenderer(colInfo));
                }
            });
        }
        return ret;
    }

    public void setDistinct(boolean distinct)
    {
        _distinct = distinct;
    }
}
