/*
 * Copyright (c) 2014-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.JavaScriptFragment;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.SafeToRenderEnum;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;

import javax.servlet.ServletContext;
import java.security.Principal;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.PageFlowUtil.jsString;

/**
 * Does some lightweight data capture about database queries and other potentially expensive work performed in the
 * context of an HTTP request.
 *
 * User: kevink
 */
public class MiniProfiler
{
    /** Message that points the user to turn on stack trace collection if desired */
    public static final String NO_STACK_TRACE_AVAILABLE = "No stack trace available. Enable capturing stack traces via the Admin Console's Profiler link.";
    private static final String CATEGORY = "miniprofiler";

    private static final BeanObjectFactory<Settings> SETTINGS_FACTORY = new BeanObjectFactory<>(Settings.class);

    static boolean _collectTroubleshootingStackTraces = false;

    private static final Cache<User, Settings> SETTINGS_CACHE = CacheManager.getBlockingCache(1000, CacheManager.DEFAULT_TIMEOUT, "Mini profiler settings", (user, argument) -> {
        // site-wide settings keyed by guest user, otherwise per-user settings
        RequestInfo current = MemTracker.get().current();
        boolean ignored = current != null && current.isIgnored();
        try
        {
            // suspend profiler while we load the settings
            if (current != null)
                current.setIgnored(true);
            Map<String, String> properties = PropertyManager.getProperties(user, ContainerManager.getRoot(), CATEGORY);
            return SETTINGS_FACTORY.fromMap(properties);
        }
        finally
        {
            // resume profiling
            if (current != null)
                current.setIgnored(ignored);
        }
    });

    private MiniProfiler() { }

    public static String getHelpTopic()
    {
        return "profiler#mini";
    }

    public static boolean isEnabled(ViewContext context)
    {
        return context.getContainer() != null && isEnabled(context.getUser());
    }

    private static boolean isEnabled(Principal user)
    {
        if (!(user instanceof User))
            return false;

        return isEnabled((User)user);
    }

    public static boolean isEnabled(User user)
    {
        if (ModuleLoader.getInstance().isStartupComplete())
            return false;

        // CONSIDER: Add CanSeeProfilingPermission ?
        if (user == null || !user.isPlatformDeveloper())
            return false;

        Settings settings = getSettings(user);
        if (settings == null)
            return false;

        return settings.isEnabled();
    }

    /** Get per-user settings */
    public static @Nullable Settings getSettings(@NotNull Principal user)
    {
        if (user instanceof User)
            return getSettings((User)user);

        return null;
    }

    /** Get per-user settings */
    public static @Nullable Settings getSettings(@Nullable User user)
    {
        Settings settings = null;
        if (user != null)
        {
            settings = SETTINGS_CACHE.get(user);

            // Stacktrace setting isn't really cached, it just piggybacks on the MiniProfiler settings bean/form
            settings.setCollectTroubleshootingStackTraces(_collectTroubleshootingStackTraces);
        }
        return settings;
    }

    /** Save per-user settings. */
    public static void saveSettings(@NotNull Settings settings, @NotNull User user)
    {
        // Troubleshooting stacktraces are site-wide only
        setCollectTroubleshootingStackTraces(settings._collectTroubleshootingStackTraces);

        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(user, ContainerManager.getRoot(), CATEGORY, true);
        SETTINGS_FACTORY.toStringMap(settings, map);
        map.save();
        SETTINGS_CACHE.remove(user);
    }

    /** Reset per-user settings. */
    public static void resetSettings(@NotNull User user)
    {
        PropertyManager.getNormalStore().deletePropertySet(CATEGORY);
        SETTINGS_CACHE.remove(user);
    }

