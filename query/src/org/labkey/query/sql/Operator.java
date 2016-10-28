/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
import org.labkey.api.data.dialect.SqlDialect;

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
                public void appendSql(SqlBuilder builder, Query query, Iterable<QNode> operands)
                {
                    builder.pushPrefix("");
                    int i = 0;
                    for (QNode operand : operands)
                    {
                        boolean paren = needsParentheses((QExpr) operand, true);
                        if (paren)
                            builder.pushPrefix("(");
                        ((QExpr) operand).appendSql(builder, query);
                        if (paren)
                            builder.popPrefix(")");
                        if (i == 0)
                            builder.append(" BETWEEN ");
                        else if (i == 1)
                            builder.append(" AND ");
                        ++i;
                    }
                    builder.popPrefix("");
                }
            },
    notBetween(" NOT BETWEEN ", Precedence.comparison, NOT_BETWEEN, ResultType.bool)
            {
                public void appendSql(SqlBuilder builder, Query query, Iterable<QNode> operands)
                {
                    builder.pushPrefix("");
                    int i = 0;
                    for (QNode operand : operands)
                    {
                        boolean paren = needsParentheses((QExpr) operand, true);
                        if (paren)
                            builder.pushPrefix("(");
                        ((QExpr) operand).appendSql(builder, query);
                        if (paren)
                            builder.popPrefix(")");
                        if (i == 0)
                            builder.append(" NOT BETWEEN ");
                        else if (i == 1)
                            builder.append(" AND ");
                        ++i;
                    }
                    builder.popPrefix("");
                }
            },
    add("+", Precedence.addition, PLUS, ResultType.arg, Associativity.full),
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
    multiply("*", Precedence.multiplication, STAR, ResultType.arg, Associativity.full),
    divide("/", Precedence.multiplication, DIV, ResultType.arg),
    modulo("%", Precedence.multiplication, MODULO, ResultType.arg),
    concat("||", Precedence.addition, CONCAT, ResultType.string)
            {
                public void appendSql(SqlBuilder builder, Query query, Iterable<QNode> operands)
                {
                    ArrayList<SQLFragment> terms = new ArrayList<>();
                    for (QNode operand : operands)
                    {
                        SQLFragment sqlf = ((QExpr) operand).getSqlFragment(builder.getDialect(), query);
                        JdbcType type = ((QExpr) operand).getSqlType();
                        if (null != builder.getDialect())
                            sqlf = builder.getDialect().implicitConvertToString(type, sqlf);
                        terms.add(sqlf);
                    }
                    SqlDialect d = builder.getDialect();
                    if (null == d)
                        throw new NullPointerException();
                    SQLFragment f = builder.getDialect().concatenate(terms.toArray(new SQLFragment[terms.size()]));
                    builder.append(f);
                }
            },
    not(" NOT ", Precedence.not, NOT, ResultType.bool)
            {
                public void appendSql(SqlBuilder builder, Query query, Iterable<QNode> operands)
                {
                    appendSqlUnary(builder, query, operands);
                }
            },
    and(" AND ", Precedence.and, AND, ResultType.bool, Associativity.full),
    or(" OR ", Precedence.like, OR, ResultType.bool, Associativity.full),
    like(" LIKE ", Precedence.like, LIKE, ResultType.bool)
            {
                @Override
                public void appendSql(SqlBuilder builder, Query query, Iterable<QNode> operands)
                {
                    Iterator<QNode> i = operands.iterator();
                    assert i.hasNext();
                    ((QExpr) i.next()).appendSql(builder, query);
                    builder.append(" LIKE ");
                    assert i.hasNext();
                    ((QExpr) i.next()).appendSql(builder, query);
                    if (i.hasNext())
                    {
                        builder.append(" ESCAPE ");
                        ((QExpr) i.next()).appendSql(builder, query);
                    }
                }
            },
    notLike(" NOT LIKE ", Precedence.like, NOT_LIKE, ResultType.bool)
            {
                @Override
                public void appendSql(SqlBuilder builder, Query query, Iterable<QNode> operands)
                {
                    Iterator<QNode> i = operands.iterator();
                    assert i.hasNext();
                    ((QExpr) i.next()).appendSql(builder, query);
                    builder.append(" NOT LIKE ");
                    assert i.hasNext();
                    ((QExpr) i.next()).appendSql(builder, query);
                    if (i.hasNext())
                    {
                        builder.append(" ESCAPE ");
                        ((QExpr) i.next()).appendSql(builder, query);
                    }
                }
            },
    in(" IN ", Precedence.like, IN, ResultType.bool)
            {
                @Override
                public void appendSql(SqlBuilder builder, Query query, Iterable<QNode> operands)
                {
                    appendSqlIN(builder, query, operands);
                }
            },
    notIn(" NOT IN ", Precedence.like, NOT_IN, ResultType.bool),
    bit_and("&", Precedence.bitwiseand, BIT_AND, ResultType.arg, Associativity.full, true),
    bit_or("|", Precedence.bitwiseor, BIT_OR, ResultType.arg, Associativity.full, true),
    bit_xor("^", Precedence.bitwisexor, BIT_XOR, ResultType.arg, Associativity.full, true)
            {
                @Override
                public String getOperator(SqlDialect d)
                {
                    if (null == d)
                        return "^";
                    return d.getXorOperator();
                }
            },

    exists(" EXISTS ", Precedence.unary, EXISTS, ResultType.bool)
            {
                public void appendSql(SqlBuilder builder, Query query, Iterable<QNode> operands)
                {
                    appendSqlUnary(builder, query, operands);
                }
            },
    some(" SOME ", Precedence.unary, SOME, ResultType.bool)
            {
                public void appendSql(SqlBuilder builder, Query query, Iterable<QNode> operands)
                {
                    appendSqlUnary(builder, query, operands);
                }
            },
    any(" ANY ", Precedence.unary, ANY, ResultType.bool)
            {
                public void appendSql(SqlBuilder builder, Query query, Iterable<QNode> operands)
                {
                    appendSqlUnary(builder, query, operands);
                }
            },
    all(" ALL ", Precedence.unary, ALL, ResultType.bool)
            {
                public void appendSql(SqlBuilder builder, Query query, Iterable<QNode> operands)
                {
                    appendSqlUnary(builder, query, operands);
                }
            };

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

    static HashMap<Integer, Operator> tokenTypeOperatorMap;

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
    private final boolean _forceParens;
    private final Associativity _associativity;


    private Operator(String strOp, Precedence precedence, int tokenType, ResultType resultType, Associativity assoc, boolean forceParens)
    {
        _strOp = strOp;
        _precedence = precedence;
        _tokenType = tokenType;
        _resultType = resultType;
        _forceParens = forceParens;
        _associativity = assoc;
    }

    private Operator(String strOp, Precedence precedence, int tokenType, ResultType resultType, Associativity assoc)
    {
        this(strOp, precedence, tokenType, resultType, assoc, false);
    }

    private Operator(String strOp, Precedence precedence, int tokenType, ResultType resultType)
    {
        this(strOp, precedence, tokenType, resultType, Associativity.left, false);
    }


    public Precedence getPrecedence()
    {
        return _precedence;
    }

    public String getPrefix()
    {
        return "";
    }

    public String getOperator(SqlDialect d)
    {
        return _strOp;
    }

    public void appendSql(SqlBuilder builder, Query query, Iterable<QNode> operands)
    {
        _appendSql(builder,query,operands);
    }

    private final void _appendSql(SqlBuilder builder, Query query, Iterable<QNode> operands)
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
            operand.appendSql(builder, query);
            if (paren)
            {
                builder.popPrefix(")");
            }
            builder.nextPrefix(getOperator(builder.getDialect()));
        }
        builder.popPrefix();
    }

    public void appendSqlUnary(SqlBuilder builder, Query query, Iterable<QNode> operands)
    {
        builder.append(getOperator(builder.getDialect()));
        builder.append("(");
        Iterator<QNode> i = operands.iterator();
        assert i.hasNext();
        QExpr operand = (QExpr)i.next();
        operand.appendSql(builder, query);
        assert !i.hasNext();
        builder.append(")");
    }

    public void appendSqlIN(SqlBuilder builder, Query query, Iterable<QNode> operandsIN)
    {
        _appendSql(builder, query, operandsIN);

        /*
           Using Dialect.appendInClauseSql() may generate SQL that is more type sensitive than writing the IN clause values in-line.
           We might be able to re-enable by adding some additional type consistency checks (LHS vs IN_LIST).

        ArrayList<QNode> operands = new ArrayList<>(2);
        for (QNode node : operandsIN)
            operands.add(node);

        if (operands.size() == 2 || operands.get(1).getTokenType() == IN_LIST)
        {
            QExpr inlist = (QExpr)operands.get(1);
            List<QNode> childList = inlist.childList();
            // check that all members have the same class and are constants=
            Set<Class> classes = childList.stream().map(Object::getClass).collect(Collectors.toSet());
            if (classes.size() == 1 && childList.get(0) instanceof IConstant)
            {
                // additional check, are all the types the same class in addition to being constant?
                List<Object> constants = childList.stream().map((qn) -> ((IConstant) qn).getValue()).collect(Collectors.toList());
                boolean paren = needsParentheses(((QExpr) operands.get(0)), true);
                if (paren)
                    builder.append("(");
                ((QExpr) operands.get(0)).appendSql(builder, query);
                if (paren)
                    builder.append(")");
                builder.append(" ");
                builder.getDialect().appendInClauseSql(builder, constants);
                return;
            }
        }

        // fall through
        _appendSql(builder, query, operands);
        */
    }



    public boolean needsParentheses(QExpr child, boolean isFirstChild)
    {
        if (!(child instanceof QOperator))
            return false;
        Operator opChild = ((QOperator) child).getOperator();
        if (opChild == this && getAssociativity() == Operator.Associativity.full)
            return false;
        if (_forceParens)
            return true;
        if (opChild._forceParens)
            return true;
        int compare = opChild.getPrecedence().compareTo(getPrecedence());
        if (compare < 0)
            return false;
        if (compare > 0)
            return true;
        if (isFirstChild)
            return false;
        return true;
    }

    public Associativity getAssociativity()
    {
        return _associativity;
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
