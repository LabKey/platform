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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.BasePostgreSqlDialect;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.Set;

/** Issue 52190: Expose troubleshooting data that supports postgreSQL-specific analysis */
 public class PostgresUserSchema extends UserSchema
{
    public PostgresUserSchema(User user, Container container)
    {
        super(BasePostgreSqlDialect.POSTGRES_SCHEMA_NAME, "Postgres-specific internal views for database troubleshooting", user, container, CoreSchema.getInstance().getSchema());
    }

    @Override
    public boolean canReadSchema()
    {
        return super.canReadSchema() || getUser().isTroubleshooter();
    }

    @Override
    public @Nullable TableInfo createTable(String name, ContainerFilter cf)
    {
        if (BasePostgreSqlDialect.POSTGRES_STAT_ACTIVITY_TABLE_NAME.equalsIgnoreCase(name))
            return new PostgresStatActivityTable(this);
        if (BasePostgreSqlDialect.POSTGRES_LOCKS_TABLE_NAME.equalsIgnoreCase(name))
            return new PostgresLocksTable(this);
        if (BasePostgreSqlDialect.POSTGRES_TABLE_SIZES_TABLE_NAME.equalsIgnoreCase(name))
            return new PostgresTableSizesTable(this);

        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        return Set.of(
                BasePostgreSqlDialect.POSTGRES_LOCKS_TABLE_NAME,
                BasePostgreSqlDialect.POSTGRES_STAT_ACTIVITY_TABLE_NAME,
                BasePostgreSqlDialect.POSTGRES_TABLE_SIZES_TABLE_NAME);
    }
}
