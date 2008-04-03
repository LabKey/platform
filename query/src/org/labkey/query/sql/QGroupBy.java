package org.labkey.query.sql;

public class QGroupBy extends QNode<QExpr>
{
    public void appendSource(SourceBuilder builder)
    {
        builder.pushPrefix("\nGROUP BY ");
        for (QExpr child : children())
        {
            child.appendSource(builder);
            builder.nextPrefix(",");
        }
        builder.popPrefix();
    }
}
