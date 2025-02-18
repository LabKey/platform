package org.labkey.core.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.PostgreSql91Dialect;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.TroubleshooterPermission;

import java.util.Set;

/** Issue 52190: Expose troubleshooting data that supports postgreSQL-specific analysis */
 public class PostgresUserSchema extends UserSchema
{
    public static final String POSTGRES_STAT_ACTIVITY_TABLE_NAME = "pg_stat_activity";
    public static final String POSTGRES_LOCKS_TABLE_NAME = "pg_locks";

    public PostgresUserSchema(User user, Container container)
    {
        super(PostgreSql91Dialect.POSTGRES_SCHEMA_NAME, "Postgres-specific internal views for database troubleshooting", user, container, CoreSchema.getInstance().getSchema());
    }

    @Override
    public boolean canReadSchema()
    {
        return super.canReadSchema() || getUser().hasRootPermission(TroubleshooterPermission.class);
    }

    @Override
    public @Nullable TableInfo createTable(String name, ContainerFilter cf)
    {
        if (POSTGRES_STAT_ACTIVITY_TABLE_NAME.equalsIgnoreCase(name))
            return new PostgresStatActivityTable(this);
        if (POSTGRES_LOCKS_TABLE_NAME.equalsIgnoreCase(name))
            return new PostgresLocksTable(this);

        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        return Set.of(POSTGRES_STAT_ACTIVITY_TABLE_NAME, POSTGRES_LOCKS_TABLE_NAME);
    }
}
