package org.labkey.core.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.Set;

public class PostgresUserSchema extends UserSchema
{
    public static final String POSTGRES_CONNECTIONS_TABLE_NAME = "pg_stat_activity";
    public static final String POSTGRES_LOCKS_TABLE_NAME = "pg_locks";

    public static final String NAME = "postgres";

    public PostgresUserSchema(User user, Container container)
    {
        super(NAME, "Postgres-specific internal views", user, container, CoreSchema.getInstance().getSchema());
    }

    @Override
    public @Nullable TableInfo createTable(String name, ContainerFilter cf)
    {
        // Issue 52190: Expose a data that supports postgreSQL-specific analysis
        if (POSTGRES_CONNECTIONS_TABLE_NAME.equalsIgnoreCase(name))
            return new PostgresConnectionsTable(this);
        if (POSTGRES_LOCKS_TABLE_NAME.equalsIgnoreCase(name))
            return new PostgresLocksTable(this);

        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        return Set.of(POSTGRES_CONNECTIONS_TABLE_NAME, POSTGRES_LOCKS_TABLE_NAME);
    }
}
