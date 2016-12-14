/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.MemTracker;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;

import javax.servlet.ServletContext;
import java.util.Map;
import java.util.Set;

/**
 * Does some lightweight data capture about database queries and other potentially expensive work performed in the
 * context of an HTTP request.
 *
 * User: kevink
 */
public class MiniProfiler
{
    private static final String CATEGORY = "miniprofiler";

    private static final BeanObjectFactory<Settings> SETTINGS_FACTORY = new BeanObjectFactory<>(Settings.class);

    private static final Cache<User, Settings> SETTINGS_CACHE = CacheManager.getBlockingCache(1000, CacheManager.DEFAULT_TIMEOUT, "miniprofiler settings", new CacheLoader<User, Settings>()
    {
        @Override
        public Settings load(User user, @Nullable Object argument)
        {
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
        }
    });

    private MiniProfiler() { }

    public static HelpTopic getHelpTopic()
    {
        return new HelpTopic("profiler");
    }

    public static boolean isEnabled(ViewContext context)
    {
        // CONSIDER: Add CanSeeProfilingPermission ?
        User user = context.getUser();
        return ModuleLoader.getInstance().isStartupComplete() && user != null && user.isDeveloper() && getSettings().isEnabled();
    }

    /** Get site-wide settings. */
    public static Settings getSettings()
    {
        return SETTINGS_CACHE.get(User.guest);
    }

    /** Get per-user settings if available or site-wide */
    public static Settings getSettings(@Nullable User user)
    {
        Settings settings = null;
        if (user != null)
            settings = SETTINGS_CACHE.get(user);

        if (settings == null)
            settings = SETTINGS_CACHE.get(User.guest);

        return settings;
    }

    /** Save site-wide settings. */
    public static void saveSettings(@NotNull Settings settings)
    {
        saveSettings(settings, User.guest);
    }

    public static void saveSettings(@NotNull Settings settings, @NotNull User user)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(CATEGORY, true);
        SETTINGS_FACTORY.toStringMap(settings, map);
        map.save();
        SETTINGS_CACHE.remove(user);
    }

    /** Reset site-wide settings. */
    public static void resetSettings()
    {
        resetSettings(User.guest);
    }

    /** Reset per-user settings. */
    public static void resetSettings(@NotNull User user)
    {
        PropertyManager.getNormalStore().deletePropertySet(CATEGORY);
        SETTINGS_CACHE.remove(user);
    }

    public static String renderInitScript(long currentId, Set<Long> ids, String version)
    {
        Settings settings = getSettings();

        StringBuilder sb = new StringBuilder();
        sb.append("<script type='text/javascript'>\n");
        sb.append("LABKEY.internal.MiniProfiler.init({\n");
        sb.append("  currentId:").append(currentId).append(",\n");
        sb.append("  ids:").append(ids).append(",\n");
        sb.append("  version:").append(version).append(",\n");
        sb.append("  renderPosition:'").append(settings.getRenderPosition().getStyle()).append("',\n");
        sb.append("  showTrivial:").append(settings.isShowTrivial()).append(",\n");
        sb.append("  trivialMilliseconds:").append(settings.getTrivialMillis()).append(",\n");
        sb.append("  showChildrenTime:").append(settings.isShowChildrenTime()).append(",\n");
        sb.append("  maxTracesToShow:").append(20).append(",\n");
        sb.append("  showControls:").append(settings.isShowControls()).append(",\n");
        sb.append("  authorized:true,\n");
        sb.append("  toggleShortcut:'").append(settings.getToggleShortcut() != null ? settings.getToggleShortcut() : "").append("',\n");
        sb.append("  startHidden:").append(settings.isStartHidden()).append("\n");
        sb.append("});\n");
        sb.append("</script>\n");

        return sb.toString();
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

        if (!(ModuleLoader.getInstance().isStartupComplete() && getSettings().isEnabled()))
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

    public static enum RenderPosition
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
        private boolean _enabled = AppProps.getInstance().isDevMode();
        private boolean _showChildrenTime = false;
        private boolean _showTrivial = false;
        private int _trivialMillis = 3;

        private boolean _startHidden = false;
        private boolean _showControls = true;
        private RenderPosition _renderPosition = RenderPosition.BottomRight;
        private boolean _captureCustomTimingStacktrace = true;
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

        public boolean isCaptureCustomTimingStacktrace()
        {
            return _captureCustomTimingStacktrace;
        }

        public void setCaptureCustomTimingStacktrace(boolean captureCustomTimingStacktrace)
        {
            _captureCustomTimingStacktrace = captureCustomTimingStacktrace;
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
