package org.labkey.api.module;

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.dialect.SqlDialect;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public enum SupportedDatabase
{
    mssql, pgsql;

    public static SupportedDatabase get(SqlDialect dialect)
    {
        if (dialect.isPostgreSQL())
            return pgsql;

        if (dialect.isSqlServer())
            return mssql;

        throw new IllegalStateException("Dialect not supported: " + dialect.getProductName());
    }

    // databases parameter is a comma-separated list of databases: "pgsql, mssql", "mssql", "pgsql", etc.
    public static @NotNull Set<SupportedDatabase> parseSupportedDatabases(@NotNull String databases)
    {
        return Arrays.stream(databases.split(","))
            .map(StringUtils::trimToNull)
            .filter(Objects::nonNull)
            .map(db -> EnumUtils.getEnum(SupportedDatabase.class, db))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }
}
