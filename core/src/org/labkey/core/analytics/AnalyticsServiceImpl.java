/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.labkey.api.analytics.AnalyticsService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AbstractWriteableSettingsGroup;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ConfigProperty;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyHandler;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.Map;
import java.util.Objects;

public class AnalyticsServiceImpl implements AnalyticsService
{
    static public AnalyticsServiceImpl get()
    {
        return (AnalyticsServiceImpl) AnalyticsService.get();
    }

    static public void register()
    {
        AnalyticsService.setInstance(new AnalyticsServiceImpl());
    }


    private static final String PROP_CATEGORY = "analytics";
    public static final String DEFAULT_ACCOUNT_ID = "UA-3989586-1";

    public enum TrackingStatus
    {
        disabled(false)
                {
                    @Override
                    public String getRawScript()
                    {
                        return "";
                    }
                },
        /** Use GA with URL sanitization */
        enabled(true)
                {
                    @Override
                    public String getRawScript()
                    {
                        return TRACKING_SCRIPT_TEMPLATE_ASYNC;
                    }
                },
        /** Use GA without replacing container paths */
        enabledFullURL(true)
                {
                    @Override
                    public String getRawScript()
                    {
                        return TRACKING_SCRIPT_TEMPLATE_ASYNC;
                    }
                },
        /** Custom tracking script */
        script(true)
                {
                    @Override
                    public String getRawScript()
                    {
                        return get().getSavedScript();
                    }
                };

        private final boolean _track;

        TrackingStatus(boolean track)
        {
            _track = track;
        }

        public boolean showTrackingScript()
        {
            return _track;
        }

        public abstract String getRawScript();
    }

    public enum AnalyticsProperty implements StartupProperty
    {
        trackingStatus("Analytics tracking status. Valid values: [disabled, enabled, enabledFullURL, script]"),
        accountId("Account ID"),
        trackingScript("Custom analytics script");

        private final String _description;

        AnalyticsProperty(String description)
        {
            _description = description;
        }

        @Override
        public String getDescription()
        {
            return _description;
        }
    }

    private String getProperty(AnalyticsProperty property)
    {
        Map<String, String> properties = PropertyManager.getProperties(PROP_CATEGORY);
        return properties.get(property.toString());
    }

    public void setSettings(TrackingStatus trackingStatus, String accountId, String script, User user)
    {
        AnalyticsSettingsGroup g = new AnalyticsSettingsGroup();
        g.store(trackingStatus, accountId, script, user);
    }

    /** Issue 36870 - an admittedly clunky way to hook into audit behavior */
    private static class AnalyticsSettingsGroup extends AbstractWriteableSettingsGroup
    {
        @Override
        protected String getType()
        {
            return "Analytics";
        }

        @Override
        protected String getGroupName()
        {
            return PROP_CATEGORY;
        }

        @Override
        protected User getPropertyConfigUser()
        {
            return PropertyManager.SHARED_USER;
        }

        public void store(TrackingStatus trackingStatus, String accountId, String script, User user)
        {
            Container c = ContainerManager.getRoot();
            makeWriteable(c);

            storeStringValue(AnalyticsProperty.trackingStatus.toString(), trackingStatus.toString());
            storeStringValue(AnalyticsProperty.accountId.toString(), StringUtils.trimToNull(accountId));
            storeStringValue(AnalyticsProperty.trackingScript.toString(), StringUtils.trimToNull(script));

            save();
            writeAuditLogEvent(c, user);
        }
    }

    @NotNull
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
        return Objects.requireNonNullElse(accountId, DEFAULT_ACCOUNT_ID);
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

        // Adding a null check for container as on rendering the error page, container can be null for a not found page
        if (getTrackingStatus() != TrackingStatus.enabledFullURL && null != container && !container.hasPermission(UserManager.getGuestUser(), ReadPermission.class))
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


    @Override
    public String getTrackingScript(ViewContext context)
    {
        if (!getTrackingStatus().showTrackingScript())
            return "";

        ActionURL url = context.getActionURL();
        if (null == url)
            return "";

        boolean isSecure = context.getActionURL().getScheme().startsWith("https");
        String gaJS = (isSecure ? "https://ssl" : "http://www") + ".google-analytics.com/ga.js";

        StringExpression se = StringExpressionFactory.create(getTrackingStatus().getRawScript());
        return se.eval(PageFlowUtil.map(
                "ACCOUNT_ID", getAccountId(),
                "PAGE_URL", getSanitizedUrl(context),
                "GA_JS", gaJS
                ));
    }

    static public void populateSettingsWithStartupProps()
    {
        PropertyManager.PropertyMap properties = PropertyManager.getWritableProperties(PROP_CATEGORY, true);
        ModuleLoader.getInstance().handleStartupProperties(new StartupPropertyHandler<>(PROP_CATEGORY, AnalyticsProperty.class)
        {
            @Override
            public void handle(Map<AnalyticsProperty, ConfigProperty> startupProperties)
            {
                startupProperties.forEach((ap, cp)->properties.put(ap.toString(), cp.getValue()));
            }
        });
        properties.save();
    }
}
