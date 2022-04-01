/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.encoders.EncoderUtil;
import org.jfree.chart.encoders.ImageFormat;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.action.UrlProviderOverrideHandler;
import org.labkey.api.action.UrlProviderService;
import org.labkey.api.admin.CoreUrls;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.announcements.api.Tour;
import org.labkey.api.announcements.api.TourService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.Project;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.RequestInfo;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.notification.NotificationMenuView;
import org.labkey.api.query.QueryParam;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.SecurityLogger;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.CustomLabelProvider;
import org.labkey.api.settings.CustomLabelService;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.settings.ResourceURL;
import org.labkey.api.settings.TemplateResourceHandler;
import org.labkey.api.stats.AnalyticsProvider;
import org.labkey.api.stats.AnalyticsProviderRegistry;
import org.labkey.api.util.Button.ButtonBuilder;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BadRequestException;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.ContainerUser;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.web.util.WebUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.labkey.api.util.DOM.Attribute.valign;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.cl;


public class PageFlowUtil
{
    private enum TransformFormat
    {
        html,
        xml
    }

    private static final Logger _log = LogManager.getLogger(PageFlowUtil.class);
    private static final String _newline = System.getProperty("line.separator");

    private static final Pattern urlPatternStart = Pattern.compile("((http|https|ftp|mailto)://\\S+).*");

    /**
     * Default parser class.
     */
    private static final String DEFAULT_PARSER_NAME = "org.apache.xerces.parsers.SAXParser";

    private static final String NONPRINTING_ALTCHAR = "~";

    public static final String SESSION_PAGE_ADMIN_MODE = "session-page-admin-mode";

    public static boolean useExperimentalCoreUI()
    {
        return true;
    }

    /**
     * Whether or not the user has entered the "Page Admin Mode". Used for showing/hiding controls like adding/moving/removing webparts and tabs.
     * @return boolean
     */
    public static boolean isPageAdminMode(ViewContext context)
    {
        if (context == null || context.getContainer() == null || !context.hasPermission("PageAdminMode", AdminPermission.class))
            return false;

        HttpSession session = context.getSession();
        return session != null && session.getAttribute(SESSION_PAGE_ADMIN_MODE) != null;
    }

    static public String filterXML(String s)
    {
        return filter(s,false,false);
    }


    /** HTML encode a string */
    static public String filter(CharSequence s, boolean encodeSpace, boolean encodeLinks)
    {
        if (null == s || 0 == s.length())
            return "";

        int len = s.length();
        StringBuilder sb = new StringBuilder(2 * len);
        boolean newline = false;

        for (int i = 0; i < len; ++i)
        {
            char c = s.charAt(i);

            if (!Character.isWhitespace(c))
                newline = false;
            else if ('\r' == c || '\n' == c)
                newline = true;

            switch (c)
            {
                case '&':
                    sb.append("&amp;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#039;");    // works for xml and html
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '\n':
                    if (encodeSpace)
                        sb.append("<br>\n");
                    else
                        sb.append(c);
                    break;
                case '\r':
                    break;
                case '\t':
                    if (!encodeSpace)
                        sb.append(c);
                    else if (newline)
                        sb.append("&nbsp;&nbsp;&nbsp;&nbsp;");
                    else
                        sb.append("&nbsp; &nbsp; ");
                    break;
                case ' ':
                    if (encodeSpace && newline)
                        sb.append("&nbsp;");
                    else
                        sb.append(' ');
                    break;
                case 'f':
                case 'h':
                case 'm':
                    if (encodeLinks)
                    {
                        CharSequence sub = s.subSequence(i, s.length());
                        if (StringUtilsLabKey.startsWithURL(sub))
                        {
                            Matcher m = urlPatternStart.matcher(sub);
                            if (m.find())
                            {
                                String href = m.group(1);
                                if (href.endsWith("."))
                                    href = href.substring(0, href.length() - 1);
                                // for html/xml careful of " and "> and "/>
                                int lastQuote = Math.max(href.lastIndexOf("\""), href.lastIndexOf("'"));
                                if (lastQuote >= href.length()-3)
                                    href = href.substring(0, lastQuote);
                                String filterHref = filter(href, false, false);
                                sb.append("<a href=\"").append(filterHref).append("\">").append(filterHref).append("</a>");
                                i += href.length() - 1;
                                break;
                            }
                        }
                    }
                    sb.append(c);
                    break;
                default:
                    if (c >= ' ')
                        sb.append(c);
                    else
                    {
                        if (c == 0x08) // backspace (e.g. xtandem output)
                            break;
                        sb.append(NONPRINTING_ALTCHAR);
                    }
                    break;
            }
        }

        return sb.toString();
    }

    /** HTML encode an object (using toString()) */
    public static String filter(Object o)
    {
        return filter(o == null ? null : o.toString());
    }

    /**
     * HTML encode a string
     */
    public static String filter(CharSequence s)
    {
        return filter(s, false, false);
    }


    /** HTML encode a string */
    static public String filter(CharSequence s, boolean translateWhiteSpace)
    {
        return filter(s, translateWhiteSpace, false);
    }

    static public String filterControlChars(Object o)
    {
        String s = o == null ? null : o.toString();
        if (null == s || 0 == s.length())
            return "";

        int len = s.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; ++i)
        {
            char c = s.charAt(i);
            sb.append(c >= ' ' || c == '\n' || c == '\r' || c == '\t' ? c : NONPRINTING_ALTCHAR);
        }

