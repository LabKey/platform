package org.labkey.query.sql;

public class QUnknownNode extends QNode
{
    public void appendSource(SourceBuilder builder)
    {
        builder.append(getTokenText());
    }
}
