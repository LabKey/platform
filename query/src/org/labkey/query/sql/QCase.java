package org.labkey.query.sql;

public class QCase extends QExpr
{
    public void appendSql(SqlBuilder builder)
    {
        builder.append(" CASE ");
        for (QExpr child : children())
        {
            child.appendSql(builder);
        }
        builder.append("\nEND");
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append(" CASE ");
        for (QExpr child : children())
        {
            child.appendSource(builder);
        }
        builder.append("\nEND");
    }
}
