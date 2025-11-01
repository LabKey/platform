package org.labkey.core.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.ExprColumn;

/** Backed by pg_locks view */
public class PostgresTableSizesTable extends AbstractPostgresAdminOnlyTable
{
    public PostgresTableSizesTable(@NotNull PostgresUserSchema userSchema)
    {
        super(PostgresUserSchema.POSTGRES_TABLE_SIZES_TABLE_NAME, userSchema);

        setDescription("Shows info Postgres table sizes");

        addColumn(new ExprColumn(this, "table_schema", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".table_schema"), JdbcType.VARCHAR));
        addColumn(new ExprColumn(this, "table_name", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".table_name"), JdbcType.VARCHAR));
        addColumn(new ExprColumn(this, "table_size", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".table_size"), JdbcType.BIGINT)).setFormat("#,##0");
        addColumn(new ExprColumn(this, "index_size", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".index_size"), JdbcType.BIGINT)).setFormat("#,##0");
        addColumn(new ExprColumn(this, "total_size", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".total_size"), JdbcType.BIGINT)).setFormat("#,##0");
    }


    @Override
    public @NotNull SQLFragment getFromSQL()
    {
        SQLFragment result = new SQLFragment();
        result.append("""
                SELECT
                    table_schema,
                    table_name,
                    pg_table_size('"' || table_schema || '"."' || table_name || '"') AS table_size,
                    pg_indexes_size('"' || table_schema || '"."' || table_name || '"') AS index_size,
                    pg_total_relation_size('"' || table_schema || '"."' || table_name || '"') AS total_size
                FROM information_schema.tables""");
        return result;
    }
}
