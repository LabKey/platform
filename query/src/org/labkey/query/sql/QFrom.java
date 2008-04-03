package org.labkey.query.sql;

public class QFrom extends QNode<QNode>
{
    public void appendSource(SourceBuilder builder)
    {
        builder.pushPrefix("\nFROM ");
        for (QNode child : children())
        {
            child.appendSource(builder);
            builder.nextPrefix("\n");
        }
    }
}
