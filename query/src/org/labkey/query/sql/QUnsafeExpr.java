package org.labkey.query.sql;

abstract public class QUnsafeExpr extends QExpr
{
    abstract protected void unsafeAppendSql(SqlBuilder builder);

    final public void appendSql(SqlBuilder builder)
    {
        if (!builder.allowUnsafeCode())
        {
            throw new UnsupportedOperationException();
        }
        unsafeAppendSql(builder);
    }

    abstract protected void unsafeDeclareFields(SqlBuilder builder);

    final public void declareFields()
    {
        throw new UnsupportedOperationException();
    }
}
