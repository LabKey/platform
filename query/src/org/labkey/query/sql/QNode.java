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

import antlr.collections.AST;
import org.labkey.api.util.UnexpectedException;

import java.io.Writer;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

abstract public class QNode<T extends QNode> implements Cloneable
{
    private Node _node;

    static private QNode qNode(AST ast)
    {
        if (ast == null)
            return null;
        return ((Node) ast).getQNode();
    }

    public QNode()
    {
        _node = new Node();
        _node.setQNode(this, 0);
    }

    public void setNode(Node node)
    {
        _node = node;
    }

    public void setText(String text)
    {
        _node.setText(text);
    }

    public void setTokenType(int type)
    {
        _node.setQNode(this, type);
    }

    static private class QNodeIterator<T extends QNode> implements Iterator<T>
    {
        T _node;
        public QNodeIterator(T node)
        {
            _node = node;
        }
        public boolean hasNext()
        {
            return _node != null;
        }

        public T next()
        {
            T ret = _node;
            _node = (T) _node.getNextSibling();
            return ret;
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }

    static private class QNodeIterable<T extends QNode> implements Iterable<T>
    {
        T _node;
        public QNodeIterable(T first)
        {
            _node = first;
        }
        public Iterator<T> iterator()
        {
            return new QNodeIterator(_node);
        }
    }

    public Iterable<T> children()
    {
        return new QNodeIterable(getFirstChild());
    }

    public List<T> childList()
    {
        List<T> ret = new ArrayList();
        for (T child : children())
        {
            ret.add(child);
        }
        return ret;
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

    public void insertChildren(T... children)
    {
        if (children == null)
            return;
        insertChildren(Arrays.asList(children));
    }

    public void insertChildren(List<? extends T> children)
    {
        for (int i = children.size() - 1; i >= 0; i--)
        {
            ASTUtil.insertChild(_node, children.get(i)._node);
        }
    }

    public void insertChild(T child)
    {
        // This child might belong to someone else, so we clone it.
        T newChild = (T) child.clone();
        newChild.setFirstChild(child.getFirstChild());
        ASTUtil.insertChild(_node, newChild._node);
    }

    public void appendChild(T child)
    {
        T lastChild = getLastChild();
        if (lastChild == null)
        {
            _node.setFirstChild(child._node);
        }
        else
        {
            lastChild._node.setNextSibling(child._node);
        }
    }

    public T getFirstChild()
    {
        return (T) qNode(_node.getFirstChild());
    }

    public T getLastChild()
    {
        return (T) qNode(_node.getLastChild());
    }

    public QNode getNextSibling()
    {
        return qNode(_node.getNextSibling());
    }

    public void removeChild(T node)
    {
        Node previousSibling = _node.findPreviousSibling(node._node);
        if (previousSibling == null)
        {
            _node.setFirstChild(node._node.getNextSibling());
        }
        else
        {
            previousSibling.setNextSibling(node._node.getNextSibling());
        }
    }
    public void removeSiblings()
    {
        _node.setNextSibling(null);
    }
    public void setNextSibling(QNode node)
    {
        _node.setNextSibling(node._node);
    }
    public String getTokenText()
    {
        return _node.getText();
    }

    public int getTokenType()
    {
        return _node.getType();
    }

    public boolean isNextSibling(int tokenType)
    {
        QNode nextSibling = getNextSibling();
        if (nextSibling == null)
        {
            return false;
        }
        return tokenType == nextSibling.getTokenType();
    }

    public void setFirstChild(QNode node)
    {
        if (node == null)
        {
            _node.setFirstChild(null);
            return;        
        }
        _node.setFirstChild(node._node);
    }

    public QNode clone()
    {
        try
        {
            QNode ret = (QNode) super.clone();
            ret._node = _node.clone();
            ret._node.setFirstChild(null);
            ret._node.setNextSibling(null);
            ret._node.setQNode(ret, getTokenType());
            return ret;
        }
        catch (CloneNotSupportedException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public int getLine()
    {
        return _node.getLine();
    }

    public int getColumn()
    {
        return _node.getColumn();
    }

    public void setLineAndColumn(QNode other)
    {
        if (other == null)
            return;
        _node._line = other.getLine();
        _node._column = other.getColumn();
    }

    public boolean equalsTree(QNode that)
    {
        if (this == that)
            return true;
        return this._node.equalsTree(that._node);
    }

    public void dump(PrintWriter out)
    {
        dump(out, "\n");
        out.println();
    }

    protected void dump(PrintWriter out, String nl)
    {
        out.printf("%s%s: %s", nl, getClass().getSimpleName(), getTokenText());
        for (QNode c = getFirstChild() ; c != null ; c = c.getNextSibling())
            c.dump(out, nl + "    |");
    }
}
