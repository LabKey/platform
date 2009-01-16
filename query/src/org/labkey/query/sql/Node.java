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

import antlr.CommonAST;
import antlr.Token;
import org.labkey.api.util.UnexpectedException;

import java.io.PrintWriter;

public class Node extends CommonAST implements Cloneable
{
    int _line;
    int _column;

    public Node getFirstChild()
    {
        return (Node) down;
    }

    public Node getLastChild()
    {
        return (Node) ASTUtil.getLastChild(this);
    }

    public Node getNextSibling()
    {
        return (Node) right;
    }

    public Node findPreviousSibling(Node child)
    {
        return (Node) ASTUtil.findPreviousSibling(this, child);
    }

    public void setFirstChild(Node node)
    {
        down = node;
    }

    public void setNextSibling(Node node)
    {
        right = node;
    }

    public void initialize(Token token)
    {
        super.initialize(token);
        _line = token.getLine();
        _column = token.getColumn();
    }

    public void setType(int type)
    {
        if (type == getType())
            return;
		super.setType(type);
    }

    public void insertChild(Node child)
    {
        if (getFirstChild() == null)
            setFirstChild(child);
        else
        {
            Node firstChild = getFirstChild();
            setFirstChild(child);
            child.setNextSibling(firstChild);
        }
    }


    public Node clone()
    {
        try
        {
            return (Node) super.clone();
        }
        catch (CloneNotSupportedException cnse)
        {
            throw UnexpectedException.wrap(cnse);
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


    public void dump(PrintWriter out)
    {
        dump(out, "\n");
        out.println();
    }

    protected void dump(PrintWriter out, String nl)
    {
        out.printf("%s%s: %s", nl, getClass().getSimpleName(), getText());
		for (Node c = getFirstChild() ; null != c ; c = c.getNextSibling())
            c.dump(out, nl + "    |");
    }
}
