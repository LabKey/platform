package org.labkey.bigiron.mssql;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.TableResolver;

/**
 * Created by adam on 12/4/2015.
 */
public class MicrosoftSqlServer2008R2Dialect extends BaseMicrosoftSqlServerDialect
{
    public MicrosoftSqlServer2008R2Dialect(TableResolver tableResolver)
    {
        super(tableResolver);
    }

    // Called only if rowCount and offset are both > 0
    @Override
    protected SQLFragment _limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, @NotNull String order, String groupBy, int maxRows, long offset)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT * FROM (\n");
        sql.append(select);
        sql.append(",\nROW_NUMBER() OVER (\n");
        sql.append(order);
        sql.append(") AS _RowNum\n");
        sql.append(from);
        if (filter != null) sql.append("\n").append(filter);
        if (groupBy != null) sql.append("\n").append(groupBy);
        sql.append("\n) AS z\n");
        sql.append("WHERE _RowNum BETWEEN ");
        sql.append(offset + 1);
        sql.append(" AND ");
        sql.append(offset + maxRows);

        return sql;
    }
}
