/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jfree.chart.encoders.EncoderUtil;
import org.jfree.chart.encoders.ImageFormat;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.admin.CoreUrls;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ResourceURL;
import org.labkey.api.settings.TemplateResourceHandler;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.util.WebUtils;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.tidy.Tidy;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;


public class PageFlowUtil
{
    public enum TransformFormat
    {
        html,
        xml
    }

    private static Logger _log = Logger.getLogger(PageFlowUtil.class);
    private static final String _newline = System.getProperty("line.separator");

    private static final Object[] NO_ARGS = new Object[ 0 ];

    private static final Pattern urlPattern = Pattern.compile(".*((http|https|ftp|mailto)://\\S+).*");
    private static final Pattern urlPatternStart = Pattern.compile("((http|https|ftp|mailto)://\\S+).*");

    /**
     * Default parser class.
     */
    protected static final String DEFAULT_PARSER_NAME = "org.apache.xerces.parsers.SAXParser";

    String encodeURLs(String input)
    {
        Matcher m = urlPattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        int start;
        int end = 0;
        while (m.find())
        {
            start = m.start();
            String href = m.group(1);
            if (href.endsWith(".")) href = href.substring(0, href.length() - 1);
            sb.append(input.substring(end, start));
            sb.append("<a href=\"").append(href).append("\">").append(href).append("</a>");
            end = m.end();
        }
        sb.append(input.substring(end));
        return sb.toString();
    }


    static public final String NONPRINTING_ALTCHAR = "~";
    static final String nonPrinting;
    static
    {
        StringBuffer sb = new StringBuffer();
        for (char c = 1 ; c < ' ' ; c++)
        {
            if (" \t\r\n".indexOf('c') == -1)
                sb.append(c);
        }
        nonPrinting = sb.toString();
    }

    static public String filterXML(String s)
    {
        return filter(s,false,false);
    }

    static public HString filter(HString s)
    {
        if (null == s)
            return HString.EMPTY;
        
        return new HString(filter(s.getSource()), false);
    }


