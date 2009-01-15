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

import org.labkey.api.util.UnexpectedException;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;


abstract public class QNode<T extends QNode> implements Cloneable
{
	int _tokenType;
	String _tokenText;
	int _line;
	int _column;

	LinkedList<T> _children = new LinkedList<T>();


	protected QNode()
	{

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

    public Iterable<T> children()
    {
		return _children;
    }

    public List<T> childList()
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

    public void appendChildren(T... children)
    {
        if (children == null || children.length == 0)
            return;
		appendChildren(Arrays.asList(children));
    }

    public void appendChildren(List<? extends T> children)
    {
		_children.addAll(children);
    }

    public void appendChild(T child)
    {
		_children.add(child);
    }

    public T getFirstChild()
    {
		return _children.isEmpty() ? null : _children.getFirst();
    }

    public T getLastChild()
    {
		return _children.isEmpty() ? null : _children.getLast();
    }

	public void removeChildren()
	{
		if (!_children.isEmpty())
			_children = new LinkedList<T>();
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
			ret._children = new LinkedList<T>();
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
        dump(out, "\n");
        out.println();
    }

    protected void dump(PrintWriter out, String nl)
    {
        out.printf("%s%s: %s", nl, getClass().getSimpleName(), getTokenText());
		for (QNode c : children())
            c.dump(out, nl + "    |");
    }
}
