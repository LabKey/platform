package org.labkey.query.sql;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;

import java.sql.Types;
import java.util.List;

import org.labkey.query.data.SQLTableInfo;

public class QAggregate extends QExpr
{
    public void appendSql(SqlBuilder builder)
    {
        String function = getTokenText();
        if (function.equalsIgnoreCase("stddev"))
        {
            function = builder.getDialect().getStdDevFunction();
        }
        builder.append(" " + function + "(");
        for (QExpr child : children())
        {
            child.appendSql(builder);
        }
        builder.append(")");
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append(" " + getTokenText() + "(");
        for (QExpr child : children())
        {
            child.appendSource(builder);
        }
        builder.append(")");
    }

    public int getSqlType()
    {
        if ("count".equalsIgnoreCase(getTokenText()))
        {
            return Types.INTEGER;
        }
        for (QExpr child : children())
        {
            return child.getSqlType();
        }
        return Types.OTHER;
    }

    public boolean isAggregate()
    {
        return true;
    }

    public ColumnInfo createColumnInfo(SQLTableInfo table, String alias)
    {
        ColumnInfo ret = super.createColumnInfo(table, alias);
        if ("max".equalsIgnoreCase(getTokenText()) || "min".equalsIgnoreCase(getTokenText()))
        {
            List<QExpr> children = childList();
            if (children.size() == 1 && children.get(0) instanceof QField)
            {
                QField field = (QField) children.get(0);
                ColumnInfo columnInfo = field.getColumnInfo();
                ret.copyAttributesFrom(columnInfo);
                ret.setCaption(null);
            }
        }
        return ret;
    }
}
