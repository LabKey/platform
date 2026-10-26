/*
 * Copyright (c) 2014-2026 LabKey Corporation
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
package org.labkey.api.data.queryprofiler;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.data.DbScope;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.WebPartView;

import jakarta.servlet.http.HttpServlet;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* User: jeckels
* Date: 2/13/14
*/
public class Query
{
    private final @Nullable
    DbScope _scope;
    private final String _sql;
    private boolean _truncated;
    private final @Nullable List<Object> _parameters;
    private final long _elapsed;
    private final StackTraceElement[] _stackTrace;
    private final boolean _isRequestThread;
    private Boolean _validSql = null;
    private @Nullable String _normalizationReason = null;
    private @Nullable String _transformedSql = null;

    Query(@Nullable DbScope scope, String sql, @Nullable List<Object> parameters, long elapsed, StackTraceElement[] stackTrace, boolean isRequestThread)
    {
        _scope = scope;
        _sql = sql;
        _parameters = null != parameters ? new ArrayList<>(parameters) : null;    // Make a copy... callers might modify the collection
        _elapsed = elapsed;
        _stackTrace = stackTrace;
        _isRequestThread = isRequestThread;
    }

    @Nullable
    public DbScope getScope()
    {
        return _scope;
    }

    public String getOriginalSql()
    {
        return _sql;
    }

    public String getSql()
    {
        // Transformed once, on the request thread for slow queries or else the profiler thread; the queue hands it between them
        if (null == _transformedSql)
            _transformedSql = transform(_sql);
        return _transformedSql;
    }

    public boolean isValidSql()
    {
        if (null == _validSql)
            throw new IllegalStateException("Must call getSql() before calling isValidSql()");

        return _validSql;
    }

    /** Why getSql() differs from the executed SQL, or null if it doesn't */
    public @Nullable String getNormalizationReason()
    {
        if (null == _validSql)
            throw new IllegalStateException("Must call getSql() before calling getNormalizationReason()");

        return _normalizationReason;
    }

    @Nullable
    public List<Object> getParameters()
    {
        return _parameters;  // TODO: Check parameters? Ignore InputStream, BLOBs, etc.?
    }

    public long getElapsed()
    {
        return _elapsed;
    }

    public StackTraceElement[] getStackTraceElements()
    {
        return _stackTrace;
    }

    public String getStackTrace()
    {
        if (_stackTrace == null)
        {
            return MiniProfiler.NO_STACK_TRACE_AVAILABLE;
        }
        StringBuilder sb = new StringBuilder();

        for (int i = 3; i < _stackTrace.length; i++)
        {
            String line = _stackTrace[i].toString();

            // Ignore all the servlet container stuff, #11159
            // Ignore everything before HttpView.render, standard action classes, etc., #13753
            if  (
                    line.startsWith(HttpView.class.getName() + ".render") ||
                    line.startsWith("org.labkey.jsp.compiled.org.labkey.core.view.template.bootstrap.PageTemplate_jsp._jspService") ||
                    line.startsWith(WebPartView.class.getName() + ".renderInternal") ||
                    line.startsWith(JspView.class.getName() + ".renderView") ||
                    line.startsWith(SimpleViewAction.class.getName() + ".handleRequest") ||
                    line.startsWith(FormViewAction.class.getName() + ".handleRequest") ||
                    line.startsWith("org.junit.internal.runners.TestMethodRunner.executeMethodBody") ||
                    line.startsWith("org.apache.catalina.core.ApplicationFilterChain.internalDoFilter") ||
                    line.startsWith(HttpServlet.class.getName() + ".service")
                )
                break;

            sb.append("at ");  // Improves compatibility with IntelliJ "Analyze Stacktrace" feature
            sb.append(line);
            sb.append('\n');
        }

        return sb.toString();
    }

    public boolean isRequestThread()
    {
        return _isRequestThread;
    }


    private static final int GENERATED_HASH_LENGTH = 32;
    private static final String SPECIMEN_TEMP_TABLE_PREFIX = "SpecimenUpload";
    private static final Pattern SPECIMEN_TEMP_TABLE_PATTERN = Pattern.compile("(SpecimenUpload)\\d{9}");
    private static final int MAX_SQL_LENGTH = 1_000_000;  // Arbitrary limit to avoid saving insane SQL, #16646

