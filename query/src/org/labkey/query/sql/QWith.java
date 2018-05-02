package org.labkey.query.sql;

import org.antlr.runtime.tree.CommonTree;

public class QWith extends QNode
{
    QWith(CommonTree n)
    {
        super(QAs.class);
        from(n);
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.pushPrefix("WITH ");
        for (QNode child : children())
        {
            child.appendSource(builder);
            builder.nextPrefix(",\n");
        }
        builder.popPrefix();
    }
}
