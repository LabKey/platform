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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.ContentEncodingHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.BaseApiAction;
import org.labkey.api.miniprofiler.CustomTiming;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.view.BadRequestException;
import org.springframework.web.servlet.mvc.Controller;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: kevink
 * Date: 6/27/12
 */
public class HttpUtil
{
    private static final Logger LOG = LogManager.getLogger(HttpUtil.class);

    private static final Pattern _metaRefreshRegex = Pattern.compile("<meta http-equiv=['\"]refresh['\"] content=['\"].*URL=(.*)['\"]>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

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
     * Get an InputStream from the endpoint following any HTTP redirects.
     * @param uri resource
     * @return An InputStream for the content and the final URI.
     */
    public static Pair<InputStream, URI> get(URI uri) throws IOException
    {
        try (CustomTiming ignored = MiniProfiler.custom("http", "HTTP get " + uri.getHost() + "/" + uri.getPath()))
        {
            HttpClient client = new ContentEncodingHttpClient();
            HttpGet get = new HttpGet(uri);
            BasicHttpContext context = new BasicHttpContext();
            HttpResponse resp = client.execute(get, context);

            // UNDONE: check status code
            //resp.getStatusLine().getStatusCode()

            HttpHost target = (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
            HttpUriRequest req = (HttpUriRequest) context.getAttribute(ExecutionContext.HTTP_REQUEST);

            URI finalURI = URI.create(target.toURI() + req.getURI());
            LOG.debug("HTTP GET '" + uri + "' -> resolved to '" + finalURI + "'");

            HttpEntity entity = resp.getEntity();
            InputStream is = entity.getContent();

            return Pair.of(is, finalURI);
        }
    }

    /**
     * Get HTML content from the endpoint following HTTP redirects, including meta redirects in the html.
     * CONSIDER: Tidying HTML content.
     *
     * @param uri resource
     * @return The content and the final URI.
     */
    public static Pair<String, URI> getHTML(URI uri) throws IOException
    {
        Pair<InputStream, URI> pair = get(uri);
        InputStreamReader reader = new InputStreamReader(pair.first);
        URI finalURI = pair.getValue();

        StringWriter writer = new StringWriter(5000);
        IOUtils.copy(reader, writer);
        String content = writer.toString();

        // find meta redirects
        Matcher metaMatcher = _metaRefreshRegex.matcher(content);
        if (metaMatcher.find())
        {
            String refresh = metaMatcher.group(1);
            URI redirectURI = finalURI.resolve(refresh);
            LOG.info("following meta refresh: " + redirectURI);
            return getHTML(redirectURI);
        }
        else
        {
            return Pair.of(content, finalURI);
        }
    }

    /**
     * Get HTML content from the endpoint following HTTP redirects, including meta redirects in the html.
     * HTML is converted into a DOM Document by JTidy.
     *
     * @param uri resource
     * @return The document and the final URI.
     */
    public static Pair<Document, URI> getXHTML(URI uri) throws IOException
    {
        Pair<InputStream, URI> pair = get(uri);
        InputStreamReader reader = new InputStreamReader(pair.first);
        URI finalURI = pair.getValue();

        StringWriter writer = new StringWriter(5000);
        IOUtils.copy(reader, writer);
        String content = writer.toString();

        ArrayList<String> errors = new ArrayList<>();
        Document document = TidyUtil.convertHtmlToDocument(content, true, errors);
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
            SimpleMetricsService.get().increment(DefaultModule.CORE_MODULE_NAME, "ClientApiRequests", clientLibrary);
    }

    private static @Nullable String getClientLibrary(HttpServletRequest request)
    {
        String userAgent = getUserAgent(request);
        if (null != userAgent)
        {
            if (userAgent.startsWith("Apache-HttpClient/")) // Can't distinguish between SAS, Java, and JDBC right now
                return "Java";
            else if (userAgent.startsWith("Rlabkey"))
                return "R";
            else if (userAgent.startsWith("python-requests/"))
                return "Python";
            else if (userAgent.startsWith("Perl API Client/"))
                return "Perl";
        }

        return null;
    }

    public static @Nullable String getUserAgent(HttpServletRequest request)
    {
        return request.getHeader("User-Agent");
    }
}
