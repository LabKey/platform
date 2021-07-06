package org.labkey.query.sql;

import org.antlr.runtime.tree.CommonTree;

public class QValues extends QExprList
{
    QValues()
    {
        super(QExprList.class);
    }

    @Override
    protected void from(CommonTree n)
    {
        super.from(n);
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        builder.append(" VALUES ");
        super.appendSource(builder, false);
    }
}
