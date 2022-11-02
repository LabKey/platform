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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Represents a URL, typically within this instance of LabKey Server.
 */
public class URLHelper implements Cloneable, Serializable
{
    private static final Logger LOG = LogManager.getLogger(URLHelper.class);

    protected String _scheme = "http";
    protected String _host = null;
    protected int _port = 80;
    protected Path _contextPath = Path.rootPath;
    protected Path _path = Path.emptyPath;
    protected ArrayList<Pair<String, String>> _parameters = null;    // decoded key/value pairs
    protected String _fragment = null;

    protected boolean _readOnly = false;


    protected URLHelper()
    {
    }


    public URLHelper(boolean useContextPath)
    {
        if (useContextPath)
        {
            setPath(AppProps.getInstance().getParsedContextPath());
            assert isDirectory();
        }
    }


    public URLHelper(URI uri)
    {
        _setURI(uri);
    }


    public URLHelper(String url) throws URISyntaxException
    {
        parse(url);
    }


    protected void parse(String url) throws URISyntaxException
    {
        String p;
        String q;

        String fragment = null;
        int hashIndex = url.indexOf('#');
        if (hashIndex != -1)
        {
            fragment = url.substring(hashIndex + 1);
            url = url.substring(0,hashIndex);
        }

        int i = url.indexOf('?');
        if (i == -1)
        {
            p = url;
            q = "";
        }
        else
        {
            p = url.substring(0,i);
            q = url.substring(i+1);
        }

        if (-1 != p.indexOf(' '))
            p = StringUtils.replace(p, " ", "%20");
        if (!StringUtils.isEmpty(p))
            _setURI(new URI(p));
        assert _fragment == null : "The call to _setURI unexpectedly resulted in a fragment being set" ;
        _fragment = fragment;
        setRawQuery(q);
    }


    public URLHelper(HttpServletRequest req)
    {
        setHost(req.getServerName());
        setPort(req.getServerPort());
        setScheme(req.getScheme());

        // NOTE: request.getRequestURI() is encoded unlike request.getServletPath()
        setPath(_parsePath(req.getRequestURI(), true));
        _parseQuery(req.getQueryString(), req.getCharacterEncoding());
    }


    public void setReadOnly()
    {
        _readOnly = true;
    }


    public boolean isReadOnly()
    {
        return _readOnly;
    }


    // Scheme + host + port, but no context path
    public static StringBuilder getBaseServer(String scheme, String host, int port)
    {
        StringBuilder serverBuilder = new StringBuilder();
        serverBuilder.append(scheme).append("://").append(host);
        // we need to append a port number for http connections not on port 80, and for https connections not on 443
        if (port == 80 && ("http".equals(scheme) || "ws".equals(scheme)))
            port = -1;
        if (port == 443 && ("https".equals(scheme) || "wss".equals(scheme)))
            port = -1;
        if (port != -1)
            serverBuilder.append(":").append(port);
        return serverBuilder;
    }

    public boolean isLocalUri(ViewContext currentContext)
    {
        return getHost() == null
                || currentContext == null
                || currentContext.getActionURL() == null
                || getHost().equalsIgnoreCase(currentContext.getActionURL().getHost());
    }

    public String getURIString()
    {
        StringBuilder sb = getBaseServer(getScheme(), getHost(), getPort());
        sb.append(getLocalURIString());
        return sb.toString();
    }


    public String getURIString(boolean allowSubstSyntax)
    {
        StringBuilder sb = getBaseServer(getScheme(), getHost(), getPort());
        sb.append(getLocalURIString(allowSubstSyntax));
        return sb.toString();
    }


    public String getBaseServerURI()
    {
        return getBaseServer(getScheme(), getHost(), getPort()).toString();
    }


    public String getLocalURIString()
    {
        return getLocalURIString(false);
    }

    protected boolean isDirectory()
    {
        return _path.isDirectory();
    }

    public String getLocalURIString(boolean allowSubstSyntax)
    {
        StringBuilder uriString = new StringBuilder(getPath());

        if (uriString.indexOf("/") != 0)
        {
            uriString.insert(0, '/');
        }

        appendParamsAndFragment(uriString, allowSubstSyntax);

        return uriString.toString();
    }

