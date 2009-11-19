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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.collections.ArrayStack;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.Category;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.labkey.api.query.FieldKey;
import org.labkey.api.data.CompareType;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;


public class URLHelper implements Cloneable, Serializable, Taintable
{
    /**
     * this interface allows user to plug in his own path interpreter
     */
    public interface PathParser
    {
        /**
         * path is already normalized and decoded
         */
        public String[] parsePath(String path);

        public String toPathString(String path[], boolean hasFile, boolean encode);
    }

    protected static PathParser _defaultParser = new _DirParser();
    static Category _log = Logger.getInstance(URLHelper.class);


    protected boolean _tainted = false;
    protected String _scheme = "http";
    protected String _userInfo = null;
    protected String _host = null;
    protected int _port = 80;
    protected boolean _hasFile = false;        // is the top of the path a directory or a file?
    protected ArrayStack _path = null;        // path segments usually directories and filename
    protected ArrayList<Pair<String, String>> _parameters = null;    // decoded key/value pairs
    protected String _fragment = null;

    protected boolean _readOnly = false;

    protected URLHelper()
    {
    }


    public URLHelper(URI uri)
    {
        _setURI(uri);
    }


    public URLHelper(HString url) throws URISyntaxException
    {
        this(url.getSource());
        _tainted = url.isTainted();
    }


    public URLHelper(String url) throws URISyntaxException
    {
        String p;
        String q;
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
        _setURI(new URI(p));
        setRawQuery(q);
    }


    public URLHelper(HttpServletRequest req)
    {
        setHost(req.getServerName());
        setPort(req.getServerPort());
        setScheme(req.getScheme());

        _parsePath(req.getRequestURI());
        _parseQuery(req.getQueryString(), req.getCharacterEncoding());

        /*
        if (null != req.getQueryString()  && req.getQueryString().length() > 0)
            _setURI(new URI(req.getRequestURL().append("?").append(req.getQueryString()).toString()));
        else
            _setURI(new URI(req.getRequestURL().toString()));
        */
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
        if (port != -1 && ((port != 80 && "http".equals(scheme)) || (port != 443 && "https".equals(scheme))))
        {
            serverBuilder.append(":").append(port);
        }
        return serverBuilder;
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


    public HString toLocalString(boolean allowSubstSyntax)
    {
        String local = getLocalURIString(allowSubstSyntax);
        return new HString(local,_tainted);
    }


    public String getLocalURIString()
    {
        return getLocalURIString(false);
    }


    public String getLocalURIString(boolean allowSubstSyntax)
    {
        StringBuilder uriString = new StringBuilder(getPath());
        boolean hasParams = (null != _parameters && _parameters.size() > 0);
        if (_hasFile || hasParams)
            uriString.append('?');      // makes it easier for users who want to concatentate
        if (hasParams)
            uriString.append(getQueryString(allowSubstSyntax));
        if (null != _fragment && _fragment.length() > 0)
            uriString.append("#").append(_fragment);

        if (_tainted)
        {
            //_log.warn("tainted URL: " + uriString.toString());
        }

        return uriString.toString();
    }


    // Applies HTML encoding to local URI string
    public String getEncodedLocalURIString()
    {
        return PageFlowUtil.filter(getLocalURIString());
    }


    /* server or web-app relative URI
     *
     * @param asForward if true return web-app relative URI
     * @return URI
    public URI getLocalURI(boolean asForward)
    {
        try
        {
            URI uri;
            uri = new URI(null, null, getPath(asForward), getQueryString(), getFragment());
            return uri;
        }
        catch (URISyntaxException x)
        {
            throw new RuntimeException(x);
        }
    }
    */


    protected void _setURI(URI uri)
    {
        setHost(uri.getHost());
        setPort(uri.getPort());       // TODO: Don't store -1 if port is not specified -- use scheme to save default ports
        _parsePath(uri.getPath());
        _parseQuery(uri.getRawQuery());
        setScheme(uri.getScheme());
        _fragment = uri.getFragment();
    }


    protected void _parsePath(String path)
    {
        if (null == path || 0 == path.length())
            return;
        boolean fRelative = true;
        if (path.startsWith("/"))
        {
            fRelative = false;
            path = path.substring(1);
        }
        String[] parts = pathParser().parsePath(path);
        _path = new ArrayStack();
        if (fRelative)
            _path.push(".");
        for (String part : parts)
            _path.push(PageFlowUtil.decode(part));
        _hasFile = !path.endsWith("/");
    }


    protected void _parseQuery(String query)
    {
        _parseQuery(query, "UTF-8");
    }

    protected void _parseQuery(String query, String encoding)
    {
        Pair<String, String>[] pairs = PageFlowUtil.fromQueryString(query, encoding);
        _parameters = new ArrayList<Pair<String, String>>(Arrays.asList(pairs));
    }


    public String getPath()
    {
        return getPath(false);
    }


    public String getPath(boolean asForward)
    {
        if (null == _path)
            return "";
        //noinspection unchecked
        String[] paths = (String[])_path.toArray(new String[_path.size()]);
        if (asForward)
            paths[0] = null; // contextPath
        String path;
        path = pathParser().toPathString(paths, _hasFile, !asForward);
        return path;
    }


    public String getQueryString()
    {
        return PageFlowUtil.toQueryString(_parameters);
    }

    public String getQueryString(boolean allowSubstSyntax)
    {
        return PageFlowUtil.toQueryString(_parameters, allowSubstSyntax);
    }

    public void setHost(String host)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _host = host;
    }


