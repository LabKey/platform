package org.labkey.api.data;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.view.NotFoundException;

/**
 * A simple scanner for SQL text that understands block & single-line comments, double-quoted identifiers, single-quoted strings,
 * and quote escaping. It is not a SQL tokenizer or parser... it merely enables simple text operations that are aware of comments
 * and strings. At the moment, it only supports finding individual characters outside of comments and strings. In the future, we
 * should extend it to support finding full strings, stripping comments, and other useful operations.
 *
 * TODO: Retrofit SqlScriptExecutor.stripComments() to use this
 * TODO: BaseMicrosoftSqlServerDialect._addReselect sql.indexOf("WHERE") should use SqlScanner instead
 * Consider: Use in SQLFragment.getFilterText() replacements
 *
 * Created by adam on 6/29/2017.
 */
public class SqlScanner
{
    private final String _sql;

    public SqlScanner(SQLFragment sql)
    {
        _sql = sql.getRawSQL();
    }

    /**
     * Find index of the specific character in SQL, skipping all comments as well as single- and double-quoted strings
     * (while correctly handling escaped quotes within those strings).
     * @param find Character to find
     * @return Offset within sql if present, otherwise -1
     */
    public int find(char find)
    {
        int i = 0;

        while (i < _sql.length())
        {
            char c = _sql.charAt(i);

            if (find == c)
                return i;

            String twoChars = null;

            if (i < (_sql.length() - 1))
                twoChars = _sql.substring(i, i + 2);

            next: if ("/*".equals(twoChars))
            {
                i = _sql.indexOf("*/", i + 2) + 2;  // Skip comment completely

                if (1 == i)
                    throw new NotFoundException("Comment was not terminated");
            }
            else if ("--".equals(twoChars))
            {
                i = _sql.indexOf("\n", i + 2);  // Skip comment until end of line but leave the cr

                if (0 == i)
                    i = _sql.length();
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

            i++;
        }

        return -1;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            test("SELECT * FROM foo", '*', 7);
            test("SELECT /* comment here with ** */ * FROM foo", '*', 34);
            test("SELECT /* 'comment'' \"here\" with ** \"\" */ * FROM foo", '*', 42);

            test("SELECT \"COLUMN\" FROM bar WHERE this = that", 'O', 18);
            test("SELECT \"COLUMN\", 'VOLUME' FROM bar WHERE this = that", 'O', 28);
            test("SELECT \"CO\"\"LU\"\"MN\", 'VOL''UM''E' FROM bar WHERE this = that", 'O', 36);
        }

        private void test(String sql, char c, int expectedIndex)
        {
            SQLFragment frag = new SQLFragment(sql);
            assertEquals("Bad index returned attempting to find " + c + " in " + frag.getRawSQL(), expectedIndex, new SqlScanner(frag).find(c));
        }
    }
}
