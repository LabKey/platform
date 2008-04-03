package org.labkey.query.sql;

public class QLimit extends QNode<QExpr>
{
    public void appendSource(SourceBuilder builder)
    {
        builder.append("\nLIMIT ");
        for (QNode child : children())
        {
            child.appendSource(builder);
        }
    }

    public int getLimit()
    {
        QExpr child = getFirstChild();
        if (!(child instanceof QNumber))
            return 0;
        return ((QNumber) child).getValue().intValue();
    }
}
