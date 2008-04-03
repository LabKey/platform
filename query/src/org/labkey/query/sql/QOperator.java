package org.labkey.query.sql;

final public class QOperator extends QExpr
{
    Operator _op;

    public void appendSql(SqlBuilder builder)
    {
        _op.appendSql(builder, children());
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.pushPrefix(_op.getPrefix());
        for (QExpr child : children())
        {
            boolean fParen = needsParentheses(child);
            if (fParen)
            {
                builder.pushPrefix("(");
            }
            child.appendSource(builder);
            if (fParen)
            {
                builder.popPrefix(")");
            }
            builder.nextPrefix(_op.getOperator());
        }
        builder.popPrefix();
    }

    public QOperator(Operator op)
    {
        _op = op;
    }

    public Operator getOperator()
    {
        return _op;
    }

    private boolean needsParentheses(QExpr child)
    {
        return _op.needsParentheses(child, child == getFirstChild());
    }

    public String getValueString()
    {
        StringBuilder ret = new StringBuilder(_op.getPrefix());
        boolean first = false;
        for (QExpr child : children())
        {
            String strChild = child.getValueString();
            if (strChild == null)
            {
                return null;
            }
            if (!first)
            {
                ret.append(_op.getOperator());
            }
            first = false;
            boolean fParen = needsParentheses(child);
            if (fParen)
            {
                ret.append("(");
            }
            ret.append(strChild);
            if (fParen)
            {
                ret.append(")");
            }
        }
        return ret.toString();
    }
}
