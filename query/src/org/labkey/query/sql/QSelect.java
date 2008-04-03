package org.labkey.query.sql;

public class QSelect extends QNode<QNode>
{
    public void appendSource(SourceBuilder builder)
    {
        builder.pushPrefix("SELECT ");
        for (QNode child : children())
        {
            child.appendSource(builder);
            builder.nextPrefix(",\n");
        }
        builder.popPrefix();
    }
}
