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

import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenStream;
import org.antlr.runtime.tree.CommonTree;
import org.apache.log4j.Category;
import org.labkey.api.util.UnexpectedException;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Node extends CommonTree implements Cloneable
{
    public Node()
    {
    }

    public Node(Token t)
    {
        super(t);
//        _line = t.getLine();
//        _column = t.getCharPositionInLine();
    }


    public Node(Node node)
    {
        super(node);
    }

    public static class Error extends Node
    {
        RecognitionException e;

        Error(TokenStream input, Token start, Token end, RecognitionException e)
        {
            this.e = e;        
        }

        @Override
        public int getType()
        {
            return Token.INVALID_TOKEN_TYPE;
        }
    }

//    int _line;
//    int _column;

//    public Node getFirstChild()
//    {
//        return (Node) down;
//    }
//
//    public Node getNextSibling()
//    {
//        return (Node) right;
//    }
//
//    public void setFirstChild(Node node)
//    {
//        down = node;
//    }
//
//    public void setNextSibling(Node node)
//    {
//        right = node;
//    }

//    public void initialize(Token token)
//    {
//        super.
//        super.initialize(token);
//        _line = token.getLine();
//        _column = token.getColumn();
//    }
//
//    public void setType(int type)
//    {
//        if (type == getType())
//            return;
//		super.setType(type);
//    }
//
//    public void insertChild(Node child)
//    {
//        if (getFirstChild() == null)
//            setFirstChild(child);
//        else
//        {
//            Node firstChild = getFirstChild();
//            setFirstChild(child);
//            child.setNextSibling(firstChild);
//        }
//    }


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

    public int getColumn()
    {
        return getCharPositionInLine();
    }


}
