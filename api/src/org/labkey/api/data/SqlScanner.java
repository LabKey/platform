package org.labkey.api.data;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.BaseScanner;
import org.labkey.api.view.NotFoundException;

import java.util.Collection;

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
public class SqlScanner extends BaseScanner
{
    public SqlScanner(String sql)
    {
        super(sql);
    }

    public SqlScanner(SQLFragment sql)
    {
        this(sql.getRawSQL());
    }

    /**
     * Scans the SQL, skipping all comments as well as single- and double-quoted strings (while correctly handling escaped
     * quotes within those strings) and calling handler methods along the way.
     * @param fromIndex index from which to start the scan
     * @param handler Handler whose methods will be invoked
     */
    @Override
    public void scan(int fromIndex, Handler handler)
    {
        int i = fromIndex;

        while (i < _text.length())
        {
            char c = _text.charAt(i);
            String twoChars = null;

            if (i < (_text.length() - 1))
                twoChars = _text.substring(i, i + 2);

            next: if ("/*".equals(twoChars))
            {
                int endIndex = _text.indexOf("*/", i + 2) + 2;  // Skip to end of comment

                if (1 == endIndex)
                    throw new NotFoundException("Comment starting at position " + i + " was not terminated");

                if (!handler.comment(i, endIndex))
                    return;

                i = endIndex - 1; // Leave i at the last character of the comment ('/')
            }
            else if ("--".equals(twoChars))
            {
                int endIndex = _text.indexOf("\n", i + 2);

                if (-1 == endIndex)
                    endIndex = _text.length();

                if (!handler.comment(i, endIndex))
                    return;

                i = endIndex - 1; // Leave i at the last character of the comment (before cr)
            }
            else if ('\'' == c || '"' == c)
            {
                String escape = "" + c + c;

                while (++i < _text.length())
                {
                    char c2 = _text.charAt(i);
                    twoChars = null;

                    if (i < (_text.length() - 1))
                        twoChars = _text.substring(i, i + 2);

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

        @Test
        public void testSplit()
        {
            testSplit("BEGIN; SELECT * FROM foo; END", ';', "BEGIN", " SELECT * FROM foo", " END");
            testSplit("SET THIS = TRUE  ; SHOW TRANSACTION_ISOLATION;  SET THAT = FALSE;", ';', "SET THIS = TRUE  ", " SHOW TRANSACTION_ISOLATION", "  SET THAT = FALSE", "");
            testSplit("/* This is a comment; it shouldn't affect the splitting just because there are ; characters inside */ SELECT \"SOME ID; WITH SEMI\" = 'SOME CONST; WITH SEMI'; ANOTHER STATEMENT;", ';', "/* This is a comment; it shouldn't affect the splitting just because there are ; characters inside */ SELECT \"SOME ID; WITH SEMI\" = 'SOME CONST; WITH SEMI'", " ANOTHER STATEMENT", "");
            testSplit("Some Other|Character|As a; delimiter", '|', "Some Other", "Character", "As a; delimiter");
        }

        private void testSplit(String sql, char ch, String... queries)
        {
            Collection<String> split = new SqlScanner(sql).split(ch);
            assertEquals(sql + " split to the wrong number of queries.", queries.length, split.size());

            int i = 0;

            for (String s : split)
            {
                assertEquals(queries[i++], s);
            }
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
