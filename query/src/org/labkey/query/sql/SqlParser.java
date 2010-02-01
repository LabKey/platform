/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.query.sql.antlr.SqlBaseLexer;
import org.labkey.query.sql.antlr.SqlBaseParser;
import org.labkey.query.sql.antlr.SqlBaseTokenTypes;
import static org.labkey.query.sql.antlr.SqlBaseTokenTypes.*;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;


/**
 * SqlParser is responsible for the first two phases of the SQL transformation process
 *
 * step one - the ANTLR parser returns a tree of Nodes
 * step two - translate the tree into a tree of QNodes
 * 
 */

@SuppressWarnings({"ThrowableResultOfMethodCallIgnored","ThrowableInstanceNeverThrown"})
public class SqlParser
{
	private static Logger _log = Logger.getLogger(QuerySelect.class);

    ArrayList<Exception> _parseErrors;

    //
    // PUBLIC
    //

    public SqlParser()
    {
    }


	public QNode parseQuery(String str, List<? super QueryParseException> errors)
	{
		_parseErrors = new ArrayList<Exception>();
		try
		{
			_SqlParser parser = new _SqlParser(str, _parseErrors);
			try
			{
				parser.selectStatement();
				int last = parser.LA(1);
				if (SqlBaseTokenTypes.EOF != last)
					//noinspection ThrowableInstanceNeverThrown
					_parseErrors.add(new RecognitionException("EOF expected"));
			}
			catch (Exception x)
			{
				_parseErrors.add(x);
			}

			QNode ret = null;
			if (_parseErrors.size() == 0)
			{
				Node parseRoot = (Node) parser.getAST();
				assert parseRoot != null;

				QNode qnodeRoot = convertParseTree(parseRoot);
				assert dump(qnodeRoot);
				assert MemTracker.put(qnodeRoot);

				if (qnodeRoot instanceof QQuery || qnodeRoot instanceof QUnion)
					ret = qnodeRoot;
				else
					errors.add(new QueryParseException("This does not look like a SELECT or UNION query", null, 0, 0));
			}
			
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

            QExpr ret = qnodeRoot != null && qnodeRoot instanceof QExpr ? (QExpr) qnodeRoot : null;
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
            "select","set","some","stddev","sum",
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

                if (name.equals("convert") || name.equals("cast"))
                {
                    if (!(exprList instanceof QExprList) || exprList.childList().size() != 2)
                    {
                        _parseErrors.add(new QueryParseException(name.toUpperCase() + " function expects 2 arguments", null, node.getLine(), node.getColumn()));
                        break;
                    }
                    LinkedList<QNode> args = new LinkedList<QNode>();
                    args.add(exprList.childList().get(0));
                    args.add(constantToStringNode(exprList.childList().get(1)));
                    exprList._replaceChildren(args);
                    validateConvertConstant(args.get(1));
                }
                else if (name.equals("timestampadd") || name.equals("timestampdiff"))
                {
                    if (!(exprList instanceof QExprList) || exprList.childList().size() != 3)
                    {
                        _parseErrors.add(new QueryParseException(name.toUpperCase() + " function expects 3 arguments", null, node.getLine(), node.getColumn()));
                        break;
                    }
                    assert exprList.childList().size() == 3;
                    LinkedList<QNode> args = new LinkedList<QNode>();
                    args.add(constantToStringNode(exprList.childList().get(0)));
                    args.add(exprList.childList().get(1));
                    args.add(exprList.childList().get(2));
                    exprList._replaceChildren(args);
                    validateTimestampConstant(args.get(0));
                }
                else if (name.equals("age"))
                {
                    if (!(exprList instanceof QExprList) || exprList.childList().size() < 2 || exprList.childList().size() > 3)
                    {
                        _parseErrors.add(new QueryParseException(name.toUpperCase() + " function expects 2 or 3 arguments", null, node.getLine(), node.getColumn()));
                        break;
                    }
                    assert exprList.childList().size() == 2 || exprList.childList().size() == 3;
                    LinkedList<QNode> args = new LinkedList<QNode>();
                    args.add(exprList.childList().get(0));
                    args.add(exprList.childList().get(1));
                    if (exprList.childList().size() == 3)
                        args.add(constantToStringNode(exprList.childList().get(2)));
                    exprList._replaceChildren(args);
                    if (args.size() == 3)
                        validateTimestampConstant(args.get(2));
                }

                try
                {
                    Method m = Method.valueOf(name);
                    int count = exprList.childList().size();
                    if (count < m._minArgs || count > m._maxArgs)
                    {
                        if (m._minArgs == m._maxArgs)
                            _parseErrors.add(new QueryParseException(name.toUpperCase() + " function expects " + m._minArgs + " argument" + (m._minArgs==1?"":"s"), null, node.getLine(), node.getColumn()));
                        else
                            _parseErrors.add(new QueryParseException(name.toUpperCase() + " function expects " + m._minArgs + " to " + m._maxArgs + " arguments", null, node.getLine(), node.getColumn()));
                    }
                }
                catch (Exception x)
                {

                }
                
				break;

			default:
				break;
		}

		return qnode(node, children);
	}