    public static JavaScriptFragment renderInitScript(@NotNull User user, long currentId, Set<Long> ids, String version)
    {
        Settings settings = getSettings(user);

        return JavaScriptFragment.unsafe(
            "<script type='text/javascript'>\n" +
            "LABKEY.internal.MiniProfiler.init({\n" +
            "  currentId:" + currentId + ",\n" +
            "  ids:" + ids + ",\n" +
            "  version:" + Integer.valueOf(version) + ",\n" +
            "  renderPosition:" + jsString(settings.getRenderPosition().getStyle()) + ",\n" +
            "  showTrivial:" + settings.isShowTrivial() + ",\n" +
            "  trivialMilliseconds:" + settings.getTrivialMillis() + ",\n" +
            "  showChildrenTime:" + settings.isShowChildrenTime() + ",\n" +
            "  maxTracesToShow:" + 20 + ",\n" +
            "  showControls:" + settings.isShowControls() + ",\n" +
            "  authorized:true,\n" +
            "  toggleShortcut:" + jsString(settings.getToggleShortcut() != null ? settings.getToggleShortcut() : "") + ",\n" +
            "  startHidden:" + settings.isStartHidden() + ",\n" +
            "  startMinimized:" + settings.isStartMinimized() + "\n" +
            "});\n" +
            "</script>\n"
        );
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
     * Returns a {@link Timing} that will time the code between its creation and disposal.
     * @param name descriptive name for the code that is encapsulated by the resulting AutoCloseable's lifetime.
     */
    @Nullable
    public static Timing step(String name)
    {
        RequestInfo requestInfo = MemTracker.getInstance().current();
        if (requestInfo == null || requestInfo.isIgnored())
            return null;

        if (!isEnabled(requestInfo.getUser()))
            return null;

        return requestInfo.step(name);
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
        if (isCollectTroubleshootingStackTraces())
        {
            StackTraceElement[] fullStack = Thread.currentThread().getStackTrace();
            if (fullStack.length > 0)
            {
                // Automatically omit this method from the stack
                int callerFramesToOmit = 1;
                StackTraceElement[] result = new StackTraceElement[fullStack.length - callerFramesToOmit];
                System.arraycopy(fullStack, 1, result, 0, result.length);
                return result;
            }
            return fullStack;
        }
        return null;
    }

    /** This setting will be retained only for the current instance of the web app. Once the server is restarted, it will default to false again */
    public static void setCollectTroubleshootingStackTraces(boolean enabled)
    {
        _collectTroubleshootingStackTraces = enabled;
    }

    public enum RenderPosition implements SafeToRenderEnum
    {
        TopLeft("left"),
        TopRight("right"),
        BottomLeft("bottomleft"),
        BottomRight("bottomright");

        // Part of the css style used to render the value, e.g.: profiler-left or profiler-bottomright
        private final String _style;

        RenderPosition(String style)
        {
            _style = style;
        }

        public String getStyle()
        {
            return _style;
        }
    }

    public static class Settings
    {
        private boolean _enabled = false;
        private boolean _showChildrenTime = false;
        private boolean _showTrivial = false;
        private int _trivialMillis = 3;

        private boolean _startMinimized = true;
        private boolean _startHidden = false;
        private boolean _showControls = true;
        private RenderPosition _renderPosition = RenderPosition.BottomRight;
        private boolean _collectTroubleshootingStackTraces;
        private String _toggleShortcut = "alt+p";

        public boolean isEnabled()
        {
            return _enabled;
        }

        public void setEnabled(boolean enabled)
        {
            _enabled = enabled;
        }

        public boolean isShowTrivial()
        {
            return _showTrivial;
        }

        public void setShowTrivial(boolean showTrivial)
        {
            _showTrivial = showTrivial;
        }

        public int getTrivialMillis()
        {
            return _trivialMillis;
        }

        public void setTrivialMillis(int trivialMillis)
        {
            _trivialMillis = trivialMillis;
        }

        public boolean isShowChildrenTime()
        {
            return _showChildrenTime;
        }

        public void setShowChildrenTime(boolean showChildrenTime)
        {
            _showChildrenTime = showChildrenTime;
        }

        public boolean isStartMinimized()
        {
            return _startMinimized;
        }

        public void setStartMinimized(boolean startMinimized)
        {
            _startMinimized = startMinimized;
        }

        public boolean isStartHidden()
        {
            return _startHidden;
        }

        public void setStartHidden(boolean startHidden)
        {
            _startHidden = startHidden;
        }

        public boolean isShowControls()
        {
            return _showControls;
        }

        public void setShowControls(boolean showControls)
        {
            _showControls = showControls;
        }

        public RenderPosition getRenderPosition()
        {
            return _renderPosition;
        }

        public void setRenderPosition(RenderPosition renderPosition)
        {
            _renderPosition = renderPosition;
        }

        public boolean isCollectTroubleshootingStackTraces()
        {
            return _collectTroubleshootingStackTraces;
        }

        public void setCollectTroubleshootingStackTraces(boolean collect)
        {
            _collectTroubleshootingStackTraces = collect;
        }

        public String getToggleShortcut()
        {
            return _toggleShortcut;
        }

        public void setToggleShortcut(String toggleShortcut)
        {
            _toggleShortcut = toggleShortcut;
        }
    }
}
