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
package org.labkey.api.data.queryprofiler;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.QueryLogging;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Counts the SQL statements the creating thread runs until closed, for tests that assert a code path's query count.
 */
public class QueryCounter implements DatabaseQueryListener, AutoCloseable
{
    private final Thread _thread = Thread.currentThread();
    private final @Nullable String _sqlSubstring;
    private final List<String> _sql = new ArrayList<>();

    public QueryCounter()
    {
        this(null);
    }

    /** @param sqlSubstring count only statements whose SQL contains this, ignoring case */
    public QueryCounter(@Nullable String sqlSubstring)
    {
        _sqlSubstring = sqlSubstring;
        QueryProfiler.getInstance().addListener(this);
    }

    @Override
    public boolean matches(@Nullable DbScope scope, String sql, @Nullable QueryLogging queryLogging)
    {
        // A null scope marks DatabaseMetaData and OLAP calls, which aren't SQL statements
        return scope != null && Thread.currentThread() == _thread && (_sqlSubstring == null || StringUtils.containsIgnoreCase(sql, _sqlSubstring));
    }

    @Override
    public void queryInvoked(DbScope scope, String sql, User user, Container container, QueryLogging queryLogging)
    {
        _sql.add(sql);
    }

    public int getCount()
    {
        return _sql.size();
    }

    /** The counted statements in execution order, for assertion messages */
    public List<String> getSql()
    {
        return Collections.unmodifiableList(_sql);
    }

    @Override
    public void close()
    {
        QueryProfiler.getInstance().removeListener(this);
    }

    public static class TestCase extends Assert
    {
        private static SqlSelector selector(String sql)
        {
            return new SqlSelector(CoreSchema.getInstance().getSchema(), sql);
        }

        @Test
        public void testCountsOwnThreadOnly() throws InterruptedException
        {
            SqlSelector tagged = selector("SELECT 1 AS QueryCounterTest");

            try (QueryCounter all = new QueryCounter(); QueryCounter filtered = new QueryCounter("querycountertest"))
            {
                tagged.getObject(Integer.class);
                selector("SELECT 2").getObject(Integer.class);

                Thread other = new Thread(() -> tagged.getObject(Integer.class));
                other.start();
                other.join();

                assertEquals(all.getSql().toString(), 2, all.getCount());
                assertEquals(filtered.getSql().toString(), 1, filtered.getCount());
            }
        }

        @Test
        public void testStopsCountingWhenClosed()
        {
            QueryCounter counter = new QueryCounter("QueryCounterTest");
            counter.close();
            selector("SELECT 1 AS QueryCounterTest").getObject(Integer.class);
            assertEquals(0, counter.getCount());
        }
    }
}
