/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
import org.labkey.api.query.QueryParseException;
import org.labkey.query.sql.SqlTokenTypes;

import java.sql.Types;

public class QBoolean extends QExpr implements IConstant
{
    public QBoolean()
    {
    }

    public QBoolean(boolean value)
    {
        setTokenText(value ? "true" : "false");
    }

    public Boolean getValue()
    {
        return "true".equalsIgnoreCase(getTokenText());
    }

    public void appendSql(SqlBuilder builder)
    {
        builder.append(getValue().toString());
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