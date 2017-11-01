/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.data;

import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.view.NotFoundException;

/**
 * A simple scanner for SQL text that understands block & single-line comments, double-quoted identifiers, single-quoted strings,
 * and quote escaping. It is not a SQL tokenizer or parser... it merely enables simple text operations that are aware of comments
 * and strings. It currently supports finding characters and substrings outside of comments and quoted strings, as well as
 * stripping comments. It could be extended to support search & replace and other useful text operations.
 *
 * Consider: Use in SQLFragment.getFilterText() replacements
 *
 * Created by adam on 6/29/2017.
 */
public class SqlScanner
{
    private final String _sql;

    public SqlScanner(String sql)
    {
        _sql = sql;
    }

    public SqlScanner(SQLFragment sql)
    {
        this(sql.getRawSQL());
    }

    /**
     * Returns the index within the SQL of the first occurrence of the specified character, ignoring all comments as well as
     * single- and double-quoted strings (while correctly handling escaped quotes within those strings).
     * @param ch character to find
     * @return the index of the first occurrence of the character in the SQL if present, otherwise -1
     */
    public int indexOf(int ch)
    {
        return indexOf(ch, 0);
    }

    /**
     * Returns the index within the SQL of the first occurrence of the specific character starting at fromIndex, ignoring all
     * comments as well as single- and double-quoted strings (while correctly handling escaped quotes within those strings).
     * @param ch character to find
     * @param fromIndex index from which to start the search
     * @return the index of the first occurrence of the character in the SQL if present, otherwise -1
     */
    public int indexOf(int ch, int fromIndex)
    {
        // TODO: Reject ch == '"?
        MutableInt ret = new MutableInt(-1);

        scan(fromIndex, new Handler()
        {
            @Override
            public boolean character(char c, int index)
            {
                if (ch == c)
                {
                    ret.setValue(index);
                    return false;
                }
                else
                {
                    return true;
                }
            }
        });

        return ret.getValue();
    }

    /**
     * Returns the index within the SQL of the first occurrence of the specified substring, ignoring all comments as well as
     * single- and double-quoted strings (while correctly handling escaped quotes within those strings).
     * @param str the substring to search for
     * @return the index of the first occurrence of the specified substring in the SQL if present, otherwise -1
     */
    public int indexOf(String str)
    {
        return indexOf(str, 0);
    }

    /**
     * Returns the index within the SQL of the first occurrence of the specified substring starting at fromIndex, ignoring all
     * comments as well as single- and double-quoted strings (while correctly handling escaped quotes within those strings).
     * @param str the substring to search for
     * @param fromIndex index from which to start the search
     * @return the index of the first occurrence of the specified substring in the SQL if present, otherwise -1
     */
    public int indexOf(String str, int fromIndex)
    {
        // TODO: Reject str contains '"?
        MutableInt ret = new MutableInt(-1);

        scan(fromIndex, new Handler()
        {
            @Override
            public boolean character(char c, int index)
            {
                if (_sql.regionMatches(index, str, 0, str.length()))
                {
                    ret.setValue(index);
                    return false;
                }
                else
                {
                    return true;
                }
            }
        });

        return ret.getValue();
    }

    /**
     * Returns the SQL stripped of all block and line comments (while correctly handling comment characters in quoted strings).
     * @return StringBuilder containing the stripped SQL
     */
    public StringBuilder stripComments()
    {
        StringBuilder ret = new StringBuilder();
        MutableInt previous = new MutableInt(0);

        scan(0, new Handler()
        {
            @Override
            public boolean comment(int beginIndex, int endIndex)
            {
                ret.append(_sql.substring(previous.getValue(), beginIndex));
                previous.setValue(endIndex);

                return true;
            }
        });

        ret.append(_sql.substring(previous.getValue()));

        return ret;
    }

    /**
     * Scans the SQL, skipping all comments as well as single- and double-quoted strings (while correctly handling escaped
     * quotes within those strings) and calling handler methods along the way.
     * @param fromIndex index from which to start the scan
     * @param handler Handler whose methods will be invoked
     */
    private void scan(int fromIndex, Handler handler)
    {
        int i = fromIndex;

        while (i < _sql.length())
        {
            char c = _sql.charAt(i);
            String twoChars = null;

            if (i < (_sql.length() - 1))
                twoChars = _sql.substring(i, i + 2);

            next: if ("/*".equals(twoChars))
            {
                int endIndex = _sql.indexOf("*/", i + 2) + 2;  // Skip to end of comment

                if (1 == endIndex)
                    throw new NotFoundException("Comment starting at position " + i + " was not terminated");

                if (!handler.comment(i, endIndex))
                    return;

                i = endIndex - 1; // Leave i at the last character of the comment ('/')
            }
            else if ("--".equals(twoChars))
            {
                int endIndex = _sql.indexOf("\n", i + 2);

                if (-1 == endIndex)
                    endIndex = _sql.length();

                if (!handler.comment(i, endIndex))
                    return;

                i = endIndex - 1; // Leave i at the last character of the comment (before cr)
            }
            else if ('\'' == c || '"' == c)
            {
                String escape = "" + c + c;

                while (++i < _sql.length())
                {
                    char c2 = _sql.charAt(i);
                    twoChars = null;

                    if (i < (_sql.length() - 1))
                        twoChars = _sql.substring(i, i + 2);

                    if (escape.equals(twoChars))
                        i++;
                    else if (c == c2)
                        break next;
                }

                throw new NotFoundException("Expected ending quote (" + c + ") was not found");
            }
            else
            {
                if (!handler.character(c, i))
                    return;
            }

            i++;
        }
    }

    private interface Handler
    {
        /**
         * Called for every character outside of comments and quoted strings.
         * @param c Current character
         * @param index Index of that character
         * @return true to continue scanning, false to stop
         */
        default boolean character(char c, int index)
        {
            return true;
        }

