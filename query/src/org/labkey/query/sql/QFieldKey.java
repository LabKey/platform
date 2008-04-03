package org.labkey.query.sql;

import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.data.ColumnInfo;

abstract public class QFieldKey extends QExpr
{
    static public QFieldKey of(FieldKey key)
    {
        if (key == null)
            return null;
        QFieldKey parent = of(key.getTable());
        if (parent == null)
            return new QIdentifier(key.getName());
        if (key.isAllColumns())
        {
            return new QDot(parent, new QRowStar());
        }
        return new QDot(parent, new QIdentifier(key.getName()));
    }

    public void declareFields()
    {
        throw new IllegalStateException("Fields should have been resolved.");
    }

    abstract public FieldKey getFieldKey();

    public void appendSql(SqlBuilder builder)
    {
        throw new IllegalStateException("Fields should have been resolved.");
    }

    public ColumnInfo getColumnInfo(FieldKey key, FilteredTable rootTable)
    {
        throw new IllegalStateException("Fields should have been resolved.");
    }
}
