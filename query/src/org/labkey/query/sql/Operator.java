/*
 * Copyright (c) 2006-2012 LabKey Corporation
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


import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;

import java.util.*;

import static org.labkey.query.sql.antlr.SqlBaseParser.*;

public enum Operator
{
    eq("=", Precedence.comparison, EQ, ResultType.bool),
    ne("<>", Precedence.comparison, NE, ResultType.bool),
    gt(">", Precedence.comparison, GT, ResultType.bool),
    lt("<", Precedence.comparison, LT, ResultType.bool),
    ge(">=", Precedence.comparison, GE, ResultType.bool),
    le("<=", Precedence.comparison, LE, ResultType.bool),
    is(" IS ", Precedence.comparison, IS, ResultType.bool),
    is_not(" IS NOT ", Precedence.comparison, IS_NOT, ResultType.bool),
    between(" BETWEEN ", Precedence.comparison, BETWEEN, ResultType.bool)
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
    notBetween(" NOT BETWEEN ", Precedence.comparison, NOT_BETWEEN, ResultType.bool)
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
                    builder.append(" NOT BETWEEN ");
                else if (i==1)
                    builder.append(" AND ");
                ++i;
            }
            builder.popPrefix("");
        }
    },
    add("+", Precedence.addition, PLUS, ResultType.arg),
    subtract("-", Precedence.addition, MINUS, ResultType.arg),
    plus("+", Precedence.unary, UNARY_PLUS, ResultType.arg)
    {
        public String getPrefix()
        {
            return "+";
        }
    },
    minus("-", Precedence.unary, UNARY_MINUS, ResultType.arg)
    {
        public String getPrefix()
        {
            return "-";
        }
    },
    multiply("*", Precedence.multiplication, STAR, ResultType.arg),
    divide("/", Precedence.multiplication, DIV, ResultType.arg),
    concat("||", Precedence.addition, CONCAT, ResultType.string)
    {
        public void appendSql(SqlBuilder builder, Iterable<QNode> operands)
        {
            ArrayList<SQLFragment> terms = new ArrayList<>();
            for (QNode operand : operands)
            {
                SQLFragment sqlf = ((QExpr)operand).getSqlFragment(builder.getDbSchema());
                JdbcType type = ((QExpr)operand).getSqlType();
                if (null != builder.getDialect())
                    sqlf = builder.getDialect().implicitConvertToString(type, sqlf);
                terms.add(sqlf);
            }
            SQLFragment f = builder.getDialect().concatenate(terms.toArray(new SQLFragment[terms.size()]));
            builder.append(f);
        }
    },
    not(" NOT ", Precedence.not, NOT, ResultType.bool)
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
    and(" AND ", Precedence.and, AND, ResultType.bool),
    or(" OR ", Precedence.like, OR, ResultType.bool),
    like(" LIKE ", Precedence.like, LIKE, ResultType.bool)
    {
        @Override
        public void appendSql(SqlBuilder builder, Iterable<QNode> operands)
        {
            Iterator<QNode> i = operands.iterator();
            assert i.hasNext();
            ((QExpr)i.next()).appendSql(builder);
            builder.append(" LIKE ");
            assert i.hasNext();
            ((QExpr)i.next()).appendSql(builder);
            if (i.hasNext())
            {
                builder.append(" ESCAPE ");
                ((QExpr)i.next()).appendSql(builder);
            }
        }
    },
    notLike(" NOT LIKE ", Precedence.like, NOT_LIKE, ResultType.bool)
    {
        @Override
        public void appendSql(SqlBuilder builder, Iterable<QNode> operands)
        {
            Iterator<QNode> i = operands.iterator();
            assert i.hasNext();
            ((QExpr)i.next()).appendSql(builder);
            builder.append(" NOT LIKE ");
            assert i.hasNext();
            ((QExpr)i.next()).appendSql(builder);
            if (i.hasNext())
            {
                builder.append(" ESCAPE ");
                ((QExpr)i.next()).appendSql(builder);
            }
        }
    },
    in(" IN ", Precedence.like, IN, ResultType.bool),
    notIn(" NOT IN ", Precedence.like, NOT_IN, ResultType.bool),
    bit_and("&", Precedence.addition, BIT_AND, ResultType.arg),
    bit_or("|", Precedence.bitwiseor, BIT_OR, ResultType.arg),
    bit_xor("^", Precedence.bitwiseor, BIT_XOR, ResultType.arg),

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
        tokenTypeOperatorMap = new HashMap<>();
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