    private boolean validateConvertConstant(QNode n)
    {
        if (!(n instanceof QString))
        {
            _parseErrors.add(new QueryParseException("constant expected", null, n.getLine(), n.getColumn()));
            return false;
        }
        String s = ((QString)n).getValue();
        if (s.startsWith("SQL_"))
            s = s.substring(4);
        try
        {
            Method.ConvertType.valueOf(s);
            return true;
        }
        catch (IllegalArgumentException x)
        {
            _parseErrors.add(new QueryParseException("Unrecognized constant '" + ((QString)n).getValue() + "'", null, n.getLine(), n.getColumn()));
            return false;
        }
    }


    private boolean validateTimestampConstant(QNode n)
    {
        if (!(n instanceof QString))
        {
            _parseErrors.add(new QueryParseException("constant expected", null, n.getLine(), n.getColumn()));
            return false;
        }
        String s = ((QString)n).getValue();
        if (!s.startsWith("SQL_TSI_"))
            s = "SQL_TSI_" + s;
        try
        {
            Method.TimestampDiffInterval.valueOf(s);
            return true;
        }
        catch (IllegalArgumentException x)
        {
            _parseErrors.add(new QueryParseException("Unrecognized constant '" + ((QString)n).getValue() + "'", null, n.getLine(), n.getColumn()));
            return false;
        }
    }


	private static QNode first(LinkedList<QNode> children)
	{
		return children.size() > 0 ? children.get(0) : null;
	}


	private static QNode second(LinkedList<QNode> children)
	{
		return children.size() > 1 ? children.get(1) : null;
	}
	

