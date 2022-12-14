package org.labkey.bigiron.mssql;

import org.labkey.api.data.dialect.StandardDialectStringHandler;

public class MicrosoftSqlServerStringHandler extends StandardDialectStringHandler
{
    @Override
    public String quoteStringLiteral(String value)
    {
        // Prefix string literals with N to force Unicode
        return "N" + super.quoteStringLiteral(value);
    }
}
