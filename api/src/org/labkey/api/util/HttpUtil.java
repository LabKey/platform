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
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.ContentEncodingHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.log4j.Logger;
import org.labkey.api.miniprofiler.CustomTiming;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
    private static final Logger LOG = Logger.getLogger(HttpUtil.class);

    private static final Pattern _metaRefreshRegex = Pattern.compile("<meta http-equiv=['\"]refresh['\"] content=['\"].*URL=(.*)['\"]>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    /**
     * Get an InputStream from the endpoint following any HTTP redirects.
     * @param uri resource
     * @return An InputStream for the content and the final URI.
     * @throws IOException
     */
    public static Pair<InputStream, URI> get(URI uri) throws IOException
    {
        try (CustomTiming t = MiniProfiler.custom("http", "HTTP get " + uri.getHost() + "/" + uri.getPath()))
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

}