    protected void appendParamsAndFragment(StringBuilder uriString, boolean allowSubstSyntax)
    {
        boolean hasParams = (null != _parameters && _parameters.size() > 0);
        if (hasParams)
            uriString.append('?').append(getQueryString(allowSubstSyntax));
        if (null != _fragment && _fragment.length() > 0)
            uriString.append("#").append(_fragment);
    }

    /** Applies HTML encoding to local URI string */
    public String getEncodedLocalURIString()
    {
        return PageFlowUtil.filter(getLocalURIString());
    }


    protected void _setURI(URI uri)
    {
        setHost(uri.getHost());
        setPort(uri.getPort());       // TODO: Don't store -1 if port is not specified -- use scheme to save default ports
        setPath(_parsePath(uri.getRawPath(), true));
        _parseQuery(uri.getRawQuery());
        setScheme(uri.getScheme());
        _fragment = uri.getFragment();
    }


    protected Path _parsePath(String path, boolean decode)
    {
        if (null == path || 0 == path.length() || "/".equals(path))
            return Path.rootPath;
        else
            return decode ? Path.decode(path) : Path.parse(path);
    }


    protected void _parseQuery(String query)
    {
        _parseQuery(query, "UTF-8");
    }

    protected void _parseQuery(String query, String encoding)
    {
        _parameters = new ArrayList<>(PageFlowUtil.fromQueryString(query, encoding));
    }


    public String getPath()
    {
        if (null == _path || _path.size() == 0)
            return "";
        Path p;
        if (_contextPath.size() == 0)
            p = _path;
        else
            p = _contextPath.append(_path);
        return p.encode();
    }


    public String getQueryString()
    {
        return PageFlowUtil.toQueryString(_parameters);
    }

    public String getQueryString(boolean allowSubstSyntax)
    {
        return PageFlowUtil.toQueryString(_parameters, allowSubstSyntax);
    }

