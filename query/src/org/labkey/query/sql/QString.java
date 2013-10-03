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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.JdbcType;


public class QString extends QExpr implements IConstant
{
    static public String unquote(String str)
    {
        if (str.length() < 2)
            throw new IllegalArgumentException();
        if (!str.startsWith("'") || !str.endsWith("'"))
            throw new IllegalArgumentException();
        str = str.substring(1, str.length() - 1);
        str = StringUtils.replace(str, "''", "'");
        return str;
    }

    public JdbcType getSqlType()
    {
        return JdbcType.VARCHAR;
    }

    static public String quote(String str)
    {
        return "'" + StringUtils.replace(str, "'", "''") + "'";
    }

    public QString()
    {
		super(false);
    }

    public QString(String value)
    {
		this();
        setTokenText(quote(value));
    }

    public String getValue()
    {
        return unquote(getTokenText());
    }

    public void appendSql(SqlBuilder builder, Query query)
    {
        builder.appendStringLiteral(getValue());
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append(getTokenText());
    }

    public String getValueString()
    {
        return getTokenText();
    }

    @Override
    public boolean equalsNode(QNode other)
    {
        return other instanceof QString && getValue().equals(((QString) other).getValue());
    }

    @Override
    public boolean isConstant()
    {
        return true;
    }
}
