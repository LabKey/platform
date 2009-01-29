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
import antlr.ASTFactory;
import antlr.TokenStreamRecognitionException;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.*;

import org.labkey.query.sql.antlr.SqlBaseLexer;
import org.labkey.query.sql.antlr.SqlBaseParser;
import org.labkey.query.sql.antlr.SqlBaseTokenTypes;
import static org.labkey.query.sql.antlr.SqlBaseTokenTypes.*;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.CaseInsensitiveHashSet;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.query.QueryParseException;
import org.apache.log4j.Logger;
import junit.framework.Test;
import junit.framework.TestSuite;


@SuppressWarnings({"ThrowableResultOfMethodCallIgnored","ThrowableInstanceNeverThrown"})
public class SqlParser
{
	private static Logger _log = Logger.getLogger(Query.class);

    ArrayList<Exception> _parseErrors;

    //
    // PUBLIC
    //

    public SqlParser()
    {
    }

	public QQuery parseUnion(String str, List<? super QueryParseException> errors)
	{
		_parseErrors = new ArrayList<Exception>();
		try
		{
			_SqlParser parser = new _SqlParser(str, _parseErrors);
			try
			{
				parser.union();
				int last = parser.LA(1);
				if (SqlBaseTokenTypes.EOF != last)
					//noinspection ThrowableInstanceNeverThrown
					_parseErrors.add(new RecognitionException("EOF expected"));
			}
			catch (Exception x)
			{
				_parseErrors.add(x);
			}
			if (_parseErrors.size() != 0)
				return null;

			Node parseRoot = (Node) parser.getAST();
			MemTracker.put(parseRoot);
			if (null == parseRoot)
				return null;

			QNode qnodeRoot = convertParseTree(parseRoot);
			assert dump(qnodeRoot);
			assert MemTracker.put(qnodeRoot);

			QQuery ret = qnodeRoot != null && qnodeRoot instanceof QQuery ? (QQuery) qnodeRoot : null;
			if (ret != null)
				ret.syntaxCheck(errors);
			for (Throwable e : _parseErrors)
			{
				errors.add(wrapParseException(e));
			}
			return ret;
		}
		catch (Exception e)
		{
			errors.add(wrapParseException(e));
			return null;
		}
	}

    public QQuery parseStatement(String str, List<? super QueryParseException> errors)
    {
        _parseErrors = new ArrayList<Exception>();
        try
        {
            _SqlParser parser = new _SqlParser(str, _parseErrors);
            try
            {
                parser.statement();
                int last = parser.LA(1);
                if (SqlBaseTokenTypes.UNION == last)
                    //noinspection ThrowableInstanceNeverThrown
                    _parseErrors.add(new RecognitionException("UNION is not supported"));
				else if (SqlBaseTokenTypes.EOF != last)
					//noinspection ThrowableInstanceNeverThrown
					_parseErrors.add(new RecognitionException("EOF expected"));
            }
            catch (Exception x)
            {
                _parseErrors.add(x);
            }
            if (_parseErrors.size() != 0)
                return null;

            Node parseRoot = (Node) parser.getAST();
            MemTracker.put(parseRoot);
            if (null == parseRoot)
                return null;

            QNode qnodeRoot = convertParseTree(parseRoot);
			assert dump(qnodeRoot);
            assert MemTracker.put(qnodeRoot);

            QQuery ret = qnodeRoot != null && qnodeRoot instanceof QQuery ? (QQuery) qnodeRoot : null;
            if (ret != null)
                ret.syntaxCheck(errors);
            for (Throwable e : _parseErrors)
            {
                errors.add(wrapParseException(e));
            }
            return ret;
        }
        catch (Exception e)
        {
            errors.add(wrapParseException(e));
            return null;
        }
    }

