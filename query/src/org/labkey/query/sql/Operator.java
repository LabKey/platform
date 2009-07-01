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


import java.util.*;

public enum Operator
{
    eq("=", Precedence.comparison, SqlTokenTypes.EQ, ResultType.bool),
    ne("<>", Precedence.comparison, SqlTokenTypes.NE, ResultType.bool),
    gt(">", Precedence.comparison, SqlTokenTypes.GT, ResultType.bool),
    lt("<", Precedence.comparison, SqlTokenTypes.LT, ResultType.bool),
    ge(">=", Precedence.comparison, SqlTokenTypes.GE, ResultType.bool),
    le("<=", Precedence.comparison, SqlTokenTypes.LE, ResultType.bool),
    is(" IS ", Precedence.comparison, SqlTokenTypes.IS, ResultType.bool),
    is_not(" IS NOT ", Precedence.comparison, SqlTokenTypes.IS_NOT, ResultType.bool),
    between(" BETWEEN ", Precedence.comparison, SqlTokenTypes.BETWEEN, ResultType.bool)
    {
        public void appendSql(SqlBuilder builder, Iterable<QNode> operands)
        {
            builder.pushPrefix("");
            int i=0;
            for (QNode operand : operands)
            {
                boolean paren = needsParentheses((QExpr)operand, true);
                if (paren)
                    builder.pushPrefix("(");
                ((QExpr)operand).appendSql(builder);
                if (paren)
                    builder.popPrefix(")");
                if (i==0)
                    builder.append(" BETWEEN ");
                else if (i==1)
                    builder.append(" AND ");
                ++i;
            }
            builder.popPrefix("");
        }
    },
    add("+", Precedence.addition, SqlTokenTypes.PLUS, ResultType.arg),
    subtract("-", Precedence.addition, SqlTokenTypes.MINUS, ResultType.arg),
    plus("+", Precedence.unary, SqlTokenTypes.UNARY_PLUS, ResultType.arg)
    {
        public String getPrefix()
        {
            return "+";
        }
    },
    minus("-", Precedence.unary, SqlTokenTypes.UNARY_MINUS, ResultType.arg)
    {
        public String getPrefix()
        {
            return "-";
        }
    },
    multiply("*", Precedence.multiplication, SqlTokenTypes.STAR, ResultType.arg),
    divide("/", Precedence.multiplication, SqlTokenTypes.DIV, ResultType.arg),
    concat("||", Precedence.addition, SqlTokenTypes.CONCAT, ResultType.string)
    {
        public void appendSql(SqlBuilder builder, Iterable<QNode> operands)
        {
            builder.pushPrefix("");
            for (QNode operand : operands)
            {
                ((QExpr)operand).appendSql(builder);
                builder.nextPrefix(builder.getConcatOperator());
            }
            builder.popPrefix();
        }
    },
    not(" NOT ", Precedence.not, SqlTokenTypes.NOT, ResultType.bool)
    {
        public void appendSql(SqlBuilder builder, Iterable<QNode> operands)
        {
            builder.append(getOperator());
            builder.append("(");
            Iterator<QNode> i = operands.iterator();
            assert i.hasNext();
			QExpr operand = (QExpr)i.next();
            operand.appendSql(builder);
            assert !i.hasNext();
            builder.append(")");
        }
    },
    and(" AND ", Precedence.and, SqlTokenTypes.AND, ResultType.bool),
    or(" OR ", Precedence.like, SqlTokenTypes.OR, ResultType.bool),
    like(" LIKE ", Precedence.like, SqlTokenTypes.LIKE, ResultType.bool),
    notLike(" NOT LIKE ", Precedence.like, SqlTokenTypes.NOT_LIKE, ResultType.bool),
    in(" IN ", Precedence.like, SqlTokenTypes.IN, ResultType.bool),
    notIn(" NOT IN ", Precedence.like, SqlTokenTypes.NOT_IN, ResultType.bool),
    bit_and("&", Precedence.addition, SqlTokenTypes.BIT_AND, ResultType.arg),
    bit_or("|", Precedence.bitwiseor, SqlTokenTypes.BIT_OR, ResultType.arg),
    bit_xor("^", Precedence.bitwiseor, SqlTokenTypes.BIT_XOR, ResultType.arg),

    ;

    public enum ResultType
    {
        bool, arg, string
    }

    public enum Associativity
    {
        left,
        right,
        full
    }

    static HashMap<Integer,Operator> tokenTypeOperatorMap;
    static
    {
        tokenTypeOperatorMap = new HashMap<Integer, Operator>();
        for (Operator op : values())
        {
            tokenTypeOperatorMap.put(op._tokenType, op);
        }
    }

    private final String _strOp;
    private final Precedence _precedence;
    private final int _tokenType;
    private final ResultType _resultType;

    private Operator(String strOp, Precedence precedence, int tokenType, ResultType resultType)
    {
        _strOp = strOp;
        _precedence = precedence;
        _tokenType = tokenType;
        _resultType = resultType;
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

    public void appendSql(SqlBuilder builder, Iterable<QNode> operands)
    {
        builder.pushPrefix(getPrefix());
        boolean first = true;
        for (QNode n : operands)
        {
			QExpr operand = (QExpr)n;
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
        ret.appendChildren(operands);
        return ret;
    }

    public QOperator expr(List<QNode> operands)
    {
        QOperator ret = new QOperator(this);
        ret.appendChildren(operands);
        return ret;
    }

    public boolean is(QNode expr)
    {
        if (!(expr instanceof QOperator))
            return false;
        return ((QOperator) expr).getOperator() == this;
    }

    public ResultType getResultType()
    {
        return _resultType;
    }
}
