package org.labkey.api.data.dialect;

import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;

/**
 * Implementations of limitRows() and related methods for databases that support standard syntax
 */
public class LimitRowsSqlGenerator
{
    public static SQLFragment limitRows(SQLFragment frag, int rowCount, long offset, boolean supportsOffsetWithoutLimit)
    {
        if (rowCount != Table.ALL_ROWS)
        {
            frag.append("\nLIMIT ");
            frag.append(Integer.toString(Table.NO_ROWS == rowCount ? 0 : rowCount));
        }
        else if (offset > 0 && !supportsOffsetWithoutLimit)
        {
            // This is Table.ALL_ROWS plus an offset on MySQL. MySQL OFFSET requires LIMIT (unlike PostgreSQL, where
            // LIMIT and OFFSET are independent clauses). MySQL documentation recommends specifying a very large LIMIT
            // to denote offset + all remaining rows.
            frag.append("\nLIMIT ?");
            frag.add(Integer.MAX_VALUE);
        }

        if (offset > 0)
        {
            frag.append("\nOFFSET ");
            frag.append(Long.toString(offset));
        }

        return frag;
    }

    public static SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int maxRows, long offset, boolean supportsOffsetWithoutLimit)
    {
        SQLFragment sql = appendFromFilterOrderAndGroupBy(select, from, filter, order, groupBy);

        return limitRows(sql, maxRows, offset, supportsOffsetWithoutLimit);
    }

    public static SQLFragment appendFromFilterOrderAndGroupBy(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy)
    {
        if (select == null)
            throw new IllegalArgumentException("select");
        if (from == null)
            throw new IllegalArgumentException("from");

        return appendFromFilterOrderAndGroupByNoValidation(select, from, filter, order, groupBy);
    }

    public static SQLFragment appendFromFilterOrderAndGroupByNoValidation(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy)
    {
        SQLFragment sql = new SQLFragment();
        sql.append(select);
        sql.append("\n").append(from);
        if (filter != null) sql.append("\n").append(filter);
        if (groupBy != null) sql.append("\n").append(groupBy);
        if (order != null) sql.append("\n").append(order);

        return sql;
    }
}
