package org.labkey.query.sql;

public class QWhen extends QExpr
{
    public void appendSql(SqlBuilder builder)
    {
        builder.append("\nWHEN(");
        getFirstChild().appendSql(builder);
        builder.append(")THEN(");
        getLastChild().appendSql(builder);
        builder.append(")");
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append("\nWHEN(");
        getFirstChild().appendSource(builder);
        builder.append(")THEN(");
        getLastChild().appendSource(builder);
        builder.append(")");
    }
}