        /**
         * Called for every comment detected.
         * @param beginIndex   the beginning index, inclusive
         * @param endIndex     the ending index, inclusive
         * @return             true to continue scanning, false to stop
         */
        default boolean comment(int beginIndex, int endIndex)
        {
            return true;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testIndexOf()
        {
            testIndexOf("SELECT * FROM foo", '*', 7, "SELECT", 0);
            testIndexOf("SELECT /* comment here with FROM ** */ * FROM foo", '*', 39, "FROM", 41);
            testIndexOf("SELECT /* 'comment'' \"here\" with foo ** \"\" */ * FROM foo", '*', 46, "foo", 53);

            testIndexOf("SELECT \"COLUMN\" FROM bar WHERE this = that", 'O', 18, "COLUMN", -1);
            testIndexOf("SELECT \"COLUMN\", 'VOLUME this' FROM bar WHERE this = that", 'O', 33, "this", 46);
            testIndexOf("SELECT \"CO\"\"LU\"\"MN\", 'VOL''UM''E', \"that\" FROM bar WHERE this = that", 'O', 44, "that", 64);

            testIndexOf("SELECT -- comment until the end of the line", 'O', -1, "comment", -1);
            testIndexOf("SELECT -- comment until the end of the line\nO", 'O', 44, "--", -1);
            testIndexOf("SELECT /* block comment */\nO", 'O', 27, "/*", -1);
            testIndexOf("SELECT /* block comment */\nO", 'l', -1, "*/", -1);
            testIndexOf("SELECT /* block comment */'some string'\"some identifier\"e", 'e', 56, "some", -1);
            testIndexOf("SELECT /* block comment */\nO", 'l', -1, "*/", -1);
            testIndexOf("SELECT */ not a block comment", 'a', 14, "*/", 7);

            SqlScanner scanner = new SqlScanner("SELECT -- line comment\n-- another line comment\n/* a block\ncomment\nwith line\nbreaks */\n\"identifier\n\"\n-- line comment\n");
            int i;
            assertEquals(22, i = scanner.indexOf('\n'));
            assertEquals(46, i = scanner.indexOf('\n', i + 1));
            assertEquals(85, i = scanner.indexOf('\n', i + 1));
            assertEquals(99, i = scanner.indexOf('\n', i + 1));
            assertEquals(115, i = scanner.indexOf('\n', i + 1));
            assertEquals(-1, scanner.indexOf('\n', i + 1));
        }

        private void testIndexOf(String sql, char c, int expectedCharIndex, String str, int expectedStringIndex)
        {
            SQLFragment frag = new SQLFragment(sql);
            SqlScanner scanner = new SqlScanner(frag);

            int i = scanner.indexOf(c);
            assertEquals("Bad index returned attempting to find character '" + c + "' in \"" + sql + "\"", expectedCharIndex, i);

            if (-1 != i)
                assertEquals(c, sql.charAt(i));

            i = scanner.indexOf(str);
            assertEquals("Bad index returned attempting to find substring \"" + str + "\" in \"" + sql + "\"", expectedStringIndex, i);

            if (-1 != i)
                assertTrue(sql.substring(i).startsWith(str));
        }

        @Test
        public void testStripComments()
        {
            testStripComments("SELECT * FROM foo", 17);
            testStripComments("SELECT /* comment here with ** */ * FROM foo", 18);
            testStripComments("SELECT /* 'comment'' \"here\" with ** \"\" */ * FROM foo", 18);

            testStripComments("SELECT \"COLUMN\" FROM bar WHERE this = that", 42);
            testStripComments("SELECT \"COLUMN\", 'VOLUME' FROM bar WHERE this = that", 52);
            testStripComments("SELECT \"CO\"\"LU\"\"MN\", 'VOL''UM''E' FROM bar WHERE this = that", 60);

            testStripComments("SELECT -- comment until the end of the line with no cr", 7);
            testStripComments("SELECT -- comment until the end of the line with cr\n", 8);
            testStripComments("SELECT -- line comment\n-- another line comment\n", 9);
            testStripComments("SELECT /* block comment \n that ends \n the statement */", 7);
            testStripComments("SELECT /* block comment \n that doesn't end \n the statement */\n", 8);
            testStripComments("SELECT /* block comment \n that doesn't end \n the statement */the end", 14);

            testStripComments("/* block comment *//* followed by a block comment */", 0);
            testStripComments("/* block comment *//* followed by a block comment */the end", 7);
            testStripComments("/* block comment */-- followed by a line comment with no cr", 0);
            testStripComments("/* block comment */-- followed by a line comment with cr\n", 1);

            testStripComments("SELECT \"IDENTIFIER /* with comment */\" FROM foo", 47);
            testStripComments("SELECT \"IDENTIFIER -- with comment\" FROM foo", 44);
        }

        private void testStripComments(String sql, int expectedLength)
        {
            SQLFragment frag = new SQLFragment(sql);
            StringBuilder stripped = new SqlScanner(frag).stripComments();
            assertEquals("Stripped SQL (\"" + stripped + "\") had unexpected length", expectedLength, stripped.length());
        }

        @Test(expected = NotFoundException.class)
        public void testUnterminatedIdentifier()
        {
            new SqlScanner("SELECT \"an identifier that does not terminate").stripComments();
        }

        @Test(expected = NotFoundException.class)
        public void testUnterminatedString()
        {
            new SqlScanner("SELECT 'a string that does not terminate").stripComments();
        }

        @Test(expected = NotFoundException.class)
        public void testUnterminatedBlockComment()
        {
            new SqlScanner("SELECT /* a block comment \n that does not terminate").stripComments();
        }
    }
}
