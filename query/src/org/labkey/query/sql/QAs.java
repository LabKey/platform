package org.labkey.query.sql;

public class QAs extends QNode<QExpr>
{
    public QExpr getExpression()
    {
        return getFirstChild();
    }

    public QIdentifier getAlias()
    {
        return (QIdentifier) getLastChild();
    }

    public void appendSource(SourceBuilder builder)
    {
        getFirstChild().appendSource(builder);
        builder.append(" AS ");
        getLastChild().appendSource(builder);
    }
}
