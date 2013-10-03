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

import org.labkey.api.data.JdbcType;


public class QNull extends QExpr implements IConstant
{
	public QNull()
	{
		super(false);
	}
	
    public void appendSql(SqlBuilder builder, Query query)
    {
        builder.append("NULL");
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append("NULL");
    }

    public String getValueString()
    {
        return " NULL ";
    }

    @Override
    public JdbcType getSqlType()
    {
        return JdbcType.NULL;
    }

    @Override
    public Object getValue()
    {
        return null;
    }

    @Override
    public boolean isConstant()
    {
        return true;
    }
}
