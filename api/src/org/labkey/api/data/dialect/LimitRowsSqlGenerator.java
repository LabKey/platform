package org.labkey.api.data.dialect;

import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;

/**
 * Implementations of limitRows() and related methods for databases that support standard syntax
 */
public class LimitRowsSqlGenerator
{
    public static SQLFragment limitRows(SQLFragment frag, int rowCount, long offset, LimitRowsCustomizer customizer)
    {
        if (customizer.requiresOffsetBeforeLimit())
            handleOffset(frag, offset, customizer);

        if (rowCount != Table.ALL_ROWS)
        {
            frag.append("\n");
            customizer.appendLimit(frag, Table.NO_ROWS == rowCount ? 0 : rowCount);
        }
        else if (offset > 0 && !customizer.supportsOffsetWithoutLimit())
        {
            // This is Table.ALL_ROWS plus an offset on MySQL. MySQL OFFSET requires LIMIT (unlike PostgreSQL, where
            // LIMIT and OFFSET are independent clauses). MySQL documentation recommends specifying a very large LIMIT
            // to denote offset + all remaining rows.
            frag.append("\n");
            customizer.appendLimit(frag, Integer.MAX_VALUE);
        }

        if (!customizer.requiresOffsetBeforeLimit())
            handleOffset(frag, offset, customizer);

        return frag;
    }

    private static void handleOffset(SQLFragment frag, long offset, LimitRowsCustomizer customizer)
    {
        if (offset > 0)
        {
            frag.append("\n");
            customizer.appendOffset(frag, offset);
        }
    }

    public static SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int maxRows, long offset, LimitRowsCustomizer customizer)
    {
        SQLFragment sql = appendFromFilterOrderAndGroupBy(select, from, filter, order, groupBy);

        return limitRows(sql, maxRows, offset, customizer);
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

    public interface LimitRowsCustomizer
    {
        void appendLimit(SQLFragment frag, int limit);
        void appendOffset(SQLFragment frag, long offset);
        boolean supportsOffsetWithoutLimit();
        boolean requiresOffsetBeforeLimit();
    }

    // Standard customizer used by PostgreSQL, MySQL, and Redshift
    public static class StandardLimitRowsCustomizer implements LimitRowsCustomizer
    {
        private final boolean _supportsOffsetWithoutLimit;

        public StandardLimitRowsCustomizer(boolean supportsOffsetWithoutLimit)
        {
            _supportsOffsetWithoutLimit = supportsOffsetWithoutLimit;
        }

        @Override
        public void appendLimit(SQLFragment frag, int limit)
        {
            frag.append("LIMIT ").appendValue(limit);
        }

        @Override
        public void appendOffset(SQLFragment frag, long offset)
        {
            frag.append("OFFSET ").appendValue(offset);
        }

        @Override
        public boolean supportsOffsetWithoutLimit()
        {
            return _supportsOffsetWithoutLimit;
        }

        @Override
        public boolean requiresOffsetBeforeLimit()
        {
            return false; // MySQL requires LIMIT before OFFSET; PostgreSQL doesn't care
        }
    }
}