    public QExpr parseExpr(String str, List<? super QueryParseException> errors)
    {
        _parseErrors = new ArrayList<Exception>();
        try
        {
            _SqlParser parser = new _SqlParser(str, _parseErrors);
            try
            {
                parser.expression();
                int last = parser.LA(1);
                if (SqlBaseTokenTypes.EOF != last)
                    //noinspection ThrowableInstanceNeverThrown
                    _parseErrors.add(new RecognitionException("EOF expected"));
            }
            catch (Exception x)
            {
                _parseErrors.add(x);
            }
            if (_parseErrors.size() != 0)
                return null;

            Node parseRoot = (Node) parser.getAST();
            MemTracker.put(parseRoot);
            if (null == parseRoot)
                return null;

            QNode qnodeRoot = convertParseTree(parseRoot);
			assert dump(qnodeRoot);
            assert MemTracker.put(qnodeRoot);

            QQuery ret = qnodeRoot != null && qnodeRoot instanceof QQuery ? (QQuery) qnodeRoot : null;
            if (ret != null)
                ret.syntaxCheck(errors);
            for (Throwable e : _parseErrors)
            {
                errors.add(wrapParseException(e));
            }
            return ret;
        }
        catch (Exception e)
        {
            errors.add(wrapParseException(e));
            return null;
        }
    }


