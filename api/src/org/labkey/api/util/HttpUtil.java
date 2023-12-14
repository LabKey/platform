/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.api.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.BaseApiAction;
import org.labkey.api.miniprofiler.CustomTiming;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.BadRequestException;
import org.springframework.web.servlet.mvc.Controller;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

/**
 * User: kevink
 * Date: 6/27/12
 */
public class HttpUtil
{
    private static final Logger LOG = LogHelper.getLogger(HttpUtil.class, "Simple HTTP operations");

    public enum Method
    {
        // HTTP
        // https://tools.ietf.org/html/rfc7231#section-4
        // https://tools.ietf.org/html/rfc5789#section-2
        CONNECT, DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT, TRACE,
        // WebDav
        // https://tools.ietf.org/html/rfc4918#section-9
        COPY, LOCK, MKCOL, MOVE, PROPFIND, PROPPATCH, UNLOCK,
        // LabKey methods that our WebDav implementation understands
        JSON, LASTERROR, MD5SUM, ZIP;

        public static Method valueOf(HttpServletRequest req)
        {
            try
            {
                return Method.valueOf(req.getMethod());
            }
            catch (IllegalArgumentException x)
            {
                throw new BadRequestException("Method Not Allowed", null, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        }
    }

    /**
     * Get content of an endpoint following any HTTP redirects.
     * @param uri resource
     * @return A String for the content and the final URI.
     */
    public static Pair<String, URI> getText(URI uri) throws IOException, URISyntaxException, ParseException
    {
        try (CustomTiming ignored = MiniProfiler.custom("http", "HTTP get " + uri.getHost() + "/" + uri.getPath());
             CloseableHttpClient client = HttpClientBuilder.create().build())
        {
            HttpGet get = new HttpGet(uri);
            BasicHttpContext context = new BasicHttpContext();

            return client.execute(get, context, response -> {
                BasicClassicHttpRequest req = (BasicClassicHttpRequest) context.getAttribute(HttpCoreContext.HTTP_REQUEST);
                final URI finalURI;

                try
                {
                    finalURI = req.getUri();
                }
                catch (URISyntaxException e)
                {
                    throw new HttpException("Bad URI", e);
                }

                LOG.debug("HTTP GET '" + uri + "' -> resolved to '" + finalURI + "'");
                HttpEntity entity = response.getEntity();
                String content = EntityUtils.toString(entity);

                return Pair.of(content, finalURI);
            });
        }
    }

    /**
     * Get HTML content from the endpoint following HTTP redirects, including meta redirects in the html.
     * HTML is converted into a DOM Document by JTidy.
     *
     * @param uri resource
     * @return The document and the final URI.
     */
    public static Pair<Document, URI> getXHTML(URI uri) throws IOException, URISyntaxException, ParseException
    {
        Pair<String, URI> pair = getText(uri);
        String content = pair.first;
        URI finalURI = pair.second;

        ArrayList<String> errors = new ArrayList<>();
        Document document = JSoupUtil.convertHtmlToDocument(content, true, errors);
        if (!errors.isEmpty())
        {
            LOG.warn("Error converting to XHTML document: " + uri + "\n" + errors.get(0));
            return null;
        }

        if (document == null)
        {
            LOG.warn("Error converting to XHTML document: " + uri);
            return null;
        }

        // find meta redirects
        String refresh = metaRefresh(document);
        if (refresh != null)
        {
            URI redirectURI = finalURI.resolve(refresh);
            LOG.info("following meta refresh: " + redirectURI);
            return getXHTML(redirectURI);
        }
        else
        {
            return Pair.of(document, finalURI);
        }
    }

    // I hate the DOM.
    private static String metaRefresh(Document document)
    {
        if (document == null)
            return null;

        Element html = document.getDocumentElement();
        if (html == null || !"html".equalsIgnoreCase(html.getTagName()))
            return null;

        NodeList headNL = html.getElementsByTagName("head");
        for (int headIdx = 0, headLen = headNL.getLength(); headIdx < headLen; headIdx++)
        {
            Element head = (Element)headNL.item(headIdx);
            NodeList metaNL = head.getElementsByTagName("meta");
            for (int metaIdx = 0, metaLen = metaNL.getLength(); metaIdx < metaLen; metaIdx++)
            {
                Element meta = (Element)metaNL.item(metaIdx);
                if ("Refresh".equalsIgnoreCase(meta.getAttribute("http-equiv")))
                {
                    String content = meta.getAttribute("content");
                    int urlIndex = content.indexOf("URL=");
                    if (urlIndex != -1)
                    {
                        String url = content.substring(urlIndex+"URL=".length());
                        if (url.length() > 0)
                            return url;
                    }
                }
            }
        }

        return null;
    }


    /**
     * Check for cases that should not respond with a Redirect, used by getUpgradeMaintenanceRedirect()
     * @return true if this seems like an API request based on the HTTP headers, including Content-Type and User-Agent
     */
    public static boolean isApiLike(@NotNull HttpServletRequest request, Controller action)
    {
        boolean throwUnauthorized = StringUtils.equals("UNAUTHORIZED",request.getHeader("X-ONUNAUTHORIZED"));
        boolean xmlhttp = StringUtils.equals("XMLHttpRequest", request.getHeader("x-requested-with"));
        boolean json = StringUtils.startsWith(request.getHeader("Content-Type"), "application/json");
        boolean apiClass = action instanceof BaseApiAction;
        boolean clientLibrary = getClientLibrary(request) != null;
        return !HttpUtil.isBrowser(request) && (throwUnauthorized || xmlhttp || json || apiClass || clientLibrary);
    }

    /** @return best guess if the request is from a browser vs. a WebDAV client or client API */
    public static boolean isBrowser(@NotNull HttpServletRequest request)
    {
        if ("XMLHttpRequest".equals(request.getHeader("x-requested-with")))
            return true;
        String userAgent = getUserAgent(request);
        if (null == userAgent)
            return false;
        return userAgent.startsWith("Mozilla/") || userAgent.startsWith("Opera/");
    }

    /** @return best guess if the request came from a Chrome browser */
    public static boolean isChrome(@NotNull HttpServletRequest request)
    {
        String userAgent = getUserAgent(request);
        return StringUtils.contains(userAgent, "Chrome/") || StringUtils.contains(userAgent, "Chromium/");
    }

    public static boolean isSafari(@NotNull HttpServletRequest request)
    {
        String userAgent = getUserAgent(request);
        return !isChrome(request) && StringUtils.containsIgnoreCase(userAgent, "safari");
    }

    /** @return best guess if the request came from the OSX integrated WebDAV client */
    public static boolean isMacFinder(@NotNull HttpServletRequest request)
    {
        String userAgent = getUserAgent(request);
        if (null == userAgent)
            return false;
        return userAgent.startsWith("WebDAVFS/") && userAgent.contains("Darwin/");
    }

    /** @return best guess if the request came from the Windows Explorer integrated WebDAV client */
    public static boolean isWindowsExplorer(@NotNull HttpServletRequest request)
    {
        String userAgent = getUserAgent(request);
        if (null == userAgent)
            return false;
        return userAgent.startsWith("Microsoft-WebDAV");
    }

    public static void trackClientApiRequests(HttpServletRequest request)
    {
        String clientLibrary = getClientLibrary(request);
        if (null != clientLibrary)
            SimpleMetricsService.get().increment(DefaultModule.CORE_MODULE_NAME, "clientApiRequests", clientLibrary);
    }

    private static @Nullable String getClientLibrary(HttpServletRequest request)
    {
        String userAgent = getUserAgent(request);
        if (null != userAgent)
        {
            if (userAgent.startsWith("Apache-HttpClient/") /* Note: This could be older SAS or JDBC access */ || userAgent.startsWith("LabKey Java API"))
                return "Java";
            else if (userAgent.startsWith("Rlabkey")|| userAgent.startsWith("LabKey R API"))
                return "R";
            else if (userAgent.startsWith("python-requests/")|| userAgent.startsWith("LabKey Python API"))
                return "Python";
            else if (userAgent.startsWith("LabKey JDBC API"))
                return "JDBC";
            else if (userAgent.startsWith("Perl API Client/") || userAgent.startsWith("LabKey Perl API"))
                return "Perl";
            else if (userAgent.startsWith("LabKey SAS API"))
                return "SAS";
        }

        return null;
    }

    public static @Nullable String getUserAgent(HttpServletRequest request)
    {
        return request.getHeader("User-Agent");
    }
}
