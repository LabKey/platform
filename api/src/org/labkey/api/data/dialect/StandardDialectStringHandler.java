/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

package org.labkey.api.data.dialect;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.util.DateUtil;

import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
* User: adam
* Date: Aug 13, 2011
* Time: 3:55:55 PM
*/
public class StandardDialectStringHandler implements DialectStringHandler
{
    // Pattern that captures a single quoted string literal, handling embedded single quotes that are escaped ('').  For
    // the record, here's a bad previous pattern that attempted to do the same thing: '([^']|(''))*'  This one explodes
    // with StackOverflowError when it encounters a long string literal.  See #12866.
    // English version: any number of non-quote characters followed by escaped quotes followed by any number of non-quote
    // characters... repeated any number of times... and all of it enclosed in quotes
    private static final Pattern _stringLiteralPattern = Pattern.compile("'[^']*(?:''[^']*)*'");
    private static final Pattern _quotedIdentifierPattern = Pattern.compile("\"[^\"]*(?:\"\"[^\"]*)*\"");
    private static final Pattern _parameterPattern = Pattern.compile("\\?");

    @Override
    public Pattern getStringLiteralPattern()
    {
        return _stringLiteralPattern;
    }

    @Override
    public Pattern getQuotedIdentifierPattern()
    {
        return _quotedIdentifierPattern;
    }

    @Override
    public String quoteStringLiteral(String str)
    {
        return "'" + StringUtils.replace(str, "'", "''") + "'";
    }

    @Override
    /**
     * Substitute the parameter values into the SQL statement.
     * Iterates through the SQL string
     */

    // TODO: Ignore question marks inside comments... these currently cause the code below to blow up
    public String substituteParameters(SQLFragment frag)
    {
        CharSequence sql = frag.getSqlCharSequence();
        Matcher matchIdentifier = getQuotedIdentifierPattern().matcher(sql);
        Matcher matchStringLiteral = getStringLiteralPattern().matcher(sql);
        Matcher matchParam = _parameterPattern.matcher(sql);

        StringBuilder ret = new StringBuilder();
        List<Object> params = new LinkedList<Object>(frag.getParams());
        int ich = 0;

        while (ich < sql.length())
        {
            int ichSkipTo = sql.length();
            int ichSkipPast = sql.length();

            if (matchIdentifier.find(ich))
            {
                if (matchIdentifier.start() < ichSkipTo)
                {
                    ichSkipTo = matchIdentifier.start();
                    ichSkipPast = matchIdentifier.end();
                }
            }

            if (matchStringLiteral.find(ich))
            {
                if (matchStringLiteral.start() < ichSkipTo)
                {
                    ichSkipTo = matchStringLiteral.start();
                    ichSkipPast = matchStringLiteral.end();
                }
            }

            if (matchParam.find(ich))
            {
                if (matchParam.start() < ichSkipTo)
                {
                    ret.append(frag.getSqlCharSequence().subSequence(ich, matchParam.start()));
                    if (params.isEmpty())
                        ret.append("NULL /*?missing?*/");
                    else
                        ret.append(formatParameter(params.remove(0)));
                    ich = matchParam.start() + 1;
                    continue;
                }
            }

            ret.append(frag.getSqlCharSequence().subSequence(ich, ichSkipPast));
            ich = ichSkipPast;
        }

        return ret.toString();
    }


    private String formatParameter(Object o)
    {
        Object value;

        try
        {
            value = Parameter.getValueToBind(o, null);
        }
        catch (SQLException x)
        {
            value = null;
        }

        if (value == null)
        {
            return "NULL";
        }
        else if (value instanceof String)
        {
            return quoteStringLiteral((String)value);
        }
        else if (value instanceof Date)
        {
            return quoteStringLiteral(DateUtil.formatDateTime((Date)value));
        }
        else if (value instanceof Boolean)
        {
            return booleanValue((Boolean)value);
        }
        else
        {
            return value.toString();
        }
    }


    // TODO: This is wrong -- SQL could run against any database (not just core).  Need to pass in dialect.
    private String booleanValue(Boolean value)
    {
        return CoreSchema.getInstance().getSqlDialect().getBooleanLiteral(value);
    }
}
