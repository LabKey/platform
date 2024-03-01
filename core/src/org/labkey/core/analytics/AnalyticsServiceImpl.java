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
import org.labkey.api.settings.AbstractWriteableSettingsGroup;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.StandardStartupPropertyHandler;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.filters.ContentSecurityPolicyFilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AnalyticsServiceImpl implements AnalyticsService
{
    private static final String SEPARATOR = ",";
    private static final String GOOGLE_TAG_MANAGER_URL = "https://www.googletagmanager.com";
    private static final String ANALYTICS_CSP_KEY = AnalyticsServiceImpl.class.getName();

    public static AnalyticsServiceImpl get()
    {
        return (AnalyticsServiceImpl) AnalyticsService.get();
    }

    public static void register()
    {
        AnalyticsService.setInstance(new AnalyticsServiceImpl());
    }


    private static final String PROP_CATEGORY = "analytics";

    public enum TrackingStatus
    {
        disabled
                {
                    @Override
                    public String getRawScript()
                    {
                        return "";
                    }
                },
        ga4FullUrl
                {
                    @Override
                    public String getRawScript()
                    {
                        return GA4_TRACKING_SCRIPT_TEMPLATE;
                    }
                },
        /** Custom tracking script */
        script
                {
                    @Override
                    public String getRawScript()
                    {
                        return get().getSavedScript();
                    }
                };

        public abstract String getRawScript();
    }

    public enum AnalyticsProperty implements StartupProperty
    {
        trackingStatus("Analytics tracking status. Valid values (comma delimited listing allowed): " + Arrays.toString(TrackingStatus.values())),
        measurementId("Google Analytics 4 Measurement ID"),
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

    public void resetCSP()
    {
        ContentSecurityPolicyFilter.unregisterAllowedConnectionSource(ANALYTICS_CSP_KEY);

        if (getTrackingStatus().contains(TrackingStatus.ga4FullUrl))
        {
            ContentSecurityPolicyFilter.registerAllowedConnectionSource(ANALYTICS_CSP_KEY, "https://*.googletagmanager.com", "https://*.google-analytics.com", "https://*.analytics.google.com");
        }
    }

    private String getProperty(AnalyticsProperty property)
    {
        Map<String, String> properties = PropertyManager.getProperties(PROP_CATEGORY);
        return properties.get(property.toString());
    }

    public void setSettings(Set<TrackingStatus> trackingStatus, String measurementId, String script, User user)
    {
        AnalyticsSettingsGroup g = new AnalyticsSettingsGroup();
        g.store(trackingStatus, measurementId, script, user);
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

        public void store(Set<TrackingStatus> trackingStatus, String measurementId, String script, User user)
        {
            Container c = ContainerManager.getRoot();
            makeWriteable(c);

            String statusString = StringUtils.trimToNull(StringUtils.join(trackingStatus.toArray(), SEPARATOR));
            storeStringValue(AnalyticsProperty.trackingStatus.toString(), statusString);
            storeStringValue(AnalyticsProperty.measurementId.toString(), StringUtils.trimToNull(measurementId));
            storeStringValue(AnalyticsProperty.trackingScript.toString(), StringUtils.trimToNull(script));

            get().resetCSP();

            save();
            writeAuditLogEvent(c, user);
        }
    }

    @NotNull
    public Set<TrackingStatus> getTrackingStatus()
    {
        String allStatuses = getProperty(AnalyticsProperty.trackingStatus);
        if (allStatuses == null)
        {
            return Collections.emptySet();
        }

        Set<TrackingStatus> result = new HashSet<>();

        for (String status : allStatuses.split(SEPARATOR))
        {
            try
            {
                result.add(TrackingStatus.valueOf(status));
            }
            catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    public String getMeasurementId()
    {
        return getProperty(AnalyticsProperty.measurementId);
    }

    /**
     * Returns the page url that we will report to Analytics.
     * For privacy reasons, we strip off the URL parameters if the container does not allow guest access.
     * We append the serverGUID parameter to the URL.
     */
    private String getSanitizedUrl(ViewContext context)
    {
        ActionURL actionUrl = context.cloneActionURL();

        // Add the server GUID to the URL.  Remove the "-" because they are problematic for Google Analytics regular
        // expressions.
        String guid = AppProps.getInstance().getServerGUID();
        guid = StringUtils.replace(guid, "-", ".");
        actionUrl.addParameter("serverGUID", guid);
        return actionUrl.toString();
    }


    private static final String GA4_TRACKING_SCRIPT_TEMPLATE =
            """
                    <!-- Global site tag (gtag.js) - Google Analytics -->
                    <script async src="${GA4_JS:htmlEncode}" nonce="${SCRIPT_NONCE:htmlEncode}"></script>
                    <script nonce="${SCRIPT_NONCE:htmlEncode}">
                      window.dataLayer = window.dataLayer || [];
                      function gtag(){dataLayer.push(arguments);}
                      gtag('js', new Date());
                      gtag('config', ${MEASUREMENT_ID:jsString}, { 'send_page_view': ${SEND_PAGE_VIEW} });
                    </script>
                    """;


    public String getSavedScript()
    {
        return getProperty(AnalyticsProperty.trackingScript);
    }


    @Override
    public String getTrackingScript(ViewContext context)
    {
        if (getTrackingStatus().isEmpty())
            return "";

        ActionURL url = context.getActionURL();
        if (null == url)
            return "";

        String ga4JS = GOOGLE_TAG_MANAGER_URL + "/gtag/js?id=" + getMeasurementId();
        Boolean sendPageView = true;

        if (context.isAppView())
            sendPageView = false;

        StringBuilder sb = new StringBuilder();
        for (TrackingStatus trackingStatus : getTrackingStatus())
        {
            StringExpression se = StringExpressionFactory.create(trackingStatus.getRawScript());
            sb.append(se.eval(PageFlowUtil.map(
                    "PAGE_URL", getSanitizedUrl(context),
                    "GA4_JS", ga4JS,
                    "MEASUREMENT_ID", getMeasurementId(),
                    "SEND_PAGE_VIEW", sendPageView,
                    "SCRIPT_NONCE", HttpView.currentPageConfig().getScriptNonce())));
        }
        return sb.toString();
    }

    public static void populateSettingsWithStartupProps()
    {
        PropertyManager.PropertyMap properties = PropertyManager.getWritableProperties(PROP_CATEGORY, true);
        ModuleLoader.getInstance().handleStartupProperties(new StandardStartupPropertyHandler<>(PROP_CATEGORY, AnalyticsProperty.class)
        {
            @Override
            public void handle(Map<AnalyticsProperty, StartupPropertyEntry> startupProperties)
            {
                startupProperties.forEach((ap, cp)->properties.put(ap.toString(), cp.getValue()));
            }
        });
        properties.save();
    }
}