	private QNode constantToStringNode(QNode node)
	{
        if (node instanceof QString)
        {
            QString q = new QString();
            q.setTokenText(node.getTokenText().toUpperCase());
            q.setLineAndColumn(node);
            return q;
        }
        else if (node instanceof QIdentifier)
        {
		    String s =  ((QIdentifier)node).getIdentifier();
		    QString q = new QString(s.toUpperCase());
		    q.setLineAndColumn(node);
		    return q;
        }
        else
            return node;
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



	QNode qnode(Node node)
    {
		int type = node.getType();
		QNode q = null;
		
        switch (type)
        {
            case AS:
                q = new QAs();
				break;
            case IDENT:
            case QUOTED_IDENTIFIER:
                q = new QIdentifier();
				break;
            case DOT:
                q = new QDot();
				break;
            case QUOTED_STRING:
                q = new QString();
				break;
            case TRUE:
            case FALSE:
                q = new QBoolean();
				break;
            case NUM_DOUBLE:
            case NUM_FLOAT:
            case NUM_INT:
            case NUM_LONG:
                return new QNumber(node);
            case FROM:
                q = new QFrom();
				break;
            case SELECT_FROM:
                q = new QSelectFrom();
				break;
            case SELECT:
                q = new QSelect();
				break;
            case QUERY:
                q = new QQuery();
				break;
            case WHERE:
                q = new QWhere();
				break;
            case HAVING:
                q = new QWhere(true);
                break;
            case METHOD_CALL:
                q = new QMethodCall();
				break;
            case AGGREGATE:
            case COUNT:
                q = new QAggregate();
				break;
            case EXPR_LIST:
            case IN_LIST:
                q = new QExprList();
				break;
            case ROW_STAR:
				q = new QRowStar();
				break;
            case GROUP:
                q = new QGroupBy();
				break;
            case ORDER:
                q = new QOrder();
				break;
            case CASE:
            case CASE2:
                q = new QCase();
				break;
            case WHEN:
                q = new QWhen();
				break;
            case ELSE:
                q = new QElse();
				break;
            case NULL:
                q = new QNull();
				break;
            case LIMIT:
                q = new QLimit();
				break;
            case DISTINCT:
                q = new QDistinct();
				break;
			case UNION:
            case UNION_ALL:
				return new QUnion(node);

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
				return new QUnknownNode(node);

            case EQ: case NE: case GT: case LT: case GE: case LE: case IS: case IS_NOT: case BETWEEN:
            case PLUS: case MINUS: case UNARY_MINUS: case STAR: case DIV: case CONCAT:
            case NOT: case AND: case OR: case LIKE: case NOT_LIKE: case IN: case NOT_IN:
            case BIT_AND: case BIT_OR: case BIT_XOR:
                Operator op = Operator.ofTokenType(type);
				assert op != null;
                if (op == null)
				{
					_parseErrors.add(new RecognitionException("Unexpected token '" + node.getText() + "'", null, node.getLine(), node.getColumn()));
			    	return null;
				}
				q = op.expr();
				break;
			case EXISTS:
			case ANY:
			case SOME:
			case ALL:
				_parseErrors.add(new RecognitionException("EXISTS,ANY,ALL, and SOME are not supported"));
				 return null;
			case ESCAPE:
				_parseErrors.add(new RecognitionException("LIKE ESCAPE is not supported"));
				 return null;
			case TRAILING:
			case LEADING:
			case BOTH:
			default:
	            _parseErrors.add(new RecognitionException("Unexpected token '" + node.getText() + "'", null, node.getLine(), node.getColumn()));
				return null;
        }

		assert q != null || _parseErrors.size() > 0;
		
		// default behavior for nodes that don't have QNode(Node N) constructors
		if (q != null)
			q.from(node);
		return q;
    }




    //
    // TESTS
    //


	
    /* UNDONE keywords
    class delete elements fetch indices insert into limit new set update versioned both empty leading member of trailing
 	*/
    static String[] testSql = new String[]
    {
        "SELECT 'text',1,-2,1000000L,1.0f,3.1415926535897932384626433832795,6.02214179e23,TRUE,FALSE,0x0ab12,NULL FROM R",

        "SELECT DISTINCT R.a, b AS B FROM rel R INNER JOIN S ON R.x=S.x WHERE R.y=0 AND R.a IS NULL OR R.b IS NOT NULL",
        "SELECT R.* FROM R",

        "SELECT \"a\",\"b\",AVG(x),COUNT(x),COUNT(*),MIN(x),MAX(x),SUM(x),STDDEV(x) FROM R WHERE R.x='key' GROUP BY a,b ORDER BY a ASC, b DESC, SUM(x)",

        "SELECT a = TRUE, b = FALSE, NOT c FROM R WHERE R.x IN (2,3,5,7) OR R.x BETWEEN 100 AND 200",

        "SELECT R.a, S.\"b\" FROM R LEFT OUTER JOIN S ON R.x = S.x",

        "SELECT R.a, S.\"b\" FROM R LEFT JOIN S ON R.x = S.x",

        "SELECT 'R'.a, S.b FROM R FULL JOIN S ON R.x = S.x",

		"SELECT 'R'.a, S.b FROM R FULL OUTER JOIN S ON R.x = S.x",

        "SELECT CASE WHEN R.a=R.b THEN 'same' WHEN R.c IS NULL THEN 'different' ELSE R.c END FROM R",

        "SELECT R.a FROM R WHERE R.a LIKE 'a%'",
//		"SELECT R.a FROM R WHERE R.a LIKE 'a%' AND R.b LIKE 'a/%' ESCAPE '/'",

        "SELECT MS2SearchRuns.Flag,MS2SearchRuns.Links,MS2SearchRuns.Name,MS2SearchRuns.Created,MS2SearchRuns.RunGroups FROM MS2SearchRuns",

        "SELECT CONVERT(a, VARCHAR), CONVERT(a+b, SQL_INTEGER), CONVERT(c, 'SQL_TIMESTAMP'), CONVERT(d, 'TIMESTAMP') FROM R",
        "SELECT CONVERT(a, VARCHAR), CONVERT(a+b, SQL_INTEGER), CONVERT(c, 'SQL_Timestamp'), CONVERT(d, 'Timestamp') FROM R",
        "SELECT CAST(a AS VARCHAR), CAST(a+b AS INTEGER) FROM R",
        "SELECT CAST(a AS VARCHAR), CAST(a+b AS INTEGER) FROM R",
        "SELECT TIMESTAMPDIFF(SQL_TSI_SECOND,a,b), TIMESTAMPDIFF(SECOND,a,b), TIMESTAMPDIFF('SQL_TSI_DAY',a,b), TIMESTAMPDIFF('DAY',a,b) FROM R",
        "SELECT TIMESTAMPDIFF('SQL_TSI_Second',a,b), TIMESTAMPDIFF('Second',a,b), TIMESTAMPDIFF('SQL_TSI_Day',a,b), TIMESTAMPDIFF('Day',a,b) FROM R",
		"SELECT TIMESTAMPADD(SQL_TSI_SECOND,1,b), TIMESTAMPADD(SECOND,1,b), TIMESTAMPADD('SQL_TSI_DAY',1,b), TIMESTAMPADD('DAY',1,b) FROM R",

		"SELECT (SELECT value FROM S WHERE S.x=R.x) AS V FROM R",
		"SELECT R.value AS V FROM R WHERE R.y > (SELECT MAX(S.y) FROM S WHERE S.x=R.x)",
		"SELECT R.value, T.a, T.b FROM R INNER JOIN (SELECT S.a, S.b FROM S) T",

//		"SELECT R.a FROM R WHERE EXISTS (SELECT S.b FROM S WHERE S.x=R.x)",
//		"SELECT R.a FROM R WHERE NOT EXISTS (SELECT S.b FROM S WHERE S.x=R.x)",
//		"SELECT R.a FROM R WHERE R.value > ALL (SELECT value from S WHERE S.x=R.x)",
//		"SELECT R.a FROM R WHERE R.value > ANY (SELECT value from S WHERE S.x=R.x)",
//		"SELECT R.a FROM R WHERE R.value > SOME (SELECT value from S WHERE S.x=R.x)",

        "SELECT a FROM R WHERE a=b AND b<>c AND b!=c AND c>d AND d<e AND e<=f AND f>=g AND g IS NULL AND h IS NOT NULL " +
                " AND i BETWEEN 1 AND 2 AND j+k-l=-1 AND m/n=o AND p||q=r AND (NOT s OR t) AND u LIKE '%x%' AND u NOT LIKE '%xx%' " +
                " AND v IN (1,2) AND v NOT IN (3,4) AND x&y=1 AND x|y=1 AND x^y=1",

		"SELECT a FROM R UNION SELECT b FROM S",
        "SELECT a FROM R UNION ALL SELECT b FROM S",
		"(SELECT a FROM R) UNION ALL (SELECT b FROM S UNION (SELECT c FROM T)) ORDER BY a",
        "SELECT a, b FROM (SELECT a, b FROM R UNION SELECT a, b FROM S) U",

        // HAVING
        "SELECT \"a\",\"b\",AVG(x),COUNT(x),MIN(x),MAX(x),SUM(x),STDDEV(x) FROM R WHERE R.x='key' GROUP BY a,b HAVING SUM(x)>100 ORDER BY a ASC, b DESC, SUM(x)",

        // comments
        "SELECT DISTINCT R.a, b AS B --nadlkf (*&F asdfl alsdkfj\nFROM rel R /* aldkjf (alsdf !! */ INNER JOIN S ON R.x=S.x WHERE R.y=0 AND R.a IS NULL OR R.b IS NOT NULL",

        "BROKEN",

        // nested JOINS
        "SELECT R.a, \"S\".b FROM R LEFT OUTER JOIN (S RIGHT OUTER JOIN T ON S.y = T.y) ON R.x = S.x",
        // .*
        "SELECT R.* FROM R"
    };


    static String[] failSql = new String[]
    {
		"",
        "lutefisk",
        "SELECT R.a FROM R WHERE > 5", "SELECT R.a + AS A FROM R", "SELECT (R.a +) R.b AS A FROM R",
		"SELECT R.value, T.a, T.b FROM R INNER JOIN (SELECT S.a, S.b FROM S)",
        "SELECT \"a\",\"b\",AVG(x),COUNT(x),MIN(x),MAX(x),SUM(x),STDDEV(x) FROM R WHERE R.x='key' HAVING SUM(x)>100 ORDER BY a ASC, b DESC, SUM(x)",
        "SELECT SUM(*) FROM R",

        "BROKEN",
            
        // empty select list
        "SELECT FROM R",
        // missing FROM
        "SELECT R.a WHERE R.a > 5",
        // no table name
        "SELECT S.a AS lutefisk FROM"
    };


    static String[] exprs = new String[]
    {
            "a", "a+b", "a.b", "((a))*b"
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

        private void good(String sql)
        {
            List<QueryParseException> errors = new ArrayList<QueryParseException>();
			QNode q = (new SqlParser()).parseQuery(sql,errors);
			if (errors.size() > 0)
				fail(errors.get(0).getMessage() + "\n" + sql);
			else
				assertNotNull(q);
        }


		private void bad(String sql)
		{
			List<QueryParseException> errors = new ArrayList<QueryParseException>();
			QNode q = (new SqlParser()).parseQuery(sql,errors);
			if (errors.size() == 0)
				fail("BAD: " + sql);
		}

		
        public void test()
        {
            for (String sql : testSql)
            {
                try
                {
                    if (sql.equals("BROKEN"))
                        break;
                    good(sql);
                }
                catch (Throwable t)
                {
                    fail(t.getMessage() + "\n" + sql);
                }
            }
            for (String sql : failSql)
            {
                try
                {
                    if (sql.equals("BROKEN"))
                        break;
					bad(sql);
                }
                catch (Throwable t)
                {
                    fail(sql);
                }
            }
            for (String expr : exprs)
            {
                List<QueryParseException> errors = new ArrayList<QueryParseException>();
                QExpr e = new SqlParser().parseExpr(expr,errors);
                assertTrue(errors.isEmpty());
                assertNotNull(e);
            }
        }

        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
