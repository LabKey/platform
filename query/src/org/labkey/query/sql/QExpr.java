/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ColumnInfo;

import java.util.List;


abstract public class QExpr extends QNode
{
    public QExpr()
    {
        super(QExpr.class);
    }

	public QExpr(boolean allowChildren)
	{
		super(allowChildren ? QExpr.class : null);
	}

	public QExpr(Class validChildrenClass)
	{
		super(validChildrenClass);
	}

    public FieldKey getFieldKey()
    {
        return null;
    }

    public SQLFragment getSqlFragment(SqlDialect dialect, Query query)
    {
        SqlBuilder ret = new SqlBuilder(dialect);
        appendSql(ret, query);
        return ret;
    }

    /* Query context is used only by QMethodCall as far as I know, and that usage could probably
     * be removed if some work (container context) could be evaluated at run-time instead of compile/parse.
     */
    abstract public void appendSql(SqlBuilder builder, Query query);

    public String getValueString()
    {
        return null;
    }

    @NotNull
    public JdbcType getSqlType()
    {
        return JdbcType.OTHER;
    }


    public abstract boolean isConstant();

    
    public boolean isAggregate()
    {
        // avoid ClassCastException org.labkey.query.sql.QSelectFrom cannot be cast to org.labkey.query.sql.QExpr  
        if (this instanceof QQuery)
            return false;
        for (QNode n : children())
        {
			QExpr child = (QExpr)n;
            if (child.isAggregate())
                return true;
        }
        return false;
    }


    public ColumnInfo createColumnInfo(SQLTableInfo table, String name, final Query query)
    {
        return new ExprColumn(table, name, new SQLFragment("{{expr}}"), getSqlType())
        {
            @Override
            public SQLFragment getValueSql(String tableAlias)
            {
                return getSqlFragment(getParentTable().getSchema().getSqlDialect(), query);
            }
        };
    }


    public QueryParseException fieldCheck(QNode parent, SqlDialect d)
    {
        return null;
    }


    /** If all the children are the same type, return as that type. Otherwise, return Types.OTHER */
    protected JdbcType getChildrenSqlType()
    {
        return getChildrenSqlType(childList());
    }


    static JdbcType getChildrenSqlType(List<QNode> children)
    {
        if (children.isEmpty())
            return JdbcType.OTHER;

        JdbcType result = JdbcType.NULL;
        for (QNode qNode : children)
        {
            if (qNode instanceof QExpr)
            {
                JdbcType nextType = ((QExpr) qNode).getSqlType();
                result = JdbcType.promote(result, nextType);
            }
            else
            {
                return JdbcType.OTHER;
            }
        }
        return result;
    }


    @Override
    public QExpr copyTree()
    {
        return (QExpr)super.copyTree();
    }

    @Override
    public boolean equalsNode(QNode other)
    {
        return getClass() == other.getClass();
    }
}