    public void setPath(String path)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _parsePath(path);
    }


    public void setPort(int port)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _port = port;
    }


    public void setRawQuery(String query)
    {
        //_query = query;
        _parseQuery(query);
    }


    public void setScheme(String scheme)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _scheme = scheme;
    }


    public void setFragment(String fragment)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _fragment = fragment;
    }


    public String getHost()
    {
        return _host;
    }


    public Object[] getPathParts()
    {
        return null == _path ? new Object[0] : _path.toArray();
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

    // still need other versions of getParameter
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
        if (null == _parameters) _parameters = new ArrayList<Pair<String, String>>();
        _parameters.add(new Pair<String, String>(key, value));
        return this;
    }


    public URLHelper addParameter(String key, HString value)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        if (null == _parameters)
            _parameters = new ArrayList<Pair<String, String>>();
        _parameters.add(new Pair<String, String>(key, null==value ? null : value.getSource()));
        _tainted |= (null != value && value.isTainted());
        return this;
    }
    

    public URLHelper addParameters(Map m)
    {
        return addParameters(null, m);
    }


    public URLHelper addParameters(Pair<String,String>[] parameters)
    {
        for (Pair<String, String> param : parameters)
            addParameter(param.getKey(), param.getValue());

        return this;
    }


    public URLHelper addParameters(String prefix, Map m)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        if (null == _parameters) _parameters = new ArrayList<Pair<String, String>>();
        for (Object o : m.entrySet())
        {
            Map.Entry e = (Map.Entry) o;
            if (null == e.getKey() || null == e.getValue()) continue;
            String key = (null == prefix) ? String.valueOf(e.getKey()) :
                    prefix + String.valueOf(e.getKey());
            _parameters.add(new Pair<String, String>(key, String.valueOf(e.getValue())));
        }
        return this;
    }


    private final String[] _emptyStringArray = new String[0];

    public String[] getKeysByPrefix(String prefix)
    {
        if (null == _parameters)
            return _emptyStringArray;
        LinkedHashSet<String> keys = new LinkedHashSet<String>();
        for (Pair<String, String> parameter : _parameters)
        {
            String k = parameter.first;
            if (k.startsWith(prefix))
                keys.add(k);
        }
        return keys.toArray(new String[keys.size()]);
    }


    public Pair<String,String>[] getParameters()
    {
        if (null == _parameters)
            return new Pair[0];
        return _parameters.<Pair<String, String>>toArray(new Pair[_parameters.size()]);
    }


    public String[] getParameters(String key)
    {
        if (null == _parameters)
            return _emptyStringArray;
        ArrayList<String> keys = new ArrayList<String>();
        for (Pair<String, String> p : _parameters)
        {
            String k = p.first;
            if (key.equals(k))
                keys.add(p.second);
        }
        return keys.toArray(new String[keys.size()]);
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
        TreeMap<String,String> m = new TreeMap<String,String>();
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
        if (_readOnly) throw new java.lang.IllegalStateException();
        // could try preserve parameter order...
        deleteParameter(key);
        return addParameter(key, value);
    }


    // CONSIDER: convert URLHelper implementation to use PropertyValues internally
    public PropertyValues getPropertyValues()
    {
        if (null == _parameters || _parameters.size() == 0)
            return new MutablePropertyValues();
        // convert multiple values to String[] if necessary
        MultiHashMap<String,String> map = new MultiHashMap<String,String>();
        for (Pair<String,String> p : _parameters)
            map.put(p.getKey(), p.getValue());
        MutablePropertyValues mpvs = new MutablePropertyValues();
        for (Map.Entry<String,Collection<String>> m : map.entrySet())
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


    /**
     * path manipulation
     */
    public void pushPath(Object o)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        if (null == _path) _path = new ArrayStack();
        if (_hasFile)
        {
            throw new java.lang.IllegalStateException("File already specified");
            // could try to fix up
        }
        _path.push(o);
    }


    public void setFile(Object o)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        if (null == _path)
            _path = new ArrayStack();
        if (_hasFile)
        {
            _path.pop();
            _hasFile = false;
        }
        if (null != o)
        {
            _path.push(o);
            _hasFile = true;
        }
    }


    public String getFile()
    {
        if (!_hasFile)
            return null;
        return (String) _path.peek();
    }


    public Object popPath()
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        if (_hasFile)
            _path.pop();
        _hasFile = false;
        return _path.pop();
    }


    protected PathParser pathParser()
    {
        return _defaultParser;
    }


    public static class _DirParser implements PathParser
    {
        public String[] parsePath(String path)
        {
            if (null == path || 0 == path.length())
                return new String[0];
            return path.split("/");
        }

        public String toPathString(String[] path, boolean _hasFile, boolean encode)
        {
            if (null == path || 0 == path.length)
                return "";
            StringBuilder sb = new StringBuilder();
            if (!".".equals(path[0]))
                sb.append('/');
            for (String p : path)
            {
                String enc = encode ? PageFlowUtil.encode(p) : p;
                sb.append(enc).append('/');
            }
            if (_hasFile)
                sb.setLength(sb.length() - 1);
            return sb.toString();
        }
    }


    public String toString()
    {
        return getLocalURIString(true);
    }


    public HString toHString()
    {
        return toLocalString(true);
    }


    @Override
    public URLHelper clone()
    {
        try
        {
            URLHelper n = (URLHelper) super.clone();
            n._path = (ArrayStack) _path.clone();
            n._readOnly = false;
            n._parameters = _parameters==null ? null : new ArrayList<Pair<String,String>>(_parameters.size());
            if (null != _parameters)
                for (Pair<String,String> p : _parameters)
                    n._parameters.add(p.copy());
            return n;
        }
        catch (Exception x)
        {
            Logger.getLogger(URLHelper.class).error("unexpected error", x);
            throw new RuntimeException(x);
        }
    }



    public void translatePrefix(URLHelper source, String oldPrefix, String newPrefix)
    {
        for (String key : source.getKeysByPrefix(oldPrefix))
        {
            String newKey = newPrefix + oldPrefix.substring(key.length());
            for (String value : source.getParameters(key))
            {
                addParameter(newKey, value);
            }
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
    public Map getParameterMap()
    {
        PropertyValues pvs = getPropertyValues();
        HashMap<String,String[]> map = new HashMap<String,String[]>();
        for (PropertyValue pv : pvs.getPropertyValues())
        {
            String name = pv.getName();
            Object o = pv.getValue();
            if (o instanceof String)
                map.put(name, new String[]{(String)o});
            else
                map.put(pv.getName(), (String[])o);
        }
        return map;
    }


    // like HttpRequest.getParameterMap
    public Enumeration getParameterNames()
    {
        Hashtable<String,String> h = new Hashtable<String,String>();
        for (Pair<String,String> p : _parameters)
            h.put(p.getKey(), p.getKey());
        return h.keys();
    }



    public static class Converter implements org.apache.commons.beanutils.Converter
    {
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


    public boolean isTainted()
    {
        return _tainted;
    }


    public void addFilter(String dataRegionName, FieldKey field, CompareType ct, Object value)
    {
        StringBuilder key = new StringBuilder();
        if (!StringUtils.isEmpty(dataRegionName))
        {
            key.append(dataRegionName);
            key.append(".");
        }
        key.append(field);
        key.append("~");
        key.append(ct.getUrlKey());
        addParameter(key.toString(), value == null ? "" : value.toString());
    }
}
