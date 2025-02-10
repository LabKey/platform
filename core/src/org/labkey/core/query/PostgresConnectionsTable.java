package org.labkey.core.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.ApplicationAdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

import java.util.Map;

public class PostgresConnectionsTable extends VirtualTable<CoreQuerySchema>
{
    public PostgresConnectionsTable(@Nullable CoreQuerySchema userSchema)
    {
        super(userSchema.getDbSchema(), CoreQuerySchema.POSTGRES_CONNECTIONS_TABLE_NAME, userSchema);

        if (!userSchema.getContainer().isRoot())
        {
            throw new IllegalArgumentException("Only available for root container");
        }

        ExprColumn pidColumn = new ExprColumn(this, "pid", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".pid"), JdbcType.INTEGER);
        pidColumn.setKeyField(true);
        addColumn(pidColumn);

        setDescription("Shows info about the active Postgres connections and their activity");

        addColumn(new ExprColumn(this, "usename", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".usename"), JdbcType.VARCHAR));
        addColumn(new ExprColumn(this, "blocked_by", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".blocked_by"), JdbcType.INTEGER));
        addColumn(new ExprColumn(this, "state", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".state"), JdbcType.VARCHAR));
        addColumn(new ExprColumn(this, "wait_event_type", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".wait_event_type"), JdbcType.VARCHAR));
        addColumn(new ExprColumn(this, "wait_event", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".wait_event"), JdbcType.VARCHAR));
        addColumn(new ExprColumn(this, "query_start", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".query_start"), JdbcType.TIMESTAMP));
        addColumn(new ExprColumn(this, "running_time", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".running_time"), JdbcType.OTHER));
        addColumn(new ExprColumn(this, "query", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".query"), JdbcType.VARCHAR));
    }

    @Override
    public @NotNull SQLFragment getFromSQL()
    {
        SQLFragment result = new SQLFragment();
        result.append("""
                SELECT
                  pid,
                  usename,
                  pg_blocking_pids(pid) AS blocked_by,
                  state,
                  wait_event_type,
                  wait_event,
                  query_start,
                  now() - query_start AS running_time,
                  query
                FROM pg_stat_activity""");
        return result;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (ReadPermission.class.equals(perm))
        {
            return CoreQuerySchema.canSeePostgresStateQueries(ContainerManager.getRoot(), user);
        }
        // Treat a delete as killing the connection forcibly
        return DeletePermission.class.equals(perm) && ContainerManager.getRoot().hasPermission(user, ApplicationAdminPermission.class);
    }

    @Override
    public @Nullable QueryUpdateService getUpdateService()
    {
        return new ConnectionUpdateService(this);
    }

    private class ConnectionUpdateService extends AbstractQueryUpdateService
    {
        protected ConnectionUpdateService(TableInfo queryTable)
        {
            super(queryTable);
        }

        @Override
        public boolean hasPermission(@NotNull UserPrincipal user, Class<? extends Permission> acl)
        {
            return PostgresConnectionsTable.this.hasPermission(user, acl);
        }

        @Override
        protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
        {
            return new TableSelector(PostgresConnectionsTable.this).getMap(getPid(keys));
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, @Nullable Map<Enum, Object> configParameters)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws InvalidKeyException
        {
            Integer pid = getPid(oldRow);
            if (pid == null)
            {
                throw new InvalidKeyException("No pid specified");
            }
            new SqlExecutor(getSchema()).execute(new SQLFragment("SELECT pg_terminate_backend(?)", pid));
            return oldRow;
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
        {
            throw new UnsupportedOperationException();
        }

        private Integer getPid(Map<String, Object> oldRow)
        {
            return oldRow.containsKey("pid") ? ((Number) oldRow.get("pid")).intValue() : null;
        }
    }
}
