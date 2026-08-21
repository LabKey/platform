/*
 * Copyright (c) 2024-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
