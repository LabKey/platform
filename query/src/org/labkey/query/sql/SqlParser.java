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

	final ArrayList<RecognitionException> _errors = new ArrayList<RecognitionException>();
	final _SqlParser _parser;

	public SqlParser(String str)
	{
		_parser = new _SqlParser(str);
		_parser.setASTFactory(_factory);
		_parser.setFilter(true);
		assert MemTracker.put(this);
	}

	public List<RecognitionException> getErrors()
	{
		return _errors;
	}

	public Node parseStatement() throws  RecognitionException, TokenStreamException
	{
		_parser.statement();
		int last = _parser.LA(1);
		if (SqlBaseTokenTypes.UNION == last)
			//noinspection ThrowableInstanceNeverThrown
			_errors.add(new RecognitionException("UNION is not supported"));
		if (_errors.size() != 0)
			return null;
		Node node = (Node) _parser.getAST();
		if (null == node)
			return null;
		
	   	Node ret = postProcesses(node);
		assert MemTracker.put(ret);
		return ret;
	}


	private Node postProcesses(Node node)
	{
		return depthFirst(node);
	}


	private Node depthFirst(Node node)
	{
		Node child = node.getFirstChild(), prev = null;
		for ( ; null != child ; child = child.getNextSibling())
		{
			Node result = depthFirst(child);
			if (prev == null)
				node.setFirstChild(result);
			else
				prev.setNextSibling(result);
			prev = result;
		}
		return inspect(node);
	}

	
	private Node inspect(Node node)
	{
		switch (node.getType())
		{
			case METHOD_CALL:
				Node id = first(node), exprList = second(node);
				if (null == id || null == exprList || id.getType() != IDENT && id.getType() != QUOTED_IDENTIFIER || exprList.getType() != EXPR_LIST)
					break;
				String name = ((QIdentifier)id.getQNode()).getIdentifier().toLowerCase();
				if (name.equals("convert") || name.equals("cast") || name.equals("timestampdiff"))
				{
					// convert magic constant to string
					Node constant = name.startsWith("c") ? second(exprList) : third(exprList);
					if (null != constant && (constant.getType() == IDENT || constant.getType() == QUOTED_IDENTIFIER))
						toStringNode(constant);
				}
				break;
			default:
				break;
		}
		return node;
	}

	private Node first(Node node)
	{
		return node.getFirstChild();
	}

	private Node second(Node node)
	{
		Node f = first(node);
		return null == f ? null : f.getNextSibling();
	}
	
	private Node third(Node node)
	{
		Node s = second(node);
		return null == s ? null : s.getNextSibling();
	}

	/* convert identifier to string in place */
	private void toStringNode(Node node)
	{
		String s =  ((QIdentifier)node.getQNode()).getIdentifier();
		node.setType(QUOTED_STRING);
		node.getQNode().setText(QString.quote(s));
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
}
