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

import org.labkey.api.util.UnexpectedException;

import java.io.PrintWriter;
import java.util.Arrays;
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
		assert _validChildrenClass.isAssignableFrom(child.getClass());
		_children.add(child);
    }

	void _replaceChildren(LinkedList<QNode> list)
	{
		for (QNode n : list) assert _validChildrenClass.isAssignableFrom(n.getClass());
	   	_children = list;
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

	public void setLineAndColumn(Node other)
	{
		if (other == null)
			return;
		_line = other.getLine();
		_column = other.getColumn();
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
}
