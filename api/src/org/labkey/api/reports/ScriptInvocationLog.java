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
package org.labkey.api.reports;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.logging.LogHelper;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Start/completion/duration logging for external script invocations. Separate class so it gets its own logger
 * category, enabling timing without the engines' unrelated debug output. Messages are key=value to keep log
 * analysis parser-free.
 */
public final class ScriptInvocationLog
{
    private static final Logger LOG = LogHelper.getLogger(ScriptInvocationLog.class, "R and Python script invocation start, completion, and duration");

    private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n]+");

    private ScriptInvocationLog()
    {
    }

    public interface ScriptBody<T>
    {
        T run() throws ScriptException;
    }

    /**
     * The caller's INVOCATION_LABEL binding, or null if the caller didn't set one. Labels embed user-supplied report
     * and assay design names, so line breaks are collapsed to keep one invocation on one line.
     */
    @Nullable
    public static String label(ScriptContext context)
    {
        Object label = context == null ? null : context.getAttribute(ExternalScriptEngine.INVOCATION_LABEL, ScriptContext.ENGINE_SCOPE);
        return label == null ? null : LINE_BREAKS.matcher(String.valueOf(label)).replaceAll(" ");
    }

    /**
     * Start is DEBUG because it only matters for diagnosing a hang, where a start with no completion is the sole evidence.
     * The failure line deliberately omits the exception message: for a non-zero exit that message carries the script's
     * entire stdout/stderr, which can be huge and can echo the transform session's API key.
     */
    public static <T> T time(String engine, @Nullable String label, ScriptBody<T> body) throws ScriptException
    {
        LOG.debug("script start engine={} label={}", engine, label);
        long start = System.nanoTime();
        boolean completed = false;
        try
        {
            T result = body.run();
            completed = true;
            return result;
        }
        finally
        {
            // finally rather than catch so an Error still logs a duration, which a hang never does
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            if (completed)
                LOG.info("script done engine={} label={} durationMs={}", engine, label, durationMs);
            else
                LOG.warn("script failed engine={} label={} durationMs={}", engine, label, durationMs);
        }
    }

    public static void timedOut(String engine, @Nullable String label, long timeout, TimeUnit unit)
    {
        LOG.warn("script timeout engine={} label={} timeout={} unit={}", engine, label, timeout, unit.name().toLowerCase());
    }

    public static void nonZeroExit(String engine, @Nullable String label, int exitCode)
    {
        LOG.warn("script exit engine={} label={} exitCode={}", engine, label, exitCode);
    }
}
