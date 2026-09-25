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
package org.labkey.api.data.dialect;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SqlSelector;
import org.springframework.dao.DataAccessException;

import java.util.Set;

/**
 * Read-only capture of Postgres configuration and cumulative statistics as a single JSON document. Differencing a
 * capture taken before a workload against one taken after it shows the work the database actually did.
 */
public class PostgresSnapshot
{
    public enum StatementsStatus
    {
        AVAILABLE,
        NOT_INSTALLED,
        /** CREATE EXTENSION succeeds without shared_preload_libraries, but every read of the view then fails */
        NOT_LOADED
    }

    private record StatementsSource(@NotNull StatementsStatus status, @Nullable String view) {}

    private static final int PG_16 = 160000;
    private static final int PG_17 = 170000;

    // Counter columns are captured with to_jsonb() rather than named individually because their names move between
    // major versions (blk_read_time became shared_blk_read_time in PG 17), so one query serves every version and
    // pg_settings records which version produced the capture. Views that don't exist in a version fail at parse time,
    // so those subqueries are chosen in Java.
    private static final String SNAPSHOT_SQL = """
            SELECT json_build_object(
                'takenAt', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
                'serverVersionNum', current_setting('server_version_num')::int,
                'database', (
                    SELECT to_jsonb(d) FROM pg_stat_database d WHERE datname = current_database()
                ),
                'bgwriter', (
                    SELECT to_jsonb(b) FROM pg_stat_bgwriter b
                ),
                'checkpointer', (%s),
                'wal', (
                    SELECT to_jsonb(w) FROM pg_stat_wal w
                ),
                'io', (%s),
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
                'statementsStatus', '%s',
                'statements', (%s)
            )::text""";

    private static final String CHECKPOINTER_SQL = "SELECT to_jsonb(c) FROM pg_stat_checkpointer c";

    // Checkpoint counters live in pg_stat_bgwriter until PG 17
    private static final String CHECKPOINTER_FROM_BGWRITER_SQL = "SELECT to_jsonb(b) FROM pg_stat_bgwriter b";

    private static final String IO_SQL = """
            SELECT json_object_agg(backend_type || ':' || object || ':' || context, to_jsonb(i))
            FROM pg_stat_io i""";

    private static final String NULL_SQL = "SELECT NULL::json";

    // A row's identity is (userid, dbid, queryid, toplevel), so queryid alone collides across roles and nesting levels.
    // Without pg_read_all_stats or superuser this silently returns only the LabKey role's own statements.
    private static final String STATEMENTS_SQL = """
            SELECT COALESCE(json_object_agg(queryid::text || ':' || userid::text || ':' || toplevel::text, to_jsonb(s)), '{}'::json)
            FROM %s s
            WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
              AND queryid IS NOT NULL""";

    private static final String NO_STATEMENTS_SQL = "SELECT '{}'::json";

    public static @NotNull String capture()
    {
        DbSchema schema = CoreSchema.getInstance().getSchema();
        int version = new SqlSelector(schema, "SELECT current_setting('server_version_num')::int").getObject(Integer.class);
        StatementsSource statements = resolveStatements(schema);
        String statementsSql = StatementsStatus.AVAILABLE == statements.status() ? STATEMENTS_SQL.formatted(statements.view()) : NO_STATEMENTS_SQL;

        String sql = SNAPSHOT_SQL.formatted(
            version >= PG_17 ? CHECKPOINTER_SQL : CHECKPOINTER_FROM_BGWRITER_SQL,
            version >= PG_16 ? IO_SQL : NULL_SQL,
            statements.status().name(),
            statementsSql
        );

        return new SqlSelector(schema, sql).getObject(String.class);
    }

    /** Per-query statistics come from pg_stat_statements; snapshots are far less useful without it. */
    public static @NotNull StatementsStatus getStatementsStatus()
    {
        return resolveStatements(CoreSchema.getInstance().getSchema()).status();
    }

    /**
     * The extension's schema isn't necessarily on the search_path, and referencing a view that doesn't exist fails at
     * parse time, so the view name has to be assembled from what's actually there.
     */
    private static @NotNull StatementsSource resolveStatements(DbSchema schema)
    {
        String view = new SqlSelector(schema, """
                SELECT quote_ident(n.nspname) || '.pg_stat_statements'
                FROM pg_extension e INNER JOIN pg_namespace n ON n.oid = e.extnamespace
                WHERE e.extname = 'pg_stat_statements'""").getObject(String.class);

        if (null == view)
            return new StatementsSource(StatementsStatus.NOT_INSTALLED, null);

        try
        {
            new SqlSelector(schema, "SELECT 1 FROM " + view).exists();
            return new StatementsSource(StatementsStatus.AVAILABLE, view);
        }
        catch (DataAccessException e)
        {
            return new StatementsSource(StatementsStatus.NOT_LOADED, view);
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testCapture()
        {
            Assume.assumeTrue("Requires Postgres", CoreSchema.getInstance().getSqlDialect().isPostgreSQL());

            // JSONObject rejects duplicate keys, so parsing also verifies statement keys are unique
            JSONObject snapshot = new JSONObject(capture());

            for (String key : Set.of("takenAt", "serverVersionNum", "database", "bgwriter", "checkpointer", "wal", "io", "settings", "statementsStatus", "statements"))
                assertTrue("Missing key: " + key, snapshot.has(key));

            int version = snapshot.getInt("serverVersionNum");
            assertTrue(snapshot.getJSONObject("database").has("xact_commit"));
            assertTrue(snapshot.getJSONObject("settings").has("shared_buffers"));
            assertTrue(snapshot.getJSONObject("wal").has("wal_records"));
            assertTrue(snapshot.getJSONObject("checkpointer").has(version >= PG_17 ? "num_timed" : "checkpoints_timed"));
            assertEquals(version >= PG_16, !snapshot.isNull("io"));

            StatementsStatus status = StatementsStatus.valueOf(snapshot.getString("statementsStatus"));
            assertEquals(getStatementsStatus(), status);

            JSONObject statements = snapshot.getJSONObject("statements");
            if (StatementsStatus.AVAILABLE != status)
            {
                assertTrue(statements.isEmpty());
            }
            else
            {
                for (String key : statements.keySet())
                {
                    JSONObject row = statements.getJSONObject(key);
                    assertEquals(row.get("queryid") + ":" + row.get("userid") + ":" + row.get("toplevel"), key);
                }
            }
        }
    }
}
