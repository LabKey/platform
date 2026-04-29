/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
package org.labkey.api.reports.report;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

public abstract class RedirectReport extends AbstractReport
{
    private static final Logger LOG = LogHelper.getLogger(RedirectReport.class, "Reports that send the user to some other URL");

    public static final String REDIRECT_URL = ReportDescriptor.Prop.redirectUrl.name();
    public static final String TARGET = "target";

    public RedirectReport()
    {
    }

    @Override
    public HttpView<?> renderReport(ViewContext context)
    {
        // When rendering in a portal webpart, render the redirect link and thumbnail
        if (context.get(renderParam.reportWebPart.name()) != null)
            return new JspView<>("/org/labkey/api/reports/report/view/redirectReportWebPart.jsp", this);

        redirect(context);

        return null;
    }

    protected abstract void redirect(ViewContext context);

    @Override
    public String getRunReportTarget()
    {
        return getDescriptor().getProperty(TARGET);
    }

    public void setRunReportTarget(@Nullable String target)
    {
        if (!(target == null || "_blank".equals(target)))
            throw new IllegalArgumentException("target must either be '_blank' or null");
        getDescriptor().setProperty(TARGET, target);
    }

    public void setUrl(URLHelper url)
    {
        String redirectUrl = url.getURIString();

        // If possible, save only local URI.
        if (url.getScheme() == null || url.getHost() == null ||
                redirectUrl.startsWith(AppProps.getInstance().getBaseServerUrl()))
        {
            redirectUrl = url.getLocalURIString();
        }

        getDescriptor().setProperty(REDIRECT_URL, redirectUrl);
    }

    // TODO: Returning URLHelper from here would clean up a lot of messy code in these classes
    public @Nullable String getUrl(Container c)
    {
        return getDescriptor().getProperty(REDIRECT_URL);
    }

    protected @Nullable URLHelper getURLHelper(ViewContext ctx)
    {
        String urlString = getUrl(ctx.getContainer());
        if (urlString != null)
        {
            try
            {
                return new URLHelper(urlString);
            }
            catch (URISyntaxException e)
            {
                LOG.warn("Bad URL in report {}: {}", getReportId(), urlString);
            }
        }
        return null;
    }

    public @Nullable URL getURL()
    {
        String urlString = getUrl(null);
        if (urlString == null)
            return null;

        // Create server local URL
        if (urlString.startsWith("/"))
            urlString = AppProps.getInstance().getBaseServerUrl() + urlString;

        try
        {
            return new URL(urlString);
        }
        catch (MalformedURLException mue)
        {
            LOG.warn("Error getting report URL", mue);
            return null;
        }
    }

    /**
     * URL has same hostname as this LabKey server, but is not under the LabKey webapp.
     *
     * /nonContextPath
     * http://host:port/notContextPath
     */
    public boolean isLocalLink()
    {
        String url = getUrl(null);
        if (url == null)
            return false;

        return isLocalLink(url);
    }

    private boolean isLocalLink(String url)
    {
        String contextPath = AppProps.getInstance().getContextPath();
        String baseUrl = AppProps.getInstance().getBaseServerUrl();

        return (url.startsWith("/") && !url.startsWith(contextPath)) ||
               (url.startsWith(baseUrl) && !(url.startsWith(baseUrl + contextPath)));
    }

    /**
     * URL is a LabKey local URL.
     *
     * /contextPath
     * http://host:port/contextPath
     */
    public boolean isInternalLink()
    {
        String url = getUrl(null);
        if (url == null)
            return false;

        return isInternalLink(url);
    }

    public boolean isInternalLink(String url)
    {
        String contextPath = AppProps.getInstance().getContextPath();
        String baseUrl = AppProps.getInstance().getBaseServerUrl();

        return url.startsWith(contextPath) ||
               url.startsWith(baseUrl + contextPath);
    }
}