    // Transform the SQL to improve coalescing, etc.
    private String transform(String in)
    {
        String out;

        if (in.length() > MAX_SQL_LENGTH)
        {
            out = in.substring(0, MAX_SQL_LENGTH);
            _truncated = true;
        }
        else
        {
            // Remove the randomly-generated parts of temp table names
            out = removeGeneratedHashes(in);
            if (out.contains(SPECIMEN_TEMP_TABLE_PREFIX))
            {
                Matcher matcher = SPECIMEN_TEMP_TABLE_PATTERN.matcher(out);
                if (matcher.find())
                {
                    setGeneratedName(matcher.group());
                    out = matcher.replaceAll("$1");
                }
            }
        }

        _validSql = out.equals(in);   // If we changed the SQL then it's no longer valid

        return out;
    }

    private void setGeneratedName(String name)
    {
        if (null == _normalizationReason)
            _normalizationReason = "It references \"" + name + "\", a temporary table or other generated object. Its random name was removed so that similar queries are grouped together, and the object likely no longer exists.";
    }

    // Removes the last 32 digits of each identifier's run of 32+ hex digits (a GUID.makeHash() name suffix, possibly after a prefix
    // ending in a hex digit) and any '$' before them, skipping string literals and comments. A hand-written scan because
    // java.util.regex costs ~10x more per character, which matters on the request thread for large SQL. Knows only
    // standard '...' strings, not dollar-quoted or E'...' ones, so SQL using those can group imperfectly in the profiler.
    private String removeGeneratedHashes(String in)
    {
        StringBuilder out = null;
        int copied = 0;
        int length = in.length();
        int identifierEnd = -1;  // Closing quote of the double-quoted identifier being scanned, where ' and -- are literal
        int i = 0;

        while (i < length)
        {
            char c = in.charAt(i);

            if (!isHexDigit(c))
            {
                i++;
                // All four delimiters sort at or below '/', so letters, digits, and '_' fail the first comparison
                if (c <= '/' && (c == '\'' || c == '"' || c == '-' || c == '/') && i > identifierEnd + 1)
                {
                    if (c == '\'')
                        i = skipPast(in, "'", i);  // An escaped '' just reads as two adjacent literals
                    else if (c == '"')
                        identifierEnd = skipPast(in, "\"", i) - 1;
                    else if (i < length && in.charAt(i) == c && c == '-')
                        i = skipPast(in, "\n", i + 1);
                    else if (i < length && in.charAt(i) == '*' && c == '/')
                        i = skipPast(in, "*/", i + 1);
                }
                continue;
            }

            int start = i;
            while (i < length && isHexDigit(in.charAt(i)))
                i++;

            if (i - start >= GENERATED_HASH_LENGTH && isInIdentifier(in, start, identifierEnd))
            {
                start = i - GENERATED_HASH_LENGTH;
                if (start > 0 && in.charAt(start - 1) == '$')
                    start--;
                if (null == out)
                {
                    out = new StringBuilder(length);
                    setGeneratedName(identifierAround(in, start, i));
                }
                out.append(in, copied, start);
                copied = i;
            }
        }

        return null == out ? in : out.append(in, copied, length).toString();
    }

    // A run that starts with a decimal digit outside quotes and after no identifier character is a numeric literal
    private static boolean isInIdentifier(String sql, int runStart, int identifierEnd)
    {
        if (runStart < identifierEnd || !Character.isDigit(sql.charAt(runStart)))
            return true;
        char prior = runStart > 0 ? sql.charAt(runStart - 1) : ' ';
        return Character.isLetter(prior) || prior == '_' || prior == '$';
    }

    // Index just past the next occurrence of terminator at or after from, or the end of sql if it's unterminated
    private static int skipPast(String sql, String terminator, int from)
    {
        int index = sql.indexOf(terminator, from);
        return index < 0 ? sql.length() : index + terminator.length();
    }

