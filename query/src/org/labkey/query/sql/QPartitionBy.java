package org.labkey.query.sql;

public class QPartitionBy extends QExprList
{
    public QPartitionBy()
    {
        super();
    }

    public void appendSql(SqlBuilder builder, Query query)
    {
        builder.append("OVER(");
        if (null != getFirstChild())
        {
            builder.append("PARTITION BY ");
            builder.pushPrefix("");
            for (QNode child : children())
            {
                ((QExpr) child).appendSql(builder, query);
                builder.nextPrefix(",");
            }
            builder.popPrefix();
        }
        builder.append(")");
    }

}
