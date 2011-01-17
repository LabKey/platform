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

import org.antlr.runtime.tree.CommonTree;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.util.UnexpectedException;

import javax.servlet.ServletException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.IdentityHashMap;


abstract public class QNode implements Cloneable
{
	private int _tokenType;
	private String _tokenText;
	private int _line;
	private int _column;

	private Class _validChildrenClass = QNode.class;
	private LinkedList<QNode> _children = new LinkedList<QNode>();

	protected QNode()
	{
	}

	protected QNode(CommonTree n)
	{
		from(n);
	}

	protected void from(CommonTree n)
	{
		setTokenType(n.getType());
		setTokenText(n.getText());
		setLineAndColumn(n);
	}
	
	protected QNode(Class validChildrenClass)
	{
		_validChildrenClass = validChildrenClass;
	}

    public QNode(int type, String text)
    {
		_tokenType = type;
		_tokenText = text;
    }

    public void setTokenText(String text)
    {
        _tokenText = text;
    }

    public void setTokenType(int type)
    {
        _tokenType = type;
    }

    public Iterable<QNode> children()
    {
		return _children;
    }

    public List<QNode> childList()
    {
		return _children;
    }

    public abstract void appendSource(SourceBuilder builder);

    public String getSourceText()
    {
        SourceBuilder builder = new SourceBuilder();
        appendSource(builder);
        return builder.getText();
    }

    public <C> C getChildOfType(Class<C> clazz)
    {
        for (QNode child : children())
        {
            if (clazz.isAssignableFrom(child.getClass()))
                return (C) child;
        }
        return null;
    }

    public void appendChildren(QNode... children)
    {
        if (children != null)
			for (QNode n : children)
				appendChild(n);
    }

    public void appendChildren(List<QNode> children)
    {
		for (QNode n : children())
			appendChild(n);
    }

    public void appendChild(QNode child)
    {
		assert isValidChild(child);
		_children.add(child);
    }

	void _replaceChildren(LinkedList<QNode> list)
	{
		for (QNode n : list) assert isValidChild(n);
	   	_children = list;
	}

    protected boolean isValidChild(QNode n)
    {
        return _validChildrenClass != null && _validChildrenClass.isAssignableFrom(n.getClass());
    }

    public QNode getFirstChild()
    {
		return _children.isEmpty() ? null : _children.getFirst();
    }

    public QNode getLastChild()
    {
		return _children.isEmpty() ? null : _children.getLast();
    }

	public void removeChildren()
	{
		if (!_children.isEmpty())
			_children = new LinkedList<QNode>();
	}

    public String getTokenText()
    {
        return _tokenText;
    }

    public int getTokenType()
    {
        return _tokenType;
    }


    public QNode clone()
    {
        try
        {
            QNode ret = (QNode) super.clone();
			ret._children = new LinkedList<QNode>();
            return ret;
        }
        catch (CloneNotSupportedException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public QNode copyTree()
    {
        QNode ret = clone();
        for (QNode c : children())
            ret._children.add(c.copyTree());
        return ret;
    }


    @Override
    public boolean equals(Object other)
    {
        if (!(other instanceof QNode))
            return false;
        if (!equalsNode((QNode)other))
            return false;
        if (((QNode)other).childList().size() != childList().size())
            return false;
        for (int i=0 ; i<childList().size() ; i++)
        {
            if (!childList().get(i).equals(((QNode)other).childList().get(i)))
                return false;
        }
        return true;
    }


    protected boolean equalsNode(QNode other)
    {
        // currently implemented by subclasses of QExpr
        throw new UnsupportedOperationException();
    }


    public int getLine()
    {
        return _line;
    }

    public int getColumn()
    {
        return _column;
    }

    public void setLineAndColumn(QNode other)
    {
        if (other == null)
            return;
        _line = other.getLine();
        _column = other.getColumn();
    }

	public void setLineAndColumn(CommonTree other)
	{
		if (other == null)
			return;
		_line = other.getLine();
		_column = other.getCharPositionInLine();
	}


//    public boolean equalsTree(QNode that)
//    {
//        if (this == that)
//            return true;
//		if (getTokenType() != that.getTokenType() || !StringUtils.equals(getTokenText(), that.getTokenText()))
//			return false;
//		if (_children.size() != that._children.size())
//			return false;
//
//		Iterator<QNode> a = (Iterator<QNode>)_children.iterator();
//		Iterator<QNode> b = (Iterator<QNode>)that._children.iterator();
//		while (a.hasNext())
//		{
//			if (a.next().equalsTree(b.next()))
//				return false;
//		}
//        return true;
//    }


    public void dump(PrintWriter out)
    {
        dump(out, "\n", new IdentityHashMap<Object,Object>());
        out.println();
    }

    protected void dump(PrintWriter out, String nl, IdentityHashMap<Object,Object> dumped)
    {
		boolean seen = null != dumped.put(this, this);
        out.printf("%s%s: %s\t\t{%d%s}", nl, getClass().getSimpleName(), getTokenText(), System.identityHashCode(this), seen ? " VISITED" : "");
		if (!seen)
			for (QNode c : children())
            	c.dump(out, nl + "    |", dumped);
    }



    public static class TestCase extends Assert
    {
        SqlParser parser = new SqlParser();
        private QNode p(String x)
        {
            List<QueryParseException> errors = new ArrayList<QueryParseException>();
            QNode node = parser.parseExpr(x, errors);
            assertTrue(errors.isEmpty());
            return node;
        }

        private void assertNotEquals(Object a, Object b)
        {
            assertFalse(a.equals(b));
        }
        

        private void test(String t)
        {
            test(t,t);
        }

        private void test(String exprA, String exprB)
        {
            QNode a = p(exprA);
            QNode b = p(exprB); 
            assertEquals(a, b);
        }

        @Test
        public void testEquals() throws SQLException, ServletException
        {
            // identifiers case insensitive
            test("a");
            test("a","A");
            test("a", "\"a\"");
            test("a","\"A\"");
            assertNotEquals(p("a"), p("a.b"));
            assertNotEquals(p("b"), p("a.b"));

            // string case sensitive
            test("'a'");
            assertNotEquals(p("'a'"), p("'A'"));

            test("3 * 5 + 4");
            test("CASE WHEN 1=1 THEN TRUE ELSE FALSE END");
            test("fn(A,5+4)");
            assertNotEquals(p("SUM(a)"), p("COUNT(a)"));
            assertNotEquals(p("a+b"), p("a-b"));
            // tree equals does not do arithmetic
            assertNotEquals(p("1+2"), p("2+1"));
        }
    }
}
