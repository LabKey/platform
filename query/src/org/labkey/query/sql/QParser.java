/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import antlr.RecognitionException;
import antlr.TokenStreamRecognitionException;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.util.CaseInsensitiveHashSet;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class QParser
{
    static private Set<String> keywords = new CaseInsensitiveHashSet(PageFlowUtil.set(
            "all",
            "any",
            "and",
            "as",
            "asc",
            "avg",
            "between",
            "class",
            "count",
            "delete",
            "desc",
            "distinct",
            "elements",
            "escape",
            "exists",
            "false",
            "fetch",
            "from",
            "full",
            "group",
            "having",
            "in",
            "indices",
            "inner",
            "insert",
            "into",
            "is",
            "join",
            "left",
            "like",
            "limit",
            "max",
            "min",
            "new",
            "not",
            "null",
            "or",
            "order",
            "outer",
            "right",
            "select",
            "set",
            "some",
            "sum",
            "true",
            "union",
            "update",
            "user",
            "versioned",
            "where",
            "case",
            "end",
            "else",
            "then",
            "when",
            "on",
            "both",
            "empty",
            "leading",
            "member",
            "of",
            "trailing"));


    static public SqlParser getParser(String str)
    {
        return new SqlParser(str);
    }

    static public QueryParseException wrapParseException(Throwable e)
    {
        if (e instanceof QueryParseException)
        {
            return (QueryParseException) e;
        }
        if (e instanceof TokenStreamRecognitionException)
        {
            e = ((TokenStreamRecognitionException) e).recog;
        }
        if (e instanceof RecognitionException)
        {
            RecognitionException re = (RecognitionException) e;
            return new QueryParseException(re.getMessage(), re, re.getLine(), re.getColumn());
        }
        return new QueryParseException("Unexpected exception", e, 0, 0);
    }

    static public QQuery parseStatement(String str, List<? super QueryParseException> errors)
    {
        try
        {
            SqlParser parser = getParser(str);
            QNode node = parser.parseStatement();
            QQuery ret = node != null && node instanceof QQuery ? (QQuery) node : null;
            if (ret != null)
                ret.syntaxCheck(errors);
            for (Throwable e : parser.getErrors())
            {
                errors.add(wrapParseException(e));
            }
            assert MemTracker.put(node);
            assert MemTracker.put(ret);
            return ret;
        }
        catch (Exception e)
        {
            errors.add(wrapParseException(e));
        }
        return null;
    }

    static public QExpr parseExpr(String str, List<? super QueryParseException> errors)
    {
        String full = "SELECT " + str + " AS __COLUMN__ FROM __TABLE__";
        QQuery query = parseStatement(full, errors);
        if (query == null)
        {
            return null;
        }
        if (errors.size() != 0)
            return null;
        QSelect select = query.getSelect();
        if (select == null)
        {
            return null;
        }
        if (select.childList().size() != 1)
        {
            return null;
        }
        QExpr ret = ((QAs) select.getFirstChild()).getExpression();
        return ret;
    }

    static public boolean isLegalIdentifierChar(char ch, boolean fFirst)
    {
        if (!fFirst && ch >= '0' && ch <= '9')
            return true;
        return ch == '_' || ch == '$' ||
                ch >= 'a' && ch <= 'z' ||
                ch >= 'A' && ch <= 'Z';
    }

    static public boolean isLegalIdentifier(String str)
    {
        if (str.length() == 0)
            return false;
        if (keywords.contains(str))
            return false;
        for (int i = 0; i < str.length(); i ++)
        {
            if (!isLegalIdentifierChar(str.charAt(i), i == 0))
            {
                return false;
            }
        }
        return true;
    }

    static public String makeLegalIdentifier(String str)
    {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < str.length(); i ++)
        {
            char ch = str.charAt(i);
            if (isLegalIdentifierChar(ch, i == 0))
            {
                ret.append(ch);
            }
            else
            {
                ret.append('_');
            }
        }
        return ret.toString();
    }




 /* UNDONE keywords
    class delete elements fetch indices insert into limit new set update versioned both empty leading member of trailing
 */
    static String[] testSql = new String[]
    {
        "SELECT 'text',1,-2,1.0f,3.1415926535897932384626433832795,6.02214179e23,TRUE,FALSE,0x0ab12,NULL FROM R",
            
        "SELECT DISTINCT R.a, b AS B FROM rel R INNER JOIN S ON R.x=S.x WHERE R.y=0 AND R.a IS NULL OR R.b IS NOT NULL",

        "SELECT \"a\",\"b\",AVG(x),COUNT(x),MIN(x),MAX(x),SUM(x),STDDEV(x) FROM R WHERE R.x='key' GROUP BY a,b HAVING SUM(x)>100 ORDER BY a ASC, b DESC, SUM(x)",

        "SELECT a = TRUE, b = FALSE, NOT c FROM R WHERE R.x IN (2,3,5,7) OR R.x BETWEEN 100 AND 200",

        "SELECT R.a, S.\"b\" FROM R LEFT OUTER JOIN S ON R.x = S.x",

        "SELECT R.a, S.\"b\" FROM R LEFT JOIN S ON R.x = S.x",

        "SELECT 'R'.a, S.b FROM R FULL JOIN S ON R.x = S.x",

        "SELECT CASE WHEN R.a=R.b THEN 'same' WHEN R.c IS NULL THEN 'different' ELSE R.c END FROM R",

        "SELECT R.a FROM R WHERE R.a LIKE 'a%' AND R.b LIKE 'a/%' ESCAPE '/'",

        "SELECT MS2SearchRuns.Flag,MS2SearchRuns.Links,MS2SearchRuns.Name,MS2SearchRuns.Created,MS2SearchRuns.RunGroups FROM MS2SearchRuns",

        "SELECT CONVERT(a, VARCHAR), CONVERT(a+b, SQL_INTEGER), CONVERT(c, 'SQL_TIMESTAMP'), CONVERT(d, 'TIMESTAMP') FROM R",
        "SELECT CAST(a AS VARCHAR), CAST(a+b AS INTEGER) FROM R",
        "SELECT CAST(a AS VARCHAR), CAST(a+b AS INTEGER) FROM R",
        "SELECT TIMESTAMPDIFF(a,b,SQL_TSI_SECOND), TIMESTAMPDIFF(a,b,SECOND), TIMESTAMPDIFF(a,b,'SQL_TSI_DAY'), TIMESTAMPDIFF(a,b,'DAY') FROM R",
            
		"SELECT (SELECT value FROM S WHERE S.x=R.x) AS V FROM R",
		"SELECT R.value AS V FROM R WHERE R.y > (SELECT MAX(S.y) FROM S WHERE S.x=R.x)",
		"SELECT R.value, T.a, T.b FROM R INNER JOIN (SELECT S.a, S.b FROM S) T",

		"SELECT R.a FROM R WHERE EXISTS (SELECT S.b FROM S WHERE S.x=R.x)",
		"SELECT R.a FROM R WHERE NOT EXISTS (SELECT S.b FROM S WHERE S.x=R.x)",
		"SELECT R.a FROM R WHERE R.value > ALL (SELECT value from S WHERE S.x=R.x)",
		"SELECT R.a FROM R WHERE R.value > ANY (SELECT value from S WHERE S.x=R.x)",
		"SELECT R.a FROM R WHERE R.value > SOME (SELECT value from S WHERE S.x=R.x)",

        "BROKEN",

        // nested JOINS
        "SELECT R.a, \"S\".b FROM R LEFT OUTER JOIN (S RIGHT OUTER JOIN T ON S.y = T.y) ON R.x = S.x",

        // OUTER should be optionally allowed
        "SELECT 'R'.a, S.b FROM R FULL OUTER JOIN S ON R.x = S.x",

        "SELECT R.* FROM R"
    };


    static String[] failSql = new String[]
    {
        "SELECT a FROM R UNION SELECT b FROM S",
        "lutefisk",
        "SELECT R.a FROM R WHERE > 5", "SELECT R.a + AS A FROM R", "SELECT (R.a +) R.b AS A FROM R",
		"SELECT R.value, T.a, T.b FROM R INNER JOIN (SELECT S.a, S.b FROM S)",

        "BROKEN",
        // empty select list
        "SELECT FROM R",
        // missing FROM
        "SELECT R.a WHERE R.a > 5",
        // no table name
        "SELECT S.a AS lutefisk FROM"
    };


    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }

        public TestCase(String name)
        {
            super(name);
        }

        private boolean parse(String sql)
        {
            List<QueryParseException> errors = new ArrayList<QueryParseException>();
            QQuery q = QParser.parseStatement(sql,errors);
            if (errors.size() > 0)
                return false;
            // check for QUnknown
            return true;
        }

        public void test()
        {
            for (String sql : testSql)
            {
                try
                {
                    if (sql.equals("BROKEN"))
                        break;
                    assertTrue(sql, parse(sql));
                }
                catch (Throwable t)
                {
                    fail(sql);
                }
            }
            for (String sql : failSql)
            {
                try
                {
                    if (sql.equals("BROKEN"))
                        break;
                    assertFalse(sql, parse(sql));
                }
                catch (Throwable t)
                {
                    fail(sql);
                }
            }
        }

        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }

}
