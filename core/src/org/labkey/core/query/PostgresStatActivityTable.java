package org.labkey.core.query;

import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.TransactionFilter;
import org.labkey.api.data.dialect.BasePostgreSqlDialect;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.ApplicationAdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.HtmlWriter;
import org.labkey.core.admin.AdminController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Backed by pg_stat_activity view */
public class PostgresStatActivityTable extends AbstractPostgresAdminOnlyTable
{
    private static final Logger LOG = LogHelper.getLogger(PostgresStatActivityTable.class, "Access to Postgres connection status");

    public PostgresStatActivityTable(@NotNull PostgresUserSchema userSchema)
    {
        super(BasePostgreSqlDialect.POSTGRES_STAT_ACTIVITY_TABLE_NAME, userSchema);

        setDescription("Shows info about the active Postgres connections and their activity");

        // https://www.postgresql.org/docs/current/monitoring-stats.html#MONITORING-PG-STAT-ACTIVITY-VIEW
        addColumn(new BaseColumnInfo("datid", this, JdbcType.INTEGER));
        addColumn(new BaseColumnInfo("datname", this, JdbcType.VARCHAR));

        BaseColumnInfo pidColumn = new BaseColumnInfo("pid", this, JdbcType.INTEGER);
        pidColumn.setKeyField(true);
        addColumn(pidColumn);

        addColumn(new BaseColumnInfo("leader_pid", this, JdbcType.INTEGER));
        addColumn(new BaseColumnInfo("usesysid", this, JdbcType.INTEGER));
        addColumn(new BaseColumnInfo("usename", this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo("application_name", this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo("client_addr", this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo("client_hostname", this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo("client_port", this, JdbcType.INTEGER));
        addColumn(new BaseColumnInfo("backend_start", this, JdbcType.TIMESTAMP));
        addColumn(new BaseColumnInfo("xact_start", this, JdbcType.TIMESTAMP));
        addColumn(new BaseColumnInfo("query_start", this, JdbcType.TIMESTAMP)).
                setFormat(DateUtil.ISO_DATE_TIME_FORMAT_STRING);
        addColumn(new BaseColumnInfo("state_change", this, JdbcType.TIMESTAMP)).
                setFormat(DateUtil.ISO_DATE_TIME_FORMAT_STRING);
        addColumn(new BaseColumnInfo("wait_event_type", this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo("wait_event", this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo("state", this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo("backend_xid", this, JdbcType.INTEGER));
        addColumn(new BaseColumnInfo("backend_xmin", this, JdbcType.INTEGER));
        addColumn(new BaseColumnInfo("query", this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo("backend_type", this,  JdbcType.VARCHAR));

        // Our calculated values
        var threadCol = addColumn(new ExprColumn(this, "threadsAndRequests", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".pid"), JdbcType.INTEGER));
        threadCol.setDisplayColumnFactory(ThreadDisplayColumn::new);
        addColumn(new BaseColumnInfo("running_time_ms", this, JdbcType.INTEGER));
        addColumn(new BaseColumnInfo("blocked_by", this, JdbcType.VARCHAR));

        setDefaultVisibleColumns(Arrays.asList(
                pidColumn.getFieldKey(),
                FieldKey.fromParts("blocked_by"),
                FieldKey.fromParts("datname"),
                FieldKey.fromParts("usename"),
                FieldKey.fromParts("application_name"),
                FieldKey.fromParts("state"),
                FieldKey.fromParts("wait_event"),
                FieldKey.fromParts("query_start"),
                FieldKey.fromParts("state_change"),
                FieldKey.fromParts("running_time_ms"),
                FieldKey.fromParts("query"),
                threadCol.getFieldKey()
        ));
    }

    @Override
    public @NotNull SQLFragment getFromSQL()
    {
        SQLFragment result = new SQLFragment();
        result.append("""
                SELECT
                  *,
                  CAST(pg_blocking_pids(pid) AS VARCHAR) AS blocked_by,
                  CASE WHEN (state = 'idle' OR state IS NULL) THEN NULL ELSE GREATEST(EXTRACT(MILLISECONDS FROM AGE(NOW(), query_start)), 0) END AS running_time_ms
                FROM pg_stat_activity""");
        return result;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (DeletePermission.class.equals(perm) && getUserSchema().getContainer().hasPermission(user, ApplicationAdminPermission.class))
        {
            return true;
        }
        return super.hasPermission(user, perm);
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
        public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> acl)
        {
            return PostgresStatActivityTable.this.hasPermission(user, acl);
        }

        @Override
        protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
        {
            // If there's no PID (or a bogus PID), getMap() will return null
            return new TableSelector(PostgresStatActivityTable.this).getMap(getPid(keys));
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, @Nullable Map<Enum, Object> configParameters)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws InvalidKeyException, ValidationException
        {
            // Be extra paranoid to avoid unauthorized killing of queries
            if (!hasPermission(user, DeletePermission.class))
            {
                throw new ValidationException("User is not allowed to delete terminate connections");
            }

            Integer pid = getPid(oldRow);
            if (pid == null)
            {
                throw new InvalidKeyException("No pid specified");
            }
            LOG.info("{} is killing Postgres PID {}", user.getEmail(), pid);
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

    private static class ThreadDisplayColumn extends DataColumn
    {
        private HashSetValuedHashMap<Thread, Integer> _pidsForThreads;
        public ThreadDisplayColumn(ColumnInfo colInfo)
        {
            super(colInfo);
            setTextAlign("left");
        }

        private HashSetValuedHashMap<Thread, Integer> getPidsForThreads()
        {
            if (_pidsForThreads == null)
            {
                _pidsForThreads = ConnectionWrapper.getSPIDsForThreads();
            }
            return _pidsForThreads;
        }

        @Override
        public boolean isSortable()
        {
            return false;
        }

        @Override
        public boolean isFilterable()
        {
            return false;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
        {
            Integer pid = ctx.get(getBoundColumn().getFieldKey(), Integer.class);
            List<Thread> threads = new ArrayList<>();
            if (pid != null)
            {
                for (Map.Entry<Thread, Integer> entry : getPidsForThreads().entries())
                {
                    if (entry.getValue() == pid.intValue())
                    {
                        threads.add(entry.getKey());
                    }
                }
            }
            HtmlString separator = HtmlString.EMPTY_STRING;
            for (Thread thread : threads)
            {
                out.write(separator);
                ActionURL url = new ActionURL(AdminController.ShowThreadsAction.class, ContainerManager.getRoot());
                url.setFragment(thread.getName());
                out.write(LinkBuilder.labkeyLink(thread.getName(), url).target("_blank"));
                separator = HtmlString.BR;

                // Check for HTTP threads and their async counterparts to tie queries to the request that spawned them
                var request = TransactionFilter.getRequestSummary(thread);
                if (request == null)
                {
                    request = TransactionFilter.getRequestSummary(DbScope.getEffectiveThread(thread));
                }
                if (request != null)
                {
                    out.write(separator);
                    out.write(request.toLogString());
                }
            }
        }
    }
}
