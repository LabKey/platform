/*
 * Copyright (c) 2026 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SqlSelector;

/**
 * Read-only capture of Postgres configuration and cumulative statistics as a single JSON document. Differencing a
 * capture taken before a workload against one taken after it shows the work the database actually did.
 */
public class PostgresSnapshot
{
    // Counter columns are captured with to_jsonb() rather than named individually because their names move between
    // major versions (blk_read_time became shared_blk_read_time in PG 17), so one query serves every version and
    // pg_settings records which version produced the capture.
    private static final String SNAPSHOT_SQL = """
            SELECT json_build_object(
                'takenAt', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
                'serverVersionNum', current_setting('server_version_num')::int,
                'database', (
                    SELECT to_jsonb(d) FROM pg_stat_database d WHERE datname = current_database()
                ),
                'checkpointer', (
                    SELECT to_jsonb(b) FROM pg_stat_bgwriter b
                ),
                'settings', (
                    SELECT json_object_agg(name, json_build_object(
                        'value', setting,
                        'unit', unit,
                        'source', source,
                        'pending_restart', pending_restart,
                        'boot_val', boot_val,
                        'reset_val', reset_val
                    ))
                    FROM pg_settings
                ),
                'statements', (%s)
            )::text""";

    // Without pg_read_all_stats or superuser this silently returns only the LabKey role's own statements
    private static final String STATEMENTS_SQL = """
            SELECT COALESCE(json_object_agg(queryid::text, to_jsonb(s) - 'queryid'), '{}'::json)
            FROM %s s
            WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
              AND queryid IS NOT NULL""";

    private static final String NO_STATEMENTS_SQL = "SELECT '{}'::json";

    public static @NotNull String capture()
    {
        DbSchema schema = CoreSchema.getInstance().getSchema();
        String view = resolveStatementsView(schema);
        String statementsSql = null != view ? STATEMENTS_SQL.formatted(view) : NO_STATEMENTS_SQL;

        return new SqlSelector(schema, SNAPSHOT_SQL.formatted(statementsSql)).getObject(String.class);
    }

    /**
     * Qualified, quoted name of the pg_stat_statements view, or null if the extension isn't installed. The extension's
     * schema isn't necessarily on the search_path, and referencing a view that doesn't exist fails at parse time, so
     * the subquery has to be assembled from what's actually there.
     */
    private static @Nullable String resolveStatementsView(DbSchema schema)
    {
        return new SqlSelector(schema, """
                SELECT quote_ident(n.nspname) || '.pg_stat_statements'
                FROM pg_extension e INNER JOIN pg_namespace n ON n.oid = e.extnamespace
                WHERE e.extname = 'pg_stat_statements'""").getObject(String.class);
    }
}
