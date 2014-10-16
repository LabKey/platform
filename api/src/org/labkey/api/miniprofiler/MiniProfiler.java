package org.labkey.api.miniprofiler;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.MemTracker;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;

import javax.servlet.ServletContext;
import java.util.Set;

/**
 * User: kevink
 */
public class MiniProfiler
{
    // TODO: mini profiler settings should be moved to AppProps site-level and/or user-level settings.
    static final int TRIVIAL_MILLIS = 3;
    static final boolean CUSTOM_TIMING_CAPTURE_STACKTRACE = true;
    static final boolean SHOW_CONTROLS = true;

    private MiniProfiler() { }

    public static boolean isEnabled(ViewContext context)
    {
        // Disable when running within TeamCity
        if (AppProps.getInstance().isTeamCityEnviornment())
            return false;

        // TODO: site-level and/or user-level settings to enable/disable
//        if (!AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_MINI_PROFILER))
//            return false;

        // CONSIDER: Add CanSeeProfilingPermission ?
        User user = context.getUser();
        return ModuleLoader.getInstance().isStartupComplete() &&
                (AppProps.getInstance().isDevMode() || (user != null && user.isDeveloper()));
    }

    public static String renderInitScript(long currentId, Set<Long> ids, String version)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<script type='text/javascript'>\n");
        sb.append("LABKEY.internal.MiniProfiler.init({\n");
        sb.append("  currentId:").append(currentId).append(",\n");
        sb.append("  ids:").append(ids).append(",\n");
        sb.append("  version:").append(version).append(",\n");
        sb.append("  renderPosition:").append("'bottomright'").append(",\n");
        sb.append("  showTrivial:").append(false).append(",\n");
        sb.append("  trivialMilliseconds:").append(TRIVIAL_MILLIS).append(",\n");
        sb.append("  showChildrenTime:").append(false).append(",\n");
        sb.append("  maxTracesToShow:").append(20).append(",\n");
        sb.append("  showControls:").append(SHOW_CONTROLS).append(",\n");
        sb.append("  authorized:true,\n");
        sb.append("  toggleShortcut:'alt-p',\n");
        sb.append("  startHidden:false\n");
        sb.append("});\n");
        sb.append("</script>\n");
        return sb.toString();
    }

    /**
     * Returns a {@link Timing} that will time the code between its creation and disposal.
     * @param name descriptive name for the code that is encapsulated by the resulting AutoCloseable's lifetime.
     */
    public static Timing step(String name)
    {
        RequestInfo requestInfo = MemTracker.getInstance().current();
        if (requestInfo == null)
            return null;

        return requestInfo.step(name);
    }

    /**
     * Returns a {@link CustomTiming} within the current Timing step that will time the code between its creation and disposal.
     * @param category category of timing within the current Timing step.
     * @param msg descriptive message.
     */
    public static CustomTiming custom(String category, String msg)
    {
        RequestInfo requestInfo = MemTracker.getInstance().current();
        if (requestInfo == null)
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
        if (requestInfo == null)
            return;

        requestInfo.addCustomTiming(category, elapsed, msg, detailsUrl, stackTrace);
    }
}