    static public HString filter(HStringBuilder s)
    {
        if (null == s)
            return HString.EMPTY;

        return new HString(filter(((HStringBuilder)s).getSource()), false);
    }

    
    static public String filter(String s, boolean encodeSpace, boolean encodeLinks)
    {
        if (null == s || 0 == s.length())
            return "";

        int len = s.length();
        StringBuilder sb = new StringBuilder(2 * len);
        boolean newline = false;

        for (int i=0 ; i < len; ++i)
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
                        String sub = s.substring(i);
                        if (c == 'f' && sub.startsWith("ftp://") ||
                                c == 'h' && (sub.startsWith("http://") || sub.startsWith("https://")) ||
                                c == 'm' && sub.startsWith("mailto://"))
                        {
                            Matcher m = urlPatternStart.matcher(sub);
                            if (m.find())
                            {
                                String href = m.group(1);
                                if (href.endsWith(".")) href = href.substring(0, href.length() - 1);
                                sb.append("<a href=\"").append(href).append("\">").append(href).append("</a>");
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


    public static String filter(Object o)
    {
        return filter(o == null ? null : o.toString());
    }

    /**
     * HTML encode a string
     */
    public static String filter(String s)
    {
        return filter(s, false, false);
    }


    static public String filter(String s, boolean translateWhiteSpace)
    {
        return filter(s, translateWhiteSpace, false);
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


    static public String jsString(CharSequence cs)
    {
        if (cs == null)
            return "''";

        String s;
        if (cs instanceof HString)
            s = ((HString)cs).getSource();
        else
            s = cs.toString();

        // UNDONE: what behavior do we want for tainted strings? IllegalArgumentException()?
        if (cs instanceof Taintable && ((Taintable)cs).isTainted())
        {
            if (s.toLowerCase().contains("<script"))
                return "''";
        }
        return jsString(s);
    }


    static public String jsString(String s)
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
    static public String groovyString(String s)
    {
        //replace single backslash
        s = s.replaceAll("\\\\", "\\\\\\\\");
        //replace double quote
        s = s.replaceAll("\"", "\\\\\"");
        return s;
    }

    @SuppressWarnings({"unchecked"})
    static Pair<String, String>[] _emptyPairArray = new Pair[0];   // Can't delare generic array

    public static Pair<String, String>[] fromQueryString(String query)
    {
        return fromQueryString(query, "UTF-8");
    }

    public static Pair<String, String>[] fromQueryString(String query, String encoding)
    {
        if (null == query || 0 == query.length())
            return _emptyPairArray;

        if (null == encoding)
            encoding = "UTF-8";

        List<Pair> parameters = new ArrayList<Pair>();
        if (query.startsWith("?"))
            query = query.substring(1);
        String[] terms = query.split("&");

        try
        {
            for (String term : terms)
            {
                if (0 == term.length())
                    continue;
                // NOTE: faster to decode all at once, just can't allow keys to have '=' char
                term = URLDecoder.decode(term, encoding);
                int ind = term.indexOf('=');
                if (ind == -1)
                    parameters.add(new Pair<String,String>(term.trim(), ""));
                else
                    parameters.add(new Pair<String,String>(term.substring(0, ind).trim(), term.substring(ind + 1).trim()));
            }
        }
        catch (UnsupportedEncodingException x)
        {
            throw new IllegalArgumentException(encoding, x);
        }

        //noinspection unchecked
        return parameters.toArray(new Pair[parameters.size()]);
    }


    public static Map<String, String> mapFromQueryString(String queryString)
    {
        Pair<String, String>[] pairs = fromQueryString(queryString);
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (Pair<String, String> p : pairs)
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
        StringBuffer sb = new StringBuffer();
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
        StringBuffer sb = new StringBuffer();
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


    public static <T> Map<T, T> map(T... args)
    {
        HashMap<T, T> m = new HashMap<T, T>();
        for (int i = 0; i < args.length; i += 2)
            m.put(args[i], args[i + 1]);
        return m;
    }


    public static <T> Set<T> set(T... args)
    {
        HashSet<T> s = new HashSet<T>();

        if (null != args)
            s.addAll(Arrays.asList(args));

        return s;
    }


    public static Set<String> insensitiveSet(String... strs)
    {
        TreeSet set = new TreeSet(String.CASE_INSENSITIVE_ORDER);
        set.addAll(Arrays.asList(strs));
        return set;
    }

    
    public static ArrayList pairs(Object... args)
    {
        ArrayList<Pair> list = new ArrayList<Pair>();
        for (int i = 0; i < args.length; i += 2)
            list.add(new Pair<Object,Object>(args[i], args[i + 1]));
        return list;
    }


    private static final Pattern pattern = Pattern.compile("\\+");


    /**
     * URL Encode string.
     * NOTE! this should be used on parts of a url, not an entire url
     */
    public static String encode(String s)
    {
        if (null == s)
            return "";
        try
        {
            return pattern.matcher(URLEncoder.encode(s, "UTF-8")).replaceAll("%20");
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }


    public static String decode(String s)
    {
        try
        {
            return URLDecoder.decode(s, "UTF-8");
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

    public static URI redirectToURI(HttpServletRequest request, String uri)
    {
        if (null == uri)
            uri = request.getContextPath() + "/";

        // Try redirecting to the URI stashed in the session
        try
        {
            return new URI(uri);
        }
        catch (Exception x)
        {
            // That didn't work, just redirect home
            try
            {
                return new URI(request.getContextPath());
            }
            catch (Exception y)
            {
                return null;
            }
        }
    }


    // Cookie helper function -- loops through Cookie array and returns matching value (or defaultValue if not found)
    public static String getCookieValue(Cookie[] cookies, String cookieName, String defaultValue)
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
     * boolean controlling whether or not we compress {@link ObjectOutputStream}s when we render them in HTML forms.
     * 
     */
    static private final boolean COMPRESS_OBJECT_STREAMS = true;
    static public String encodeObject(Object o) throws IOException
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
        ObjectOutputStream oos = new ObjectOutputStream(osCompressed);
        oos.writeObject(o);
        oos.close();
        osCompressed.close();
        return new String(Base64.encodeBase64(byteArrayOutputStream.toByteArray(), true));
    }


    public static Object decodeObject(String s) throws IOException
    {
        s = StringUtils.trimToNull(s);
        if (null == s)
            return null;
        
        try
        {
            byte[] buf = Base64.decodeBase64(s.getBytes());
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
            ObjectInputStream ois = new ObjectInputStream(isCompressed);
            Object obj = ois.readObject();
            return obj;
        }
        catch (ClassNotFoundException x)
        {
            throw new IOException(x.getMessage());
        }
    }

    
    public static byte[] gzip(String s)
    {
        try
        {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            GZIPOutputStream zip = new GZIPOutputStream(buf);
            zip.write(s.getBytes("UTF-8"));
            zip.close();
            return buf.toByteArray();
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
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


    // this is stupid hack to handle non-struts controllers
    public static abstract class MessageFormatter
    {
        public abstract String get(String key);
        String format(String key, String... args)
        {
            return MessageFormat.format(get(key), args);
        }
    }


    private static String _tempPath = null;

    // Under Catalina, it seems to pick \tomcat\temp
    // On the web server under Tomcat, it seems to pick c:\Documents and Settings\ITOMCAT_EDI\Local Settings\Temp
    public static String getTempDirectory()
    {
        if (null == _tempPath)
        {
            try
            {
                File temp = File.createTempFile("deleteme", null);
                _tempPath = temp.getParent() + File.separator;
                temp.delete();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        return _tempPath;
    }


    private static MimeMap _mimeMap = new MimeMap();

    public static String getContentTypeFor(String filename)
    {
        String contentType = _mimeMap.getContentTypeFor(filename);
        if (null == contentType)
        {
            contentType = "application/octet-stream";
        }
        return contentType;
    }

    private static void prepareResponseForFile(HttpServletResponse response, Map<String, String> responseHeaders, String filename, long countOfBytes, boolean asAttachment)
    {
        String contentType = getContentTypeFor(filename);
        response.reset();
        response.setContentType(contentType);
        if (countOfBytes < Integer.MAX_VALUE)
            response.setContentLength((int) countOfBytes);
        if (asAttachment)
        {
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        }
        else
        {
            response.setHeader("Content-Disposition", "filename=\"" + filename + "\"");
        }
        for (Map.Entry<String, String> entry : responseHeaders.entrySet())
            response.setHeader(entry.getKey(), entry.getValue());
    }

    public static void streamFile(HttpServletResponse response, File file, boolean asAttachment) throws IOException
    {
        streamFile(response, Collections.<String, String>emptyMap(), file, asAttachment);
    }

    public static void streamFile(HttpServletResponse response, String fileName, boolean asAttachment) throws IOException
    {
        streamFile(response, Collections.<String, String>emptyMap(), new File(fileName), asAttachment);
    }


    // Read the file and stream it to the browser
    public static void streamFile(HttpServletResponse response, Map<String, String> responseHeaders, File file, boolean asAttachment) throws IOException
    {
        // TODO: setHeader(modified)
        long length = file.length();

        FileInputStream s = null;

        try
        {
            // TODO: use FileUtils.copyData()
            s = new FileInputStream(file);

            prepareResponseForFile(response, responseHeaders, file.getName(), length, asAttachment);
            ServletOutputStream out = response.getOutputStream();
            FileUtil.copyData(s, out);
        }
        finally
        {
            IOUtils.closeQuietly(s);
        }
    }

    public static void streamFileBytes(HttpServletResponse response, String filename, byte[] bytes, boolean asAttachment) throws IOException
    {
        prepareResponseForFile(response, Collections.<String, String>emptyMap(), filename, bytes.length, asAttachment);
        response.getOutputStream().write(bytes);
    }

    // Fetch the contents of a text file, and return it in a String.
    public static String getFileContentsAsString(File aFile)
    {
        StringBuilder contents = new StringBuilder();
        BufferedReader input = null;

        try
        {
            input = new BufferedReader(new FileReader(aFile));
            String line;
            while ((line = input.readLine()) != null)
            {
                contents.append(line);
                contents.append(_newline);
            }
        }
        catch (FileNotFoundException e)
        {
            _log.error(e);
            contents.append("File not found");
            contents.append(_newline);
        }
        catch (IOException e)
        {
            _log.error(e);
        }
        finally
        {
            IOUtils.closeQuietly(input);
        }
        return contents.toString();
    }


    public static class Content
    {
        public Content(String s)
        {
            this(s, null, System.currentTimeMillis());
        }

        public Content(String s, byte[] e, long m)
        {
            content = s;
            encoded = e;
            modified = m;
        }

        public Object dependencies;
        public String content;
        public byte[] encoded;
        public long modified;
    }


    // Marker class for caching absense of content -- can't use a single marker object because of dependency handling.
    public static class NoContent extends Content
    {
        public NoContent(Object dependsOn)
        {
            super(null);
            dependencies = dependsOn;
        }
    }


    public static Content getViewContent(ModelAndView mv, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        final StringWriter writer = new StringWriter();
        HttpServletResponse sresponse = new HttpServletResponseWrapper(response)
            {
                public PrintWriter getWriter()
                {
                    return new PrintWriter(writer);
                }
            };
        mv.getView().render(mv.getModel(), request, sresponse);
        String sheet = writer.toString();
        Content c = new Content(sheet);
        return c;
    }


	// UNDONE: Move to FileUtil
    // Fetch the contents of an input stream, and return in a String.
    public static String getStreamContentsAsString(InputStream is)
    {
		return getReaderContentsAsString(new BufferedReader(new InputStreamReader(is)));
    }


	public static String getReaderContentsAsString(BufferedReader reader)
	{
		StringBuilder contents = new StringBuilder();
		String line;
		try
		{
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
		finally
		{
			IOUtils.closeQuietly(reader);
		}
		return contents.toString();
	}

	
    // Fetch the contents of an input stream, and return it in a list.
    public static List<String> getStreamContentsAsList(InputStream is) throws IOException
    {
        List<String> contents = new ArrayList<String>();
        BufferedReader input = new BufferedReader(new InputStreamReader(is));

        try
        {
            String line;
            while ((line = input.readLine()) != null)
                contents.add(line);
        }
        finally
        {
            IOUtils.closeQuietly(input);
        }

        return contents;
    }


    public static boolean empty(String str)
    {
        return null == str || str.trim().length() == 0;
    }


    static Pattern patternPhone = Pattern.compile("((1[\\D]?)?\\(?(\\d\\d\\d)\\)?[\\D]*)?(\\d\\d\\d)[\\D]?(\\d\\d\\d\\d)");

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


    // Generates JavaScript that redirects to a new location when Enter is pressed.  Use this on pages that have
    // button links but don't submit a form.
    public static String generateRedirectOnEnter(ActionURL url)
    {
        return "\n<script type=\"text/javascript\">\n" +
                "document.onkeydown = keyListener;\n" +
                "function keyListener(e)" +
                "{\n" +
                "   if (!e)\n" +
                "   {\n" +
                "      //for IE\n" +
                "      e = window.event;\n" +
                "   }\n" +
                "   if (13 == e.keyCode)\n" +
                "   {\n" +
                "      document.location = \"" + PageFlowUtil.filter(url) + "\";\n" +
                "   }\n" +
                "}\n" +
                "</script>\n";
    }


    /*
     * Renders a span wrapped in a link (<a>)
     * Consider: is there any way to name this method in such a way as to
     * make the order of parameters unambiguous?
     */
    public static String generateButton(String text, String href)
    {
        return generateButton(text, href, null);
    }

    public static String generateButton(String text, HString href)
    {
        return generateButton(text, null==href ? null : href.getSource(), null);
    }

    public static String generateButton(String text, String href, String onClick)
    {
        return generateButton(text, href, onClick, "");
    }

    public static String generateButton(String text, String href, String onClick, String attributes)
    {
        return "<a class=\"labkey-button\" href=\"" + filter(href) + "\"" +
                (onClick != null ? " onClick=" + wrapOnClick(onClick) : "") +
                (attributes != null ? " " + attributes : "") +
                "><span>" + filter(text) + "</span></a>";
    }

    public static String generateButton(String text, URLHelper href)
    {
        return generateButton(text, href, null);
    }

    public static String generateButton(String text, URLHelper href, String onClick)
    {
        return generateButton(text, href.toString(), onClick);
    }

    /* Renders an input of type submit wrapped in a span */
    public static String generateSubmitButton(String text)
    {
        return generateSubmitButton(text, null);
    }

    public static String generateSubmitButton(String text, String onClickScript)
    {
        return generateSubmitButton(text, onClickScript, null);
    }

    public static String generateSubmitButton(String text, String onClick, String attributes)
    {
        return generateSubmitButton(text, onClick, attributes, true);
    }

    public static String generateSubmitButton(String text, String onClick, String attributes, boolean enabled)
    {
        return generateSubmitButton(text, onClick, attributes, enabled, false);
    }

    public static String generateSubmitButton(String text, String onClick, String attributes, boolean enabled, boolean disableOnClick)
    {
        String guid = GUID.makeGUID();
        char quote = getUsedQuoteSymbol(onClick); // we're modifying the javascript, so need to use whatever quoting the caller used

        String submitCode = "submitForm(document.getElementById(" + quote + guid + quote + ").form); return false;";

        String onClickMethod;

        if (disableOnClick)
        {
            onClick = onClick != null ? onClick + ";Ext.get(this).replaceClass('labkey-button', 'labkey-disabled-button')" :
                    "Ext.get(this).replaceClass('labkey-button', 'labkey-disabled-button')";
        }

        if (onClick == null || "".equals(onClick))
            onClickMethod = submitCode;
        else
            onClickMethod = "this.form = document.getElementById(" + quote + guid + quote + ").form; if (isTrueOrUndefined(function() {" + onClick + "}.call(this))) " +  submitCode;

        StringBuilder sb = new StringBuilder();

        sb.append("<input type=\"submit\" style=\"display: none;\" id=\"");
        sb.append(guid);
        sb.append("\">");

        if (enabled)
            sb.append("<a class=\"labkey-button\"");
        else
            sb.append("<a class=\"labkey-disabled-button\"");

        sb.append(" href=\"#\"");

        sb.append(" onclick=").append(wrapOnClick(onClickMethod));

        if (attributes != null)
            sb.append(" ").append(" ").append(attributes);

        sb.append("><span>").append(filter(text)).append("</span></a>");

        return sb.toString();
    }

    /* Renders a span and a drop down arrow image wrapped in a link */
    public static String generateDropDownButton(String text, String href, String onClick, String attributes)
    {
        return "<a class=\"labkey-menu-button\" href=\"" + filter(href) + "\"" + (onClick != null ? " onClick=" + wrapOnClick(onClick) : "") +
                (attributes != null ? " " + attributes : "") +
                "><span>" + filter(text) + "</span>&nbsp;<img src=\"" + HttpView.currentView().getViewContext().getContextPath() +
                "/_images/button_arrow.gif\" class=\"labkey-button-arrow\"></a>";
    }

    /* Renders a span and a drop down arrow image wrapped in a link */
    public static String generateDropDownButton(String text, String href, String onClick)
    {
        return generateDropDownButton(text, href, onClick, null);
    }

    /* Renders text and a drop down arrow image wrapped in a link not of type labkey-button */
    public static String generateDropDownTextLink(String text, String href, String onClick)
    {
        return "<a class=\"labkey-header\" style=\"font-weight: bold;position: relative;padding-right:1em\" href=\"" + filter(href) + "\"" +
                (onClick != null ? " onClick=" + wrapOnClick(onClick) : "") +
                "><span>" + text + "</span>&nbsp;<img src=\"" + HttpView.currentView().getViewContext().getContextPath() + "/_images/text_link_arrow.gif\" class=\"labkey-button-arrow\"></a>";
    }

    /* Renders a lightly colored inactive button, or in other words, a disabled span wrapped in a link of type labkey-disabled-button */
    public static String generateDisabledButton(String text)
    {
        return "<a class=\"labkey-disabled-button\" disabled><span>" + filter(text) + "</span></a>";
    }

    /* Renders a lightly colored inactive button */
    public static String generateDisabledSubmitButton(String text, String onClick, String attributes)
    {
        return generateSubmitButton(text, onClick, attributes, false);
    }

    /* This function is used so that the onClick script can use either " or ' quote scheme inside of itself */
    public static String wrapOnClick(String onClick)
    {
        char quote = getUnusedQuoteSymbol(onClick);

        return quote + onClick + quote;
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

    public static String textLink(String text, String href, String id)
    {
        return textLink(text, href, null, id);
    }

    public static String textLink(String text, String href)
    {
        return textLink(text, href, null, null);
    }

    public static String textLink(String text, HString href, String onClickScript, String id)
    {
        return "[<a href=\"" + filter(href) + "\"" +
                (id != null ? " id=\"" + id + "\"" : "") +
                (onClickScript != null ? " onClick=\"" + onClickScript + "\"" : "") +
                ">" + text + "</a>]";
    }

    public static String textLink(String text, String href, String onClickScript, String id)
    {
        return "[<a href=\"" + filter(href) + "\"" +
                (id != null ? " id=\"" + id + "\"" : "") +
                (onClickScript != null ? " onClick=\"" + onClickScript + "\"" : "") +
                ">" + text + "</a>]";
    }

    public static String textLink(String text, ActionURL url, String onClickScript, String id)
    {
        return "[<a href=\"" + filter(url) + "\"" +
                (id != null ? " id=\"" + id + "\"" : "") +
                (onClickScript != null ? " onClick=\"" + onClickScript + "\"" : "") +
                ">" + text + "</a>]";
    }

    public static String textLink(String text, ActionURL url)
    {
        return textLink(text, url.getLocalURIString(), null, null);
    }

    public static String textLink(String text, ActionURL url, String id)
    {
        return textLink(text, url.getLocalURIString(), null, id);
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

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml, String linkOnClick)
    {
        return helpPopup(title, helpText, htmlHelpText, linkHtml, 0, linkOnClick);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml, int width)
    {
        return helpPopup(title, helpText, htmlHelpText, linkHtml, width, null);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml, int width, String linkOnClick)
    {
        if (title == null && !htmlHelpText)
        {
            // use simple tooltip
            if (linkOnClick == null)
                linkOnClick = "return false";

            StringBuilder link = new StringBuilder();
            link.append("<a href=\"#\" tabindex=\"-1\" onClick=\""+ linkOnClick + "\" title=\"");
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
                showHelpDivArgs.append(", ").append(filter(jsString(filter(String.valueOf(width) + "px"))));
            if (linkOnClick == null)
            {
                linkOnClick = "return showHelpDiv(" + showHelpDivArgs + ");";
            }
            StringBuilder link = new StringBuilder();
            link.append("<a href=\"#\" tabindex=\"-1\" " +
                    "onClick=\""+ linkOnClick + "\" " +
                    "onMouseOut=\"return hideHelpDivDelay();\" " +
                    "onMouseOver=\"return showHelpDivDelay(" + showHelpDivArgs + ");\"");
            link.append(">").append(linkHtml).append("</a>");
            return link.toString();
        }
    }


    /**
     * helper for script validation
     */
    public static String convertHtmlToXml(String html, Collection<String> errors)
    {
        return tidy(html, true, errors);
    }


    static Pattern scriptPattern = Pattern.compile("(<script.*?>)(.*?)(</script>)", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);        

    public static Document convertHtmlToDocument(final String html, final Collection<String> errors)
    {
        Tidy tidy = new Tidy();
        tidy.setShowWarnings(false);
        tidy.setIndentContent(false);
        tidy.setSmartIndent(false);
        tidy.setInputEncoding("UTF-8");
        tidy.setOutputEncoding("UTF-8");
        tidy.setDropEmptyParas(false); // radeox wikis use <p/> -- don't remove them
        tidy.setTrimEmptyElements(false); // keeps tidy from converting <p/> to <br><br>

        // TIDY does not property parse the contents of script tags!
        // see bug 5007
        // CONSIDER: fix jtidy see ParserImpl$ParseScript
        Map<String,String> scriptMap = new HashMap<String,String>();
        StringBuffer stripped = new StringBuffer(html.length());
        Matcher scriptMatcher = scriptPattern.matcher(html);
        int unique = html.hashCode();
        int count = 0;

        while (scriptMatcher.find())
        {
            count++;
            String key = "{{{" + unique + ":::" + count + "}}}";
            String match = scriptMatcher.group(2);
            scriptMap.put(key,match);
            scriptMatcher.appendReplacement(stripped, "$1" + key + "$3");
        }
        scriptMatcher.appendTail(stripped);

        StringWriter err = new StringWriter();
        try
        {
            // parse wants to use streams
            tidy.setErrout(new PrintWriter(err));
            Document doc = tidy.parseDOM(new ByteArrayInputStream(stripped.toString().getBytes("UTF-8")), null);

            // fix up scripts
            if (null != doc && null != doc.getDocumentElement())
            {
                NodeList nl = doc.getDocumentElement().getElementsByTagName("script");
                for (int i=0 ; i<nl.getLength() ; i++)
                {
                    Node script = nl.item(i);
                    NodeList childNodes = script.getChildNodes();
                    if (childNodes.getLength() != 1)
                        continue;
                    Node child = childNodes.item(0);
                    if (!(child instanceof CharacterData))
                        continue;
                    String contents = ((CharacterData)child).getData();
                    String replace = scriptMap.get(contents);
                    if (null == replace)
                        continue;
                    doc.createTextNode(replace);
                    script.removeChild(childNodes.item(0));
                    script.appendChild(doc.createTextNode(replace));
                }
            }

            tidy.getErrout().close();
            for (String error : err.toString().split("\n"))
            {
                if (error.contains("Error:"))
                    errors.add(error.trim());
            }
            return doc;
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
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
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            t.transform(new DOMSource(node), new StreamResult(out));
            out.close();

            return new String(out.toByteArray(), "UTF-8").trim();
        }
        catch (TransformerFactoryConfigurationError e)
        {
            throw new RuntimeException("There was a problem creating the XML transformer factory." +
                    " If you specified a class name in the 'javax.xml.transform.TransformerFactory' system property," +
                    " please ensure that this class is included in the classpath for web application.", e);
        }
    }


    public static String tidy(final String html, boolean asXML, final Collection<String> errors)
    {
        Tidy tidy = new Tidy();
        if (asXML)
            tidy.setXHTML(true);
        tidy.setShowWarnings(false);
        tidy.setIndentContent(false);
        tidy.setSmartIndent(false);
        tidy.setInputEncoding("UTF-8"); // utf8
        tidy.setOutputEncoding("UTF-8"); // utf8
        tidy.setDropEmptyParas(false); // allow <p/> in html wiki pages

        StringWriter err = new StringWriter();

        try
        {
            // parse wants to use streams
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            tidy.setErrout(new PrintWriter(err));
            tidy.parse(new ByteArrayInputStream(html.getBytes("UTF-8")), out);
            tidy.getErrout().close();
            for (String error : err.toString().split("\n"))
            {
                if (error.contains("Error:"))
                    errors.add(error.trim());
            }
            return new String(out.toByteArray(), "UTF-8");
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }

    public static String tidyXML(final String xml, final Collection<String> errors)
    {
        Tidy tidy = new Tidy();
        tidy.setXmlOut(true);
        tidy.setXmlTags(true);
        tidy.setShowWarnings(false);
        tidy.setIndentContent(false);
        tidy.setSmartIndent(false);
        tidy.setInputEncoding("UTF-8"); // utf8
        tidy.setOutputEncoding("UTF-8"); // utf8

        StringWriter err = new StringWriter();

        try
        {
            // parse want's to use streams
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            tidy.setErrout(new PrintWriter(err));
            tidy.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")), out);
            tidy.getErrout().close();
            for (String error : err.toString().split("\n"))
            {
                if (error.contains("Error:"))
                    errors.add(error.trim());
            }
            return new String(out.toByteArray(), "UTF-8");
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }


    private static void parserSetFeature(XMLReader parser, String feature, boolean b)
    {
        try
        {
            parser.setFeature(feature, b);
        }
        catch (SAXNotSupportedException e)
        {
            _log.error("parserSetFeature", e);
        }
        catch (SAXNotRecognizedException e)
        {
            _log.error("parserSetFeature", e);
        }
    }

    public static String getStandardIncludes(Container c)
    {
        StringBuilder sb = getFaviconIncludes(c);
        sb.append(getStylesheetIncludes(c, false));
        return sb.toString();
    }

    public static StringBuilder getFaviconIncludes(Container c)
    {
        StringBuilder sb = new StringBuilder();

        ResourceURL faviconURL = TemplateResourceHandler.FAVICON.getURL(c);

        sb.append("    <link rel=\"shortcut icon\" href=\"");
        sb.append(PageFlowUtil.filter(faviconURL));
        sb.append("\" />\n");

        sb.append("    <link rel=\"icon\" href=\"");
        sb.append(PageFlowUtil.filter(faviconURL));
        sb.append("\" />\n");

        return sb;
    }

    // TODO: Use email param or eliminate it
    public static StringBuilder getStylesheetIncludes(Container c, boolean email)
    {
        StringBuilder sb = new StringBuilder();

        ResourceURL stylesheetURL = new ResourceURL("stylesheet.css", ContainerManager.getRoot());

        sb.append("    <link href=\"");
        sb.append(PageFlowUtil.filter(stylesheetURL));
        sb.append("\" type=\"text/css\" rel=\"stylesheet\"/>\n");

        ResourceURL printStyleURL = new ResourceURL("printStyle.css", ContainerManager.getRoot());
        sb.append("    <link href=\"");
        sb.append(PageFlowUtil.filter(printStyleURL));
        sb.append("\" type=\"text/css\" rel=\"stylesheet\" media=\"print\"/>\n");

        CoreUrls coreUrls = urlProvider(CoreUrls.class);

        sb.append("    <link href=\"");
        sb.append(PageFlowUtil.filter(coreUrls.getThemeStylesheetURL()));
        sb.append("\" type=\"text/css\" rel=\"stylesheet\"/>\n");

        ActionURL rootCustomStylesheetURL = coreUrls.getCustomStylesheetURL();

        if (null != rootCustomStylesheetURL)
        {
            sb.append("    <link href=\"");
            sb.append(PageFlowUtil.filter(rootCustomStylesheetURL));
            sb.append("\" type=\"text/css\" rel=\"stylesheet\"/>\n");
        }

        if (!c.isRoot())
        {
            ActionURL containerThemeStylesheetURL = coreUrls.getThemeStylesheetURL(c);

            if (null != containerThemeStylesheetURL)
            {
                sb.append("    <link href=\"");
                sb.append(PageFlowUtil.filter(containerThemeStylesheetURL));
                sb.append("\" type=\"text/css\" rel=\"stylesheet\"/>\n");
            }

            ActionURL containerCustomStylesheetURL = coreUrls.getCustomStylesheetURL(c);

            if (null != containerCustomStylesheetURL)
            {
                sb.append("    <link href=\"");
                sb.append(PageFlowUtil.filter(containerCustomStylesheetURL));
                sb.append("\" type=\"text/css\" rel=\"stylesheet\"/>\n");
            }
        }

        return sb;
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

        if (StringUtils.trimToEmpty(html).length() == 0)
            return "";

        // UNDONE: use convertHtmlToDocument() instead of tidy() to avoid double parsing
        String xml = tidy(html, true, errors);
        if (errors.size() > 0)
            return null;

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
            catch (UnsupportedEncodingException e)
            {
                _log.error(e.getMessage(), e);
                errors.add(e.getMessage());
            }
            catch (IOException e)
            {
                _log.error(e.getMessage(), e);
                errors.add(e.getMessage());
            }
            catch (SAXException e)
            {
                _log.error(e.getMessage(), e);
                errors.add(e.getMessage());
            }
        }

        if (errors.size() > 0 || (null != scriptWarnings && scriptWarnings.size() > 0))
            return null;
        
        // let's return html not xhtml
        String tidy = tidy(html, false, errors);
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



    static final String extJsRoot = "ext-2.2";
    static Integer serverHash = null;

    public static String extJsRoot()
    {
        return extJsRoot;
    }

    public static String jsInitObject()
    {
        AppProps props = AppProps.getInstance();
        String contextPath = props.getContextPath();
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("contextPath:'").append(contextPath).append("'");
        sb.append(",");
        sb.append("imagePath:'").append(contextPath).append("/_images'");
        sb.append(",");
        sb.append("extJsRoot:'").append(extJsRoot).append("'");
        sb.append(",");
        sb.append("devMode:").append(props.isDevMode()?"true":"false");
        sb.append(",");
        if (null == serverHash)
            serverHash = 0x7fffffff & props.getServerSessionGUID().hashCode();
        sb.append("hash:'").append(serverHash).append("'");
        sb.append(",");

        //TODO: these should be passed in by callers
        ViewContext context = HttpView.currentView().getViewContext();
        Container container = context.getContainer();
        User user = HttpView.currentView().getViewContext().getUser();
        sb.append("user:{id:").append(user.getUserId());
        sb.append(",displayName:").append(jsString(user.getDisplayName(context)));
        sb.append(",email:").append(PageFlowUtil.jsString(user.getEmail()));
        sb.append(",sessionid:").append(jsString(getSessionId(context.getRequest())));
        sb.append(",canInsert:").append(container.hasPermission(user, ACL.PERM_INSERT) ? "true" : "false");
        sb.append(",canUpdate:").append(container.hasPermission(user, ACL.PERM_UPDATE) ? "true" : "false");
        sb.append(",canUpdateOwn:").append(container.hasPermission(user, ACL.PERM_UPDATEOWN) ? "true" : "false");
        sb.append(",canDelete:").append(container.hasPermission(user, ACL.PERM_DELETE) ? "true" : "false");
        sb.append(",canDeleteOwn:").append(container.hasPermission(user, ACL.PERM_DELETEOWN) ? "true" : "false");
        sb.append(",isAdmin:").append(container.hasPermission(user, ACL.PERM_ADMIN) ? "true" : "false");
        sb.append(",isSystemAdmin:").append(user.isAdministrator() ? "true" : "false");
        sb.append(",isGuest:").append(user.isGuest() ? "true" : "false");
        sb.append("}"); //end user object

        sb.append(",container:{id:'").append(container.getId()).append("'");
        sb.append(",path:").append(jsString(container.getPath()));
        sb.append("}"); //end container object

        sb.append(",serverName:(").append(PageFlowUtil.jsString(props.getServerName())).append(" || 'LabKey Server')");
        sb.append(",versionString:").append(PageFlowUtil.jsString(props.getLabkeyVersionString()));
        sb.append("}"); //end config
        return sb.toString();
    }


    private static class ValidateHandler extends org.xml.sax.helpers.DefaultHandler
    {
        static HashSet<String> _illegalElements = new HashSet<String>();

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

        static HashSet<String> _illegalAttributes = new HashSet<String>();

        Collection<String> _errors;
        HashSet<String> _reported = new HashSet<String>();


        ValidateHandler(Collection<String> errors)
        {
            _errors = errors;
        }


        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
        {
            String e = qName.toLowerCase();
            if ((e.startsWith("?") || _illegalElements.contains(e)) && !_reported.contains(e))
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
                if ("href".equals(a))
                {
                    if (value.indexOf("script") != -1 && value.indexOf("script") < value.indexOf(":") && !_reported.contains("href"))
                    {
                        _reported.add("href");
                        _errors.add("Script is not allowed in 'href' attribute on element <" + qName + ">.");
                    }
                }
                if ("style".equals(a))
                {
                    if ((value.indexOf("behavior") != -1 || value.indexOf("url") != -1 || value.indexOf("expression") != -1) && !_reported.contains("style"))
                    {
                        _reported.add("style");
                        _errors.add("Style attribute cannot contain behaviors, expresssions, or urls. Error on element <" + qName + ">.");
                    }
                }
            }
        }

        @Override
        public void warning(SAXParseException e) throws SAXException
        {
        }

        @Override
        public void error(SAXParseException e) throws SAXException
        {
            _errors.add(e.getMessage());
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException
        {
            _errors.add(e.getMessage());
        }
    }


    //
    // TestCase
    //


    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


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


        public void testFilter()
        {
            assertEquals(filter("this is a test"), "this is a test");
            assertEquals(filter("<this is a test"), "&lt;this is a test");
            assertEquals(filter("this is a test<"), "this is a test&lt;");
            assertEquals(filter("'t'&his is a test\""), "'t'&amp;his is a test&quot;");
            assertEquals(filter("<>\"&"), "&lt;&gt;&quot;&amp;");
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }


    public static void sendAjaxCompletions(HttpServletResponse response, List<AjaxCompletion> completions) throws IOException
    {
        response.setContentType("text/xml");
        response.setHeader("Cache-Control", "no-cache");
        Writer writer = response.getWriter();
        writer.write("<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>\n");
        writer.write("<completions>");
        for (AjaxCompletion completion : completions)
        {
            writer.write("<completion>\n");
            writer.write("    <display>" + filter(completion.getKey()) + "</display>");
            writer.write("    <insert>" + filter(completion.getValue()) + "</insert>");
            writer.write("</completion>\n");
        }
        writer.write("</completions>");
    }


    // Compares two objects even if they're null.
    public static boolean nullSafeEquals(Object o1, Object o2)
    {
        if (null == o1)
            return null == o2;

        return o1.equals(o2);
    }



    //
    //  From PFUtil
    //

    @Deprecated
    @SuppressWarnings("deprecation")
    static public ActionURL urlFor(Enum action, Container container)
    {
        String pageFlowName = ModuleLoader.getInstance().getPageFlowForPackage(action.getClass().getPackage());
        return new ActionURL(pageFlowName, action.toString(), container);
    }

    /**
     * Returns a specified <code>UrlProvider</code> interface implementation, for use
     * in writing URLs implemented in other modules.
     *
     * @param inter interface extending UrlProvider
     * @return an implementation of the interface.
     */
    static public <P extends UrlProvider> P urlProvider(Class<P> inter)
    {
        return ModuleLoader.getInstance().getUrlProvider(inter);
    }

    static public String helpTopic(Class<? extends Controller> action)
    {
        return SpringActionController.getPageFlowName(action) + "/" + SpringActionController.getActionName(action);
    }

    static private String h(Object o)
    {
        return PageFlowUtil.filter(o);
    }

    static public <T> String strSelect(String selectName, Map<T,String> map, T current)
    {
        return strSelect(selectName, map.keySet(), map.values(), current);
    }

    static public String strSelect(String selectName, Collection<?> values, Collection<String> labels, Object current)
    {
        if (values.size() != labels.size())
            throw new IllegalArgumentException();
        StringBuilder ret = new StringBuilder();
        ret.append("<select name=\"");
        ret.append(h(selectName));
        ret.append("\">");
        boolean found = false;
        Iterator itValue;
        Iterator<String> itLabel;
        for (itValue  = values.iterator(), itLabel = labels.iterator();
             itValue.hasNext() && itLabel.hasNext();)
        {
            Object value = itValue.next();
            String label = itLabel.next();
            boolean selected = !found && ObjectUtils.equals(current, value);
            ret.append("\n<option value=\"");
            ret.append(h(value));
            ret.append("\"");
            if (selected)
            {
                ret.append(" SELECTED");
                found = true;
            }
            ret.append(">");
            ret.append(h(label));
            ret.append("</option>");
        }
        ret.append("</select>");
        return ret.toString();
    }

    static public void close(Closeable closeable)
    {
        if (closeable == null)
            return;
        try
        {
            closeable.close();
        }
        catch (IOException e)
        {
            _log.error("Error in close", e);
        }
    }

    static public String getResourceAsString(Class clazz, String resource)
    {
        InputStream is = null;
        try
        {
            is = clazz.getResourceAsStream(resource);
            if (is == null)
                return null;
            return PageFlowUtil.getStreamContentsAsString(is);
        }
        finally
        {
            close(is);
        }
    }

    static public String _gif()
    {
        return _gif(1, 1);
    }

    static public String _gif(int height, int width)
    {
        StringBuilder ret = new StringBuilder();
        ret.append("<img src=\"");
        ret.append(AppProps.getInstance().getContextPath());
        ret.append("/_.gif\" height=\"");
        ret.append(height);
        ret.append("\" width=\"");
        ret.append(width);
        ret.append("\">");
        return ret.toString();
    }

    static public String strCheckbox(String name, boolean checked)
    {
        return strCheckbox(name, null, checked);
    }
    
    static public String strCheckbox(String name, String value, boolean checked)
    {
        StringBuilder out = new StringBuilder();
        String htmlName = h(name);
        
        out.append("<input type=\"checkbox\" name=\"");
        out.append(htmlName);
        out.append("\"");
        if (null != value)
        {
            out.append(" value=\"");
            out.append(h(value));
            out.append("\"");
        }
        if (checked)
        {
            out.append(" checked");
        }
        out.append(">");
        out.append("<input type=\"hidden\" name=\"");
        out.append(SpringActionController.FIELD_MARKER);
        out.append(htmlName);
        out.append("\">");
        return out.toString();
    }


    /** CONSOLIDATE ALL .lastFilter handling **/

    public static void saveLastFilter()
    {
        ViewContext context = HttpView.getRootContext();
        saveLastFilter(context, context.getActionURL(), "");
    }


    // scope is not fully supported
    public static void saveLastFilter(ViewContext context, ActionURL url, String scope)
    {
        boolean lastFilter = ColumnInfo.booleanFromString(url.getParameter(scope + DataRegion.LAST_FILTER_PARAM));
        if (lastFilter)
            return;
        ActionURL clone = url.clone();
        clone.deleteParameter(scope + DataRegion.LAST_FILTER_PARAM);
        HttpSession session = context.getRequest().getSession(false);
        // We should already have a session at this point, but check anyway - see bug #7761
        if (session != null)
        {
            session.setAttribute(url.getPath() + "#" + scope + DataRegion.LAST_FILTER_PARAM, clone);
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
}
