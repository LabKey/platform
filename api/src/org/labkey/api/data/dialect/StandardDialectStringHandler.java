/*
 * Copyright (c) 2011-2015 LabKey Corporation
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
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.util.DateUtil;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/*
* User: adam
* Date: Aug 13, 2011
* Time: 3:55:55 PM
*/
public class StandardDialectStringHandler implements DialectStringHandler
{
    @Override
    public String quoteStringLiteral(String str)
    {
        return "'" + StringUtils.replace(str, "'", "''") + "'";
    }


    @Override
    // Substitute the parameters into the SQL string, following the rules for quoted identifiers, string literals, comments, etc.

    // Previously, we used regular expressions to find (and ignore) string literals and quoted identifiers while doing
    // parameter substitution, but the first attempt exploded with long string literals (#12866) and the second attempt
    // occasionally failed to return. So, we wrote this dumb little parser instead.
    public String substituteParameters(SQLFragment frag)
    {
        String sql = frag.getSQL();
        StringBuilder ret = new StringBuilder();
        List<Object> params = new LinkedList<>(frag.getParams());

        int begin = 0;
        int current = 0;

        while (current < sql.length())
        {
            char c = sql.charAt(current);

            switch(c)
            {
                case('?'):
                    ret.append(sql.subSequence(begin, current));
                    if (params.isEmpty())
                        ret.append("NULL /*?missing?*/");
                    else
                        ret.append(formatParameter(params.remove(0)));
                    current++;
                    begin = current;
                    break;
                case('\''):
                    current = findEndOfStringLiteral(sql, current + 1);
                    break;
                case('"'):
                    current = findEndOfQuotedIdentifier(sql, current + 1);
                    break;
                case('/'):
                    current = findEndOfBlockComment(sql, current + 1);
                    break;
                case('-'):
                    current = findEndOfLineComment(sql, current + 1);
                    break;
                default:
                    current++;
            }
        }

        ret.append(sql.subSequence(begin, current));

        return ret.toString();
    }

    // Note: we don't bother looking for escaped single quotes ('') inside the string... the parser will just treat this
    // as two single quoted strings in a row, which is fine for parameter substitution purposes
    protected int findEndOfStringLiteral(CharSequence sql, int current)
    {
        while (current < sql.length())
        {
            char c = sql.charAt(current++);
            if (c == '\'')
                break;
        }

        return current;
    }


    // Note: we don't bother looking for escaped double quotes ("") inside the identifier... the parser will just treat
    // this as two quoted identifiers in a row, which is fine for parameter substitution purposes
    private int findEndOfQuotedIdentifier(CharSequence sql, int current)
    {
        while (current < sql.length())
        {
            char c = sql.charAt(current++);
            if (c == '"')
                break;
        }

        return current;
    }


    enum Previous {firstSlash, star, somethingElse}

    // We just hit a slash character... probably a block comment, but maybe not.
    private int findEndOfBlockComment(CharSequence sql, int current)
    {
        Previous prev = Previous.firstSlash;

        while (current < sql.length())
        {
            char c = sql.charAt(current++);

            switch(prev)
            {
                case firstSlash:
                    if (c == '*')
                        prev = Previous.somethingElse;
                    else
                        return --current;  // Not a comment after all, back it up so we don't lose this character
                    break;
                case star:
                    if (c == '/')
                        return current;   // End of comment... we're done

                    prev = Previous.somethingElse;
                    current--;  // Not the end of comment, back it up so we don't lose this character
                    break;
                case somethingElse:
                    if (c == '*')
                        prev = Previous.star;
                    break;
            }
        }

        return current;
    }


