package org.labkey.query.sql;

public class QElse extends QExpr
{
    public void appendSql(SqlBuilder builder)
    {
        builder.append("\nELSE(");
        for (QExpr child : children())
        {
            child.appendSql(builder);
        }
        builder.append(")");
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append("\nELSE(");
        for (QExpr child : children())
        {
            child.appendSource(builder);
        }
        builder.append(")");
    }
}
