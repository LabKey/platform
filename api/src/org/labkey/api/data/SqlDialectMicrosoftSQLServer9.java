package org.labkey.api.data;

import org.labkey.api.util.PageFlowUtil;

/**
 * User: kevink
 * Date: Jan 28, 2008 2:56:27 PM
 */
public class SqlDialectMicrosoftSQLServer9 extends SqlDialectMicrosoftSQLServer
{
    private static SqlDialectMicrosoftSQLServer9 _instance = new SqlDialectMicrosoftSQLServer9();

    public static SqlDialectMicrosoftSQLServer getInstance()
    {
        return _instance;
    }
    
    public SqlDialectMicrosoftSQLServer9()
    {
        super();
        reservedWordSet.addAll(PageFlowUtil.set(
           "EXTERNAL", "PIVOT", "REVERT", "SECURITYAUDIT", "TABLESAMPLE", "UNPIVOT"
        ));
    }

    @Override
    public boolean supportOffset()
    {
        return true;
    }

    @Override
    protected SQLFragment _limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, int rowCount, long offset)
    {
        if (order == null || order.trim().length() == 0)
            throw new IllegalArgumentException("ERROR: ORDER BY clause required to limit");

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT * FROM (\n");
        sql.append(select);
        sql.append(",\nROW_NUMBER() OVER (\n");
        sql.append(order);
        sql.append(") AS _RowNum\n");
        sql.append(from);
        if (filter != null) sql.append("\n").append(filter);
        sql.append("\n) AS z\n");
        sql.append("WHERE _RowNum BETWEEN ");
        sql.append(offset + 1);
        sql.append(" AND ");
        sql.append(offset + rowCount);
        return sql;
    }
}
