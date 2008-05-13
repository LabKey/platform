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

import java.sql.Types;

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

    public int getSqlType()
    {
        return Types.VARCHAR;
    }

    static public String quote(String str)
    {
        return "'" + StringUtils.replace(str, "'", "''") + "'";
    }

    public QString()
    {

    }

    public QString(String value)
    {
        setText(quote(value));
    }

    public String getValue()
    {
        return unquote(getTokenText());
    }

    public void appendSql(SqlBuilder builder)
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
}
