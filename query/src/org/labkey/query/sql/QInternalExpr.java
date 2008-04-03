package org.labkey.query.sql;

abstract public class QInternalExpr extends QExpr
{
    public void appendSource(SourceBuilder builder)
    {
        throw new UnsupportedOperationException();
    }
}
