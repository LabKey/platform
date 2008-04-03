package org.labkey.query.sql;

import org.labkey.api.query.FieldKey;

public class QDot extends QFieldKey
{
    public QDot()
    {
    }

    public QDot(QExpr left, QExpr right)
    {
        insertChildren(left, right);
    }

    public FieldKey getFieldKey()
    {
        FieldKey left = getFirstChild().getFieldKey();
        if (left == null)
            return null;
        FieldKey right = getLastChild().getFieldKey();
        if (right == null || right.getTable() != null)
        {
            return null;
        }
        return new FieldKey(left, right.getName());
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.pushPrefix("");
        for (QNode child : children())
        {
            child.appendSource(builder);
            builder.nextPrefix(".");
        }
        builder.popPrefix();
    }

    public String getValueString()
    {
        StringBuilder ret = new StringBuilder();
        boolean first = true;
        for (QExpr child : children())
        {
            if (!first)
            {
                ret.append(".");
            }
            first = false;
            String strChild = child.getValueString();
            if (strChild == null)
                return null;
            ret.append(strChild);
        }
        return ret.toString();
    }
}
