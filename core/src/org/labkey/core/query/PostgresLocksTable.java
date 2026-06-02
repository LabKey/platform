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
import org.labkey.api.query.QueryForeignKey;

/** Backed by pg_locks view */
public class PostgresLocksTable extends AbstractPostgresAdminOnlyTable
{
    public PostgresLocksTable(@NotNull PostgresUserSchema userSchema)
    {
        super(BasePostgreSqlDialect.POSTGRES_LOCKS_TABLE_NAME, userSchema);

        setDescription("Shows info about the currently held Postgres locks");

        // https://www.postgresql.org/docs/current/view-pg-locks.html
        addColumn(new BaseColumnInfo("locktype", this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo("database", this, JdbcType.INTEGER));
        addColumn(new BaseColumnInfo("relation", this, JdbcType.INTEGER));
        addColumn(new BaseColumnInfo("page", this, JdbcType.INTEGER));
        addColumn(new BaseColumnInfo("tuple", this, JdbcType.INTEGER));
        addColumn(new BaseColumnInfo("virtualxid", this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo("transactionid", this, JdbcType.INTEGER));
        addColumn(new BaseColumnInfo("classid", this, JdbcType.INTEGER));
        addColumn(new BaseColumnInfo("objid", this, JdbcType.INTEGER));
        addColumn(new BaseColumnInfo("objsubid", this, JdbcType.INTEGER));
        addColumn(new BaseColumnInfo("virtualtransaction", this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo("pid", this, JdbcType.INTEGER)).
                setFk(new QueryForeignKey.Builder(userSchema, null).table(BasePostgreSqlDialect.POSTGRES_STAT_ACTIVITY_TABLE_NAME).raw(true));
        addColumn(new BaseColumnInfo("mode", this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo("granted", this, JdbcType.BOOLEAN));
        addColumn(new BaseColumnInfo("fastpath", this, JdbcType.BOOLEAN));
    }


    @Override
    public @NotNull SQLFragment getFromSQL()
    {
        SQLFragment result = new SQLFragment();
        result.append("SELECT * FROM pg_locks");
        return result;
    }
}
