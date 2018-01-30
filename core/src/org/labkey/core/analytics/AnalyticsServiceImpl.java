/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.core.analytics;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.analytics.AnalyticsService;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ConfigProperty;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.Map;

public class AnalyticsServiceImpl implements AnalyticsService
{
    static public AnalyticsServiceImpl get()
    {
        return (AnalyticsServiceImpl) AnalyticsService.get();
    }

    static public void register()
    {
        AnalyticsService.set(new AnalyticsServiceImpl());
    }


    private static final String PROP_CATEGORY = "analytics";
    public static final String DEFAULT_ACCOUNT_ID = "UA-3989586-1";

    public enum TrackingStatus
    {
        disabled,
        enabled,    // google analytics 'property  id'
        script      // tracking script
    }

    public enum AnalyticsProperty
    {
        trackingStatus,
        accountId,
        trackingScript,
    }

    private String getProperty(AnalyticsProperty property)
    {
        Map<String, String> properties = PropertyManager.getProperties(PROP_CATEGORY);
        return properties.get(property.toString());
    }

    public void setSettings(TrackingStatus trackingStatus, String accountId, String script)
    {
        PropertyManager.PropertyMap properties = PropertyManager.getWritableProperties(PROP_CATEGORY, true);
        properties.put(AnalyticsProperty.trackingStatus.toString(), trackingStatus.toString());
        properties.put(AnalyticsProperty.accountId.toString(), StringUtils.trimToNull(accountId));
        properties.put(AnalyticsProperty.trackingScript.toString(), StringUtils.trimToNull(script));
        properties.save();
    }

    public TrackingStatus getTrackingStatus()
    {
        String strStatus = getProperty(AnalyticsProperty.trackingStatus);
        if (strStatus == null)
        {
            return TrackingStatus.disabled;
        }
        try
        {
            return TrackingStatus.valueOf(strStatus);
        }
        catch (IllegalArgumentException iae)
        {
            return TrackingStatus.disabled;
        }
    }

    public String getAccountId()
    {
        String accountId = getProperty(AnalyticsProperty.accountId);
        if (accountId != null)
        {
            return accountId;
        }
        return DEFAULT_ACCOUNT_ID;
    }


    private boolean showTrackingScript(ViewContext context)
    {
        switch (getTrackingStatus())
        {
            default:
                return false;
            case enabled:
                return true;
            case script:
                return true;
        }
    }

    /**
     * Returns the page url that we will report to Analytics.
     * For privacy reasons, we strip off the URL parameters if the container does not allow guest access.
     * We append the serverGUID parameter to the URL.
     */
    private String getSanitizedUrl(ViewContext context)
    {
        ActionURL actionUrl = context.cloneActionURL();
        Container container = context.getContainer();
        if (!container.hasPermission(UserManager.getGuestUser(), ReadPermission.class))
        {
            actionUrl.deleteParameters();
            actionUrl.setExtraPath(container.getId());
        }
        // Add the server GUID to the URL.  Remove the "-" because they are problematic for Google Analytics regular
        // expressions.
        String guid = AppProps.getInstance().getServerGUID();
        guid = StringUtils.replace(guid, "-", ".");
        actionUrl.addParameter("serverGUID", guid);
        return actionUrl.toString();
    }


    /**
     * The Google Analytics tracking script.
     * <p>For an explanation of what settings are available on the pageTracker object, see
     * <a href="http://code.google.com/apis/analytics/docs/gaJSApi.html">Google Analytics Tracking API</a>
     */
    // new style (async)
    static final private String TRACKING_SCRIPT_TEMPLATE_ASYNC =
        "<script type=\"text/javascript\">\n"+
        "var _gaq = _gaq || [];\n" +
        "_gaq.push(['_setAccount', ${ACCOUNT_ID:jsString}]);\n" +
        "_gaq.push(['_setDetectTitle', false]);\n" +
        "_gaq.push(['_trackPageview', ${PAGE_URL:jsString}]);\n" +
        "</script>\n"+
        "<script async=\"async\" type=\"text/javascript\" src=\"${GA_JS:htmlEncode}\"></script>\n";


    public String getSavedScript()
    {
        return getProperty(AnalyticsProperty.trackingScript);
    }


    private String getRawScript()
    {
        switch (getTrackingStatus())
        {
            case script:
                return getSavedScript();
            case enabled:
                return TRACKING_SCRIPT_TEMPLATE_ASYNC;
            case disabled:
            default:
                return "";
        }
    }

    public String getTrackingScript(ViewContext context)
    {
        if (!showTrackingScript(context))
            return "";

        boolean isSecure = context.getActionURL().getScheme().startsWith("https");
        String gaJS = (isSecure ? "https://ssl" : "http://www") + ".google-analytics.com/ga.js";

        StringExpression se = StringExpressionFactory.create(getRawScript());
        String trackingScript = se.eval(PageFlowUtil.map(
                "ACCOUNT_ID", getAccountId(),
                "PAGE_URL", getSanitizedUrl(context),
                "GA_JS", gaJS
                ));
        return trackingScript;
    }

    static public void populateSettingsWithStartupProps()
    {
        final boolean isBootstrap = ModuleLoader.getInstance().isNewInstall();
        PropertyManager.PropertyMap properties = PropertyManager.getWritableProperties(PROP_CATEGORY, true);
        ModuleLoader.getInstance().getConfigProperties(PROP_CATEGORY)
                .forEach(prop ->{
                    try
                    {
                        AnalyticsProperty analyticsProperty = AnalyticsProperty.valueOf(prop.getName());
                        if (isBootstrap || prop.getModifier() != ConfigProperty.modifier.bootstrap)
                            properties.put(analyticsProperty.toString(), prop.getValue());
                    }
                    catch (IllegalArgumentException ex)
                    {
                        Logger.getLogger(AnalyticsServiceImpl.class).warn("error handling startup property", ex);
                    }
                });
        properties.save();
    }
}
