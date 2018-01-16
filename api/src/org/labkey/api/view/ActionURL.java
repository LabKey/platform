/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.SimpleAction;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates URL generation and parsing based on controller/container/action conventions.
 * This class has to be kept in sync with ViewServlet.
 */
public class ActionURL extends URLHelper implements Cloneable
{
    private static boolean useContainerRelativeURL()
    {
        return AppProps.getInstance().getUseContainerRelativeURL();
    }

    public enum Param
    {
        returnUrl,
        redirectUrl,    // mostly deprecated for returnUrl
        cancelUrl,
        successUrl
    }

    private boolean _baseServerPropsInitialized = false;
    
    // ActionURL always uses the AppProps settings for scheme, host, and port.  We defer setting these since we
    // only need them when generating absolute URLs (which are rare).

    private String _controller = "project";
    private String _action = "begin";
    private boolean _isCanonical = true;    // e.g. not using GUID or __r1 syntax or #!

    
    /**
     * Old pageflow constructor
     */
    @Deprecated
    public ActionURL(String controller, String actionName, Container container)
    {
        this(true);
        _controller = controller;
        _action = actionName;
        setContainer(container);
    }


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
        if (SimpleAction.class.equals(actionClass))
        {
            throw new IllegalArgumentException("SimpleAction cannot be resolved correctly to a URL, as it backs a wide variety of HTML file-based actions");
        }
        _controller = SpringActionController.getControllerName(actionClass);
        if (_controller == null)
        {
            throw new IllegalStateException("Could not find a controller name for " + actionClass);
        }
        _action = SpringActionController.getActionName(actionClass);
        if (_action == null)
        {
            throw new IllegalStateException("Could not find an action name for " + actionClass);
        }
        setContainer(container);
    }


    /**
     * used by ViewContext
     */
    ActionURL(HttpServletRequest request) throws ServletException
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


    private String toPathString(Path contextPath, String controller, String action, Path extraPath, boolean encode)
    {
        if (useContainerRelativeURL())
            return toPathStringNew(contextPath, controller, action, extraPath, encode);
        else
            return toPathStringOld(contextPath, controller, action, extraPath, encode);
    }

    /** Generates in the format of /contextPath/controller/containerPath/action.view */
    private static String toPathStringOld(Path contextPath, String controller, String action, Path extraPath, boolean encode)
    {
        Path path = contextPath.append(controller).append(extraPath);
        if (null != action)
        {
            if (-1 == action.indexOf('.'))
                action = action + ".view";
            path = path.append(action, false);
        }
        return encode ? path.encode() : path.toString();
    }

    /** Generates in the format of /contextPath/containerPath/controller-action.view */
    private static String toPathStringNew(Path contextPath, String pageFlow, String action, Path extraPath, boolean encode)
    {
        Path path = contextPath.append(extraPath);
        if (null != action && null != pageFlow)
        {
            action = pageFlow + "-" + action + (-1 == action.indexOf('.') ? ".view" : "");
            path = path.append(action, false);
        }
        return encode ? path.encode() : path.toString();
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
    public ActionURL addParameter(String key, String value)
    {
        return (ActionURL) super.addParameter(key, value);
    }

    public ActionURL addParameter(Enum key, int value)
    {
        return addParameter(key.name(), value);
    }

    public ActionURL addParameter(Enum key, long value)
    {
        return addParameter(key.name(), value);
    }

    public ActionURL addParameter(String key, int value)
    {
        return (ActionURL) super.addParameter(key, String.valueOf(value));
    }

    public ActionURL addParameter(String key, long value)
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
    public ActionURL addParameters(List<Pair<String, String>> parameters)
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

    public ActionURL replaceParameter(Enum key, long value)
    {
        return replaceParameter(key.toString(), Long.toString(value));
    }

    @Override
    public ActionURL replaceParameter(String key, String value)
    {
        return (ActionURL) super.replaceParameter(key, value);
    }

    // Add returnURL as a parameter using standard parameter name
    public ActionURL addReturnURL(URLHelper returnURL)
    {
        return replaceParameter(ActionURL.Param.returnUrl, returnURL.getLocalURIString());
    }

    public URLHelper getReturnURL()
    {
        String returnURLStr = getParameter(ActionURL.Param.returnUrl);
        if (null == returnURLStr)
            return null;

        try
        {
            return new URLHelper(returnURLStr);
        }
        catch (IllegalArgumentException | URISyntaxException e)
        {
            return null;
        }
    }


    public ActionURL(String url)
    {
        this();
        String context = getContextPath();
        URI uri;

        String p = "";
        String q = "";
        String f = "";
        int i = url.indexOf('?');
        int k = url.indexOf('#');

        // be sure to not look for '?' query indicator to the right of the '#' fragment indicator
        // https://tools.ietf.org/html/rfc3986#section-3.5

        if (i == -1 && k == -1)
        {
            p = url;
            q = "";
            f = "";
        }
        else if (i != -1 && k == -1)
        {
            p = url.substring(0,i);
            q = url.substring(i+1);
            f = "";
        }
        else if (i == -1 && k != -1)
        {
            p = url.substring(0,k);
            q = "";
            f = url.substring(k+1);
        }
        else if (i != -1 && k != -1 && i < k)
        {
            p = url.substring(0,i);
            q = url.substring(i+1, k);
            f = url.substring(k+1);
        }
        else if (i != -1 && k != -1 && i > k)
        {
            p = url.substring(0,i);
            q = "";
            f = url.substring(k+1);
        }

        try
        {
            // make sure the path does not contain any unencoded spaces cause that will cause new URI to fail
            if (-1 != p.indexOf(' '))
                p = StringUtils.replace(p, " ", "%20");
            uri = new URI(p);
        }
        catch (URISyntaxException use)
        {
            throw new IllegalArgumentException(use.getMessage(), use);
        }

        String path = uri.getPath();
        if (path == null)
        {
            throw new IllegalArgumentException("No path found in URI: " + p);
        }
        if (path.startsWith(context))
        {
            if (!"/".equals(context))
                path = path.substring(context.length());
        }

        setPath(path);
        setRawQuery(q);
        setFragment(f);
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
    public ActionURL setPath(String pathStr)
    {
        if (_readOnly)
            throw new java.lang.IllegalStateException();

        String controller = null;
        Path path = Path.parse(pathStr);

        if (path.size() < 1)
            throw new IllegalArgumentException(pathStr);
        String action = path.get(path.size()-1);
        path = path.getParent();

        // parse action.view or controller-action.view
        int i = action.lastIndexOf('.');
        action = -1==i ? action : action.substring(0, i);
        int dash = action.lastIndexOf('-');
        if (dash > 0)
        {
            controller = action.substring(0, dash);
            action = action.substring(dash+1);
            setIsCanonical(useContainerRelativeURL());
        }
        else
        {
            setIsCanonical(!useContainerRelativeURL());
        }

        // parse controller
        if (null == controller)
        {
            if (path.size() < 1)
                throw new IllegalArgumentException(pathStr);
            controller = path.get(0);
            path = path.subpath(1, path.size());
        }

        _path = path;
        _action = action;
        _controller = controller.toLowerCase();
        return this;
    }


    public ActionURL setAction(Class<? extends Controller> actionClass)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _controller = SpringActionController.getControllerName(actionClass);
        _action = SpringActionController.getActionName(actionClass);
        return this;
    }


    public String getAction()
    {
        return _action;
    }

    public String getController()
    {
        return _controller;
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


    // CONSIDER: should this override getParsedPath()
    public Path getFullParsedPath()
    {
        return _contextPath.append(_controller).append(_path).append(_action + ".view");
    }


    public void setIsCanonical(boolean b)
    {
        if (_readOnly) throw new java.lang.IllegalStateException();
        _isCanonical = b;
    }

    public boolean isCanonical()
    {
        return _isCanonical;
    }

    @Override
    public ActionURL clone()
    {
        return (ActionURL) super.clone();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof URLHelper)
        {
            return toString().equals(obj.toString());
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return toString().hashCode();
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test() throws Exception
        {
            String s;

            ActionURL a = new ActionURL("PageFlow", "action", ContainerManager.getHomeContainer());
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
            if (useContainerRelativeURL())
                assertEquals(parse.getContextPath() + "/path/controller-action.view?foo=bar", toString);
            else
                assertEquals(parse.getContextPath() + "/controller/path/action.view?foo=bar", toString);
        }
    }
}
