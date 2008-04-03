package org.labkey.query.sql;

public class QSelectFrom extends QNode<QNode>
{
    public QSelect getSelect()
    {
        return getChildOfType(QSelect.class);
    }

    public QFrom getFrom()
    {
        return getChildOfType(QFrom.class);
    }


    public void appendSource(SourceBuilder builder)
    {
        getSelect().appendSource(builder);
        getFrom().appendSource(builder);
    }
}
