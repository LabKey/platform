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
package org.labkey.api.view;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.data.Container;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.*;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;


/**
 * This class has to be kept in sync with ViewServlet
 */
public class ActionURL extends URLHelper implements Cloneable
{
    private boolean _baseServerPropsInitialized = false;
    
    // ActionURL always uses the AppProps settings for scheme, host, and port.  We defer setting these since we
    // only need them when generating absolute URLs (which are rare).

    private String _controller = "project";
    private String _action = "begin";

    
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
        if (useAppContextPath)
            setContextPath(AppProps.getInstance().getContextPath());
    }

    /**
     * This is the main constructor for creating ActionURLs.  Many of the others
     * exist for historic reasons.  Use this constructor where possible.
     *
     * @param actionClass the class of the Spring action
     * @param container the path for the container
     */
    public ActionURL(Class<? extends Controller> actionClass, Container container)
    {
        this(true);
        _controller = SpringActionController.getPageFlowName(actionClass);
        _action = SpringActionController.getActionName(actionClass);
        setContainer(container);
    }


    /**
     * Old pageflow constructor
     */
    @Deprecated
    public ActionURL(String pageFlow, String actionName, Container container)
    {
        this(true);
        _controller = pageFlow;
        _action = actionName;
        setContainer(container);
    }


    /**
     * Worse old page flow constructor 
     */
    @Deprecated
    public ActionURL(String pageFlow, String actionName, String extraPath)
    {
        this(true);
        _controller = pageFlow;
        _action = actionName;
        setExtraPath(extraPath);
    }


    /**
     * used by ViewContext
     */
    ActionURL(HttpServletRequest request)
            throws ServletException
    {
        this();

        try
        {
            setPath(request.getServletPath());
        }
        catch (IllegalArgumentException x)
        {
            throw new ServletException("Invalid path");
        }
        assert getContextPath().equals(request.getContextPath()) : "contextPath is not configured properly, check application configuration";

        setContextPath(request.getContextPath());
        setRawQuery(request.getQueryString());
        setHost(request.getServerName());
        setPort(request.getServerPort());
        setScheme(request.getScheme());
    }


    private static String toPathString(Path contextPath, String pageFlow, String action, Path extraPath, boolean encode)
    {
        Path path = contextPath.append(pageFlow).append(extraPath);
        if (null != action)
        {
            if (-1 == action.indexOf('.'))
                action = action + ".view";
            path = path.append(action, false);
        }
        String str = encode ? path.encode() : path.toString();
        return str;
    }


    private static String toPathString(String pageFlow, String action, Path extraPath, boolean encode)
    {
        Path contextPath = AppProps.getInstance().getParsedContextPath();
        return toPathString(contextPath, pageFlow, action, extraPath, encode);
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
        String pathString = toPathString(pageFlow, action, _path, true);
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


    public String getParameter(Enum key)
    {
        return getParameter(key.toString());
    }

    @Override
    public ActionURL addParameter(Enum key, boolean value)
    {
        return (ActionURL)super.addParameter(key, value);
    }

    @Override
    public ActionURL addParameter(String key, boolean value)
    {
        return (ActionURL)super.addParameter(key, value);
    }

    @Override
    public ActionURL addParameter(String key, HString value)
    {
        return (ActionURL) super.addParameter(key, value);
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

    public ActionURL addParameter(Enum e, HString value)
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


    // TODO: Remove, since it's redundant with the below.  Left in place for now to avoid rebuild
    // Add returnURL as a parameter using standard parameter name
    public ActionURL addReturnURL(ActionURL returnURL)
    {
        return addParameter(ReturnUrlForm.Params.returnUrl, returnURL.getLocalURIString());
    }


    // Add returnURL as a parameter using standard parameter name
    public ActionURL addReturnURL(URLHelper returnURL)
    {
        return addParameter(ReturnUrlForm.Params.returnUrl, returnURL.getLocalURIString());
    }


    public ActionURL(HString url)
    {
        this(url.getSource());
        // UNDONE:
        _tainted = url.isTainted();
    }


    public ActionURL(String url)
    {
        this();
        String context = getContextPath();
        URI uri;

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

        try
        {
            uri = new URI(p);
        }
        catch (URISyntaxException use)
        {
            throw new IllegalArgumentException(use.getMessage(), use);
        }

        String path = uri.getPath();
        if (path.startsWith(context))
        {
            if (!"/".equals(context))
                path = path.substring(context.length());
        }

        setPath(path);
        setRawQuery(q);
    }


    public ActionURL setContainer(Container c)
    {
        _path = null == c ? Path.rootPath : c.getParsedPath();
        return this;
    }


    public ActionURL setExtraPath(String extraPath)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        if (null == extraPath) extraPath = "";
        _path = Path.parse(extraPath);
        return this;
    }


    public String getExtraPath()
    {
        String path = _path.toString();
        if (path.endsWith("/"))
            path = path.substring(0,path.length()-1);
        return path;
    }


    @Override
    public void setPath(String pathStr)
    {
        if (_readOnly)
            throw new java.lang.IllegalStateException();
        Path path = Path.decode(pathStr);
        if (path.size() < 2)
            throw new IllegalArgumentException(pathStr);

        String action = path.get(path.size()-1);
        int i = action.lastIndexOf('.');
        action = -1==i ? action : action.substring(0, i);

        _controller = path.get(0).toLowerCase();
        _action = action;
        _path = path.subpath(1,path.size()-1);
    }


    @Deprecated
    public ActionURL setPageFlow(String pageFlow)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _controller = pageFlow;
        return this;
    }


    public String getPageFlow()
    {
        return _controller;
    }


    public ActionURL setAction(Class<? extends Controller> actionClass)
    {
        setPageFlow(SpringActionController.getPageFlowName(actionClass));
        setAction(SpringActionController.getActionName(actionClass));
        return this;
    }


    /**
     *
     * @deprecated use the version that takes a class instead
     */
    @Deprecated
    public ActionURL setAction(String action)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _action = action;
        return this;
    }


    public String getAction()
    {
        return _action;
    }


    @Override
    protected boolean isDirectory()
    {
        return null == _action;
    }
    

    @Override
    public String getPath(boolean asForward)
    {
        return toPathString(_contextPath, _controller, _action, _path, !asForward);
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
            assertEquals(parse.getContextPath() + "/controller/path/action.view?foo=bar", toString);
        }


        public static Test suite()
        {
            TestSuite suite = new TestSuite(TestCase.class);
            return suite;
        }
    }
}