    public URLHelper setHost(String host)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _host = host;
        return this;
    }


    /**
     * The path argument is not URL encoded.
     */
    public URLHelper setPath(String path)
    {
        return setParsedPath(_parsePath(path, false));
    }

    /**
     * The path argument is not URL encoded.
     */
    public URLHelper setPath(Path path)
    {
        return setParsedPath(path);
    }


    public URLHelper setPort(int port)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _port = port;
        return this;
    }


    public URLHelper setRawQuery(String query)
    {
        _parseQuery(query);
        return this;
    }


    public URLHelper setScheme(String scheme)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _scheme = scheme;
        return this;
    }


    public URLHelper setFragment(String fragment)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _fragment = fragment;
        return this;
    }


    public String getHost()
    {
        return _host;
    }


    /** NOTE URLHelper is dumb wrt contextPath, it does not know what the webapp contextPath is
     * it's up to the caller to use the contextPath and path part consistently
     */
    public URLHelper setContextPath(String contextPath)
    {
        assert contextPath.isEmpty() || contextPath.startsWith("/");
        if (_readOnly) throw new java.lang.IllegalStateException();
        if (StringUtils.isEmpty(contextPath))
            _contextPath = Path.rootPath;
        else
            _contextPath = Path.parse(contextPath);
        return this;
    }

    @JsonIgnore
    public URLHelper setContextPath(Path contextPath)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _contextPath = contextPath;
        return this;
    }


    /** NOTE URLHelper is dumb wrt contextPath, it does not know what the webapp contextPath is */
    public String getContextPath()
    {
        if (_contextPath.size() == 0)
            return "";
        return _contextPath.toString();
    }


    public Path getParsedPath()
    {
        return null == _path ? Path.rootPath : _path;
    }

    public URLHelper setParsedPath(Path path)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _path = path;
        return this;
    }


    public int getPort()
    {
        return _port;
    }


    public String getRawQuery()
    {
        return getQueryString();
    }


    public String getScheme()
    {
        return _scheme;
    }


    public String getFragment()
    {
        return _fragment;
    }

    /*
      * parameter manipulation routines
      */

    // NOTE: this follows ServletRequest.getParameter()
    // DO NOT USE if you expect more than one value to be provided for <i>key</i>
    public String getParameter(String key)
    {
        if (null == _parameters) return null;
        for (Pair<String, String> p : _parameters)
        {
            if ((p.first).equals(key))
                return p.second;
        }
        return null;
    }


    public URLHelper addParameter(Enum key, boolean value)
    {
        return addParameter(key.name(), value);
    }

    public URLHelper addParameter(String key, boolean value)
    {
        return addParameter(key, Boolean.toString(value));
    }


    public URLHelper addParameter(String key, String value)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        if (key.equals("returnURL"))
            ReturnUrlForm.throwBadParam();
        if (null == _parameters) _parameters = new ArrayList<>();
        _parameters.add(new Pair<>(key, value));
        return this;
    }


    public URLHelper addParameters(Map m)
    {
        return addParameters(null, m);
    }


    public URLHelper addParameters(Pair<String,String>[] parameters)
    {
        return addParameters(Arrays.asList(parameters));
    }

    public URLHelper addParameters(List<Pair<String,String>> parameters)
    {
        for (Pair<String, String> param : parameters)
            addParameter(param.getKey(), param.getValue());

        return this;
    }


    public URLHelper addParameters(String prefix, Map m)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        if (null == _parameters) _parameters = new ArrayList<>();
        for (Object o : m.entrySet())
        {
            Map.Entry e = (Map.Entry) o;
            if (null == e.getKey() || null == e.getValue()) continue;
            String key = (null == prefix) ? String.valueOf(e.getKey()) :
                    prefix + String.valueOf(e.getKey());
            // HttpServletRequest.getParameterMap() returns String->String[], so handle those specially here
            if (e.getValue() instanceof String[])
            {
                for (String value : (String[])e.getValue())
                {
                    addParameter(key, value);
                }
            }
            else
            {
                _parameters.add(new Pair<>(key, String.valueOf(e.getValue())));
            }
        }
        return this;
    }


    private final String[] _emptyStringArray = new String[0];

    public String[] getKeysByPrefix(String prefix)
    {
        if (null == _parameters)
            return _emptyStringArray;
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (Pair<String, String> parameter : _parameters)
        {
            String k = parameter.first;
            if (k.startsWith(prefix))
                keys.add(k);
        }
        return keys.toArray(new String[keys.size()]);
    }


    public List<Pair<String,String>> getParameters()
    {
        if (null == _parameters)
            return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(_parameters));
    }


    public List<String> getParameterValues(String key)
    {
        if (null == _parameters)
            return Collections.emptyList();
        List<String> keys = new ArrayList<>();
        for (Pair<String, String> p : _parameters)
        {
            String k = p.first;
            if (key.equals(k))
                keys.add(p.second);
        }
        return Collections.unmodifiableList(keys);
    }


    /**
     * delete parameter with given key, exact match
     */
    public URLHelper deleteParameter(String key)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        if (null != _parameters)
        {
            for (int i = _parameters.size() - 1; i >= 0; i--)
            {
                String k = _parameters.get(i).first;
                if (k.equals(key))
                    _parameters.remove(i);
            }
        }
        return this;
    }

    /**
     * delete all parameters
     */
    public URLHelper deleteParameters()
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        if (null != _parameters) _parameters.clear();
        return this;
    }


    /**
     * delete parameter with given key, exact match or ~suffix match
     */
    public URLHelper deleteFilterParameters(String key)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        if (null != _parameters)
        {
            for (int i = _parameters.size() - 1; i >= 0; i--)
            {
                String k = _parameters.get(i).first;
                if (k.startsWith(key) && (k.equals(key) || k.charAt(key.length()) == '~'))
                    _parameters.remove(i);
            }
        }
        return this;
    }


    /** same as deleteFilterParameters, but for "." instead of "~" */
    public URLHelper deleteScopeParameters(String key)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        if (null != _parameters)
        {
            for (int i = _parameters.size() - 1; i >= 0; i--)
            {
                String k = _parameters.get(i).first;
                if (k.startsWith(key) && (k.equals(key) || k.charAt(key.length()) == '.'))
                    _parameters.remove(i);
            }
        }
        return this;
    }


    /**
     * get all parameters that start with key + "."
     * NOTE does not handle duplicate keys
     */
    public Map<String,String> getScopeParameters(String scope)
    {
        TreeMap<String,String> m = new TreeMap<>();
        for (Pair<String,String> p : _parameters)
        {
            String key = p.getKey();
            if (key.length() > scope.length() && key.startsWith(scope) && key.charAt(scope.length()) == '.')
                m.put(key.substring(scope.length()+1), p.getValue());
        }
        return m;
    }


    public URLHelper replaceParameter(String key, String value)
    {
        if (_readOnly) throw new java.lang.IllegalStateException("This ActionURL is immutable");
        // could try preserve parameter order...
        deleteParameter(key);
        return addParameter(key, value);
    }

    public URLHelper replaceParameter(String key, long value)
    {
        return replaceParameter(key, Long.toString(value));
    }

    // CONSIDER: convert URLHelper implementation to use PropertyValues internally
    public PropertyValues getPropertyValues()
    {
        if (null == _parameters || _parameters.size() == 0)
            return new MutablePropertyValues();
        // convert multiple values to String[] if necessary
        MultiValuedMap<String, String> map = new ArrayListValuedHashMap<>();
        for (Pair<String, String> p : _parameters)
            map.put(p.getKey(), p.getValue());
        MutablePropertyValues mpvs = new MutablePropertyValues();
        for (Map.Entry<String, Collection<String>> m : map.asMap().entrySet())
        {
            if (m.getValue().size() == 1)
                mpvs.addPropertyValue(m.getKey(), ((List<String>)m.getValue()).get(0));
            else
                mpvs.addPropertyValue(m.getKey(), m.getValue().toArray(new String[m.getValue().size()]));
        }
        return mpvs;
    }


    public void setPropertyValues(PropertyValues pvs)
    {
        deleteParameters();
        addPropertyValues(pvs);
    }


    public void addPropertyValues(PropertyValues pvs)
    {
        if (null == pvs)
            return;
        for (PropertyValue pv : pvs.getPropertyValues())
        {
            Object v = pv.getValue();
            if (null == v)
                continue;
            if (v.getClass().isArray())
            {
                Object[] a = (Object[])v;
                for (Object o : a)
                {
                    if (o != null)
                        addParameter(pv.getName(), String.valueOf(o));

                }
            }
            else
            {
                addParameter(pv.getName(), String.valueOf(v));
            }
        }
    }


    public URLHelper setFile(String name)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        Path p = null==_path ? Path.rootPath : _path;
        if (!p.isDirectory())
            p = p.getParent();
        if (null != name)
            _path = p.append(name, false);
        return this;
    }


    public String getFile()
    {
        if (!_path.isDirectory())
            return null;
        return _path.getName();
    }


    @JsonValue
    public String toString()
    {
        return getLocalURIString(true);
    }


    @Override
    public URLHelper clone()
    {
        try
        {
            URLHelper n = (URLHelper) super.clone();
            n._path = _path;
            n._readOnly = false;
            n._parameters = _parameters == null ? null : new ArrayList<>(_parameters.size());
            if (null != _parameters)
                n._parameters.addAll(_parameters.stream().map(Pair::copy).collect(Collectors.toList()));
            return n;
        }
        catch (Exception x)
        {
            LOG.error("unexpected error", x);
            throw new RuntimeException(x);
        }
    }



    // CONSIDER: translate internal representation to use PropertyValues
    public void addParameters(PropertyValues pvs)
    {
        addParameters(null, pvs);
    }

    public void addParameters(String prefix, PropertyValues pvs)
    {
        for (PropertyValue pv : pvs.getPropertyValues())
        {
            String name = null == prefix ? pv.getName() : prefix + "." + pv.getName();
            Object v = pv.getValue();
            if (null == v) continue;
            if (!(v instanceof Array))
                addParameter(name,String.valueOf(v));
            else
            {
                for (Object o : (Object[])v)
                {
                    addParameter(name,String.valueOf(o));
                }
            }
        }
    }


    // like HttpRequest.getParameterMap
    public Map<String, String[]> getParameterMap()
    {
        PropertyValues pvs = getPropertyValues();
        HashMap<String,String[]> map = new HashMap<>();
        for (PropertyValue pv : pvs.getPropertyValues())
        {
            String name = pv.getName();
            Object o = pv.getValue();
            if (o instanceof String || o == null)
                map.put(name, new String[]{(String)o});
            else
                map.put(name, (String[])o);
        }
        return map;
    }


    // like HttpRequest.getParameterMap
    public Enumeration<String> getParameterNames()
    {
        Hashtable<String,String> h = new Hashtable<>();
        if (null != _parameters)
            for (Pair<String,String> p : _parameters)
                h.put(p.getKey(), p.getKey());
        return h.keys();
    }


    /**
     * works for URLs with out multi-valued parameters
     * no guarantees about what happens with multi-valued parameters, impl may change
     */
    public static boolean queryEqual(URLHelper a, URLHelper b)
    {
        // null check
        if (a._parameters == b._parameters) return true;
        if (a._parameters == null || b._parameters == null) return false;
        if (a._parameters.size() != b._parameters.size()) return false;
        HashMap<String,String> bmap = new HashMap<>(b._parameters.size());
        for (Pair<String,String> p : b._parameters)
            bmap.put(p.first, p.second);
        for (Pair<String,String> p : a._parameters)
        {
            if (!StringUtils.isEmpty(p.second) && !p.second.equals(bmap.get(p.first)))
                return false;
        }
        return true;
    }
    


    public static class Converter implements org.apache.commons.beanutils.Converter
    {
        @Override
        public Object convert(Class type, Object value)
        {
            if (value == null)
                return null;
            if (value instanceof URLHelper)
                return value;
            try
            {
                return new URLHelper(String.valueOf(value));
            }
            catch (URISyntaxException e)
            {
                throw new ConversionException(e);
            }
        }
    }

    public void addFilter(String dataRegionName, FieldKey field, CompareType ct, Object value)
    {
        SimpleFilter filter = new SimpleFilter(field, value, ct);
        filter.applyToURL(this, dataRegionName);
    }

    public boolean isHttpURL()
    {
        if (null == getScheme() && null == getHost())
            return true;
        if (null == getScheme() || null == getHost())
            return false;
        String scheme = getScheme().toLowerCase();
        if ("https".equals(scheme) || "http".equals(scheme))
            return true;
        return false;
    }

    public static boolean isHttpURL(String url)
    {
        if (StringUtils.isEmpty(url))
            return false;
        try
        {
            return new URLHelper(url).isHttpURL();
        }
        catch (URISyntaxException x)
        {
            return false;
        }
    }

    public static String staticResourceUrl(String resourcePath)
    {
        boolean useStaticServer = false;
        if (useStaticServer)
        {
            // TODO this is for prototype only
            return "http://static:" + AppProps.getInstance().getServerPort() + resourcePath;
        }
        else
        {
            return AppProps.getInstance().getContextPath() + resourcePath;
        }
    }

    public boolean isConfiguredExternalHost()
    {
        String requestedHost = this.getHost();
        if (StringUtils.trimToNull(requestedHost) != null )
            return AppProps.getInstance().getExternalRedirectHosts().stream().anyMatch(requestedHost::equalsIgnoreCase);

        return false;
    }

    // Issue 35896 - Disallow external redirects to URLs not on the whitelist
    public boolean isAllowableHost()
    {
        String host = StringUtils.trimToNull(this.getHost());

        // We have a returnURL that includes a server host name
        if (host != null)
        {
            // Check if it matches the current server's preferred host name, per the base server URL setting
            String allowedHost = null;
            try
            {
                allowedHost = new URL(AppProps.getInstance().getBaseServerUrl()).getHost();
            }
            catch (MalformedURLException ignored) {}

            if (!host.equalsIgnoreCase(allowedHost))
            {
                // Server host name that doesn't match, log and possibly reject based on config
                // Allow 'localhost' for servers in dev mode
                boolean isConfigured = AppProps.getInstance().isDevMode() && "localhost".equalsIgnoreCase(host);
                isConfigured |= this.isConfiguredExternalHost();

                if (!isConfigured)
                {
                    String logMessageDetails = "returnURL value: " + this.toString();
                    HttpServletRequest request = HttpView.currentRequest();
                    if (request != null)
                    {
                        logMessageDetails += " from URL: " + request.getRequestURL();
                        if (request.getHeader("Referer") != null)
                        {
                            logMessageDetails += " with referrer: " + request.getHeader("Referer");
                        }
                    }

                    LOG.warn("Rejected external host redirect " + logMessageDetails +
                            "\nPlease configure external redirect url host from: Admin gear --> Site --> Admin Console --> Settings --> External Redirect Hosts");
                    return false;
                }
                else
                {
                    LOG.debug("Detected configured external host returnURL: " + this.toString());
                }
            }
        }

        return true;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testHtmlSafety() throws URISyntaxException
        {
            // urls are included in many jsp w/o additional html encoding
            // so you might ask... is it safe?
            URLHelper h;
            try
            {
                new URLHelper("http://server/<script>hi</script>");
                fail("Should fail with illegal character");
            }
            catch (URISyntaxException x)
            {
            }
            try
            {
                new URLHelper("http://server/\"file");
                fail("Should fail with illegal character");
            }
            catch (URISyntaxException x)
            {
            }
            h = new URLHelper("http://server/'%22%3Cscript%3Ehi%3C/script%3E");
            assertFalse(StringUtils.containsAny(h.toString(), "'\"<>"));
            assertFalse(StringUtils.containsAny(h.getLocalURIString(), "'\"<>"));
            h = new URLHelper("http://server/index.html?'%22<script>hi</script>=x");
            assertFalse(StringUtils.containsAny(h.toString(), "'\"<>"));
            assertFalse(StringUtils.containsAny(h.getLocalURIString(), "'\"<>"));
            h = new URLHelper("http://server/index.html?x='%22<script>hi</script>");
            assertFalse(StringUtils.containsAny(h.toString(), "'\"<>"));
            assertFalse(StringUtils.containsAny(h.getLocalURIString(), "'\"<>"));
        }

        @Test
        public void testQuotes() throws URISyntaxException
        {
            // Test that single and double quotes are encoded in URLs.  Helps ensure that they are safe in various contexts even if not re-encoded.
            String s;
            s = new URLHelper("http://server/single'quote/?q'uery=p'aram").toString();
            assertFalse(s.contains("'"));
            try
            {
                s = new URLHelper("http://server/double\"quote/?q\"uery=p\"aram").toString();
                fail("Expected throw URISyntaxException");
            }
            catch (URISyntaxException x)
            {
                /* expected */
            }
            s = new URLHelper("http://server/doublequote/?q\"uery=p\"aram").toString();
            assertFalse(s.contains("\""));
            try
            {
                s = new URLHelper("http://server/back\\slash/?q\\uery=p\\aram").toString();
                fail("Expected throw URISyntaxException");
            }
            catch (URISyntaxException x)
            {
                /* expected */
            }
            s = new URLHelper("http://server/backslash/?q\\uery=p\\aram").toString();
            assertFalse(s.contains("\\"));
        }

        @Test
        public void testParseWithHash() throws URISyntaxException
        {
            URLHelper h = new URLHelper("http://server/ehr-animalHistory.view?#subjects:AB12&inputType:singleSubject&showReport" +
                    ":1&activeReport:virusTesting");

            String url = "/ehr-animalHistory.view#subjects:AB12&inputType:singleSubject&showReport:1&activeReport:virusTesting";

            assertEquals(url, h.toString());
        }
    }
}
