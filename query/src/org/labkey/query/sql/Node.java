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

import antlr.CommonAST;
import antlr.Token;
import antlr.collections.AST;
import org.labkey.api.util.UnexpectedException;

public class Node extends CommonAST implements Cloneable
{
    QNode _qnode;
    int _line;
    int _column;

    public QNode getQNode()
    {
        return _qnode;
    }

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

    public void setQNode(QNode node, int type)
    {
        super.setType(type);
        _qnode = node;
        _qnode.setNode(this);
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
        setQNode(newQNode(type), type);
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

    private QNode newQNode(int type)
    {
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
        }
        Operator op = Operator.ofTokenType(type);
        if (op != null)
        {
            return op.expr();
        }
        return new QUnknownNode(type);
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
}
