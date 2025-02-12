package org.labkey.core.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryForeignKey;

public class PostgresLocksTable extends AbstractPostgresAdminOnlyTable
{
    public PostgresLocksTable(@NotNull PostgresUserSchema userSchema)
    {
        super(PostgresUserSchema.POSTGRES_LOCKS_TABLE_NAME, userSchema);

        setDescription("Shows info about the currently held Postgres locks");

        // https://www.postgresql.org/docs/current/view-pg-locks.html
        addColumn(new ExprColumn(this, "locktype", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".locktype"), JdbcType.VARCHAR));
        addColumn(new ExprColumn(this, "database", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".database"), JdbcType.INTEGER));
        addColumn(new ExprColumn(this, "relation", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".relation"), JdbcType.INTEGER));
        addColumn(new ExprColumn(this, "page", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".page"), JdbcType.INTEGER));
        addColumn(new ExprColumn(this, "tuple", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".tuple"), JdbcType.INTEGER));
        addColumn(new ExprColumn(this, "virtualxid", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".virtualxid"), JdbcType.VARCHAR));
        addColumn(new ExprColumn(this, "transactionid", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".transactionid"), JdbcType.INTEGER));
        addColumn(new ExprColumn(this, "classid", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".classid"), JdbcType.INTEGER));
        addColumn(new ExprColumn(this, "objid", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".objid"), JdbcType.INTEGER));
        addColumn(new ExprColumn(this, "objsubid", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".objsubid"), JdbcType.INTEGER));
        addColumn(new ExprColumn(this, "virtualtransaction", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".virtualtransaction"), JdbcType.VARCHAR));
        addColumn(new ExprColumn(this, "pid", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".pid"), JdbcType.INTEGER)).
                setFk(new QueryForeignKey.Builder(userSchema, null).table(PostgresUserSchema.POSTGRES_CONNECTIONS_TABLE_NAME).raw(true));
        addColumn(new ExprColumn(this, "mode", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".mode"), JdbcType.VARCHAR));
        addColumn(new ExprColumn(this, "granted", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".granted"), JdbcType.BOOLEAN));
        addColumn(new ExprColumn(this, "fastpath", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".fastpath"), JdbcType.BOOLEAN));
    }


    @Override
    public @NotNull SQLFragment getFromSQL()
    {
        SQLFragment result = new SQLFragment();
        result.append("SELECT * FROM pg_locks");
        return result;
    }
}
