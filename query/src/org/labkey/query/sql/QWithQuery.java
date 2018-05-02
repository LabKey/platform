package org.labkey.query.sql;

public class QWithQuery extends QNode
{
    public QWith getWith()
    {
        return getChildOfType(QWith.class);
    }

    public QExpr getExpr()
    {
        return getChildOfType(QExpr.class);
    }

    public void appendSource(SourceBuilder builder)
    {
        getWith().appendSource(builder);
        getExpr().appendSource(builder);
    }
}
