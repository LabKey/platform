package org.labkey.query.sql;

import org.apache.commons.lang.StringUtils;

public class QExprList extends QExpr
{
    public QExprList()
    {

    }

    public QExprList(QExpr... children)
    {
        for (QExpr child : children)
        {
            appendChild(child);
        }
    }
    public void appendSql(SqlBuilder builder)
    {
        builder.append("(");
        builder.pushPrefix("");
        for (QExpr child : children())
        {
            child.appendSql(builder);
            builder.nextPrefix(",");
        }
        builder.popPrefix();
        builder.append(")");
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append("(");
        builder.pushPrefix("");
        for (QExpr child : children())
        {
            child.appendSource(builder);
            builder.nextPrefix(",");
        }
        builder.popPrefix();
        builder.append(")");
    }

    public String getValueString()
    {
        StringBuilder ret = new StringBuilder("(");
        String strComma = "";
        for (QExpr child : children())
        {
            String strChild = child.getValueString();
            if (StringUtils.isEmpty(strChild))
                return null;
            ret.append(strComma);
            strComma = ",";
            ret.append(strChild);
        }
        ret.append(")");
        return ret.toString();
    }
}
