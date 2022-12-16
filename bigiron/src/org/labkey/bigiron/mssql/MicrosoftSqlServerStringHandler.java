package org.labkey.bigiron.mssql;

import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.StandardDialectStringHandler;

public class MicrosoftSqlServerStringHandler extends StandardDialectStringHandler
{
    public MicrosoftSqlServerStringHandler(SqlDialect dialect)
    {
        super(dialect);
    }

    @Override
    public String quoteStringLiteral(String value)
    {
        // Prefix string literals with N to force Unicode
        return "N" + super.quoteStringLiteral(value);
    }
}