	private boolean dump(QNode node)
	{
		if (null != node && _log.isDebugEnabled())
		{
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			node.dump(pw);
			pw.close();
			_log.debug(sw.toString());
		}
		return true;
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
    

    //
    // IMPL
    //

    static private Set<String> keywords = new CaseInsensitiveHashSet(PageFlowUtil.set(
            "all","any","and","as","asc","avg",
            "between","both",
            "case","class","count",
            "delete","desc","distinct",
            "elements","else","empty","end","escape","exists",
            "false","fetch","from","full",
            "group",
            "having",
            "in","indices","inner","insert","into","is",
            "join",
            "leading","left","like","limit",
            "max","member","min",
            "new","not","null",
            "of","on","or","order","outer",
            "right",
            "select","set","some","sum",
            "trailing","then","true",
            "union","update","user",
            "versioned",
            "when","where"
            ));


    static QueryParseException wrapParseException(Throwable e)
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


	private QNode convertParseTree(Node node)
	{
		Node child = node.getFirstChild();
		LinkedList<QNode> l = new LinkedList<QNode>();
		for ( ; null != child ; child = child.getNextSibling())
		{
			QNode q = convertParseTree(child);
			if (q != null)
				l.add(q);
			else
				assert _parseErrors.size() > 0;
		}
		return convertNode(node, l);
	}

	
	private QNode convertNode(Node node, LinkedList<QNode> children)
	{
		switch (node.getType())
		{
			case METHOD_CALL:
                QNode id = first(children), exprList = second(children);
                if (!(id instanceof QIdentifier))
                        break;
                String name = ((QIdentifier)id).getIdentifier().toLowerCase();

                if (name.equals("convert") || name.equals("cast") || name.equals("timestampdiff"))
                {
                    if (!(exprList instanceof QExprList))
                             break;
                    LinkedList<QNode> args = new LinkedList<QNode>();
                    int i = 1;
                    for (QNode n : exprList.children())
                    {
                        if (n instanceof QIdentifier && (name.equals("timestamp") ? i==3 : i==2))
                            args.add(toStringNode(n));
                        else
                            args.add(n);
                        i++;
                    }
                    exprList._replaceChildren(args);
                }
				break;

//			case SELECT_FROM:
//				QNode select = qnode(node, children);
//				QQuery qquery = new QQuery();
//				qquery.appendChild(select);
//				return qquery;

			default:
				break;
		}

		return qnode(node, children);
	}


	private static QNode first(LinkedList<QNode> children)
	{
		return children.size() > 0 ? children.get(0) : null;
	}


	private static QNode second(LinkedList<QNode> children)
	{
		return children.size() > 1 ? children.get(1) : null;
	}
	

	/* convert identifier to string */
	private QString toStringNode(QNode node)
	{
		String s =  ((QIdentifier)node).getIdentifier();
		QString q = new QString(s);
		q.setLineAndColumn(node);
		return q;
	}


    static private ASTFactory _factory = new ASTFactory()
    {
        public Class getASTNodeType(int tokenType)
        {
            return Node.class;
        }
    };

    private static class _SqlParser extends SqlBaseParser
	{
        final ArrayList<Exception> _errors;
        
		public _SqlParser(String str, ArrayList<Exception> errors)
		{
			super(new SqlBaseLexer(new StringReader(str)));
            setASTFactory(_factory);
            setFilter(true);
            _errors = errors;
            assert MemTracker.put(this);
		}

		@Override
		public void reportError(RecognitionException ex)
		{
			_errors.add(ex);
		}
	}


	QNode qnode(Node n, LinkedList<QNode> children)
	{
		QNode q = qnode(n);
		if (q != null)
			q._replaceChildren(children);
		return q;
	}


	QNode qnode(Node n)
	{
		QNode q = newQNode(n);
		if (q == null)
			return null;
		q.setTokenType(n.getType());
		q.setTokenText(n.getText());
		q.setLineAndColumn(n);
		return q;
	}


	QNode newQNode(Node node)
    {
		int type = node.getType();
		
        switch (type)
        {
            case SqlTokenTypes.AS:
                return new QAs();
            case SqlTokenTypes.IDENT:
            case SqlTokenTypes.QUOTED_IDENTIFIER:
                return new QIdentifier();
            case SqlTokenTypes.DOT:
                return new QDot();
            case SqlTokenTypes.QUOTED_STRING:
                return new QString();
            case SqlTokenTypes.TRUE:
            case SqlTokenTypes.FALSE:
                return new QBoolean();
            case SqlTokenTypes.NUM_DOUBLE:
            case SqlTokenTypes.NUM_FLOAT:
            case SqlTokenTypes.NUM_INT:
            case SqlTokenTypes.NUM_LONG:
                return new QNumber();
            case SqlTokenTypes.FROM:
                return new QFrom();
            case SqlTokenTypes.SELECT_FROM:
                return new QSelectFrom();
            case SqlTokenTypes.SELECT:
                return new QSelect();
            case SqlTokenTypes.QUERY:
                return new QQuery();
            case SqlTokenTypes.WHERE:
                return new QWhere();
            case SqlTokenTypes.METHOD_CALL:
                return new QMethodCall();
            case SqlTokenTypes.AGGREGATE:
            case SqlTokenTypes.COUNT:
                return new QAggregate();
            case SqlTokenTypes.EXPR_LIST:
            case SqlTokenTypes.IN_LIST:
                return new QExprList();
            case SqlTokenTypes.ROW_STAR:
                return new QRowStar();
            case SqlTokenTypes.GROUP:
                return new QGroupBy();
            case SqlTokenTypes.ORDER:
                return new QOrder();
            case SqlTokenTypes.CASE:
            case SqlTokenTypes.CASE2:
                return new QCase();
            case SqlTokenTypes.WHEN:
                return new QWhen();
            case SqlTokenTypes.ELSE:
                return new QElse();
            case SqlTokenTypes.NULL:
                return new QNull();
            case SqlTokenTypes.LIMIT:
                return new QLimit();
            case SqlTokenTypes.DISTINCT:
                return new QDistinct();

			case ON:
			case INNER:
			case LEFT:
			case RIGHT:
			case OUTER:
			case JOIN:
			case FULL:
			case ASCENDING:
			case DESCENDING:
			case RANGE:
				return new QUnknownNode(type);

            case EQ: case NE: case GT: case LT: case GE: case LE: case IS: case IS_NOT: case BETWEEN:
            case PLUS: case MINUS: case UNARY_MINUS: case STAR: case DIV: case CONCAT:
            case NOT: case AND: case OR: case LIKE: case NOT_LIKE: case IN: case NOT_IN:
            case BIT_AND: case BIT_OR: case BIT_XOR:
                Operator op = Operator.ofTokenType(type);
                if (op != null)
                    return op.expr();

			case EXISTS:
			case ANY:
			case SOME:
			case ALL:
				_parseErrors.add(new RecognitionException("EXISTS,ANY,ALL, and SOME are not supported"));
				return null;
			case ESCAPE:
				_parseErrors.add(new RecognitionException("LIKE ESCAPE is not supported"));
				return null;
			case HAVING:
				_parseErrors.add(new RecognitionException("HAVING is not supported"));
				return null;

			case TRAILING:
			case LEADING:
			case BOTH:
			default:
	            _parseErrors.add(new RecognitionException("Unexpected token '" + node.getText() + "'", null, node.getLine(), node.getColumn()));
                return new QUnknownNode(type);
        }
    }


    //
    // TESTS
    //

    /* UNDONE keywords
    class delete elements fetch indices insert into limit new set update versioned both empty leading member of trailing
 	*/
    static String[] testSql = new String[]
    {
        "SELECT 'text',1,-2,1.0f,3.1415926535897932384626433832795,6.02214179e23,TRUE,FALSE,0x0ab12,NULL FROM R",

        "SELECT DISTINCT R.a, b AS B FROM rel R INNER JOIN S ON R.x=S.x WHERE R.y=0 AND R.a IS NULL OR R.b IS NOT NULL",

        "SELECT \"a\",\"b\",AVG(x),COUNT(x),MIN(x),MAX(x),SUM(x),STDDEV(x) FROM R WHERE R.x='key' GROUP BY a,b ORDER BY a ASC, b DESC, SUM(x)",

        "SELECT a = TRUE, b = FALSE, NOT c FROM R WHERE R.x IN (2,3,5,7) OR R.x BETWEEN 100 AND 200",

        "SELECT R.a, S.\"b\" FROM R LEFT OUTER JOIN S ON R.x = S.x",

        "SELECT R.a, S.\"b\" FROM R LEFT JOIN S ON R.x = S.x",

        "SELECT 'R'.a, S.b FROM R FULL JOIN S ON R.x = S.x",

        "SELECT CASE WHEN R.a=R.b THEN 'same' WHEN R.c IS NULL THEN 'different' ELSE R.c END FROM R",

        "SELECT R.a FROM R WHERE R.a LIKE 'a%'",
//		"SELECT R.a FROM R WHERE R.a LIKE 'a%' AND R.b LIKE 'a/%' ESCAPE '/'",

        "SELECT MS2SearchRuns.Flag,MS2SearchRuns.Links,MS2SearchRuns.Name,MS2SearchRuns.Created,MS2SearchRuns.RunGroups FROM MS2SearchRuns",

        "SELECT CONVERT(a, VARCHAR), CONVERT(a+b, SQL_INTEGER), CONVERT(c, 'SQL_TIMESTAMP'), CONVERT(d, 'TIMESTAMP') FROM R",
        "SELECT CAST(a AS VARCHAR), CAST(a+b AS INTEGER) FROM R",
        "SELECT CAST(a AS VARCHAR), CAST(a+b AS INTEGER) FROM R",
        "SELECT TIMESTAMPDIFF(a,b,SQL_TSI_SECOND), TIMESTAMPDIFF(a,b,SECOND), TIMESTAMPDIFF(a,b,'SQL_TSI_DAY'), TIMESTAMPDIFF(a,b,'DAY') FROM R",

		"SELECT (SELECT value FROM S WHERE S.x=R.x) AS V FROM R",
		"SELECT R.value AS V FROM R WHERE R.y > (SELECT MAX(S.y) FROM S WHERE S.x=R.x)",
		"SELECT R.value, T.a, T.b FROM R INNER JOIN (SELECT S.a, S.b FROM S) T",

//		"SELECT R.a FROM R WHERE EXISTS (SELECT S.b FROM S WHERE S.x=R.x)",
//		"SELECT R.a FROM R WHERE NOT EXISTS (SELECT S.b FROM S WHERE S.x=R.x)",
//		"SELECT R.a FROM R WHERE R.value > ALL (SELECT value from S WHERE S.x=R.x)",
//		"SELECT R.a FROM R WHERE R.value > ANY (SELECT value from S WHERE S.x=R.x)",
//		"SELECT R.a FROM R WHERE R.value > SOME (SELECT value from S WHERE S.x=R.x)",

        "SELECT a FROM R WHERE a=b AND b<>c AND b!=c AND b^=c AND c>d AND d<e AND e<=f AND f>=g AND g IS NULL AND h IS NOT NULL " +
                " AND i BETWEEN 1 AND 2 AND j+k-l=-1 AND m/n=o AND p||q=r AND (NOT s OR t) AND u LIKE '%x%' AND u NOT LIKE '%xx%' " +
                " AND v IN (1,2) AND v NOT IN (3,4) AND x&y=1 AND x|y=1 AND x^y=1",

        "BROKEN",

		// HAVING
		"SELECT \"a\",\"b\",AVG(x),COUNT(x),MIN(x),MAX(x),SUM(x),STDDEV(x) FROM R WHERE R.x='key' GROUP BY a,b HAVING SUM(x)>100 ORDER BY a ASC, b DESC, SUM(x)",

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
			QQuery q = (new SqlParser()).parseUnion(sql,errors);
            return !(errors.size() > 0 || null == q);
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
