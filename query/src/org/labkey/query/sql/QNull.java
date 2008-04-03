package org.labkey.query.sql;

public class QNull extends QExpr
{
    public void appendSql(SqlBuilder builder)
    {
        builder.append("NULL");
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append("NULL");
    }

    public String getValueString()
    {
        return " NULL ";
    }
}
