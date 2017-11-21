/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.JdbcType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.util.UnexpectedException;

import javax.servlet.ServletException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.labkey.query.sql.antlr.SqlBaseParser.FALSE;
import static org.labkey.query.sql.antlr.SqlBaseParser.NUM_DOUBLE;
import static org.labkey.query.sql.antlr.SqlBaseParser.NUM_FLOAT;
import static org.labkey.query.sql.antlr.SqlBaseParser.NUM_INT;
import static org.labkey.query.sql.antlr.SqlBaseParser.NUM_LONG;
import static org.labkey.query.sql.antlr.SqlBaseParser.QUOTED_STRING;
import static org.labkey.query.sql.antlr.SqlBaseParser.TRUE;


abstract public class QNode implements Cloneable
{
	private int _tokenType;
	private String _tokenText;
	private int _line;
	private int _column;

	private Class _validChildrenClass = QNode.class;
	private LinkedList<QNode> _children = new LinkedList<>();

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
        if (n instanceof SupportsAnnotations && this instanceof SupportsAnnotations)
        {
            Map<String,Object> a = ((SupportsAnnotations)n).getAnnotations();
            ((SupportsAnnotations)this).setAnnotations(convertAnnotations(a));
        }
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
			_children = new LinkedList<>();
	}

    public String getTokenText()
    {
        return _tokenText;
    }

    public int getTokenType()
    {
        return _tokenType;
    }

    static Map<String,Object> convertAnnotations(Map<String,Object> a)
    {
        if (null == a)
            return null;
        HashMap<String,Object> annotations = new HashMap<>();
        for (Map.Entry<String,Object> e : a.entrySet())
            annotations.put(e.getKey(), constant(e.getValue()));
        return annotations;
    }

    static Object constant(Object o)
    {
        if (o instanceof CommonTree)
        {
            CommonTree n = (CommonTree)o;
            switch (n.getType())
            {
                case QUOTED_STRING:
                    o = new QString();
                    ((QString)o).from(n);
                    break;
                case TRUE:
                case FALSE:
                    o = new QBoolean();
                    ((QString)o).from(n);
                    break;
                case NUM_DOUBLE:
                case NUM_FLOAT:
                case NUM_INT:
                case NUM_LONG:
                    o =new QNumber(n);
                    break;
                default:
                    break;
            }
        }
        if (o instanceof IConstant)
            return ((IConstant)o).getValue();
        return null;
    }


    public QNode clone()
    {
        try
        {
            QNode ret = (QNode) super.clone();
			ret._children = new LinkedList<>();
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
        dump(out, "\n", new IdentityHashMap<>());
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



    public void addFieldRefs(Object referant)
    {
        for (QNode child : childList())
            child.addFieldRefs(referant);
    }


    public void releaseFieldRefs(Object referant)
    {
        for (QNode child : childList())
            child.releaseFieldRefs(referant);
    }


    public static class TestCase extends Assert
    {
        SqlParser parser = new SqlParser();

        //* simple resovler for expression testing */
        QExpr resolveFields(QExpr expr, @Nullable QNode parent, @Nullable Object referant)
        {
            FieldKey key = expr.getFieldKey();
            if (key != null)
            {
                // TODO : have some known fields e.g. a,b,c,x,y,z
            }

            QExpr methodName = null;
            if (expr instanceof QMethodCall)
            {
                methodName = (QExpr)expr.childList().get(0);
                if (null == methodName.getFieldKey())
                    methodName = null;
            }

            QExpr ret = (QExpr) expr.clone();
            for (QNode child : expr.children())
            {
                //
                if (child == methodName)
                    ret.appendChild(new QField(null, methodName.getTokenText(), child));
                else
                    ret.appendChild(resolveFields((QExpr)child, expr, referant));
            }
            return ret;
        }


        private QNode parse(String x)
        {
            List<QueryParseException> errors = new ArrayList<>();
            QNode node = parser.parseExpr(x, errors);
            assertTrue(errors.isEmpty());
            return node;
        }

        private QExpr bind(String x)
        {
            QNode node = parse(x);
            QExpr expr = resolveFields((QExpr)node, null, null);
            return expr;
        }

        private void test(String t)
        {
            test(t,t);
        }

        private void test(String exprA, String exprB)
        {
            QNode a = parse(exprA);
            QNode b = parse(exprB);
            assertEquals(a, b);
        }

        private void test(String expr, JdbcType t)
        {
            QExpr n = bind(expr);
            assertEquals(n.getSqlType(), t);
        }


        @Test
        public void testType() throws Exception
        {
            test("CONVERT(now(),SQL_DATE)", JdbcType.DATE);
            test("CAST(now() AS DATE)", JdbcType.DATE);
            test("CASE WHEN 1=1 THEN CONVERT(now(),SQL_DATE) ELSE CAST(now() AS DATE) END", JdbcType.DATE);
            test("1 + 2.0", JdbcType.DOUBLE);
            test("'hello' | 'world'", JdbcType.VARCHAR);
        }


        @Test
        public void testEquals() throws SQLException, ServletException
        {
            // identifiers case insensitive
            test("a");
            test("a","A");
            test("a", "\"a\"");
            test("a","\"A\"");
            assertNotEquals(parse("a"), parse("a.b"));
            assertNotEquals(parse("b"), parse("a.b"));

            // string case sensitive
            test("'a'");
            assertNotEquals(parse("'a'"), parse("'A'"));

            test("3 * 5 + 4");
            test("CASE WHEN 1=1 THEN TRUE ELSE FALSE END");
            test("fn(A,5+4)");
            assertNotEquals(parse("SUM(a)"), parse("COUNT(a)"));
            assertNotEquals(parse("a+b"), parse("a-b"));
            // tree equals does not do arithmetic
            assertNotEquals(parse("1+2"), parse("2+1"));
        }
    }
}
