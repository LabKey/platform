package org.labkey.api.sql;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;


/**
 * Here are some SQL helpers for use when writing LABKEY sql.
 * When writing SQL for postgres or sql server, please use the appropriate SqlDialect.
 */
public class LabKeySql
{
    static public @NotNull String unquoteString(@NotNull String str)
    {
        if (str.length() < 2 || !str.startsWith("'") || !str.endsWith("'"))
            throw new IllegalArgumentException("Expected identifier to be surrounded by single quotes");
        str = str.substring(1, str.length() - 1);
        str = StringUtils.replace(str, "''", "'");
        return str;
    }

    static public @NotNull String quoteString(@NotNull String str)
    {
        return "'" + StringUtils.replace(str, "'", "''") + "'";
    }

    static public @NotNull String unquoteIdentifier(@NotNull String str)
    {
        if (str.length() < 2 || !str.startsWith("\"") || !str.endsWith("\""))
            throw new IllegalArgumentException("Expected identifier to be surrounded by double quotes");
        str = str.substring(1, str.length() - 1);
        str = StringUtils.replace(str, "\"\"", "\"");
        return str;
    }

    static public @NotNull String quoteIdentifier(@NotNull String str)
    {
        return "\"" + StringUtils.replace(str, "\"", "\"\"") + "\"";
    }
}