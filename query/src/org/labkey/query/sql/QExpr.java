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

import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ColumnInfo;

import java.sql.Types;


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

    public SQLFragment getSqlFragment(DbSchema schema)
    {
        SqlBuilder ret = new SqlBuilder(schema);
        appendSql(ret);
        return ret;
    }

    abstract public void appendSql(SqlBuilder builder);

    public String getValueString()
    {
        return null;
    }

    public int getSqlType()
    {
        return Types.OTHER;
    }

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

    public ColumnInfo createColumnInfo(SQLTableInfo table, String name)
    {
        DbSchema schema = table.getSchema();
        SQLFragment sql = getSqlFragment(schema);
        return new ExprColumn(table, name, sql, getSqlType());
    }

    public QueryParseException fieldCheck(QNode parent)
    {
        return null;
    }
}
