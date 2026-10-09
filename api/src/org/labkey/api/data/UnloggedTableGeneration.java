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
package org.labkey.api.data;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.Throttle;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.logging.LogHelper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * GH Issue 1698: Detects PostgreSQL emptying UNLOGGED tables, which it does on standby promotion (failover, switchover)
 * and crash recovery. A one-row UNLOGGED marker table is reset along with them, so a missing row bumps the generation.
 */
public class UnloggedTableGeneration
{
    private static final Logger LOG = LogHelper.getLogger(UnloggedTableGeneration.class, "Detects resets of UNLOGGED materialized tables");
    private static final long CHECK_INTERVAL = TimeUnit.SECONDS.toMillis(5);
    // Held for the life of the server so TempTableTracker drops the marker only at shutdown
    private static final Object MARKER_REF = new Object();
    // A failed check is retried on the next read, so an outage would otherwise warn on every read
    private static final Throttle<String> CHECK_FAILURE_THROTTLE = new Throttle<>("UNLOGGED reset check failures", 100, CacheManager.HOUR,
            failure -> LOG.warn("Failed to check for a reset of UNLOGGED tables ({}). Further failures of this kind log at DEBUG for an hour.", failure));

    private static final AtomicLong _generation = new AtomicLong();
    private static final Lock _checkLock = new ReentrantLock();
    private static volatile long _lastCheck = 0;
    private static volatile @Nullable String _markerName = null;

    private UnloggedTableGeneration()
    {
    }

    /**
     * The current generation, checking the marker first if the last check is stale. Cheap enough to call on every read.
     * Checks run only from these calls, never on a timer, so a server that never reads an UNLOGGED table never queries the marker.
     */
    public static String current()
    {
        if (HeartBeat.currentTimeMillis() - _lastCheck > CHECK_INTERVAL && _checkLock.tryLock())
        {
            try
            {
                if (HeartBeat.currentTimeMillis() - _lastCheck > CHECK_INTERVAL)
                    check();
            }
            finally
            {
                _checkLock.unlock();
            }
        }
        return String.valueOf(_generation.get());
    }

    static void checkForTest()
    {
        _checkLock.lock();
        try
        {
            check();
        }
        finally
        {
            _checkLock.unlock();
        }
    }

    /** Empties the marker as a reset would and checks it immediately. Callers must empty their own UNLOGGED tables. */
    public static void simulateResetForTest()
    {
        current();
        _checkLock.lock();
        try
        {
            new SqlExecutor(DbScope.getLabKeyScope()).execute(new SQLFragment("TRUNCATE ").append(markerTable(_markerName)));
            check();
        }
        finally
        {
            _checkLock.unlock();
        }
    }

    private static void check()
    {
        DbScope scope = DbScope.getLabKeyScope();
        // Own connection, so a caller's rollback can't undo the marker insert
        try (Connection conn = scope.getPooledConnection())
        {
            String markerName = _markerName;
            if (null == markerName)
                createMarker(scope, conn);
            else
                verifyMarker(scope, conn, markerName);
            _lastCheck = HeartBeat.currentTimeMillis();
        }
        catch (SQLException | RuntimeException x)
        {
            // _lastCheck stays put, so the first read after an outage (e.g. a failover) checks again right away
            CHECK_FAILURE_THROTTLE.execute(failureKind(x));
            LOG.debug("Failed to check for a reset of UNLOGGED tables", x);
        }
    }

    /** Root cause class plus SQLState. Messages can embed hosts or timings, which would defeat the throttle. */
    private static String failureKind(Exception x)
    {
        Throwable t = x instanceof RuntimeSQLException rsx ? rsx.getSQLException() : x;
        SQLException sqlx = ExceptionUtils.throwableOfType(t, SQLException.class);
        Throwable root = Objects.requireNonNullElse(ExceptionUtils.getRootCause(t), t);
        return root.getClass().getSimpleName() + (null == sqlx ? "" : " " + sqlx.getSQLState());
    }

    private static void verifyMarker(DbScope scope, Connection conn, String markerName)
    {
        try
        {
            SQLFragment exists = new SQLFragment("SELECT EXISTS (SELECT 1 FROM ").append(markerTable(markerName)).append(")");
            if (Boolean.TRUE.equals(new SqlSelector(scope, conn, exists).getObject(Boolean.class)))
                return;

            _generation.incrementAndGet();
            LOG.info("UNLOGGED tables were reset by the database, likely a failover or crash recovery. Materialized views will rebuild.");
            new SqlExecutor(scope, conn).execute(new SQLFragment("INSERT INTO ").append(markerTable(markerName)).append(" (x) VALUES (1)"));
        }
        catch (RuntimeException x)
        {
            if (!SqlDialect.isObjectNotFoundException(x))
                throw x;
            _generation.incrementAndGet();
            LOG.info("UNLOGGED table marker {} is missing. Materialized views will rebuild.", markerName);
            createMarker(scope, conn);
        }
    }

    private static void createMarker(DbScope scope, Connection conn)
    {
        // A fresh name never collides with tables TempTableTracker purges asynchronously at startup
        String name = "unlogged_marker_" + GUID.makeHash();
        TempTableTracker.track(name, MARKER_REF);
        new SqlExecutor(scope, conn).execute(new SQLFragment("CREATE UNLOGGED TABLE ").append(markerTable(name)).append(" (x INT)"));
        new SqlExecutor(scope, conn).execute(new SQLFragment("INSERT INTO ").append(markerTable(name)).append(" (x) VALUES (1)"));
        _markerName = name;
    }

    private static SQLFragment markerTable(String name)
    {
        return new SQLFragment().appendIdentifier(DbSchema.getTemp().getName()).append(".").appendIdentifier(name);
    }
}
