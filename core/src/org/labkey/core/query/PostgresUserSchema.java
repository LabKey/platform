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
