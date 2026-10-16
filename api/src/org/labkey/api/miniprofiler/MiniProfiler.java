/*
 * Copyright (c) 2014-2026 LabKey Corporation
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
package org.labkey.api.miniprofiler;

import jakarta.servlet.ServletContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ConnectionUsage;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.MemTracker;
import org.labkey.api.view.ViewServlet;

/**
 * Does some lightweight data capture about database queries and other potentially expensive work performed in the
 * context of an HTTP request.
 */
public class MiniProfiler
{
    /** Message that points the user to turn on stack trace collection if desired */
    public static final String NO_STACK_TRACE_AVAILABLE = "No stack trace available. Enable capturing stack traces via the Admin Console's Profiler link.";
    static boolean _collectTroubleshootingStackTraces = false;

    private MiniProfiler() { }

    /** Get per-user settings */
    public static @Nullable Settings getSettings()
    {
        Settings settings = new Settings();
        settings.setCollectTroubleshootingStackTraces(_collectTroubleshootingStackTraces);
        settings.setTrackConnectionUsage(ConnectionUsage.isEnabled());

        return settings;
    }

    /** Save per-user settings. */
    public static void saveSettings(@NotNull Settings settings)
    {
        // Troubleshooting stacktraces and connection usage are site-wide only
        setCollectTroubleshootingStackTraces(settings._collectTroubleshootingStackTraces);
        ConnectionUsage.setEnabled(settings._trackConnectionUsage);
    }

    public static void addObject(Object object)
    {
        RequestInfo requestInfo = MemTracker.getInstance().current();
        if (requestInfo == null || requestInfo.isIgnored())
            return;

        // NOTE: Can't check the server has finished startup since some startup tasks may allocate objects and cause deadlock between MemTracker._put and ModuleLoader.STARTUP_LOCK
//        if (!(ModuleLoader.getInstance().isStartupComplete() && getSettings().isEnabled()))
//            return;

        requestInfo.addObject(object);
    }

    /**
     * Returns a {@link CustomTiming} within the current Timing step that will time the code between its creation and disposal.
     * @param category category of timing within the current Timing step.
     * @param msg descriptive message.
     */
    @Nullable
    public static CustomTiming custom(String category, String msg)
    {
        RequestInfo requestInfo = MemTracker.getInstance().current();
        if (requestInfo == null || requestInfo.isIgnored())
            return null;

        if (!(ModuleLoader.getInstance().isStartupComplete())) // && getSettings().isEnabled()))
            return null;

        return requestInfo.custom(category, msg);
    }

    /**
     * Adds a completed "sql" CustomTiming to the current Timing step.
     */
    public static void addQuery(long elapsed, String sql, @Nullable StackTraceElement[] stackTrace)
    {
        // Compose URL manually. Creating ActionURL looks up contextPath which can
        // execute a query and log it, resulting in StackOverflowError or deadlock.
        String url = null;
        ServletContext ctx = ViewServlet.getViewServletContext();
        if (ctx != null)
            url = ctx.getContextPath() + "/admin/queryStackTraces.view?sqlHashCode=" + sql.hashCode();

        addCustomTiming("sql", elapsed, sql, url, stackTrace);
    }

    /**
     * Adds a completed CustomTiming to the current Timing step.
     */
    public static void addCustomTiming(String category, long elapsed, String msg, @Nullable String detailsUrl, @Nullable StackTraceElement[] stackTrace)
    {
        RequestInfo requestInfo = MemTracker.getInstance().current();
        if (requestInfo == null || requestInfo.isIgnored())
            return;

        if (!(ModuleLoader.getInstance().isStartupComplete())) // && getSettings().isEnabled()))
            return;

        requestInfo.addCustomTiming(category, elapsed, msg, detailsUrl, stackTrace);
    }

    /** @return true if an admin has opted-in to collecting stack traces to troubleshoot memory leaks, unclosed result sets, or similar problems */
    public static boolean isCollectTroubleshootingStackTraces()
    {
        return _collectTroubleshootingStackTraces;
    }

    /**
     * @return the stack of the calling thread if the collection of these troubleshooting stack traces has been enabled.
     * It is expensive to generate these, so this approach is intended to make it easy to only get them during active
     * investigation. Returns null if the setting is disabled.
     */
    @Nullable
    public static StackTraceElement[] getTroubleshootingStackTrace()
    {
        return isCollectTroubleshootingStackTraces() ? getTroubleshootingStackTraceUnconditionally() : null;
    }

    /**
     * @return the stack of the calling thread regardless of the state of the "enable stack traces" setting.
     */
    @NotNull
    public static StackTraceElement[] getTroubleshootingStackTraceUnconditionally()
    {
        StackTraceElement[] fullStack = Thread.currentThread().getStackTrace();
        if (fullStack.length > 0)
        {
            // Automatically omit this method from the stack
            int callerFramesToOmit = 2;
            StackTraceElement[] result = new StackTraceElement[fullStack.length - callerFramesToOmit];
            System.arraycopy(fullStack, 1, result, 0, result.length);
            return result;
        }
        return fullStack;
    }

    /** This setting will be retained only for the current instance of the web app. Once the server is restarted, it will default to false again */
    public static void setCollectTroubleshootingStackTraces(boolean enabled)
    {
        _collectTroubleshootingStackTraces = enabled;
    }

    public static class Settings
    {
        private boolean _collectTroubleshootingStackTraces;
        private boolean _trackConnectionUsage;

        public boolean isCollectTroubleshootingStackTraces()
        {
            return _collectTroubleshootingStackTraces;
        }

        public void setCollectTroubleshootingStackTraces(boolean collect)
        {
            _collectTroubleshootingStackTraces = collect;
        }

        public boolean isTrackConnectionUsage()
        {
            return _trackConnectionUsage;
        }

        public void setTrackConnectionUsage(boolean track)
        {
            _trackConnectionUsage = track;
        }
    }
}