    // Expands [start, end) to the whole schema-qualified identifier
    private static String identifierAround(String sql, int start, int end)
    {
        while (start > 0 && isIdentifierChar(sql.charAt(start - 1)))
            start--;
        while (end < sql.length() && isIdentifierChar(sql.charAt(end)))
            end++;
        return sql.substring(start, end);
    }

    private static boolean isIdentifierChar(char c)
    {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.';
    }

    private static boolean isHexDigit(char c)
    {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    public boolean isTruncated()
    {
        return _truncated;
    }

    public static class TestCase extends Assert
    {
        private static final String HASH = "0123456789abcdef0123456789abcdef";

        private static String transform(String sql)
        {
            return new Query(null, sql, null, 0, null, false).getSql();
        }

        @Test
        public void testGeneratedNames()
        {
            assertEquals("SELECT * FROM temp.tt_lsids", transform("SELECT * FROM temp.tt_lsids$" + HASH));
            assertEquals("SELECT * FROM temp.ExpMaterial_x_ t", transform("SELECT * FROM temp.ExpMaterial_x_" + HASH + " t"));
            assertEquals("SELECT * FROM edges JOIN temp.fn_()", transform("SELECT * FROM edges" + HASH + " JOIN temp.fn_" + HASH.toUpperCase() + "()"));
            assertEquals("CREATE INDEX ix__col ON t", transform("CREATE INDEX ix_" + HASH + "_col ON t"));
            assertEquals("WITH \"\" AS (SELECT 1)", transform("WITH \"" + HASH + "\" AS (SELECT 1)"));
            assertEquals("WITH  AS (SELECT 1)", transform("WITH " + HASH.substring(10) + HASH.substring(0, 10) + " AS (SELECT 1)"));
            assertEquals("SELECT * FROM temp.SampleScopeB", transform("SELECT * FROM temp.SampleScopeB" + HASH));
            assertEquals("SELECT * FROM temp.\"tt_\" WHERE \"Bob's\" = 1", transform("SELECT * FROM temp.\"tt_" + HASH + "\" WHERE \"Bob's\" = 1"));
            assertEquals("SELECT 'it''s' -- don't\nFROM temp.tt_ /* ' */", transform("SELECT 'it''s' -- don't\nFROM temp.tt_" + HASH + " /* ' */"));
            assertEquals("INSERT INTO SpecimenUpload", transform("INSERT INTO SpecimenUpload123456789"));
        }

        @Test
        public void testOtherHexUnchanged()
        {
            for (String sql : List.of(
                "SELECT * FROM core.containers WHERE entityid = 'c0a8b1e2-3f4d-5e6f-7a8b-9c0d1e2f3a4b'",
                "SELECT '" + HASH + "abcd'",
                "SELECT '" + HASH + HASH + "'",
                "SELECT * FROM t WHERE md5 = 'd41d8cd98f00b204e9800998ecf8427e'",
                "SELECT * FROM t WHERE x = 'it''s " + HASH + "' -- " + HASH,
                "SELECT /* " + HASH + " */ 1",
                "SELECT 'unterminated " + HASH,
                "SELECT * FROM t WHERE x = 123456789012345678901234567890123456",
                "SELECT 1.123456789012345678901234567890123456e10",
                "SELECT x FROM study.c108d1468_demographics"))
            {
                Query query = new Query(null, sql, null, 0, null, false);
                assertEquals(sql, query.getSql());
                assertTrue(query.isValidSql());
                assertNull(query.getNormalizationReason());
            }
        }

        @Test
        public void testNormalizationReason()
        {
            assertNormalizedName("temp.tt_lsids$" + HASH, "SELECT * FROM temp.tt_lsids$" + HASH + " t JOIN temp.edges" + HASH + " e ON 1=1");
            assertNormalizedName("SpecimenUpload123456789", "INSERT INTO SpecimenUpload123456789 VALUES (?)");
        }

        private static void assertNormalizedName(String expectedName, String sql)
        {
            Query query = new Query(null, sql, null, 0, null, false);
            query.getSql();
            assertFalse(query.isValidSql());
            String reason = query.getNormalizationReason();
            assertNotNull(reason);
            assertTrue(reason, reason.contains("\"" + expectedName + "\""));
        }
    }
}
