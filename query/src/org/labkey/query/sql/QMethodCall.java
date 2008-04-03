package org.labkey.query.sql;

import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.data.*;

import java.util.List;
import java.util.ArrayList;
import java.util.ListIterator;

import org.labkey.query.data.SQLTableInfo;

public class QMethodCall extends QExpr
{
    public QMethodCall()
    {

    }

    public void appendSql(SqlBuilder builder)
    {
        MethodInfo method = getMethod();
        if (method == null)
        {
            builder.appendStringLiteral("Unrecognized method " + getField().getFieldKey());
            return;
        }
        List<SQLFragment> arguments = new ArrayList();
        for (QExpr expr : getLastChild().children())
        {
            arguments.add(expr.getSqlFragment(builder.getDbSchema()));
        }
        builder.append(method.getSQL(builder.getDbSchema(), arguments.toArray(new SQLFragment[0])));
    }

    public ColumnInfo createColumnInfo(SQLTableInfo table, String alias)
    {
        MethodInfo method = getMethod();
        if (method == null)
        {
            return super.createColumnInfo(table, alias);
        }
        List<ColumnInfo> arguments = new ArrayList();

        for (ListIterator<QExpr> it = getLastChild().childList().listIterator(); it.hasNext();)
        {
            QExpr expr = it.next();
            arguments.add(expr.createColumnInfo(table, "arg" + it.previousIndex()));
        }
        return method.createColumnInfo(table, arguments.toArray(new ColumnInfo[0]), alias);
    }

    public MethodInfo getMethod()
    {
        return getField().getMethod();
    }

    public void appendSource(SourceBuilder builder)
    {
        getFirstChild().appendSource(builder);
        builder.append("(");
        builder.pushPrefix("");

        for (QExpr child : getLastChild().children())
        {
            child.appendSource(builder);
            builder.nextPrefix(",");
        }
        builder.popPrefix();
        builder.append(")");
    }

    public QField getField()
    {
        if (!(getFirstChild() instanceof QField))
        {
            throw new QueryException("Fields should have been resolved");
        }
        return (QField) getFirstChild();
    }

    public QueryParseException fieldCheck(QNode parent)
    {
        if (getMethod() == null)
        {
            return new QueryParseException("Unknown method " + getField().getName(), null, getLine(), getColumn());
        }
        return null;
    }
}
