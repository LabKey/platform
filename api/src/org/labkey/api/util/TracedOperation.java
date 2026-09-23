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
package org.labkey.api.util;

import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.logging.LogHelper;

import java.util.Map;

/**
 * A Datadog APM span paired with a summary log line, for a unit of work whose row counts are known only once it has run.
 * The span is activated, so database work performed inside nests under it.
 *
 * Callers report success with completed(), or hand the work to run(). An exception or early return closes the operation
 * as incomplete. Incomplete alone is not tagged as an APM error. An enclosing request span already carries genuine
 * failure info, and for the row APIs the common case is input the server rejected.
 * run() does tag the exceptions it catches, since it can tell them apart.
 */
public class TracedOperation implements AutoCloseable
{
    private static final Logger LOG = LogHelper.getLogger(TracedOperation.class, "Target, row counts, and elapsed time for mutating API requests");

    /** Operations at least this slow log at INFO, so they're visible on the deployments that send no APM data */
    private static final long SLOW_MS = 30_000;

    private final Span _span;
    private final Scope _scope;
    private final long _startNanos;

    private String _description;
    private String _rowSummary;
    private boolean _completed;

    private TracedOperation(String operationName)
    {
        Tracer tracer = GlobalTracer.get();
        _span = tracer.buildSpan(operationName).start();
        // Never a service-entry span, so Datadog computes no hits/duration/error metrics for it without this
        _span.setTag(DDTags.MEASURED, true);
        _scope = tracer.activateSpan(_span);
        _startNanos = System.nanoTime();
        _description = operationName;
    }

    public static TracedOperation start(@NotNull String operationName)
    {
        return new TracedOperation(operationName);
    }

    /** Groups the operation in APM. Keep the cardinality low - it's a trace metric dimension, so no user-defined names. */
    public TracedOperation resource(@NotNull String resourceName)
    {
        _span.setTag(DDTags.RESOURCE_NAME, resourceName);
        return this;
    }

    /** What the log line calls this operation. Free to carry detail too identifying or too high-cardinality to tag. */
    public TracedOperation describedAs(@NotNull String description)
    {
        _description = description;
        return this;
    }

    public TracedOperation tag(@NotNull String name, @Nullable Object value)
    {
        if (value != null)
            _span.setTag(name, value.toString());
        return this;
    }

    public void completed(int rows)
    {
        _span.setTag("labkey.rows", rows);
        _rowSummary = rows + " rows";
        _completed = true;
    }

    /** Runs work that reports no row count, recording any exception on the span before letting it propagate */
    public void run(@NotNull Runnable work)
    {
        try
        {
            work.run();
            _completed = true;
        }
        catch (Throwable t)
        {
            Tags.ERROR.set(_span, true);
            _span.log(Map.of(Fields.ERROR_OBJECT, t));
            throw t;
        }
        finally
        {
            close();
        }
    }

    @Override
    public void close()
    {
        long elapsedMs = (System.nanoTime() - _startNanos) / 1_000_000;

        try
        {
            // Written on both branches; a tag set only when true can't be grouped on in Datadog
            _span.setTag("labkey.completed", _completed);
            _scope.close();
        }
        finally
        {
            _span.finish();
        }

        String message = formatMessage(_description, _completed, _rowSummary, elapsedMs);
        if (elapsedMs >= SLOW_MS)
            LOG.info(message);
        else
            LOG.debug(message);
    }

    static String formatMessage(String description, boolean completed, @Nullable String rowSummary, long elapsedMs)
    {
        if (!completed)
            return description + ": did not complete after " + elapsedMs + " ms";
        return description + ": " + (null == rowSummary ? "" : rowSummary + ", ") + elapsedMs + " ms";
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testMessage()
        {
            assertEquals("insert samples.Blood in /Home/Study: 500 rows, 20 ms",
                    formatMessage("insert samples.Blood in /Home/Study", true, "500 rows", 20));
            assertEquals("materialize exp.Material: 3 ms",
                    formatMessage("materialize exp.Material", true, null, 3));
            assertEquals("update lists.People in /Home: did not complete after 12 ms",
                    formatMessage("update lists.People in /Home", false, null, 12));
        }

        @Test
        public void testNoAgentAttached()
        {
            // GlobalTracer hands back a no-op implementation when the Datadog agent isn't attached
            try (TracedOperation op = TracedOperation.start("labkey.test").resource("test").describedAs("test").tag("labkey.query", "a.b"))
            {
                op.completed(1);
            }
            TracedOperation.start("labkey.test").resource("test").describedAs("test").run(() -> {});
        }
    }
}