        return sb.toString();
    }

    /**
     * put quotes around a JavaScript string, and HTML encode that.
     */
    public static String filterQuote(Object value)
    {
        if (value == null)
            return "null";
        String ret = PageFlowUtil.filter("\"" + PageFlowUtil.groovyString(value.toString()) + "\"");
        ret = ret.replace("&#039;", "\\&#039;");
        return ret;
    }

    /**
     * Creates a JavaScript string literal of an HTML escaped value.
     *
     * Ext, for example, will use the 'id' config parameter as an attribute value in an XTemplate.
     * The string value is inserted directly into the dom and so should be HTML encoded.
     *
     * @param s String to escaped
     * @return The JavaScript string literal of the HTML escaped value.
     */
    // For example, given the string: "\"'>--></script><script type=\"text/javascript\">alert(\"8(\")</script>"
    // the method will return: "'&quot;&#039;&gt;--&gt;&lt;/script&gt;&lt;script type=&quot;text/javascript&quot;&gt;alert(&quot;8(&quot;)&lt;/script&gt;'"
    public static String qh(String s)
    {
        return PageFlowUtil.jsString(PageFlowUtil.filter(s));
    }

    public static String jsString(CharSequence cs)
    {
        if (cs == null)
            return "''";

        return jsString(cs.toString());
    }

    @Deprecated // usages look wrong to me -- they should just use q()?
    public static HtmlString jsString(HtmlString hs)
    {
        return HtmlString.unsafe(jsString(hs.toString()));
    }

    public static String jsString(ActionURL url)
    {
        return jsString(url.getLocalURIString());
    }

    public static String jsString(String s)
    {
        if (s == null)
            return "''";

        StringBuilder js = new StringBuilder(s.length() + 10);
        js.append("'");
        int len = s.length();
        for (int i = 0 ; i<len ; i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '\\':
                    js.append("\\\\");
                    break;
                case '\n':
                    js.append("\\n");
                    break;
                case '\r':
                    js.append("\\r");
                    break;
                case '<':
                    js.append("\\x3C");
                    break;
                case '>':
                    js.append("\\x3E");
                    break;
                case '\'':
                    js.append("\\'");
                    break;
                case '\"':
                    js.append("\\\"");
                    break;
                default:
                    js.append(c);
                    break;
            }
        }
        js.append("'");
        return js.toString();
    }

    //used to output strings from Java in Groovy script.
    private static String groovyString(String s)
    {
        //replace single backslash
        s = s.replaceAll("\\\\", "\\\\\\\\");
        //replace double quote
        s = s.replaceAll("\"", "\\\\\"");
        return s;
    }

    private static final List<Pair<String, String>> _emptyPairList = Collections.emptyList();

    public static List<Pair<String, String>> fromQueryString(String query)
    {
        return fromQueryString(query, StringUtilsLabKey.DEFAULT_CHARSET.name());
    }

    public static List<Pair<String, String>> fromQueryString(String query, String encoding)
    {
        if (null == query || 0 == query.length())
            return _emptyPairList;

        if (null == encoding)
            encoding = StringUtilsLabKey.DEFAULT_CHARSET.name();

        List<Pair<String, String>> parameters = new ArrayList<>();
        if (query.startsWith("?"))
            query = query.substring(1);
        String[] terms = query.split("&");

        try
        {
            for (String term : terms)
            {
                if (0 == term.length())
                    continue;

                // NOTE: faster to decode entire term all at once, but key may contain '=' char
                int ind = term.indexOf('=');
                String key;
                String val;
                if (ind == -1)
                {
                    key = URLDecoder.decode(term.trim(), encoding);
                    val = "";
                }
                else
                {
                    key = URLDecoder.decode(term.substring(0, ind).trim(), encoding);
                    val = URLDecoder.decode(term.substring(ind + 1).trim(), encoding);
                }

                parameters.add(new Pair<>(key, val));
            }
        }
        catch (UnsupportedEncodingException x)
        {
            throw new IllegalArgumentException(encoding, x);
        }

        return parameters;
    }


    public static Map<String, String> mapFromQueryString(String queryString)
    {
        Map<String, String> m = new LinkedHashMap<>();
        for (Pair<String, String> p : fromQueryString(queryString))
            m.put(p.getKey(), p.getValue());

        return m;
    }


    public static String toQueryString(Collection<? extends Map.Entry<?,?>> c)
    {
        return toQueryString(c, false);
    }


    public static String toQueryString(Collection<? extends Map.Entry<?,?>> c, boolean allowSubstSyntax)
    {
        if (null == c || c.isEmpty())
            return null;
        String strAnd = "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?,?> entry : c)
        {
            sb.append(strAnd);
            Object key = entry.getKey();
            if (null == key)
                continue;
            Object v = entry.getValue();
            String value = v == null ? "" : String.valueOf(v);
            sb.append(encode(String.valueOf(key)));
            sb.append('=');
            if (allowSubstSyntax && value.length()>3 && value.startsWith("${") && value.endsWith("}"))
                sb.append(value);
            else
                sb.append(encode(value));
            strAnd = "&";
        }
        return sb.toString();
    }


    public static String toQueryString(PropertyValues pvs)
    {
        if (null == pvs || pvs.isEmpty())
            return null;
        String strAnd = "";
        StringBuilder sb = new StringBuilder();
        for (PropertyValue entry : pvs.getPropertyValues())
        {
            Object key = entry.getName();
            if (null == key)
                continue;
            String encKey = encode(String.valueOf(key));
            Object v = entry.getValue();
            if (v == null || v instanceof String || !v.getClass().isArray())
            {
                sb.append(strAnd);
                sb.append(encKey);
                sb.append('=');
                sb.append(encode(v==null?"":String.valueOf(v)));
                strAnd = "&";
            }
            else
            {
                Object[] a = (Object[])v;
                for (Object o : a)
                {
                    sb.append(strAnd);
                    sb.append(encKey);
                    sb.append('=');
                    sb.append(encode(o==null?"":String.valueOf(o)));
                    strAnd = "&";
                }
            }
        }
        return sb.toString();
    }


    // Identifies LabKey-specific parameters that shouldn't be persisted or exported, #30532
    public static boolean isInternalParameter(@NotNull String parameterName)
    {
        return parameterName.startsWith("X-LABKEY-");
    }


    /**
        Return a map of <T, T>. Note: iteration order of this map is unpredictable.
     */
    public static <T> Map<T, T> map(T... args)
    {
        HashMap<T, T> m = new HashMap<>();
        for (int i = 0; i < args.length; i += 2)
            m.put(args[i], args[i + 1]);
        return m;
    }


    /**
        Return a case-insensitive map of Objects. Note: iteration order of this map is unpredictable.
     */
    public static Map<String, Object> mapInsensitive(Object... args)
    {
        Map<String,Object> m = new CaseInsensitiveHashMap<>();
        for (int i = 0; i < args.length; i += 2)
            m.put(String.valueOf(args[i]), args[i + 1]);
        return m;
    }


    /**
     * Return a set of T that iterates in the order of the provided arguments.
     */
    public static <T> Set<T> set(T... args)
    {
        HashSet<T> s = new LinkedHashSet<>();

        if (null != args)
            s.addAll(Arrays.asList(args));

        return s;
    }


    /**
     * URL Encode string.
     * NOTE! this should be used on parts of a url, not an entire url
     *
     * Like JavaScript encodeURIComponent()
     */
    public static String encode(String s)
    {
        if (null == s)
            return "";
        String enc = URLEncoder.encode(s, StringUtilsLabKey.DEFAULT_CHARSET);
        return StringUtils.replace(enc, "+", "%20");
    }

    public static String encodeURIComponent(String s)
    {
        return encode(s);
    }

    /**
     * Like JavaScript encodeURI()
     */
    static final BitSet dontEncode = new BitSet(256);
    static
    {   String except = ",/?:@&=+$#_-.*";
        for (int i=0 ; i<except.length() ; i++)
            dontEncode.set(except.charAt(i));
        for (int i='a' ; i<='z' ; i++)
            dontEncode.set(i);
        for (int i='A' ; i<='Z' ; i++)
            dontEncode.set(i);
        for (int i='0' ; i<='9' ; i++)
            dontEncode.set(i);
    }

    public static String encodeURI(String s)
    {
        try
        {
            StringBuilder sb = new StringBuilder();
            int len=s.length(),start=0,end=0;
            while (start < s.length())
            {
                for (end=start; end < len && dontEncode.get(s.charAt(end)) ; end++)
                    { /* */ }
                sb.append(s,start,end);
                if (end < len)
                {
                    String ch = s.substring(end,end+1);
                    if (ch.charAt(0)==' ')
                        sb.append("%20");
                    else
                        sb.append(URLEncoder.encode(ch, StringUtilsLabKey.DEFAULT_CHARSET.name()));
                }
                start = end+1;
            }
            return sb.toString();
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }


    /**
     * URL decode a string.
     */
    public static String decode(String s)
    {
        try
        {
            return null==s ? "" : URLDecoder.decode(s, StringUtilsLabKey.DEFAULT_CHARSET.name());
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }

    /**
     * Encode path URL parts, preserving path separators.
     * @param path The raw path to encode.
     * @return An encoded version of the path parameter.
     */
    public static String encodePath(String path)
    {
        String[] parts = path.split("/");
        String ret = "";
        for (int i = 0; i < parts.length; i++)
        {
            if (i > 0)
                ret += "/";
            ret += encode(parts[i]);
        }
        return ret;
    }


    @Nullable
    public static Cookie getCookie(HttpServletRequest request, String cookieName)
    {
        for (Cookie cookie : request.getCookies())
        {
            if (cookieName.equals(cookie.getName()))
                return cookie;
        }
        return null;
    }


    // Cookie helper function -- loops through Cookie array and returns matching value (or defaultValue if not found)
    public static String getCookieValue(Cookie[] cookies, String cookieName, @Nullable String defaultValue)
    {
        if (null != cookies)
            for (Cookie cookie : cookies)
            {
                if (cookieName.equals(cookie.getName()))
                    return (cookie.getValue());
            }
        return (defaultValue);
    }


    /**
     * boolean controlling whether or not we compress JSON-serialized objects when we render them in HTML forms.
     */
    static private final boolean COMPRESS_OBJECT_STREAMS = true;

    static public HtmlString encodeObject(Object o) throws IOException
    {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        OutputStream osCompressed;
        if (COMPRESS_OBJECT_STREAMS)
        {
            osCompressed = new DeflaterOutputStream(byteArrayOutputStream);
        }
        else
        {
            osCompressed = byteArrayOutputStream;
        }
        try (OutputStream os=osCompressed; Writer w = new OutputStreamWriter(os, StringUtilsLabKey.DEFAULT_CHARSET))
        {
            Class cls = o.getClass();
            final JSONObject json;
            if (o instanceof Map)
            {
                json = new JSONObject((Map)o);
            }
            else
            {
                ObjectFactory f = ObjectFactory.Registry.getFactory(cls);
                json = new JSONObject();
                f.toMap(o, json);
            }
            w.write(json.toString());
        }
        osCompressed.close();
        return HtmlString.unsafe(new String(Base64.encodeBase64(byteArrayOutputStream.toByteArray(), true), StringUtilsLabKey.DEFAULT_CHARSET));
    }

    public static <T> T decodeObject(Class<T> cls, String encoded) throws IOException
    {
        assert Object.class != cls;

        encoded = StringUtils.trimToNull(encoded);
        if (null == encoded)
            return null;

        byte[] buf = Base64.decodeBase64(encoded.getBytes(StringUtilsLabKey.DEFAULT_CHARSET));
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(buf);
        InputStream isCompressed;

        if (COMPRESS_OBJECT_STREAMS)
        {
            isCompressed = new InflaterInputStream(byteArrayInputStream);
        }
        else
        {
            isCompressed = byteArrayInputStream;
        }
        try (InputStream is=isCompressed; Reader r = new InputStreamReader(is, StringUtilsLabKey.DEFAULT_CHARSET))
        {
            JSONObject json = new JSONObject(IOUtils.toString(r));

            if (cls == Map.class || cls == HashMap.class)
                return (T)json;

            ObjectFactory f = ObjectFactory.Registry.getFactory(cls);
            Object o = f.fromMap(json);
            if (cls.isAssignableFrom(o.getClass()))
                return (T)o;
            throw new ClassCastException("Could not create class: " + cls.getName());
        }
        catch (IllegalArgumentException x)
        {
            throw new IOException(x);
        }
        catch (ZipException x)
        {
            throw new BadRequestException("Invalid .oldValues parameter value", BadRequestException.HowBad.Malicious);
        }
    }

    public static int[] toInts(Collection<String> strings)
    {
        return toInts(strings.toArray(new String[strings.size()]));
    }


    public static int[] toInts(String[] strings)
    {
        int[] result = new int[strings.length];
        for (int i = 0; i < strings.length; i++)
        {
            result[i] = Integer.parseInt(strings[i]);
        }
        return result;
    }


    private static MimeMap _mimeMap;

    public static String getContentTypeFor(@NotNull String filename)
    {
        // Lazy initialization
        if (_mimeMap == null)
        {
            _mimeMap = new MimeMap();
        }

        String contentType = _mimeMap.getContentTypeFor(filename);
        if (null == contentType)
        {
            contentType = "application/octet-stream";
        }
        return contentType;
    }

    public static String getContentTypeFor(File file)
    {
        MediaType type = getMediaTypeFor(file);
        if (type == null || type.toString() == null)
        {
            return "application/octet-stream";
        }
        return type.toString();
    }

    /**
     * Uses Tika to examine the contents of a file to detect the content type
     * of a file.
     * @return MediaType object
     */
    public static MediaType getMediaTypeFor(File file)
    {
        return getMediaTypeFor(file.toPath());
    }

    public static MediaType getMediaTypeFor(Path file)
    {
        try
        {
            DefaultDetector detector = new DefaultDetector();
            Metadata metaData = new Metadata();
            String filename = file.getFileName().toString();

            // use the metadata to hint at the type for a faster lookup
            metaData.add(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            metaData.add(Metadata.CONTENT_TYPE, PageFlowUtil.getContentTypeFor(filename));

            return detector.detect(TikaInputStream.get(file), metaData);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets up the response to stream back a file. The content type is inferred by the filename extension.
     */
    public static void prepareResponseForFile(HttpServletResponse response, Map<String, String> responseHeaders, @NotNull String filename, boolean asAttachment)
    {
        _prepareResponseForFile(response, responseHeaders, getContentTypeFor(filename), filename, asAttachment);
    }

    /**
     * Sets up the response to stream back a file. The content type is detected by the file contents.
     */
    @Deprecated //Prefer the Path version
    public static void prepareResponseForFile(HttpServletResponse response, Map<String, String> responseHeaders, File file, boolean asAttachment)
    {
        prepareResponseForFile(response, responseHeaders, file.toPath(), asAttachment);
    }

    public static void prepareResponseForFile(HttpServletResponse response, Map<String, String> responseHeaders, Path file, boolean asAttachment)
    {
        if (file == null)
            throw new IllegalArgumentException("file cannot be null");

        String fileName = file.getFileName().toString();
        MediaType mediaType = getMediaTypeFor(file);
        String contentType = getContentTypeFor(fileName);

        if (MediaType.TEXT_PLAIN.equals(mediaType) && startsWith(contentType,"text/"))
        {
            // don't do anything, extension is probably fine
        }
        else if (mediaType != null && mediaType.compareTo(MediaType.parse(contentType)) != 0)
        {
            try
            {
                MimeType mimeType = MimeTypes.getDefaultMimeTypes().forName(mediaType.toString());
                contentType = mediaType.toString();

                // replace the extension of the filename with one that matches the content type
                String ext = FileUtil.getExtension(fileName);
                if (ext != null && mimeType != null)
                {
                    fileName = fileName.substring(0, fileName.length() - (ext.length() + 1)) + mimeType.getExtension();
                }
            }
            catch (MimeTypeException e)
            {
                throw new RuntimeException(e);
            }
        }
        _prepareResponseForFile(response, responseHeaders, contentType, fileName, asAttachment);
    }

    private static void _prepareResponseForFile(HttpServletResponse response, Map<String, String> responseHeaders, String fileContentType, String fileName, boolean asAttachment)
    {
        String contentType = responseHeaders.get("Content-Type");
        if (null == contentType && null != fileContentType)
            contentType = fileContentType;
        response.reset();
        response.setContentType(contentType);
        if (asAttachment)
        {
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        }
        else
        {
            response.setHeader("Content-Disposition", "filename=\"" + fileName + "\"");
        }
        for (Map.Entry<String, String> entry : responseHeaders.entrySet())
            response.setHeader(entry.getKey(), entry.getValue());
    }

    /**
     * Read the file and stream it to the browser through the response.
     *
     * @param detectContentType If set to true, then the content type is detected, else it is inferred from the extension
     * of the file name.
     * @throws IOException
     */
    public static void streamFile(HttpServletResponse response, File file, boolean asAttachment, boolean detectContentType) throws IOException
    {
        if (detectContentType)
            streamFile(response, Collections.emptyMap(), file, asAttachment);
        else
        {
            try
            {
                streamFile(response, Collections.emptyMap(), file.getName(), new FileInputStream(file), asAttachment);
            }
            catch (FileNotFoundException e)
            {
                throw new NotFoundException(file.getName());
            }
        }
    }

    public static void streamFile(HttpServletResponse response, Path file, boolean asAttachment, boolean detectContentType) throws IOException
    {
        String filename = file.getFileName().toString();
        if (detectContentType)
            streamFile(response, Collections.emptyMap(), file, asAttachment);
        else
        {
            try (InputStream is = Files.newInputStream(file))
            {
                streamFile(response, Collections.emptyMap(), filename, is, asAttachment);
            }
            catch (FileNotFoundException e)
            {
                throw new NotFoundException(filename);
            }
        }
    }

    @Deprecated // Prefer the Path version
    public static void streamFile(HttpServletResponse response, File file, boolean asAttachment) throws IOException
    {
        streamFile(response, file.toPath(), asAttachment, false);
    }

    public static void streamFile(HttpServletResponse response, Path file, boolean asAttachment) throws IOException
    {
        streamFile(response, file, asAttachment, false);
    }


    /**
     * Read the file and stream it to the browser through the response. The content type of the file is detected
     * from the contents of the file.
     */
    @Deprecated //Prefer the Path version
    public static void streamFile(@NotNull HttpServletResponse response, @NotNull Map<String, String> responseHeaders, File file, boolean asAttachment) throws IOException
    {
        streamFile(response,responseHeaders, file.toPath(), asAttachment);
    }

    public static void streamFile(@NotNull HttpServletResponse response, @NotNull Map<String, String> responseHeaders, Path file, boolean asAttachment) throws IOException
    {
        try (InputStream is = Files.newInputStream(file))
        {
            prepareResponseForFile(response, responseHeaders, file, asAttachment);
            ServletOutputStream out = response.getOutputStream();
            FileUtil.copyData(is, out);
        }
    }

    /**
     * Read the file and stream it to the browser through the response. The content type of the file is detected
     * from the file name extension.
     */
    public static void streamFile(@NotNull HttpServletResponse response, @NotNull Map<String, String> responseHeaders, @NotNull String name, InputStream is, boolean asAttachment) throws IOException
    {
        try
        {
            prepareResponseForFile(response, responseHeaders, name, asAttachment);
            ServletOutputStream out = response.getOutputStream();
            FileUtil.copyData(is, out);
        }
        finally
        {
            IOUtils.closeQuietly(is);
        }
    }


    public static void streamFileBytes(@NotNull HttpServletResponse response, @NotNull String filename, @NotNull byte[] bytes, boolean asAttachment) throws IOException
    {
        prepareResponseForFile(response, Collections.emptyMap(), filename, asAttachment);
        response.getOutputStream().write(bytes);
    }


    public static void streamLogFile(HttpServletResponse response, long startingOffset, File logFile) throws Exception
    {
        if (logFile.exists())
        {
            try (FileInputStream fIn = new FileInputStream(logFile))
            {
                //noinspection ResultOfMethodCallIgnored
                fIn.skip(startingOffset);
                OutputStream out = response.getOutputStream();
                response.setContentType("text/plain");
                FileUtil.copyData(fIn, out);
            }
        }
    }


    public static class Content
    {
        public Content(String s)
        {
            this(s, null, System.currentTimeMillis());
        }

        public Content(String s, @Nullable byte[] e, long m)
        {
            content = s;
            encoded = e;
            if (null == e && null != s)
                encoded = s.getBytes(StringUtilsLabKey.DEFAULT_CHARSET);
            modified = m;
        }

        public Content copy()
        {
            Content ret = new Content(content, encoded, modified);
            ret.dependencies = dependencies;
            ret.compressed = compressed;
            return ret;
        }

        public Object dependencies;
        public String content;
        public byte[] encoded;
        public byte[] compressed;
        public long modified;

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Content content1 = (Content) o;

            if (modified != content1.modified) return false;
            if (content != null ? !content.equals(content1.content) : content1.content != null) return false;
            if (dependencies != null ? !dependencies.equals(content1.dependencies) : content1.dependencies != null)
                return false;
            if (!Arrays.equals(encoded, content1.encoded)) return false;
            //if (!Arrays.equals(compressed, content1.compressed)) return false;
            return true;
        }

        @Override
        public int hashCode()
        {
            int result = dependencies != null ? dependencies.hashCode() : 0;
            result = 31 * result + (content != null ? content.hashCode() : 0);
            result = 31 * result + (encoded != null ? Arrays.hashCode(encoded) : 0);
            //result = 31 * result + (compressed != null ? Arrays.hashCode(compressed) : 0);
            result = 31 * result + (int) (modified ^ (modified >>> 32));
            return result;
        }
    }


    // Marker class for caching absence of content -- can't use a single marker object because of dependency handling.
    public static class NoContent extends Content
    {
        public NoContent(Object dependsOn)
        {
            super(null);
            dependencies = dependsOn;
        }
    }

    public static void sendContent(HttpServletRequest request, HttpServletResponse response, Content content, String contentType) throws IOException
    {
        // TODO content.getContentType()
        response.setContentType(contentType);
        ResponseHelper.setPrivate(response, 35);
        response.setDateHeader("Last-Modified", content.modified);

        if (!checkIfModifiedSince(request, content.modified))
        {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        if (StringUtils.trimToEmpty(request.getHeader("Accept-Encoding")).contains("gzip") && null != content.compressed)
        {
            response.setHeader("Content-Encoding", "gzip");
            response.getOutputStream().write(content.compressed);
        }
        else
        {
            response.getOutputStream().write(content.encoded);
        }
    }


    /**
     * TODO: This code needs to be shared with DavController.checkModifiedSince
     *
     * CONSIDER: implementing these actions directly via WebdavResolver using something
     * like the SymbolicLink class.
     *
     * ref 10499
     */
    private static boolean checkIfModifiedSince(HttpServletRequest request, long lastModified)
    {
        try
        {
            long headerValue = request.getDateHeader("If-Modified-Since");
            if (headerValue != -1)
            {
                // If an If-None-Match header has been specified, if modified since
                // is ignored.
                if ((request.getHeader("If-None-Match") == null))
                {
                    if (lastModified < headerValue + 1000)
                    {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    return false;
                    }
                }
            }
        }
        catch (IllegalArgumentException illegalArgument)
        {
            return true;
        }
        return true;
    }


    // Fetch the contents of a text file, and return it in a String.
    public static String getFileContentsAsString(File aFile)
    {
        try
        {
            return getReaderContentsAsString(Readers.getReader(aFile));
        }
        catch (FileNotFoundException e)
        {
            StringBuilder contents = new StringBuilder();

            _log.error(e);
            contents.append("File not found");
            contents.append(_newline);

            return contents.toString();
        }
    }


    /** Fetch the contents of an InputStream using the standard LabKey charset (currently UTF-8) and return in a String. Closes the InputStream after consuming it */
    public static String getStreamContentsAsString(InputStream is)
    {
		return getReaderContentsAsString(Readers.getReader(is));
    }


    /** Fetch the contents of a BufferedReader and return in a String. Closes the reader after consuming it */
	public static String getReaderContentsAsString(BufferedReader reader)
	{
		StringBuilder contents = new StringBuilder();
		try (Reader ignored = reader)
		{
            String line;

			while ((line = reader.readLine()) != null)
			{
				contents.append(line);
				contents.append(_newline);
			}
		}
		catch (IOException e)
		{
			_log.error("getStreamContentsAsString", e);
		}
		return contents.toString();
	}


    // Fetch the contents of an input stream, and return it in a list, skipping comment lines is skipComments == true.
    // Assumes stream is encoded using the LabKey standard character set
    public static List<String> getStreamContentsAsList(InputStream is, boolean skipComments) throws IOException
    {
        List<String> contents = new ArrayList<>();

        try (BufferedReader input = Readers.getReader(is))
        {
            String line;
            while ((line = input.readLine()) != null)
                if (!skipComments || !line.startsWith("#"))
                    contents.add(line);
        }

        return contents;
    }

    public static boolean empty(String str)
    {
        return null == str || str.trim().length() == 0;
    }


    private static final Pattern patternPhone = Pattern.compile("((1[\\D]?)?\\(?(\\d\\d\\d)\\)?[\\D]*)?(\\d\\d\\d)[\\D]?(\\d\\d\\d\\d)");

    public static String formatPhoneNo(String s)
    {
        s = StringUtils.trimToNull(s);
        if (null == s)
            return "";
        Matcher m = patternPhone.matcher(s);
        if (!m.find())
            return s;
        //for (int i=0 ; i<=m.groupCount() ; i++) System.err.println(i + " " + m.group(i));
        StringBuffer sb = new StringBuffer(20);
        m.appendReplacement(sb, "");
        String area = m.group(3);
        String exch = m.group(4);
        String num = m.group(5);
        if (null != area && 0 < area.length())
            sb.append("(").append(area).append(") ");
        sb.append(exch).append("-").append(num);
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     *  Returns an onClick handler that posts to the specified href, providing a CSRF token. If confirmMessage is not null,
     *  displays a confirmation dialog and requires an "OK" before posting. Used by NavTree, LinkBuilder, and ButtonBuilder,
     *  this shouldn't be called directly by other code paths.
     */
    public static String postOnClickJavaScript(String href, @Nullable String confirmMessage)
    {
        return null == confirmMessage ? "LABKEY.Utils.postToAction(" + jsString(href) + ");" : "LABKEY.Utils.confirmAndPost(" + jsString(confirmMessage) + ", " + jsString(href) + ");";
    }

    public static ButtonBuilder button(String text)
    {
        return new ButtonBuilder(text);
    }

    public static LinkBuilder link(String text)
    {
        return new LinkBuilder(text);
    }

    public static LinkBuilder iconLink(String iconCls, @Nullable String tooltip)
    {
        return new LinkBuilder().iconCls(iconCls).tooltip(tooltip);
    }

    private static final String ARBITRARY_LETTER = "Q";

    /**
     * Converts the provided string into a legal HTML ID or Name, based on the HTML 4 rules:
     * <ul>
     * <li>Must begin with a letter ([A-Za-z])</li>
     * <li>May be followed by any number of letters, digits ([0-9]), hyphens ("-"), underscores ("_"), colons (":"), and periods (".")</li>
     * </ul>
     * See <a href="https://www.w3.org/TR/html4/types.html#type-id">W3C HTML 4.01 Specification</a><br>
     *
     * Removes all illegal characters (e.g., whitespace and non-alphanumeric characters except the four mentioned above) and then
     * pre-pends an arbitrary letter if the stripped version doesn't start with a letter. The return value is guaranteed to have
     * a legal form, but of course it's not guaranteed to be unique on the page.
     *
     * @param s Any string
     * @return A legal HTML ID produced from the string. Passing null is valid and returns a single letter ID.
     */
    public static @NotNull HtmlString makeHtmlId(@Nullable String s)
    {
        final String ret;

        if (null == s)
        {
            ret = ARBITRARY_LETTER;
        }
        else
        {
            String stripped = s.replaceAll("[^0-9A-Za-z\\-_:.]", "");

            if (stripped.isEmpty())
                ret = ARBITRARY_LETTER;
            else if (!Character.isLetter(stripped.charAt(0)))
                ret = ARBITRARY_LETTER + stripped;
            else
                ret = stripped;
        }

        // We've stripped all characters that could possibly cause HTML problems
        return HtmlString.unsafe(ret);
    }

    public static HtmlString generateBackButton()
    {
        return generateBackButton("Back");
    }

    public static HtmlString generateBackButton(String text)
    {
        return button(text).href("#").onClick("LABKEY.setDirty(false); window.history.back(); return false;").getHtmlString();
    }

    /* Renders text and a drop down arrow image wrapped in a link not of type labkey-button */
    public static String generateDropDownTextLink(String text, String href, String onClick, boolean bold, String offset,
                                                  String id, Map<String, String> properties)
    {
        String additions = getAttributes(properties);

        return "<a class=\"labkey-menu-text-link dropdown-toggle\" style=\"" + (bold ? "font-weight: bold;" : "") + "\" href=\"" + filter(href) + "\" " + additions +
                " onClick=\"if (this.className.indexOf('labkey-disabled-button') != -1) return false; " + (onClick == null ? "" : filter(onClick)) + "\"" +
                (id == null ? "" : " id=\"" + filter(id) + "PopupLink\"") + "><span" +
                (id == null ? "" : " id=\"" + filter(id) + "PopupText\"") + ">" + filter(text) + "</span>&nbsp;<span class=\"fa fa-caret-down\" style=\"position:relative;color:lightgray\"></span></a>";
    }

    /* Renders image and a drop down wrapped in an unstyled link */
    public static String generateDropDownImage(String text, String href, String onClick, String imageSrc, String imageId,
                                               Integer imageHeight, Integer imageWidth, Map<String, String> properties)
    {
        String additions = getAttributes(properties);

        return "<a href=\"" + filter(href) +"\" " + additions +
            " onClick=\"if (this.className.indexOf('labkey-disabled-button') != -1) return false; " + (onClick == null ? "" : filter(onClick)) + "\"" +
            "><img id=\"" + imageId + "\" title=\"" + filter(text) + "\" src=\"" + imageSrc + "\" " +
            (imageHeight == null ? "" : " height=\"" + imageHeight + "\"") + (imageWidth == null ? "" : " width=\"" + imageWidth + "\"") + "/></a>";
    }

    /* Renders image using font icon and a drop down wrapped in an unstyled link */
    public static String generateDropDownFontIconImage(String text, String href, String onClick, String imageCls,
                                                       String imageId, Map<String, String> properties)
    {
        String additions = getAttributes(properties);

        return "<a href=\"" + filter(href) +"\" " + additions +
                " onClick=\"if (this.className.indexOf('labkey-disabled-button') != -1) return false; " + (onClick == null ? "" : filter(onClick)) + "\"" +
                "><span id=\"" + imageId + "\" title=\"" + filter(text) + "\" class=\"" + imageCls + "\"></span></a>";
    }

    // TODO: Why no HTML filtering?
    private static String getAttributes(Map<String, String> properties)
    {
        if (properties == null || properties.isEmpty())
            return "";

        StringBuilder attributes = new StringBuilder();

        for (Map.Entry<String, String> entry : properties.entrySet())
        {
            attributes.append(entry.getKey());
            attributes.append("=\"");
            attributes.append(entry.getValue());
            attributes.append("\" ");
        }

        return attributes.toString();
    }

    /**
     * If the provided text uses ", return '. If it uses ', return ".
     * This is useful to quote javascript.
     */
    public static char getUnusedQuoteSymbol(String text)
    {
        if (text == null || text.equals(""))
            return '"';

        int singleQuote = text.indexOf('\'');
        int doubleQuote = text.indexOf('"');
        if (doubleQuote == -1 || (singleQuote != -1 && singleQuote <= doubleQuote))
            return '"';
        return '\'';
    }

    public static char getUsedQuoteSymbol(String text)
    {
        char c = getUnusedQuoteSymbol(text);
        if (c == '"')
            return '\'';
        return '"';
    }

    @Deprecated    // Use LinkBuilder directly - see PageFlowUtil.link(). 33 usages.
    public static String textLink(String text, URLHelper url)
    {
        return link(text).href(url).toString();
    }

    public static String helpPopup(String title, String helpText)
    {
        return helpPopup(title, helpText, false);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText)
    {
        return helpPopup(title, helpText, htmlHelpText, 0);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, int width)
    {
        String questionMarkHtml = "<span class=\"labkey-help-pop-up\">?</span>";
        return helpPopup(title, helpText, htmlHelpText, questionMarkHtml, width);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml)
    {
        return helpPopup(title, helpText, htmlHelpText, linkHtml, 0, null);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml, String onClickScript)
    {
        return helpPopup(title, helpText, htmlHelpText, linkHtml, 0, onClickScript);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml, int width)
    {
        return helpPopup(title, helpText, htmlHelpText, linkHtml, width, null);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml, int width, @Nullable String onClickScript)
    {
        if (title == null && !htmlHelpText)
        {
            // use simple tooltip
            if (onClickScript == null)
                onClickScript = "return false";

            StringBuilder link = new StringBuilder();
            link.append("<a href=\"#\" tabindex=\"-1\" onClick=\"").append(onClickScript).append("\" title=\"");
            link.append(filter(helpText));
            link.append("\">").append(linkHtml).append("</a>");
            return link.toString();
        }
        else
        {
            StringBuilder showHelpDivArgs = new StringBuilder("this, ");
            showHelpDivArgs.append(filter(jsString(filter(title)), true)).append(", ");
            // The value of the javascript string literal is used to set the innerHTML of an element.  For this reason, if
            // it is text, we escape it to make it HTML.  Then, we have to escape it to turn it into a javascript string.
            // Finally, since this is script inside of an attribute, it must be HTML escaped again.
            showHelpDivArgs.append(filter(jsString(htmlHelpText ? helpText : filter(helpText, true))));
            if (width != 0)
                showHelpDivArgs.append(", ").append(filter(jsString(filter(width + "px"))));
            if (onClickScript == null)
            {
                onClickScript = "return showHelpDiv(" + showHelpDivArgs + ");";
            }
            StringBuilder link = new StringBuilder();
            link.append("<a href=\"#\" tabindex=\"-1\" onClick=\"");
            link.append(onClickScript);
            link.append("\" onMouseOut=\"return hideHelpDivDelay();\" onMouseOver=\"return showHelpDivDelay(");
            link.append(showHelpDivArgs).append(");\"");
            link.append(">").append(linkHtml).append("</a>");
            return link.toString();
        }
    }


    public static String convertNodeToHtml(Node node) throws TransformerException, IOException
    {
        return convertNodeToString(node, TransformFormat.html);
    }

    public static String convertNodeToXml(Node node) throws TransformerException, IOException
    {
        return convertNodeToString(node, TransformFormat.xml);
    }

    public static String convertNodeToString(Node node, TransformFormat format) throws TransformerException, IOException
    {
        try
        {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.METHOD, format.toString());
            t.setOutputProperty(OutputKeys.ENCODING, StringUtilsLabKey.DEFAULT_CHARSET.name());

            try (ByteArrayOutputStream out = new ByteArrayOutputStream())
            {
                t.transform(new DOMSource(node), new StreamResult(out));
                return out.toString(StringUtilsLabKey.DEFAULT_CHARSET).trim();
            }
        }
        catch (TransformerFactoryConfigurationError e)
        {
            throw new RuntimeException("There was a problem creating the XML transformer factory." +
                " If you specified a class name in the 'javax.xml.transform.TransformerFactory' system property," +
                " please ensure that this class is included in the classpath for web application.", e);
        }
    }


    private static void parserSetFeature(XMLReader parser, String feature, boolean b)
    {
        try
        {
            parser.setFeature(feature, b);
        }
        catch (SAXNotSupportedException | SAXNotRecognizedException e)
        {
            _log.error("parserSetFeature", e);
        }
    }


    public static SafeToRender getAppIncludes(ViewContext context, @Nullable  LinkedHashSet<ClientDependency> resources)
    {
        return _getStandardIncludes(context, null, resources, false, false);
    }


    public static SafeToRender getStandardIncludes(ViewContext context, @Nullable LinkedHashSet<ClientDependency> resources, boolean includePostParameters)
    {
        return _getStandardIncludes(context, null, resources, true, includePostParameters);
    }

    public static SafeToRender getStandardIncludes(ViewContext context, PageConfig config)
    {
        return _getStandardIncludes(context, config, config.getClientDependencies(), true, config.shouldIncludePostParameters());
    }


    private static SafeToRender _getStandardIncludes(ViewContext context, @Nullable PageConfig config, @Nullable LinkedHashSet<ClientDependency> extraResources,
            boolean includeDefaultResources, boolean includePostParameters)
    {
        Container c = context.getContainer();

        if (null == c)
            c = ContainerManager.getRoot();

        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();

        if (includeDefaultResources)
        {
            // Respect App Properties regarding Ext3 configuration
            if (AppProps.getInstance().isExt3APIRequired())
                resources.add(ClientDependency.fromPath("clientapi/ext3"));
            else if (AppProps.getInstance().isExt3Required())
                resources.add(ClientDependency.fromPath("Ext3"));

            // TODO: Turn this into a lib.xml
            // core/css/core.js requires jQuery
            resources.add(ClientDependency.fromPath("internal/jQuery"));
            resources.add(ClientDependency.fromPath("core/css/core.js"));

            // Always include clientapi and internal
            resources.add(ClientDependency.fromPath("clientapi"));
            resources.add(ClientDependency.fromPath("internal"));
        }

        // Add mini-profiler as dependency if enabled
        long miniProfilerId = -1;
        if (MiniProfiler.isEnabled(context))
        {
            RequestInfo req = MemTracker.getInstance().current();
            if (req != null)
            {
                miniProfilerId = req.getId();
                resources.add(ClientDependency.fromPath("miniprofiler"));
            }
        }

        if (extraResources != null)
            resources.addAll(extraResources);

        resources.removeIf(Objects::isNull);

        // Use a SafeToRenderBuilder since standard includes are a mix of HTML and JavaScript
        SafeToRenderBuilder builder = SafeToRenderBuilder.of()
            .append(getFaviconIncludes(c))
            .append(getLabkeyJS(context, config, resources, includePostParameters))
            .append(getStylesheetIncludes(c, resources, includeDefaultResources))
            .append(getManifestIncludes(c, resources))
            .append(getJavaScriptIncludes(c, resources));

        if (miniProfilerId != -1)
        {
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            ids.add(miniProfilerId);
            ids.addAll(MemTracker.get().getUnviewed(context.getUser()));

            builder.append(MiniProfiler.renderInitScript(context.getUser(), miniProfilerId, ids, getServerSessionHash()));
        }

        return builder;
    }

    public static HtmlString getFaviconIncludes(Container c)
    {
        ResourceURL faviconURL = TemplateResourceHandler.FAVICON.getURL(c);

        HtmlStringBuilder builder = HtmlStringBuilder.of()
            .append(HtmlString.unsafe("<link rel=\"shortcut icon\" href=\""))
            .append(faviconURL.toString())
            .append(HtmlString.unsafe("\">\n"))
            .append(HtmlString.unsafe("<link rel=\"icon\" href=\""))
            .append(faviconURL.toString())
            .append(HtmlString.unsafe("\">\n"));

        return builder.getHtmlString();
    }

    // Outputs <link> elements for standard stylesheets (but not Ext stylesheets). Note hrefs are relative, so callers may
    // need to output a <base> element prior to calling.
    public static SafeToRender getStylesheetIncludes(Container c)
    {
        return getStylesheetIncludes(c, null, true);
    }

    // Outputs <link> elements for standard stylesheets, Ext stylesheets, and client dependency stylesheets, as required.
    // Note that hrefs are relative, so callers may need to output a <base> element prior to calling.
    private static SafeToRender getStylesheetIncludes(Container c, @Nullable LinkedHashSet<ClientDependency> resources, boolean includeDefaultResources)
    {
        CoreUrls coreUrls = urlProvider(CoreUrls.class);
        StringBuilder sb = new StringBuilder();

        Formatter F = new Formatter(sb);
        String link = "<link href=\"%s\" type=\"text/css\" rel=\"stylesheet\">\n";

        Set<String> preIncludedCss = getExtJSStylesheets(c, resources);
        for (String cssPath : preIncludedCss)
            F.format(link, PageFlowUtil.filter(staticResourceUrl(cssPath)));

        if (includeDefaultResources)
        {
            F.format(link, PageFlowUtil.filter(staticResourceUrl("/core/css/core.css")));
            F.format(link, PageFlowUtil.filter(staticResourceUrl("/core/css/" + resolveThemeName(c) + ".css")));

            ActionURL rootCustomStylesheetURL = coreUrls.getCustomStylesheetURL();

            if (c.isRoot())
            {
                    /* Add the root customStylesheet */
                if (null != rootCustomStylesheetURL)
                    F.format(link, PageFlowUtil.filter(rootCustomStylesheetURL));
            }
            else
            {
                ActionURL containerCustomStylesheetURL = coreUrls.getCustomStylesheetURL(c);

                    /* Add the container relative customStylesheet */
                if (null != containerCustomStylesheetURL)
                    F.format(link, PageFlowUtil.filter(containerCustomStylesheetURL));
                else if (null != rootCustomStylesheetURL)
                    F.format(link, PageFlowUtil.filter(rootCustomStylesheetURL));
            }
        }

        SafeToRenderBuilder builder = SafeToRenderBuilder.of();
        builder.append(HtmlString.unsafe(sb.toString()));

        if (resources != null)
            writeCss(c, builder, resources, preIncludedCss);

        return builder;
    }

    @NotNull
    public static Set<String> getExtJSStylesheets(Container c, Set<ClientDependency> resources)
    {
        // TODO: After the UX Refresh is permanently in place this could be refactored to exist within ClientDependency
        // getCssPaths(). Currently, the stylesheet ordering still matters so it is easier to just make this accessible
        // here.
        Set<String> extCSS = new HashSet<>();

        if (null != resources)
        {
            boolean ext3Included = false;
            boolean ext4Included = false;
            String themeName = resolveThemeName(c);

            for (ClientDependency resource : resources)
            {
                for (String paths : resource.getJsPaths(c, AppProps.getInstance().isDevMode()))
                {
                    if (!ext3Included && paths.startsWith("ext-3.4.1/ext-all"))
                    {
                        ext3Included = true;
                        extCSS.add("core/css/ext3_" + themeName + ".css");
                    }

                    if (!ext4Included && paths.startsWith("ext-4.2.1/ext-all"))
                    {
                        ext4Included = true;
                        extCSS.add("core/css/ext4_" + themeName + ".css");
                    }

                    if (ext3Included && ext4Included)
                        break;
                }
            }
        }

        return extCSS;
    }

    private static void writeCss(Container c, SafeToRenderBuilder builder, LinkedHashSet<ClientDependency> resources, Set<String> preIncludedCss)
    {
        HtmlStringBuilder html = HtmlStringBuilder.of();
        Set<String> cssFiles = new HashSet<>();
        if (resources != null)
        {
            for (ClientDependency r : resources)
            {
                for (String script : r.getCssPaths(c))
                {
                    html.append(HtmlString.unsafe("<link href=\""));
                    if (!ClientDependency.isExternalDependency(script))
                    {
                        html.append(AppProps.getInstance().getContextPath());
                        html.append("/");
                    }
                    html.append(script);
                    html.append(HtmlString.unsafe("\" type=\"text/css\" rel=\"stylesheet\">"));

                    cssFiles.add(script);
                }
            }
        }

        builder.append(html);

        //cache list of CSS files to prevent double-loading
        if (cssFiles.size() > 0)
        {
            SafeToRenderBuilder scriptBuilder = SafeToRenderBuilder.of();
            scriptBuilder.append(HtmlString.unsafe("<script type=\"text/javascript\">\n"));
            scriptBuilder.append(JavaScriptFragment.unsafe("LABKEY.requestedCssFiles("));
            String comma = "";

            if (null != preIncludedCss)
            {
                for (String path : preIncludedCss)
                {
                    scriptBuilder.append(JavaScriptFragment.unsafe(comma + jsString(path)));
                    comma = ",";
                }
            }

            for (String s : cssFiles)
            {
                if (!ClientDependency.isExternalDependency(s))
                {
                    scriptBuilder.append(JavaScriptFragment.unsafe(comma + jsString(s)));
                    comma = ",";
                }
            }
            scriptBuilder.append(JavaScriptFragment.unsafe(");\n"));
            scriptBuilder.append(HtmlString.unsafe("</script>\n"));

            builder.append(scriptBuilder);
        }
    }

    // Manifest files are included as links in the HTML head. These files are used to identify resources for progressive
    // web apps like flash page and home page link icons. There can be multiple on the same page.
    private static HtmlString getManifestIncludes(Container c, @Nullable LinkedHashSet<ClientDependency> resources)
    {
        StringBuilder sb = new StringBuilder();

        if (resources != null)
        {
            Set<String> manifestFiles = new HashSet<>();
            for (ClientDependency r : resources)
            {
                for (String manifest : r.getManifestPaths(c))
                {
                    if (!manifestFiles.contains(manifest))
                    {
                        sb.append("<link href=\"");
                        if (!ClientDependency.isExternalDependency(manifest))
                        {
                            sb.append(AppProps.getInstance().getContextPath());
                            sb.append("/");
                        }
                        sb.append(filter(manifest));
                        sb.append("\" rel=\"manifest\">");

                        manifestFiles.add(manifest);
                    }
                }
            }
        }

        // TODO: Rewrite the above using LinkBuilder
        return HtmlString.unsafe(sb.toString());
    }

    // TODO: Remove... unused? (Move string to JSpBase.getExtJs3Root())
    public static String extJsRoot()
    {
        return "ext-3.4.1";
    }

    public static String DEFAULT_THEME_NAME = "Seattle";

    public static String resolveThemeName(Container c)
    {
        String themeName = DEFAULT_THEME_NAME;

        if (c != null)
        {
            themeName = LookAndFeelProperties.getInstance(c).getThemeName();

            // TODO: This needs to be refactored to allow themes by convention
            if (!"seattle".equalsIgnoreCase(themeName) &&
                    !"sky".equalsIgnoreCase(themeName) &&
                    !"overcast".equalsIgnoreCase(themeName) &&
                    !"harvest".equalsIgnoreCase(themeName) &&
                    !"leaf".equalsIgnoreCase(themeName) &&
                    !"ocean".equalsIgnoreCase(themeName) &&
                    !"mono".equalsIgnoreCase(themeName) &&
                    !"madison".equalsIgnoreCase(themeName))
                return "overcast";
        }

        return themeName.toLowerCase();
    }


    /**
     * Return URL for static webapp resources.
     *
     * If we had a way to configure a domain for static resources, this would be the place to
     * fix-up the generated URL.
     */
    final static String staticResourcePrefix = AppProps.getInstance().getStaticFilesPrefix();

    public static String staticResourceUrl(String resourcePath)
    {
        String slash = resourcePath.startsWith("/") ? "" : "/";
        if (null != staticResourcePrefix)
        {
            return staticResourcePrefix + slash + resourcePath;
        }
        if (resourcePath.contains(".cache.") || resourcePath.endsWith(".ttf") || resourcePath.endsWith(".woff2"))        // CONSIDER: move DavController.alwaysCache() somewhere we can call it
            return AppProps.getInstance().getContextPath() + slash + resourcePath;
        else
            return AppProps.getInstance().getContextPath() + slash + resourcePath + "?" + getServerSessionHash();
    }


    public static SafeToRender getLabkeyJS(ViewContext context, @Nullable PageConfig config, @Nullable LinkedHashSet<ClientDependency> resources, boolean includePostParameters)
    {
        SafeToRenderBuilder builder = SafeToRenderBuilder.of()
            .append(getScriptTag("/labkey.js"));

        // Include client-side error reporting scripts only if necessary and as early as possible.
        if ((AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_JAVASCRIPT_MOTHERSHIP) || AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_JAVASCRIPT_SERVER)) &&
                (AppProps.getInstance().getExceptionReportingLevel() != ExceptionReportingLevel.NONE || AppProps.getInstance().isSelfReportExceptions()))
        {
            builder
                .append(getScriptTag("/stacktrace.min.js"))
                .append(getScriptTag("/mothership.js"));
        }

        return builder
            .append(HtmlString.unsafe("<script type=\"text/javascript\">\n"))
            .append(JavaScriptFragment.unsafe("LABKEY.init("))
            .append(jsInitObject(context, config, resources, includePostParameters))
            .append(JavaScriptFragment.unsafe(");\n"))
            .append(HtmlString.unsafe("</script>\n"));
    }

    public static HtmlString getScriptTag(String path)
    {
        return HtmlStringBuilder.of()
            .append(HtmlString.unsafe("<script src=\""))
            .append(staticResourceUrl(path))
            .append(HtmlString.unsafe("\" type=\"text/javascript\"></script>\n"))
            .getHtmlString();
    }

    private static SafeToRender getJavaScriptIncludes(Container c, LinkedHashSet<ClientDependency> resources)
    {
        /*
           scripts: the scripts that should be explicitly included
           included: the scripts that are implicitly included, which will include the component scripts on a minified library.
          */
        LinkedHashSet<String> includes = new LinkedHashSet<>();
        LinkedHashSet<String> implicitIncludes = new LinkedHashSet<>();

        getJavaScriptFiles(c, resources, includes, implicitIncludes);

        SafeToRenderBuilder builder = SafeToRenderBuilder.of();

        builder.append(HtmlString.unsafe("<script type=\"text/javascript\">\n"));
        builder.append(JavaScriptFragment.unsafe("LABKEY.loadedScripts("));
        String comma = "";
        for (String s : implicitIncludes)
        {
            if (!ClientDependency.isExternalDependency(s))
            {
                builder.append(JavaScriptFragment.unsafe(comma + jsString(s)));
                comma = ",";
            }
        }
        builder.append(JavaScriptFragment.unsafe(");\n"));
        builder.append(HtmlString.unsafe("</script>\n"));

        for (String s : includes)
        {
            HtmlStringBuilder scriptReference = HtmlStringBuilder.of(HtmlString.unsafe("<script src=\""))
                .append(ClientDependency.isExternalDependency(s) ? s : staticResourceUrl("/" + s))
                .append(HtmlString.unsafe("\" type=\"text/javascript\"></script>\n"));
            builder.append(scriptReference);
        }

        return builder.getSafeToRender();
    }

    /** use this version if you don't care which errors are html parsing errors and which are safety warnings */
    public static String validateHtml(String html, Collection<String> errors, boolean scriptAsErrors)
    {
        return validateHtml(html, errors, scriptAsErrors ? null : errors);
    }

    /** validate an html fragment */
    public static String validateHtml(String html, Collection<String> errors, Collection<String> scriptWarnings)
    {
        if (errors.size() > 0 || (null != scriptWarnings && scriptWarnings.size() > 0))
            throw new IllegalArgumentException("empty errors collection expected");

        // NOTE: tidy is unhappy if there is nothing but comments and whitespace
        //       and also about degenerate comments, such as "<!-->"
        //       We will remove the degenerates from anything we return
        String htmlWithNoDegenerates = html.replaceAll("<!--*>|<!>", "");

        String trimmedHtml = StringUtils.trimToEmpty(htmlWithNoDegenerates);

        if (trimmedHtml.length() == 0)
            return "";

        trimmedHtml = trimmedHtml.replaceAll("<!--.*?-->", "");
        trimmedHtml = StringUtils.trimToEmpty(trimmedHtml);
        if (trimmedHtml.isEmpty())
            return htmlWithNoDegenerates;

        // UNDONE: use convertHtmlToDocument() instead of tidy() to avoid double parsing
        String xml = TidyUtil.tidyHTML(trimmedHtml, true, errors);
        if (errors.size() > 0)
        {
            scriptWarnings.add("Unable to complete script check due to errors in HTML");
            return null;
        }

        if (null != scriptWarnings)
        {
            try
            {
                XMLReader parser = XMLReaderFactory.createXMLReader(DEFAULT_PARSER_NAME);
                parserSetFeature(parser, "http://xml.org/sax/features/namespaces", false);
                parserSetFeature(parser, "http://xml.org/sax/features/namespace-prefixes", false);
                parserSetFeature(parser, "http://xml.org/sax/features/validation", false);
                parserSetFeature(parser, "http://apache.org/xml/features/validation/dynamic", false);
                parserSetFeature(parser, "http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
                parserSetFeature(parser, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                parserSetFeature(parser, "http://apache.org/xml/features/validation/schema", false);
                parserSetFeature(parser, "http://apache.org/xml/features/validation/schema-full-checking", false);
                parserSetFeature(parser, "http://apache.org/xml/features/validation/dynamic", false);
                parserSetFeature(parser, "http://apache.org/xml/features/continue-after-fatal-error", false);

                parser.setContentHandler(new ValidateHandler(scriptWarnings));
                parser.parse(new InputSource(new StringReader(xml)));
            }
            catch (IOException | SAXException e)
            {
                _log.error(e.getMessage(), e);
                errors.add(e.getMessage());
                scriptWarnings.add("Unable to complete script check due to errors in HTML");
            }
        }

        if (errors.size() > 0 || (null != scriptWarnings && scriptWarnings.size() > 0))
            return null;

        // let's return html not xhtml
        String tidy = TidyUtil.tidyHTML(htmlWithNoDegenerates, false, errors);
        //FIX: 4528: old code searched for "<body>" but the body element can have attributes
        //and Word includes some when saving as HTML (even Filtered HTML).
        int beginOpenBodyIndex = tidy.indexOf("<body");
        int beginCloseBodyIndex = tidy.lastIndexOf("</body>");
        assert beginOpenBodyIndex != -1 && beginCloseBodyIndex != -1: "Tidied HTML did not include a body element!";
        int endOpenBodyIndex = tidy.indexOf('>', beginOpenBodyIndex);
        assert endOpenBodyIndex != -1 : "Could not find closing > of open body element!";

        tidy = tidy.substring(endOpenBodyIndex + 1, beginCloseBodyIndex).trim();
        return tidy;
    }

    public static String sanitizeHtml(String html, Collection<String> errors)
    {
        if (html == null)
            return null;
        if (!html.toLowerCase().contains("target=\"_blank\""))
            return html;
        boolean modified = false;
        try
        {
            Document document = TidyUtil.convertHtmlToDocument(html, false, errors);
            NodeList hrefs = document.getElementsByTagName("a");
            for(int hrefIndex = 0; hrefIndex < hrefs.getLength(); hrefIndex++)
            {
                Element href = (Element) hrefs.item(hrefIndex);
                String target = href.getAttribute("target");
                if ("_blank".equals(target))
                {
                    String rel = href.getAttribute("rel");
                    if (rel == null || !rel.contains("noopener") || !rel.contains("noreferrer"))
                    {
                        modified = true;
                        href.setAttribute("rel", "noopener noreferrer");
                    }
                }
            }
            if (!modified)
                return html;
            String xhtml = documentToString(document);
            if (xhtml != null && !html.contains("<body>")) // tidy adds <html><body> wrapping
            {
                int beginOpenBodyIndex = xhtml.indexOf("<body");
                int beginCloseBodyIndex = xhtml.lastIndexOf("</body>");
                int endOpenBodyIndex = xhtml.indexOf('>', beginOpenBodyIndex);
                xhtml = xhtml.substring(endOpenBodyIndex + 1, beginCloseBodyIndex).trim();
            }

            return xhtml;
        }
        catch (Exception e)
        {
            _log.error(e.getMessage(), e);
            errors.add(e.getMessage());
            return null;
        }
    }

    public static String documentToString(Document document) {
        try
        {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer trans = tf.newTransformer();
            StringWriter sw = new StringWriter();
            trans.transform(new DOMSource(document), new StreamResult(sw));
            return sw.toString();
        }
        catch (TransformerException tEx)
        {
            tEx.printStackTrace();
        }
        return null;
    }

    private static final int SERVER_HASH = 0x7fffffff & AppProps.getInstance().getServerSessionGUID().hashCode();
    private static final String SERVER_HASH_STRING = Integer.toString(SERVER_HASH);

    // This is used during server-side JavaScript initialization -- see core/resources/scripts/labkey/init.js
    @SuppressWarnings("UnusedDeclaration")
    public static JSONObject jsInitObject()
    {
        // Ugly: Is there some way for the JavaScript initialization in init.js to pass through the ViewContext?
        ViewContext context = HttpView.currentView().getViewContext();
        return jsInitObject(context, null, new LinkedHashSet<>(), false);
    }

    public static JSONObject jsInitObject(ContainerUser context, @Nullable PageConfig config, @Nullable LinkedHashSet<ClientDependency> resources, boolean includePostParameters)
    {
        AppProps appProps = AppProps.getInstance();
        String contextPath = appProps.getContextPath();
        JSONObject json = new JSONObject();

        // Expose some experimental flags to the client
        // Note: If you update this set of flags please update enum on client in @labkey/api
        JSONObject experimental = new JSONObject();
        experimental.put("containerRelativeURL", appProps.getUseContainerRelativeURL());
        experimental.put("useExperimentalCoreUI", useExperimentalCoreUI());
        experimental.put(AppProps.EXPERIMENTAL_JAVASCRIPT_MOTHERSHIP, AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_JAVASCRIPT_MOTHERSHIP));
        experimental.put(AppProps.EXPERIMENTAL_JAVASCRIPT_SERVER, AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_JAVASCRIPT_SERVER));
        experimental.put(AppProps.EXPERIMENTAL_NO_GUESTS, AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_NO_GUESTS));
        json.put("experimental", experimental);

        json.put("contextPath", contextPath);
        json.put("imagePath", contextPath + "/_images");
        json.put("extJsRoot", extJsRoot());
        json.put("extThemeName_42", resolveThemeName(context.getContainer()));
        json.put("devMode", appProps.isDevMode());
        json.put("homeContainer", ContainerManager.getHomeContainer().getName());
        Container shared = ContainerManager.getSharedContainer();
        if (null != shared) // not good
            json.put("sharedContainer", shared.getName());
        json.put("hash", getServerSessionHash());

        Container container = context.getContainer();
        User user = context.getUser();

        if (container != null)
            json.put("moduleContext", getModuleClientContext(context, resources));

        // Current container determines default formats and date parsing mode; fall back on root setting, which is better than nothing.
        Container settingsContainer = null != container ? container : ContainerManager.getRoot();
        json.put("extDefaultDateFormat", ExtUtil.toExtDateFormat(DateUtil.getDateFormatString(settingsContainer)));
        json.put("extDefaultDateTimeFormat", ExtUtil.toExtDateFormat(DateUtil.getDateTimeFormatString(settingsContainer)));
        String numberFormat = Formats.getNumberFormatString(settingsContainer);
        if (null != numberFormat)
            json.put("extDefaultNumberFormat", ExtUtil.toExtNumberFormat(numberFormat));

        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(settingsContainer);
        json.put("useMDYDateParsing", laf.getDateParsingMode().getDayMonth() == DateUtil.MonthDayOption.MONTH_DAY);

        // For now, all input forms should format using ISO date format, to ensure we can parse the dates we format. We can't
        // guarantee that the default date display formats are parseable. We might change this in the future, so use this
        // property instead of hard-coding formats on the client.
        json.put("extDateInputFormat", ExtUtil.toExtDateFormat(DateUtil.getStandardDateFormatString()));

        if (null != user)
        {
            SecurityLogger.indent("jsInitObject");
            json.put("user", User.getUserProps(user, container));
            if (user.isImpersonated())
                json.put("impersonatingUser", User.getUserProps(user.getImpersonatingUser(), container));
            SecurityLogger.outdent();
        }

        if (null != container)
        {
            json.put("container", container.toJSON(user, false));
            json.put("demoMode", DemoMode.isDemoMode(container, user));
        }

        Container project = (null == container || container.isRoot()) ? null : container.getProject();

        if (null != project)
        {
            JSONObject projectProps = new JSONObject();
            projectProps.put("id", project.getId());
            projectProps.put("path", project.getPath());
            projectProps.put("name", project.getName());
            projectProps.put("title", project.getTitle());
            projectProps.put("rootId", ContainerManager.getRoot().getId());
            json.put("project", projectProps);
        }

        json.put("tours", getTourJson(container));
        String serverName = appProps.getServerName();
        json.put("serverName", StringUtils.isNotEmpty(serverName) ? serverName : "LabKey Server");
        json.put("versionString", appProps.getReleaseVersion());
        json.put("helpLinkPrefix", HelpTopic.getHelpLinkPrefix());
        json.put("jdkJavaDocLinkPrefix", HelpTopic.getJdkJavaDocLinkPrefix());

        if (AppProps.getInstance().isExperimentalFeatureEnabled(NotificationMenuView.EXPERIMENTAL_NOTIFICATION_MENU))
            json.put("notifications", Map.of("unreadCount", NotificationService.get().getNotificationCountByUser(null, user.getUserId(), true)));

        JSONObject defaultHeaders = new JSONObject();
        defaultHeaders.put("X-ONUNAUTHORIZED", "UNAUTHORIZED");

        boolean pageAdminMode = false;
        // Be tolerant of not having a ViewContext, or having one but being detached from an HttpServletRequest
        if (context instanceof ViewContext)
        {
            ViewContext viewContext = (ViewContext)context;
            pageAdminMode = isPageAdminMode(viewContext);
            HttpServletRequest request = viewContext.getRequest();
            if (request != null)
            {
                json.put("login", AuthenticationManager.getLoginPageConfiguration(getTermsOfUseProject(project, request.getParameter(ActionURL.Param.returnUrl.name()))));
                if (includePostParameters && "post".equalsIgnoreCase(request.getMethod()))
                    json.put("postParameters", request.getParameterMap());
                String tok = CSRFUtil.getExpectedToken(request, null);
                if (null != tok)
                {
                    json.put("CSRF", tok);
                    defaultHeaders.put(CSRFUtil.csrfHeader, tok);
                }
            }
        }
        json.put("pageAdminMode", pageAdminMode);
        if (appProps.isUserRequestedAdminOnlyMode() || ModuleLoader.getInstance().isUpgradeRequired())
            json.put("adminOnlyMode", true);
        json.put("defaultHeaders", defaultHeaders);

        AnalyticsProviderRegistry analyticsProviderRegistry = AnalyticsProviderRegistry.get();
        if (analyticsProviderRegistry != null)
        {
            Map<String, String> analyticProviders = new HashMap<>();
            for (AnalyticsProvider provider : analyticsProviderRegistry.getAllAnalyticsProviders())
                analyticProviders.put(provider.getName(), provider.getLabel());
            json.put("analyticProviders", analyticProviders);
        }

        CustomLabelService customLabelService = CustomLabelService.get();
        if (customLabelService != null)
        {
            Map<String, Map<String, String>> moduleLabels = new HashMap<>();
            for (CustomLabelProvider provider : customLabelService.getCustomLabelProviders())
            {
                Map<String, String> labels = provider.getCustomLabels(container);
                if (labels != null)
                    moduleLabels.put(provider.getName(), labels);
            }
            if (!moduleLabels.isEmpty())
                json.put("labels", moduleLabels);
        }

        // Include a few server-generated GUIDs/UUIDs
        json.put("uuids", Arrays.asList(GUID.makeGUID(), GUID.makeGUID(), GUID.makeGUID()));

        if (null != config)
            json.put("portalContext", config.getPortalContext());

        return json;
    }

    private static JSONObject getTourJson(Container container)
    {
        JSONObject tourProps = new JSONObject();
        TourService service = TourService.get();

        if (null != service && null != container)
        {
            for (Tour tour : service.getApplicableTours(container))
            {
                tourProps.put(tour.getRowId().toString(), tour.abbrevDef());
            }
        }
        return tourProps;
    }

    public static String getServerSessionHash()
    {
        return SERVER_HASH_STRING;
    }

    @Nullable
    public static Project getTermsOfUseProject(Container container, String returnURL)
    {
        Container termsContainer = null;

        if (null != returnURL)
        {
            try
            {
                URLHelper urlHelper = new URLHelper(returnURL);
                Container redirectContainer = ContainerManager.getForPath(new ActionURL(urlHelper.getLocalURIString()).getExtraPath());
                if (null != redirectContainer)
                    termsContainer = redirectContainer.getProject();
            }
            catch (IllegalArgumentException | URISyntaxException iae)
            {
                // the redirect URL isn't an action url or isn't well formed, so we can't get the container. Ignore.
            }
        }

        if (null == termsContainer)
        {
            if (null == container || container.isRoot())
                return null;
            else
                termsContainer = container.getProject();
        }

        return new Project(termsContainer);
    }

    private static class ValidateHandler extends org.xml.sax.helpers.DefaultHandler
    {
        private static final HashSet<String> _illegalElements = new HashSet<>();

        static
        {
            _illegalElements.add("link");
            _illegalElements.add("style");
            _illegalElements.add("script");
            _illegalElements.add("object");
            _illegalElements.add("applet");
            _illegalElements.add("form");
            _illegalElements.add("input");
            _illegalElements.add("button");
            _illegalElements.add("frame");
            _illegalElements.add("frameset");
            _illegalElements.add("iframe");
            _illegalElements.add("embed");
            _illegalElements.add("plaintext");
        }

        private final Collection<String> _errors;
        private final HashSet<String> _reported = new HashSet<>();


        ValidateHandler(Collection<String> errors)
        {
            _errors = errors;
        }

        @Override
        public void processingInstruction(String target, String data)
        {
            if (!_reported.contains("processingInstruction"))
            {
                _reported.add("processingInstruction");
                _errors.add("Illegal processing instruction <?" + target + ">.");
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
        {
            String e = qName.toLowerCase();
            if ((e.startsWith("?") || e.startsWith("<") || _illegalElements.contains(e)) && !_reported.contains(e))
            {
                _reported.add(e);
                _errors.add("Illegal element <" + qName + ">. For permissions to use this element, contact your system administrator.");
            }

            for (int i = 0; i < attributes.getLength(); i++)
            {
                String a = attributes.getQName(i).toLowerCase();
                String value = attributes.getValue(i).toLowerCase();

                if ((a.startsWith("on") || a.startsWith("behavior")) && !_reported.contains(a))
                {
                    _reported.add(a);
                    _errors.add("Illegal attribute '" + attributes.getQName(i) + "' on element <" + qName + ">.");
                }
                if ("href".equals(a) || "src".equals(a))
                {
                    if (value.contains("script") && value.indexOf("script") < value.indexOf(":") && !_reported.contains("href"))
                    {
                        _reported.add("href");
                        _errors.add("Script is not allowed in '" + a + "' attribute on element <" + qName + ">.");
                    }
                }
                if ("style".equals(a))
                {
                    String valueStrippedOfComments = value.replaceAll("/\\*.*?\\*/", "");
                    if ((valueStrippedOfComments.contains("behavior") || valueStrippedOfComments.contains("url") || valueStrippedOfComments.contains("expression")) && !_reported.contains("style"))
                    {
                        _reported.add("style");
                        _errors.add("Style attribute cannot contain behaviors, expresssions, or urls. Error on element <" + qName + ">.");
                    }
                }
            }

        }

        @Override
        public void warning(SAXParseException e)
        {
        }

        @Override
        public void error(SAXParseException e)
        {
            _errors.add(e.getMessage());
        }

        @Override
        public void fatalError(SAXParseException e)
        {
            _errors.add(e.getMessage());
        }
    }


    public static boolean isRobotUserAgent(String userAgent)
    {
        if (StringUtils.isEmpty(userAgent))
            return true;
        userAgent = userAgent.toLowerCase();

        /* our big crawlers are... */
        // Google
//        if (userAgent.contains("googlebot"))
//            return true;
        // Yahoo
        if (userAgent.contains("yahoo! slurp"))
            return true;
        // Microsoft
//        if (userAgent.contains("bingbot") || userAgent.contains("msnbot"))
//            return true;
        if (userAgent.contains("msiecrawler"))  // client site-crawler
            return false;
        // Pingdom
//        if (userAgent.contains("pingdom.com_bot"))
//            return true;
        // a bot
        if (userAgent.contains("rpt-httpclient"))
            return true;
        if (userAgent.contains("qwantify"))
            return true;
        if (userAgent.contains("elb-healthchecker"))
            return true;

        // just about every bot contains "bot", "crawler" or "spider"
        // including yandexbot, ahrefsbot, mj12bot, ezooms.bot, gigabot, voilabot, exabot
        if (userAgent.contains("bot") || userAgent.contains("crawler") || userAgent.contains("spider"))
            return true;

        // Not a robot, but not a "real" user and known in some cases to issue many requests without reusing sessions
        if (userAgent.contains("mathematica httpclient"))
            return true;

        return false;
    }


    //
    // TestCase
    //
    public static class TestBean
    {
        private int i;
        private String s;
        private Date d;

        public TestBean(){}

        public TestBean(int i, String s, Date d)
        {
            this.i = i;
            this.s = s;
            this.d = d;
        }

        public int getI()
        {
            return i;
        }

        public void setI(int i)
        {
            this.i = i;
        }

        public String getS()
        {
            return s;
        }

        public void setS(String s)
        {
            this.s = s;
        }

        public Date getD()
        {
            return d;
        }

        public void setD(Date d)
        {
            this.d = d;
        }
    }

    public static class TestCase extends Assert
    {

        @Test
        public void testScriptDetection()
        {
            assertHtmlParsing("<b>No script here, friends</b>", 0, 0);
            assertHtmlParsing("<p><script>alert('script');</script></p>", 0, 1);

            // Bogus tag trips error reporting, so assume there might be script
            assertHtmlParsing("<Bad.Tag><script>alert('script');</script></Bad.Tag>", 1, 1);
            assertHtmlParsing("<Bad.Tag>No script here, friends</Bad.Tag>", 1, 1);

            // Unclosed tags - not considered an error
            assertHtmlParsing("<b><script>alert('script');</script>", 0, 1);
            assertHtmlParsing("<b>No script", 0, 0);
        }

        private void assertHtmlParsing(String html, int expectedErrors, int expectedScriptWarnings)
        {
            List<String> errors = new ArrayList<>();
            List<String> scriptWarnings = new ArrayList<>();
            PageFlowUtil.validateHtml(html, errors, scriptWarnings);
            assertEquals("Wrong number of errors", expectedErrors, errors.size());
            assertEquals("Wrong number of script warnings", expectedScriptWarnings, scriptWarnings.size());
        }

        @Test
        public void testPhone()
        {
            assertEquals(formatPhoneNo("5551212"), "555-1212");
            assertEquals(formatPhoneNo("2065551212"), "(206) 555-1212");
            assertEquals(formatPhoneNo("12065551212"), "(206) 555-1212");
            assertEquals(formatPhoneNo("206.555.1212"), "(206) 555-1212");
            assertEquals(formatPhoneNo("1-206) 555.1212  "), "(206) 555-1212");
            assertEquals(formatPhoneNo("1-206) 555.1212  "), "(206) 555-1212");
            assertEquals(formatPhoneNo("1(206) 555.1212  "), "(206) 555-1212");
            assertEquals(formatPhoneNo("1 (206)555.1212"), "(206) 555-1212");
            assertEquals(formatPhoneNo("(206)-555.1212  "), "(206) 555-1212");
            assertEquals(formatPhoneNo("work (206)555.1212"), "work (206) 555-1212");
            assertEquals(formatPhoneNo("206.555.1212 x0001"), "(206) 555-1212 x0001");
        }


        @Test
        public void testFilter()
        {
            assertEquals(filter("this is a test"), "this is a test");
            assertEquals(filter("<this is a test"), "&lt;this is a test");
            assertEquals(filter("this is a test<"), "this is a test&lt;");
            assertEquals(filter("'t'&his is a test\""), "&#039;t&#039;&amp;his is a test&quot;");
            assertEquals(filter("<>\"&"), "&lt;&gt;&quot;&amp;");
        }


        @Test
        public void testEncode()
        {
            assertEquals("%20", encode(" "));
            assertEquals("/hello/world?", encodeURI("/hello/world?"));
            assertEquals("/hel%20lo/wo%3Crld?", encodeURI("/hel lo/wo<rld?"));
            assertEquals("/hel%20lo/wo%3Crld?%3E", encodeURI("/hel lo/wo<rld?>"));
            assertEquals("%2Fhello%2Fworld%3F", encodeURIComponent("/hello/world?"));
            assertEquals("%2Fhello%2Fworld", encodeURIComponent("/hello/world"));
        }


        @Test
        public void testRobot()
        {
            List<String> bots = Arrays.asList(
                "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html",
                "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)",
                "Pingdom.com_bot_version_1.4_(http://www.pingdom.com/)",
                "Googlebot-Image/1.0",
                "Mozilla/5.0 (compatible; AhrefsBot/2.0; +http://ahrefs.com/robot/)",
                "Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)",
                "Gigabot/3.0 (http://www.gigablast.com/spider.html)",
                "msnbot-media/1.1 (+http://search.msn.com/msnbot.htm)",
                "Mozilla/5.0 (compatible; Ezooms/1.0; ezooms.bot@gmail.com)",
                "Mozilla/5.0 (iPhone; U; CPU iPhone OS 4_1 like Mac OS X; en-us) AppleWebKit/532.9 (KHTML, like Gecko) Version/4.0.5 Mobile/8B117 Safari/6531.22.7 (compatible; Googlebot-Mobile/2.1; +http://www.google.com/bot.html)",
                "Mozilla/5.0 (compatible; MJ12bot/v1.4.0; http://www.majestic12.co.uk/bot.php?+)",
                "Mozilla/5.0 (Windows; U; Windows NT 5.1; fr; rv:1.8.1) VoilaBot BETA 1.2 (support.voilabot@orange-ftgroup.com)",
                "Mozilla/5.0 (compatible; Exabot/3.0; +http://www.exabot.com/go/robot)",
                "Yeti/1.0 (NHN Corp.; http://help.naver.com/robots/)",
                "DoCoMo/2.0 N905i(c100;TB;W24H16) (compatible; Googlebot-Mobile/2.1; +http://www.google.com/bot.html)",
                "SAMSUNG-SGH-E250/1.0 Profile/MIDP-2.0 Configuration/CLDC-1.1 UP.Browser/6.2.3.3.c.1.101 (GUI) MMP/2.0 (compatible; Googlebot-Mobile/2.1; +http://www.google.com/bot.html)",
                "Mozilla/5.0 (compatible; AhrefsBot/1.0; +http://ahrefs.com/robot/)",
                "SETOOZBOT/5.0 ( compatible; SETOOZBOT/0.30 ; http://www.setooz.com/bot.html )",
                "Mozilla/5.0 (compatible; bnf.fr_bot; +http://www.bnf.fr/fr/outils/a.dl_web_capture_robot.html)",
                "AdMedia bot",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 5_0_1 like Mac OS X) (compatible; Yeti-Mobile/0.1; +http://help.naver.com/robots/)",
                "Mozilla/5.0 (compatible; Dow Jones Searchbot)");
            List<String> nots = Arrays.asList(
                "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/535.7 (KHTML, like Gecko) Chrome/16.0.912.36 Safari/535.7",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.6; rv:9.0a2) Gecko/20111101 Firefox/9.0a2",
                "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.1; WOW64; Trident/6.0)",
                "Opera/9.80 (Windows NT 6.1; U; es-ES) Presto/2.9.181 Version/12.00",
                "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10_6_8; de-at) AppleWebKit/533.21.1 (KHTML, like Gecko) Version/5.0.5 Safari/533.21.1"
            );
            for (String ua : bots)
                assertTrue(isRobotUserAgent(ua));
            for (String ua : nots)
                assertFalse(isRobotUserAgent(ua));
        }

        @Test
        public void testMakeHtmlId()
        {
            testMakeHtmlId(null);
            testMakeHtmlId("");
            testMakeHtmlId("!@#$");
            testMakeHtmlId(")(*&^%$");
            testMakeHtmlId("</html>");
            testMakeHtmlId("123@#$%");
            testMakeHtmlId("foo");
            testMakeHtmlId("!@#1!@#");
            testMakeHtmlId("ABC");
            testMakeHtmlId("!BC234");
            testMakeHtmlId("1A-34-FB-44");
        }

        @Test
        public void testEncodeObject() throws Exception
        {
            TestBean bean = new TestBean(5,"five",new Date(DateUtil.parseISODateTime("2005-05-05 05:05:05")));
            String s = encodeObject(bean).toString();

            TestBean copy = decodeObject(TestBean.class, s);
            assertNotNull(copy);
            assertEquals(bean.i, copy.i);
            assertEquals(bean.s, copy.s);
            assertEquals(bean.d, copy.d);

            Map<String,Object> map = (Map<String,Object>)decodeObject(Map.class, s);
            assertNotNull(map);
            assertEquals(bean.i, map.get("i"));
            assertEquals(bean.s, map.get("s"));
            assertEquals(bean.d.getTime(), DateUtil.parseDateTime((String)map.get("d")));
        }

        private void testMakeHtmlId(@Nullable String id)
        {
            HtmlString legalId = makeHtmlId(id);
            assertTrue(id + " was converted to " + legalId + ", which is not a legal HTML ID!", isLegalId(legalId.toString()));
        }

        private boolean isLegalId(String id)
        {
            return !id.isEmpty() && Character.isLetter(id.charAt(0)) && id.replaceAll("[0-9A-Za-z\\-_:.]", "").isEmpty();
        }

        @Test
        public void testFilterURL()
        {
            String html;

            html = filter("click here http://this/is/a/test.view.", true, true);
            assertTrue(html.contains("href=\"http:"));
            assertTrue(html.contains("view</a>."));

            html = filter("click here http://this/is/a/test.view ", true, true);
            assertTrue(html.contains("href=\"http:"));
            assertTrue(html.contains("view</a>"));

            html = filter("click here http://this/is/a/test.view", true, true);
            assertTrue(html.contains("href=\"http:"));
            assertTrue(html.contains("view</a>"));
        }
    }

    /** @return true if the UrlProvider exists. */
    static public <P extends UrlProvider> boolean hasUrlProvider(Class<P> inter)
    {
        return UrlProviderService.getInstance().hasUrlProvider(inter);
    }

    /**
     * Returns a specified <code>UrlProvider</code> interface implementation, for use
     * in writing URLs implemented in other modules.
     *
     * @param inter interface extending UrlProvider
     * @return an implementation of the interface.
     */
    @Nullable
    static public <P extends UrlProvider> P urlProvider(Class<P> inter)
    {
        return UrlProviderService.getInstance().getUrlProvider(inter);
    }

    /**
     * Returns a specified <code>UrlProvider</code> interface implementation, for use
     * in writing URLs implemented in other modules. If the user passes true for the checkForOverrides param,
     * we will create and return a UrlProviderOverrideHandler which will take into account any module registered
     * overrides to the given interface. If module overrides exist, they will be checked first for non-null results
     * when a given method of the interface is invoked before falling back to the default implementation.
     *
     * @param inter interface extending UrlProvider
     * @param checkForOverrides true to check for module overrides to this interface
     * @return an implementation of the interface.
     */
    @Nullable
    static public <P extends UrlProvider> P urlProvider(Class<P> inter, boolean checkForOverrides)
    {
        if (checkForOverrides)
        {
            P impl = urlProvider(inter);
            ClassLoader cl = inter.getClassLoader();
            return (P) Proxy.newProxyInstance(cl, new Class[]{inter}, new UrlProviderOverrideHandler(inter, impl));
        }

        return urlProvider(inter);
    }

    static private String h(Object o)
    {
        return PageFlowUtil.filter(o);
    }

    /**
     * CONSOLIDATE ALL .lastFilter handling
     */
    public static void saveLastFilter(ViewContext context, ActionURL url, String scope)
    {
        boolean lastFilter = BaseColumnInfo.booleanFromString(url.getParameter(scope + DataRegion.LAST_FILTER_PARAM));
        if (lastFilter)
            return;
        ActionURL clone = url.clone();

        // Don't store offset. It's especially bad because there may not be that many rows the next time you
        // get to a URL that uses the .lastFilter
        for (String paramName : clone.getParameterMap().keySet())
        {
            if (paramName.endsWith("." + QueryParam.offset))
            {
                clone.deleteParameter(paramName);
            }
        }

        clone.deleteParameter(scope + DataRegion.LAST_FILTER_PARAM);
        HttpSession session = context.getRequest().getSession(false);
        // We should already have a session at this point, but check anyway - see bug #7761
        if (session != null)
        {
            try
            {
                session.setAttribute(url.getPath() + "#" + scope + DataRegion.LAST_FILTER_PARAM, clone);
            }
            catch (IllegalStateException ignored)
            {
                // Session may have been invalidated elsewhere, but there's no way to check
            }
        }
    }

    public static ActionURL getLastFilter(ViewContext context, ActionURL url)
    {
        ActionURL ret = (ActionURL) context.getSession().getAttribute(url.getPath() + "#" + DataRegion.LAST_FILTER_PARAM);
        return ret != null ? ret.clone() : url.clone();
    }

    public static ActionURL addLastFilterParameter(ActionURL url)
    {
        return url.addParameter(DataRegion.LAST_FILTER_PARAM, "true");
    }


    public static ActionURL addLastFilterParameter(ActionURL url, String scope)
    {
        return url.addParameter(scope + DataRegion.LAST_FILTER_PARAM, "true");
    }

    public static String getSessionId(HttpServletRequest request)
    {
        return WebUtils.getSessionId(request);
    }

    /**
     * Stream the text back to the browser as a PNG
     */
    public static void streamTextAsImage(HttpServletResponse response, String text, int width, int height, Color textColor) throws IOException
    {
        Font font = new Font("SansSerif", Font.PLAIN, 12);

        BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = buffer.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(textColor);
        g2.setFont(font);
        FontMetrics metrics = g2.getFontMetrics();
        int fontHeight = metrics.getHeight();
        int spaceWidth = metrics.stringWidth(" ");

        int x = 5;
        int y = fontHeight + 5;

        StringTokenizer st = new StringTokenizer(text, " ");
        // Line wrap to fit
        while (st.hasMoreTokens())
        {
            String token = st.nextToken();
            int tokenWidth = metrics.stringWidth(token);
            if (x != 5 && tokenWidth + x > width)
            {
                x = 5;
                y += fontHeight;
            }
            g2.drawString(token, x, y);
            x += tokenWidth + spaceWidth;
        }

        response.setContentType("image/png");
        EncoderUtil.writeBufferedImage(buffer, ImageFormat.PNG, response.getOutputStream());
    }

    public static JSONObject getModuleClientContext(ContainerUser context, @Nullable LinkedHashSet<ClientDependency> resources)
    {
        JSONObject ret = new JSONObject();

        // Don't call getPageContextJson() implementations until all modules have started, #33401
        if (resources != null && ModuleLoader.getInstance().isStartupComplete())
        {
            Container c = context.getContainer();
            User u = context.getUser();
            Set<Module> modules = new HashSet<>();

            for (ClientDependency cd : resources)
            {
                modules.addAll(cd.getRequiredModuleContexts(c));
            }

            modules.addAll(c.getActiveModules(u));

            for (Module m : modules)
            {
                ret.put(m.getName().toLowerCase(), m.getPageContextJson(context));
            }
        }

        return ret;
    }

    public static void getJavaScriptFiles(Container c, LinkedHashSet<ClientDependency> dependencies, LinkedHashSet<String> includes, LinkedHashSet<String> implicitIncludes)
    {
        for (ClientDependency r : dependencies)
        {
            HttpServletRequest request = HttpView.currentRequest();
            boolean debugScriptMode = null != request && Boolean.parseBoolean(request.getParameter("debugScripts"));

            if (AppProps.getInstance().isDevMode() || debugScriptMode)
            {
                includes.addAll(r.getJsPaths(c, true));
                implicitIncludes.addAll(r.getJsPaths(c, true));
            }
            else
            {
                includes.addAll(r.getJsPaths(c, false));
                //include both production and devmode scripts for requiresScript()
                implicitIncludes.addAll(r.getJsPaths(c, true));
                implicitIncludes.addAll(r.getJsPaths(c, false));
            }
        }
    }

    public static HtmlString getDataRegionHtmlForPropertyObjects(Map<String, Object> propValueMap)
    {
        Map<String, String> stringValMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : propValueMap.entrySet())
            stringValMap.put(entry.getKey(), null==entry.getValue() ? "" : entry.getValue().toString());
        return getDataRegionHtmlForPropertyValues(stringValMap);
    }

    public static HtmlString getDataRegionHtmlForPropertyValues(Map<String, String> propValueMap)
    {
        StringBuilder sb = new StringBuilder();
        final AtomicInteger rowCount = new AtomicInteger();
        DOM.TABLE(cl("labkey-data-region-legacy","labkey-show-borders"),
                TR(TD(cl("labkey-column-header"),"Property"),TD(cl("labkey-column-header"),"Value")),
                propValueMap.entrySet().stream().map(entry ->
                    TR(cl(rowCount.getAndIncrement() % 2 == 0, "labkey-alternate-row", "labkey-row"),
                        TD(at(valign,"top"),entry.getKey()),
                        TD(at(valign,"top"),entry.getValue())))
        ).appendTo(sb);
        return HtmlString.unsafe(sb.toString());
    }
}
