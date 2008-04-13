package org.labkey.api.analytics;

import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

/**
 * Adds analytics tracking code to HTML pages.
 */
public class AnalyticsService
{
    static private Interface instance;

    static public Interface get() {
        return instance;
    }

    static public void set(Interface impl) {
        instance = impl;
    }

    static public String getTrackingScript() {
        Interface i = get();
        if (i == null) {
            return "";
        }
        return i.getTrackingScript(HttpView.getRootContext());
    }

    public interface Interface {
        String getTrackingScript(ViewContext viewContext);

        String getSanitizedUrl(ViewContext viewContext);
    }
}
