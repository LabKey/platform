package org.labkey.query.sql;

import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;

public class QRowStar extends QExpr
{
    public void appendSource(SourceBuilder builder)
    {
        builder.append("*");
    }

    public FieldKey getFieldKey()
    {
        return null;
    }

    public QueryParseException syntaxCheck(QNode parent)
    {
        return new QueryParseException("COUNT(*) is not supported", null, getLine(), getColumn());
    }

    public void appendSql(SqlBuilder builder)
    {
        throw new UnsupportedOperationException();
    }
}
