package org.labkey.api.data;

/**
 * A method on a table.
 * Most tables do not have methods.  Methods are often used to expose lookup columns, where the lookup column
 * name is not known at query design time.
 * It's unfortunate that both the method "getSQL" and "createColumnInfo" need to exist.
 *
 */
public interface MethodInfo
{
    /**
     * Return a {@link ColumnInfo} whose {@link ColumnInfo#getValueSql } will be the result of evaluating the method.
     */
    ColumnInfo createColumnInfo(TableInfo parentTable, ColumnInfo[] arguments, String alias);

    SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments);
}