    // We just hit a dash character... check if it's a line comment and skip to the end of the line if it is.
    private int findEndOfLineComment(CharSequence sql, int current)
    {
        boolean firstDash = true;

        while (current < sql.length())
        {
            char c = sql.charAt(current++);

            if (firstDash)
            {
                if ('-' == c)
                    firstDash = false;
                else
                    return --current;  // Not a comment after all, back it up so we don't lose this character
            }
            else
            {
                if (10 == c || 13 == c)
                    return current;
            }
        }

        return current;
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
            return quoteStringLiteral(DateUtil.formatDateTimeISO8601((Date)value));
        }
        else if (value instanceof Boolean)
        {
            return booleanValue((Boolean)value);
        }
        else if (value instanceof Array)
        {
            return quoteStringLiteral(value.toString());
        }
        else if (value instanceof Object[])
        {
            return quoteStringLiteral("{\"" + StringUtils.join((Object[])value, "\", \"") + "\"}");
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


    // ParameterSubstitutionTest tests the full substitution process; this tests edge conditions in the parser.
    public static class TestCase extends Assert
    {
        @Test
        public void testSqlParserMethods()
        {
            StandardDialectStringHandler handler = new StandardDialectStringHandler();
            assertEquals(14, handler.findEndOfStringLiteral("'foo bar blick", 1));      // Non-terminated should be end of string
            assertEquals(15, handler.findEndOfStringLiteral("'foo bar blick'", 1));     // Terminated at end of string should be end of string
            assertEquals(15, handler.findEndOfStringLiteral("'foo bar blick''", 1));    // Terminated should be at character after ending quote
            assertEquals(8, handler.findEndOfStringLiteral("'foo ba''r blick''", 1));   // Escaped quotes are treated as two strings in a row
            assertEquals(17, handler.findEndOfStringLiteral("'foo ba''r blick''", 9));  // Second string should be at character after ending quote
            assertEquals(16, handler.findEndOfStringLiteral("'foo ba''r blick", 9));    // Non-terminated should be end of string

            assertEquals(7, handler.findEndOfQuotedIdentifier("\"foobar", 1));                  // Non-terminated should be end of string
            assertEquals(8, handler.findEndOfQuotedIdentifier("\"foobar\"", 1));                // Terminated at end of string should be end of string
            assertEquals(8, handler.findEndOfQuotedIdentifier("\"foobar\"\"", 1));              // Terminated should be at character after ending quote
            assertEquals(8, handler.findEndOfQuotedIdentifier("\"foobar\"\"rblick\"", 1));      // Escaped quotes are treated as two strings in a row
            assertEquals(15, handler.findEndOfQuotedIdentifier("\"foobar\"\"blick\"''", 9));    // Second string should be at character after ending quote
            assertEquals(14, handler.findEndOfQuotedIdentifier("\"foobar\"\"blick", 9));        // Non-terminated should be end of string

            assertEquals(16, handler.findEndOfBlockComment("/* foo bar blick", 1));
            assertEquals(27, handler.findEndOfBlockComment("/* foo bar\nblick\nblue blood", 1));
            assertEquals(27, handler.findEndOfBlockComment("/* foo bar\nblick\nblue blood", 1));
            assertEquals(30, handler.findEndOfBlockComment("/* foo bar\nblick\nblue blood */ more stuff at the end", 1));
            assertEquals(4, handler.findEndOfBlockComment("/**/ a very short comment", 1));
            assertEquals(5, handler.findEndOfBlockComment("/*\n*/ another short comment", 1));
            assertEquals(6, handler.findEndOfBlockComment("/*\n**/ double *", 1));
            assertEquals(42, handler.findEndOfBlockComment("/**lot's*of*stars*in*the* /comment/******/ double *", 1));
            assertEquals(1, handler.findEndOfBlockComment("/- not a valid comment", 1));
            assertEquals(1, handler.findEndOfBlockComment("//- not a valid comment", 1));

            assertEquals(16, handler.findEndOfLineComment("-- foo bar blick", 1));
            assertEquals(17, handler.findEndOfLineComment("-- foo bar blick\n", 1));
            assertEquals(7, handler.findEndOfLineComment("-- foo\nline feed", 1));
            assertEquals(7, handler.findEndOfLineComment("-- foo\rcarriage return", 1));
            assertEquals(7, handler.findEndOfLineComment("-- foo\n-- bar blick", 1));
            assertEquals(1, handler.findEndOfLineComment("- not a valid comment", 1));
        }
    }
}
