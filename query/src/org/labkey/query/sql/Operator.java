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


import java.util.*;

import org.labkey.query.sql.SqlTokenTypes;

public enum Operator
{
    eq("=", Precedence.comparison, SqlTokenTypes.EQ),
    ne("<>", Precedence.comparison, SqlTokenTypes.NE),
    gt(">", Precedence.comparison, SqlTokenTypes.GT),
    lt("<", Precedence.comparison, SqlTokenTypes.LT),
    ge(">=", Precedence.comparison, SqlTokenTypes.GE),
    le("<=", Precedence.comparison, SqlTokenTypes.LE),
    is(" IS ", Precedence.comparison, SqlTokenTypes.IS),
    is_not(" IS NOT ", Precedence.comparison, SqlTokenTypes.IS_NOT),
    add("+", Precedence.addition, SqlTokenTypes.PLUS),
    subtract("-", Precedence.addition, SqlTokenTypes.MINUS),
    plus("+", Precedence.unary, SqlTokenTypes.UNARY_PLUS)
    {
        public String getPrefix()
        {
            return "+";
        }
    },
    minus("-", Precedence.unary, SqlTokenTypes.UNARY_MINUS)
    {
        public String getPrefix()
        {
            return "-";
        }
    },
    multiply("*", Precedence.multiplication, SqlTokenTypes.STAR),
    divide("/", Precedence.multiplication, SqlTokenTypes.DIV),
    concat("||", Precedence.addition, SqlTokenTypes.CONCAT)
    {
        public void appendSql(SqlBuilder builder, Iterable<QExpr> operands)
        {
            builder.pushPrefix("");
            for (QExpr operand : operands)
            {
                operand.appendSql(builder);
                builder.nextPrefix(builder.getConcatOperator());
            }
            builder.popPrefix();
        }
    },
    not(" NOT ", Precedence.not, SqlTokenTypes.NOT)
    {
        public void appendSql(SqlBuilder builder, Iterable<QExpr> operands)
        {
            builder.append(getOperator());
            builder.append("(");
            Iterator<QExpr> i =operands.iterator();
            assert i.hasNext();
            i.next().appendSql(builder);
            assert !i.hasNext();
            builder.append(")");
        }
    },
    and(" AND ", Precedence.and, SqlTokenTypes.AND),
    or(" OR ", Precedence.like, SqlTokenTypes.OR),
    like(" LIKE ", Precedence.like, SqlTokenTypes.LIKE),
    notLike(" NOT LIKE ", Precedence.like, SqlTokenTypes.NOT_LIKE),
    in(" IN ", Precedence.like, SqlTokenTypes.IN),
    notIn(" NOT IN ", Precedence.like, SqlTokenTypes.NOT_IN),
    bit_and("&", Precedence.addition, SqlTokenTypes.BIT_AND),
    bit_or("|", Precedence.bitwiseor, SqlTokenTypes.BIT_OR),
    bit_xor("^", Precedence.bitwiseor, SqlTokenTypes.BIT_XOR),

    ;

    public enum Associativity
    {
        left,
        right,
        full
    }

    static HashMap<Integer,Operator> tokenTypeOperatorMap;
    static
    {
        tokenTypeOperatorMap = new HashMap();
        for (Operator op : values())
        {
            tokenTypeOperatorMap.put(op._tokenType, op);
        }
    }

    private final String _strOp;
    private final Precedence _precedence;
    private final int _tokenType;

    private Operator(String strOp, Precedence precedence, int tokenType)
    {
        _strOp = strOp;
        _precedence = precedence;
        _tokenType = tokenType;
    }

    public Precedence getPrecedence()
    {
        return _precedence;
    }

    public String getPrefix()
    {
        return "";
    }

    public String getOperator()
    {
        return _strOp;
    }

    public void appendSql(SqlBuilder builder, Iterable<QExpr> operands)
    {
        builder.pushPrefix(getPrefix());
        boolean first = true;
        for (QExpr operand : operands)
        {
            boolean paren = needsParentheses(operand, first);
            first = false;
            if (paren)
            {
                builder.pushPrefix("(");
            }
            operand.appendSql(builder);
            if (paren)
            {
                builder.popPrefix(")");
            }
            builder.nextPrefix(getOperator());
        }
        builder.popPrefix();
    }

    public boolean needsParentheses(QExpr child, boolean isFirstChild)
    {
        if (!(child instanceof QOperator))
            return false;
        Operator opChild = ((QOperator) child).getOperator();
        int compare = opChild.getPrecedence().compareTo(getPrecedence());
        if (compare < 0)
            return false;
        if (compare > 0)
            return true;
        if (isFirstChild)
            return false;
        if (opChild == this && getAssociativity() == Operator.Associativity.full)
            return false;
        return true;

    }

    public Associativity getAssociativity()
    {
        return Associativity.left;
    }

    static public Operator ofTokenType(int type)
    {
        return tokenTypeOperatorMap.get(type);
    }

    public QOperator expr(QExpr ... operands)
    {
        QOperator ret = new QOperator(this);
        ret.insertChildren(operands);
        return ret;
    }

    public QOperator expr(List<QExpr> operands)
    {
        QOperator ret = new QOperator(this);
        ret.insertChildren(operands);
        return ret;
    }

    public boolean is(QNode expr)
    {
        if (!(expr instanceof QOperator))
            return false;
        return ((QOperator) expr).getOperator() == this;
    }
}
