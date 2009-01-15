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

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.ASTFactory;

import java.io.StringReader;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

import org.labkey.query.sql.antlr.SqlBaseLexer;
import org.labkey.query.sql.antlr.SqlBaseParser;
import org.labkey.query.sql.antlr.SqlBaseTokenTypes;
import static org.labkey.query.sql.antlr.SqlBaseTokenTypes.*;
import org.labkey.api.util.MemTracker;

public class SqlParser
{
	static private ASTFactory _factory = new ASTFactory()
	{
		public Class getASTNodeType(int tokenType)
		{
			return Node.class;
		}
	};

	final ArrayList<Exception> _errors = new ArrayList<Exception>();
	final _SqlParser _parser;

	public SqlParser(String str)
	{
		_parser = new _SqlParser(str);
		_parser.setASTFactory(_factory);
		_parser.setFilter(true);
		assert MemTracker.put(this);
	}

	public List<Exception> getErrors()
	{
		return _errors;
	}

	public QNode parseStatement()
	{
		try
		{
			_parser.statement();
			int last = _parser.LA(1);
			if (SqlBaseTokenTypes.UNION == last)
				//noinspection ThrowableInstanceNeverThrown
				_errors.add(new RecognitionException("UNION is not supported"));
		}
		catch (Exception x)
		{
			_errors.add(x);
		}
		if (_errors.size() != 0)
			return null;

		Node node = (Node) _parser.getAST();
		MemTracker.put(node);
		if (null == node)
			return null;

	   	QNode ret = postProcesses(node);
		assert MemTracker.put(ret);
		
		return ret;
	}


	private QNode postProcesses(Node node)
	{
		return depthFirst(node);
	}


	private QNode depthFirst(Node node)
	{
		Node child = node.getFirstChild(), prev = null;
		LinkedList<QNode> l = new LinkedList<QNode>();
		for ( ; null != child ; child = child.getNextSibling())
		{
			l.add(depthFirst(child));
		}
		return inspect(node, l);
	}

	
	private QNode inspect(Node node, LinkedList<QNode> children)
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
					LinkedList<QNode> args = exprList._children;
					QNode constant = name.startsWith("c") ? second(args) : third(args);
					if (null != constant && (constant instanceof QIdentifier))
				    	args.set(args.indexOf(constant), toStringNode(constant));
					break;
				}
				break;
			
			default:
				break;
		}
		
		return qnode(node, children);
	}


	private QNode first(LinkedList<QNode> children)
	{
		return children.size() > 0 ? children.get(0) : null;
	}


	private QNode second(LinkedList<QNode> children)
	{
		return children.size() > 1 ? children.get(1) : null;
	}
	
	private QNode third(LinkedList<QNode> children)
	{
		return children.size() > 2 ? children.get(2) : null;
	}


	/* convert identifier to string */
	private QString toStringNode(QNode node)
	{
		String s =  ((QIdentifier)node).getIdentifier();
		QString q = new QString(s);
		q.setLineAndColumn(node);
		return q;
	}


	/* private impl to control public methods */
	private class _SqlParser extends SqlBaseParser
	{
		public _SqlParser(String str)
		{
			super(new SqlBaseLexer(new StringReader(str)));
		}

		@Override
		public void reportError(RecognitionException ex)
		{
			_errors.add(ex);
		}
	}


	QNode qnode(Node n, LinkedList<QNode> children)
	{
		QNode<QNode> q = qnode(n);
		q._children = children;
		return q;
	}


	QNode<QNode> qnode(Node n)
	{
		QNode<QNode> q = newQNode(n);
		q.setTokenType(n.getType());
		q.setTokenText(n.getText());
		q._line = n._line;
		q._column = n._column;
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
			case RANGE:
			case INNER:
			case LEFT:
			case RIGHT:
			case OUTER:
			case JOIN:
			case FULL:
			case HAVING:
			case ASCENDING:
			case DESCENDING:
			case BETWEEN:
			case ESCAPE:
			case EXISTS:
			case ANY:
			case SOME:
			case ALL:
				return new QUnknownNode(type);
        }

        Operator op = Operator.ofTokenType(type);
        if (op != null)
        {
            return op.expr();
        }

		//noinspection ThrowableInstanceNeverThrown
		this._errors.add(new RecognitionException("Unexpected token '" + node.getText() + "'"));
        return new QUnknownNode(type);
    }
}
