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

import antlr.ASTFactory;
import antlr.RecognitionException;
import antlr.TokenStreamRecognitionException;
import org.labkey.api.query.QueryParseException;
import org.labkey.query.sql.SqlParser;
import org.labkey.query.sql.antlr.SqlBaseTokenTypes;
import org.labkey.api.util.CaseInsensitiveHashSet;
import org.labkey.api.util.PageFlowUtil;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import junit.framework.Test;
import junit.framework.TestSuite;

public class QParser
{
    static private ASTFactory _factory = new ASTFactory()
    {
        public Class getASTNodeType(int tokenType) {
            return Node.class;
        }
    };

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
        SqlParser parser = new SqlParser(str);
        parser.setASTFactory(_factory);
        parser.setFilter(true);
        return parser;
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
            parser.statement();
            int last = parser.LA(1);
            if (SqlBaseTokenTypes.UNION == last)
                throw new QueryParseException("UNION is not supported", null, 0, 0);
            Node node = (Node) parser.getAST();
            QQuery ret = node == null ? null : (QQuery) node.getQNode();
            if (ret != null)
                ret.syntaxCheck(errors);
            for (Throwable e : parser.getErrors())
            {
                errors.add(wrapParseException(e));
            }
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
        ret.removeSiblings();
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




 /* other keywords
    all any cast class convert delete elements exists fetch full indices insert into limit new set some
    timstampdiff update versioned both empty leading memeber of trailing
 */
    static String[] testSql = new String[]
    {
        "SELECT 'text',1.0f,TRUE,FALSE,0x0ab12 FROM R",
            
        "SELECT DISTINCT R.a, b AS B FROM rel R INNER JOIN S ON R.x=S.x WHERE R.y=0 AND R.a IS NULL OR R.b IS NOT NULL",

        "SELECT \"a\",\"b\",AVG(x),COUNT(x),MIN(x),MAX(x),SUM(x),STDDEV(x) FROM R WHERE R.x='key' GROUP BY a,b HAVING SUM(x)>100 ORDER BY a ASC, b DESC, SUM(x)",

        "SELECT a = TRUE, b = FALSE, NOT c FROM R WHERE R.x IN (2,3,5,7) OR R.x BETWEEN 100 AND 200",

        "SELECT R.a, S.\"b\" FROM R LEFT OUTER JOIN S ON R.x = S.x",

        "SELECT R.a, S.\"b\" FROM R LEFT JOIN S ON R.x = S.x",

        "SELECT 'R'.a, S.b FROM R FULL JOIN S ON R.x = S.x",

        "SELECT CASE WHEN R.a=R.b THEN 'same' WHEN R.c IS NULL THEN 'different' ELSE R.c END FROM R",

        "SELECT R.a FROM R WHERE R.a LIKE 'a%' AND R.b LIKE 'a/%' ESCAPE '/'",

        "SELECT MS2SearchRuns.Flag,MS2SearchRuns.Links,MS2SearchRuns.Name,MS2SearchRuns.Created,MS2SearchRuns.RunGroups FROM MS2SearchRuns",

        // TEST FROM (SELECT)
        // WHERE EXISTS/ANY/SOME (SELECT)

        "BROKEN",

        // nested JOINS
        "SELECT R.a, \"S\".b FROM R LEFT OUTER JOIN (S RIGHT OUTER JOIN T ON S.y = T.y) ON R.x = S.x",

        // should OUTER be optionally allowed
        "SELECT 'R'.a, S.b FROM R FULL OUTER JOIN S ON R.x = S.x",

        "SELECT R.* FROM R"
    };


    static String[] failSql = new String[]
    {
        "SELECT a FROM R UNION SELECT b FROM S",
        "lutefisk",
        "SELECT R.a FROM R WHERE > 5", "SELECT R.a + AS A FROM R", "SELECT (R.a +) R.b AS A FROM R",

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
