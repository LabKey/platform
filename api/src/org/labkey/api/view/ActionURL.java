/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.view;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.collections.ArrayStack;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.query.FieldKey;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.common.util.Pair;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * This class has to be kept in sync with ViewServlet
 */
public class ActionURL extends URLHelper implements Cloneable
{
    protected static URLHelper.PathParser _viewParser = new _ViewParser();

    static final int indexContextPath = 0;
    static final int indexPageFlow = 1;
    static final int indexExtraPath = 2;
    static final int indexAction = 3;

    private boolean _baseServerPropsInitialized = false;

    static Pattern urlPattern = indexExtraPath == 1 ?
            // ExtraPath/PageFlow/Action
            Pattern.compile("(.*)/(\\w*(?:\\-\\w*)*)/(\\w*)\\.(.*)") :
            // PageFlow/ExtraPath/Action
            Pattern.compile("/(\\w*(?:\\-\\w*)*)(/.*)?/(\\w*)\\.(.*)");


    // ActionURL always uses the AppProps settings for scheme, host, and port.  We defer setting these since we
    // only need them when generating absolute URLs (which are rare).
    private void ensureBaseServerProps()
    {
        if (_baseServerPropsInitialized)
            return;

        _scheme = AppProps.getInstance().getScheme();
        _host = AppProps.getInstance().getServerName();
        _port = AppProps.getInstance().getServerPort();

        _baseServerPropsInitialized = true;
    }

    public int getPort()
    {
        ensureBaseServerProps();
        return super.getPort();
    }

    public String getScheme()
    {
        ensureBaseServerProps();
        return super.getScheme();
    }

    public String getHost()
    {
        ensureBaseServerProps();
        return super.getHost();
    }

    private static String getBaseServerURL(String scheme, String host, int port)
    {
        StringBuilder sb = getBaseServer(scheme, host, port);
        sb.append(AppProps.getInstance().getContextPath());
        return sb.toString();
    }

    public static String getBaseServerURL()
    {
        AppProps props = AppProps.getInstance();
        return getBaseServerURL(props.getScheme(), props.getServerName(), props.getServerPort());
    }

    public ActionURL()
    {
        this(true);
    }

    public ActionURL(boolean useAppContextPath)
    {
        _path = new ArrayStack(indexAction + 1);
        if (useAppContextPath)
            setContextPath(AppProps.getInstance().getContextPath());
    }

    /**
     * This is the main contstructor for creating ActionURLs.  Many of the others
     * exist for historic reasons.  Use this constructor where possible.
     *
     * @param actionClass the class of the Spring action
     * @param container the path for the container
     */
    public ActionURL(Class<? extends Controller> actionClass, Container container)
    {
        this(SpringActionController.getPageFlowName(actionClass), SpringActionController.getActionName(actionClass), container);
    }

    @Deprecated
    public static String toPathString(String pageFlow, String action, Container c)
    {
        return toPathString(pageFlow, action, c.getPath());
    }


    @Deprecated
    public static String toPathString(String pageFlow, String action, String extraPath)
    {
        String[] parts = new String[4];
        parts[indexContextPath] = AppProps.getInstance().getContextPath(); //request.getContextPath();
        parts[indexPageFlow] = pageFlow;
        parts[indexExtraPath] = (null == extraPath ? "" : extraPath);  // Allow extraPath == null
        parts[indexAction] = action;
        return _viewParser.toPathString(parts, true, true);
    }


    /**
     * Old pageflow constructor
     */
    @Deprecated
    public ActionURL(String pageFlow, String actionName, Container container)
    {
        this(pageFlow, actionName, container.getPath());
    }

    /**
     * Worse old page flow constructor 
     */
    @Deprecated
    public ActionURL(String pageFlow, String actionName, String extraPath)
    {
        this(true);
        setPageFlow(pageFlow);
        setExtraPath(extraPath);
        setAction(actionName);
    }


    @Deprecated
    public ActionURL setPageFlow(String pageFlow)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _arraySet(_path, indexPageFlow, pageFlow);

