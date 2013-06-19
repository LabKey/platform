/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * User: migra
 * Date: Mar 7, 2006
 * Time: 3:00:54 PM
 */
public abstract class RedirectReport extends AbstractReport
{
    private static final Logger LOG = Logger.getLogger(RedirectReport.class);

    public static final String REDIRECT_URL = ReportDescriptor.Prop.redirectUrl.name();
    public static final String TARGET = "target";

    public RedirectReport()
    {
    }

    public HttpView renderReport(ViewContext context) throws Exception
    {
        String url = getUrl(context.getContainer());

        // When rendering in a portal webpart, render the redirect link and thumbnail
        if (context.get(renderParam.reportWebPart.name()) != null)
            return new JspView<>("/org/labkey/api/reports/report/view/redirectReportWebPart.jsp", this);

        return HttpView.redirect(url);
    }

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

    public String getUrl(Container c)
    {
        return getDescriptor().getProperty(REDIRECT_URL);
    }

    public URL getURL()
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
     * URL is not a LabKey local URL or a host local URL.
     *
     */
    public boolean isExternalLink()
    {
        String url = getUrl(null);
        if (url == null)
            return false;

        return (url.startsWith("http://") || url.startsWith("https://")) &&
               !isInternalLink(url) && !isLocalLink(url);
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
