package org.labkey.bigiron.mssql;

import org.labkey.api.data.dialect.StandardDialectStringHandler;

public class MicrosoftSqlServerStringHandler extends StandardDialectStringHandler
{
    @Override
    protected String stringValue(String value)
    {
        // Prefix string literals with N to force Unicode
        return "N" + quoteStringLiteral(value);
    }
}
