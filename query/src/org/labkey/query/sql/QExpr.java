package org.labkey.query.sql;

import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;

import java.sql.Types;

import org.labkey.query.data.SQLTableInfo;

abstract public class QExpr extends QNode<QExpr>
{
    public QExpr()
    {
        super();
    }

    public FieldKey getFieldKey()
    {
        return null;
    }

    public SQLFragment getSqlFragment(DbSchema schema)
    {
        SqlBuilder ret = new SqlBuilder(schema);
        appendSql(ret);
        return ret;
    }

    abstract public void appendSql(SqlBuilder builder);

    public String getValueString()
    {
        return null;
    }

    public QExpr clonePreserveChildren()
    {
        QExpr ret = (QExpr) super.clone();
        ret.setFirstChild(getFirstChild());
        return ret;
    }

    public int getSqlType()
    {
        return Types.OTHER;
    }

    public boolean isAggregate()
    {
        // avoid ClassCastException org.labkey.query.sql.QSelectFrom cannot be cast to org.labkey.query.sql.QExpr  
        if (this instanceof QQuery)
            return false;
        for (QExpr child : children())
        {
            if (child.isAggregate())
                return true;
        }
        return false;
    }

    public ColumnInfo createColumnInfo(SQLTableInfo table, String alias)
    {
        DbSchema schema = table.getSchema();
        SQLFragment sql = getSqlFragment(schema);
        return new ExprColumn(table, alias, sql, getSqlType());
    }

    public QueryParseException fieldCheck(QNode parent)
    {
        return null;
    }

    public QueryParseException syntaxCheck(QNode parent)
    {
        return null;
    }
}
