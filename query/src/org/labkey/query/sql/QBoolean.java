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

import org.labkey.api.data.SqlDialect;

import java.sql.Types;

public class QBoolean extends QExpr implements IConstant
{
    public QBoolean()
    {
		super(false);
    }

    public QBoolean(boolean value)
    {
		super(false);
        setTokenText(value ? "true" : "false");
    }

    public Boolean getValue()
    {
        return "true".equalsIgnoreCase(getTokenText());
    }

    public void appendSql(SqlBuilder builder)
    {
        SqlDialect d = builder.getDialect();
        builder.append(getValue().booleanValue() ? d.getBooleanTRUE() : d.getBooleanFALSE());
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append(getValue().toString());
    }

    public int getSqlType()
    {
        return Types.BOOLEAN;
    }

    public String getValueString()
    {
        return getValue().toString();
    }
}