        return this;
    }


    /**
     * Create a url based on the container in this URL
     *
     * @param action   New action. No encoding or substitution will occur
     * @param params   New params. All old params will be deleted. No encoding will occur so that substitution using
     *                 ${} will work properly
     * @param pageFlow Name of the pageflow to redirect to
     */
    @Deprecated
    public String relativeUrl(String action, String params, String pageFlow)
    {
        String pathString = toPathString(pageFlow, action, getExtraPath());
        return pathString + "?" + (null == params ? "" : params);
    }

    /**
     * Return a string URL based on the container in this URL.
     * param map will be placed in params. No encoding will be done on param values of the
     * form ${substExpression}  Other param values will be encoded.
     *
     * @param action       The page flow action. Cannot be null
     * @param params       Params will be "replaced" on URL. Don't use duplicate params here.
     * @param pageFlow     current pageflow if null
     * @param deleteParams delete parameters before replacing
     */
    @Deprecated
    public String relativeUrl(String action, Map params, String pageFlow, boolean deleteParams)
    {
        assert null != action;
        String paramStr;

        ActionURL url = this.clone();
        if (deleteParams)
            url.deleteParameters();

        if (null != params)
        {
            Collection<Map.Entry<String, String>> entries = params.entrySet();

            for (Map.Entry entry : entries)
                url.replaceParameter(entry.getKey().toString(), entry.getValue().toString());
        }

        //NOTE. Don't encode parameters cause we'll use for subst later
        StringBuffer sb = new StringBuffer();
        for (Pair<String, String> parameter : url._parameters)
        {
            sb.append("&").append(parameter.getKey()).append("=");
            String value = parameter.getValue();
            if (null != value && value.length() >= 3 && value.charAt(0) == '$' && value.charAt(1) == '{' && value.charAt(value.length() - 1) == '}')
                sb.append(value);
            else
                sb.append(PageFlowUtil.encode(value));
        }
        paramStr = sb.toString();

        return relativeUrl(action, paramStr, pageFlow != null ? pageFlow : getPageFlow());
    }

    /**
     * Create a url based on the container in this URL and pageFlow
     *
     * @param action New action. No encoding or substitution will occur
     * @param params New params. All old params will be deleted. No encoding will occur so that substitution using
     *               ${} will work properly
     */
    @Deprecated
    public String relativeUrl(String action, String params)
    {
        return relativeUrl(action, params, getPageFlow());
    }

    /**
     * used by ViewContext
     */
    ActionURL(HttpServletRequest request)
            throws ServletException
    {
        // UNDONE: rearrange this code so that it uses parsePath()
        this();
        Matcher m = urlPattern.matcher(request.getServletPath());
        if (!m.matches())
            throw new ServletException("invalid path");

        assert getContextPath().equals(request.getContextPath()) : "contextPath is not configured properly, check application configuration";

        setContextPath(request.getContextPath());
        setExtraPath(m.group(indexExtraPath));
        setPageFlow(m.group(indexPageFlow));
        setAction(m.group(indexAction));
        setRawQuery(request.getQueryString());

        setHost(request.getServerName());
        setPort(request.getServerPort());
        setScheme(request.getScheme());
    }

    public String getParameter(Enum key)
    {
        return getParameter(key.toString());
    }

    @Override
    public ActionURL addParameter(String key, String value)
    {
        return (ActionURL) super.addParameter(key, value);
    }

    public ActionURL addParameter(Enum key, int value)
    {
        return addParameter(key.name(), value);
    }

    public ActionURL addParameter(String key, int value)
    {
        return (ActionURL) super.addParameter(key, String.valueOf(value));
    }

    public ActionURL addParameter(Enum e, String value)
    {
        return addParameter(e.name(), value);
    }

    @Override
    public ActionURL addParameters(Map m)
    {
        return (ActionURL) super.addParameters(m);
    }

    @Override
    public ActionURL addParameters(Pair<String, String>[] parameters)
    {
        return (ActionURL)super.addParameters(parameters);
    }

    @Override
    public ActionURL addParameters(String prefix, Map m)
    {
        return (ActionURL) super.addParameters(prefix, m);
    }

    @Override
    public ActionURL deleteParameter(String key)
    {
        return (ActionURL) super.deleteParameter(key);
    }

    public ActionURL deleteParameter(Enum key)
    {
        return (ActionURL) super.deleteParameter(key.name());
    }

    @Override
    public ActionURL deleteParameters()
    {
        return (ActionURL) super.deleteParameters();
    }

    @Override
    public ActionURL deleteFilterParameters(String key)
    {
        return (ActionURL) super.deleteFilterParameters(key);
    }

    @Override
    public ActionURL deleteScopeParameters(String key)
    {
        return (ActionURL) super.deleteScopeParameters(key);
    }
    
    public ActionURL replaceParameter(Enum key, String value)
    {
        return replaceParameter(key.toString(), value);
    }

    @Override
    public ActionURL replaceParameter(String key, String value)
    {
        return (ActionURL) super.replaceParameter(key, value);
    }


    // Add returnURL as a parameter using standard parameter name
    public ActionURL addReturnURL(ActionURL returnURL)
    {
        return addParameter(ReturnUrlForm.Params.returnUrl, returnURL.getLocalURIString());
    }


    public ActionURL(String url)
    {
        this();
        // UNDONE: should just hand up to URLHelper and let parsePath work
        // UNDONE: however there is a problem with context path handling
        String context = getContextPath();
        URI uri;
        try
        {
            uri = new URI(url);
        }
        catch (URISyntaxException use)
        {
            throw new IllegalArgumentException(use.getMessage(), use);
        }

        String path = uri.getPath();
        if (path.startsWith(context))
            if (!"/".equals(context))
                path = path.substring(context.length());

        Matcher m = urlPattern.matcher(path);
        if (!m.matches())
            throw new IllegalArgumentException(url +  " is not a valid ViewServlet url.");

        setExtraPath(m.group(indexExtraPath));
        setPageFlow(m.group(indexPageFlow));
        setAction(m.group(indexAction));

        String query = uri.getRawQuery();
        if (null != query && query.length() > 0)
            setRawQuery(query);
    }


    public ActionURL setContextPath(String contextPath)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _arraySet(_path, indexContextPath, contextPath);
        return this;
    }


    public String getContextPath()
    {
        return _path.size() > indexContextPath ? (String) _path.get(indexContextPath) : null;
    }


    public ActionURL setExtraPath(String extraPath)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        if (null == extraPath) extraPath = "";
        _arraySet(_path, indexExtraPath, extraPath);

        return this;
    }


    public String getExtraPath()
    {
        return _path.size() > indexExtraPath ? (String) _path.get(indexExtraPath) : null;
    }


    public String getPageFlow()
    {
        return _path.size() > indexPageFlow ? (String) _path.get(indexPageFlow) : null;
    }

    public ActionURL setAction(Class<? extends Controller> actionClass)
    {
        setPageFlow(SpringActionController.getPageFlowName(actionClass));
        setAction(SpringActionController.getActionName(actionClass));
        return this;
    }

    @Deprecated
    public ActionURL setAction(String action)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _arraySet(_path, indexAction, action);
        _hasFile = action != null;
        return this;
    }


    public String getAction()
    {
        return _path.size() > indexAction ? (String) _path.get(indexAction) : null;
    }


    private static boolean _isEmpty(String s)
    {
        return null == s || 0 == s.length();
    }


    private static void _arraySet(ArrayList<String> a, int i, String s)
    {
        while (a.size() <= i)
            a.add(null);
        a.set(i, s);
    }


    private static boolean _inRange(String[] a, int i)
    {
        return 0 <= i && i < a.length;
    }


    protected URLHelper.PathParser pathParser()
    {
        return _viewParser;
    }


    public static class _ViewParser implements URLHelper.PathParser
    {
        public String[] parsePath(String path)
        {
            assert false : "NYI: need to know context path";
            return new String[0];
        }


        private StringBuffer _pathAppend(StringBuffer sb, String part, boolean encode)
        {
            // make sure there is exactly one / between current path and new part
            if (!(sb.length() > 0 && sb.charAt(sb.length() - 1) == '/'))
                sb.append('/');

            if (part.length() > 0 && part.charAt(0) == '/')
                part = part.substring(1);

            String enc = encode ? PageFlowUtil.encode(part) : part;
            sb.append(enc);
            return sb;
        }


        public String toPathString(String[] path, boolean _hasFile, boolean encode)
        {
            StringBuffer sb = new StringBuffer();

            // contextPath
            if (_inRange(path, indexContextPath) && !_isEmpty(path[indexContextPath]))
                _pathAppend(sb, path[indexContextPath], encode);

            if (_inRange(path, indexPageFlow) && !_isEmpty(path[indexPageFlow]))
                _pathAppend(sb, path[indexPageFlow], encode);

            if (_inRange(path, indexExtraPath))
            {
                // Pull apart extraPath, encode each bit, and piece it together again... allows for spaces, etc. in folder names
                if (path[indexExtraPath].equals("/") || path[indexExtraPath].equals(""))
                    _pathAppend(sb, "", encode);
                else
                {
                    String[] extraPathParts = path[indexExtraPath].split("/");
                    for (String extraPathPart : extraPathParts)
                        _pathAppend(sb, extraPathPart, encode);
                }
                if (indexExtraPath == path.length - 1)
                    _pathAppend(sb, "begin.view", false);
            }

            // action
            if (_inRange(path, indexAction) && null != path[indexAction])
            {
                _pathAppend(sb, path[indexAction], encode);
                if (-1 == path[indexAction].indexOf('.'))
                    sb.append(".view");
            }
            else
                _pathAppend(sb, "", encode);

            return sb.toString();
        }
    }


    @Override
    public ActionURL clone()
    {
        return (ActionURL) super.clone();
    }


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


        public void test() throws Exception
        {
            String s;

            ActionURL a = new ActionURL("PageFlow", "action", "path");
            a.addParameter("key", "a");
            ActionURL b = a.clone();

//            TestCase.assertTrue(a._path != b._path);
//            TestCase.assertTrue(a._parameters != b._parameters);

            // url is unchanged after clone
            String stringA = a.getLocalURIString();
            String stringB = b.getLocalURIString();
            TestCase.assertEquals(stringA, stringB);

            // modify ActionURL
            a.replaceParameter("key", "A");

            // a is changed
            s = a.getLocalURIString();
            assertTrue(!stringA.equals(s));

            // b is not changed
            s = b.getLocalURIString();
            TestCase.assertEquals(s, stringB);

            ActionURL parse = new ActionURL("/Controller/path/action.view?foo=bar");
            String toString = parse.getLocalURIString();
            assertEquals(parse.getContextPath() + "/Controller/path/action.view?foo=bar", toString);
        }


        public static Test suite()
        {
            TestSuite suite = new TestSuite(TestCase.class);
            return suite;
        }
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
