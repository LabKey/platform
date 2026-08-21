/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.core.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.BasePostgreSqlDialect;

/** Backed by pg_locks view */
public class PostgresTableSizesTable extends AbstractPostgresAdminOnlyTable
{
    public PostgresTableSizesTable(@NotNull PostgresUserSchema userSchema)
    {
        super(BasePostgreSqlDialect.POSTGRES_TABLE_SIZES_TABLE_NAME, userSchema);

        setDescription("Shows info Postgres table sizes");

        addColumn(new BaseColumnInfo("table_schema", this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo("table_name", this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo("table_size", this, JdbcType.BIGINT)).setFormat("#,##0");
        addColumn(new BaseColumnInfo("index_size", this, JdbcType.BIGINT)).setFormat("#,##0");
        addColumn(new BaseColumnInfo("total_size", this, JdbcType.BIGINT)).setFormat("#,##0");
    }

    @Override
    public @NotNull SQLFragment getFromSQL()
    {
        SQLFragment result = new SQLFragment();
        result.append("""
                SELECT
                    table_schema,
                    table_name,
                    pg_table_size(quote_ident(table_schema) || '.' || quote_ident(table_name)) AS table_size,
                    pg_indexes_size(quote_ident(table_schema) || '.' || quote_ident(table_name)) AS index_size,
                    pg_total_relation_size(quote_ident(table_schema) || '.' || quote_ident(table_name)) AS total_size
                FROM information_schema.tables
                WHERE table_schema NOT IN ('public', 'information_schema') AND table_schema NOT LIKE 'pg_%'""");
        return result;
    }
}
