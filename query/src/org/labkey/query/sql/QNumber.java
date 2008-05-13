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

public class QNumber extends QExpr implements IConstant
{
    public QNumber()
    {
    }

    public void setText(String str)
    {
        super.setText(str);

    }

    public QNumber(Number value)
    {
        if (value instanceof Double)
        {
            setTokenType(SqlTokenTypes.NUM_DOUBLE);
        }
        else if (value instanceof Float)
        {
            setTokenType(SqlTokenTypes.NUM_FLOAT);
        }
        else if (value instanceof Integer)
        {
            setTokenType(SqlTokenTypes.NUM_INT);
        }
        else if (value instanceof Long)
        {
            setTokenType(SqlTokenTypes.NUM_LONG);
        }
        else
        {
            throw new IllegalArgumentException();
        }
        setText(value.toString());
    }

    public Number getValue()
    {
        String text = getTokenText();
        if (StringUtils.isEmpty(text))
            return null;
        switch (getTokenType())
        {
            case SqlTokenTypes.NUM_DOUBLE:
                return Double.valueOf(text);
            case SqlTokenTypes.NUM_FLOAT:
                return Float.valueOf(text);
            case SqlTokenTypes.NUM_INT:
                return Integer.valueOf(text);
            case SqlTokenTypes.NUM_LONG:
                return Long.valueOf(text);
            default:
                throw new QueryParseException("Unexpected", null, getLine(), getColumn());
        }
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
        return Types.DOUBLE;
    }

    public String getValueString()
    {
        return getValue().toString();
    }
}
