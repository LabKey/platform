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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.miniprofiler.RequestInfo;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SpringModule;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryService.Environment;
import org.labkey.api.security.SecurityPointcutService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.MemoryUsageLogger;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.SessionAppender;
import org.labkey.api.util.ShuttingDownException;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UniqueID;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Main entry point for HTTP requests to be fed by views and actions as implemented in LabKey Server. Receives
 * incoming requests for *.view, *.api, and so forth.
 *
 * Must keep {@link ActionURL} in sync so that URLs are routed to the appropriate action to handle them.
 */
public class ViewServlet extends HttpServlet
{
    private static final Logger _log = Logger.getLogger(ViewServlet.class);

    public static final String ORIGINAL_URL_STRING = "LABKEY.OriginalURL";           // String
    public static final String ORIGINAL_URL_URLHELPER = "LABKEY.OriginalURLHelper";  // URLHelper
    public static final String REQUEST_ACTION_URL = "LABKEY.RequestURL";             // ActionURL
    public static final String REQUEST_STARTTIME = "LABKEY.StartTime";
    public static final String REQUEST_UID_COUNTER = "LABKEY.Counter";                   // Incrementing counter scoped to a single request
    // useful for access log
    public static final String REQUEST_ACTION = "LABKEY.action";
    public static final String REQUEST_CONTROLLER = "LABKEY.controller";
    public static final String REQUEST_CONTAINER = "LABKEY.container";

    public static final String MOCK_REQUEST_HEADER = "X-Mock-Request";
    public static final String MOCK_REQUEST_CSRF = "Mock-Request-Inherently-Trusted-For-CSRF";

    private static ServletContext _servletContext = null;
    private static String _serverHeader = null;

    private static final AtomicInteger _requestCount = new AtomicInteger();
    private static final AtomicInteger _pendingRequestCount = new AtomicInteger();
    private static final ThreadLocal<Boolean> IS_REQUEST_THREAD = new ThreadLocal<>();

    private static Map<Class<? extends Controller>, String> _controllerClassToName = null;
    private static volatile boolean _shuttingDown = false;

    private static SecurityPointcutService securityPointcut = null;

    public static String getControllerName(Class<? extends Controller> controllerClass)
    {
        return _controllerClassToName.get(controllerClass);
    }

    public static int getRequestCount()
    {
        return _requestCount.get();
    }

    /**
     * Map our custom path names to ones that struts can deal with.
     * /<PathInfo>/<PageFlow>/<Action>.view --> /</PageFlow>/<Action>.do?_pathInfo=<PathInfo> .
     * NYI: special case /<Action>.view --> /action.do
     */

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        if (_shuttingDown)
        {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "The server is shutting down");
            return;
        }
        try
        {
            _pendingRequestCount.incrementAndGet();
            _service(request, response);
        }
        finally
        {
            _pendingRequestCount.decrementAndGet();
        }
    }


    protected void _service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        long startTime = System.currentTimeMillis();

        request.setAttribute(REQUEST_STARTTIME, startTime);
        UniqueID.initializeRequestScopedUID(request);
        response.setHeader("Server", _serverHeader);
        HttpSession session = request.getSession(true);

        boolean isDebugEnabled = _log.isDebugEnabled();
        if (isDebugEnabled)
        {
            User user = (User) request.getUserPrincipal();
            String description = request.getMethod() + " " + request.getRequestURI() + "?" + Objects.toString(request.getQueryString(), "") + " (" + (user.isGuest() ? "guest" : user.getEmail()) + ";" + session.getId() + ")";
            _log.debug(">> " + description);
        }

        MemoryUsageLogger.logMemoryUsage(_requestCount.incrementAndGet());
        try (RequestInfo r = MemTracker.get().startProfiler(request, request.getRequestURI()))
        {
            SessionAppender.initThread(request);

            ActionURL url;
            response.setBufferSize(32768);

            try
            {
                url = requestActionURL(request);
                if (null == url)
                {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                request.setAttribute(REQUEST_ACTION, url.getAction());
                request.setAttribute(REQUEST_CONTROLLER, url.getController());
                request.setAttribute(REQUEST_CONTAINER, url.getExtraPath());
                r.setName(url.getController() + "/" + url.getAction());
            }
            catch (RedirectException e)
            {
                ExceptionUtil.handleException(request, response, e, "Container redirect", false);
                return;
            }
            catch (Exception x)
            {
                ExceptionUtil.handleException(request, response, new NotFoundException("Invalid URL"), null, false);
                return;
            }

            Module module = ModuleLoader.getInstance().getModuleForController(url.getController());
            if (module == null)
            {
                ExceptionUtil.handleException(request, response, new NotFoundException("No LabKey Server module registered to handle request for controller: " + url.getController()), null, false);
                return;
            }


            if (null != securityPointcut)
            {
                if (!securityPointcut.beforeResolveAction(request, response, module, url.getController(), url.getAction()))
                    return;
            }

            module.dispatch(request, response, url);

            if (isDebugEnabled)
            {
                _log.debug("<< " + request.getMethod());
            }
        }
    }


    /*
     *  Forward to the controller action defined by url
     *  acts like a GET on the provided URL
     */
    public static void forwardActionURL(HttpServletRequest request, HttpServletResponse response, ActionURL url)
            throws IOException, ServletException
    {
        Module module = ModuleLoader.getInstance().getModuleForController(url.getController());
        if (module == null)
        {
            throw new IllegalArgumentException(url.toString());
        }
        module.dispatch(new ForwardWrapper(request, url), response, url);
    }

    static class ForwardWrapper extends HttpServletRequestWrapper
    {
        final MockHttpServletRequest _mock;
        ForwardWrapper(HttpServletRequest request, ActionURL url)
        {
            super(request);
            _mock = new MockHttpServletRequest(ViewServlet.getViewServletContext(), "GET", url.getPath());
            for (Pair<String,String> p : url.getParameters())
                _mock.setParameter(p.getKey(), p.getValue());
        }
        @Override
        public String getQueryString()
        {
            return _mock.getQueryString();
        }
        public String getRequestURI()
        {
            return _mock.getRequestURI();
        }
        @Override
        public String getParameter(String s)
        {
            return _mock.getParameter(s);
        }
        @Override
        public Map<String, String[]> getParameterMap()
        {
            return _mock.getParameterMap();
        }
        @Override
        public Enumeration<String> getParameterNames()
        {
            return _mock.getParameterNames();
        }
        @Override
        public String[] getParameterValues(String s)
        {
            return _mock.getParameterValues(s);
        }
    }


    public static void initialize()
    {
        initializeControllerMaps();
        initializeAllSpringControllers();
        securityPointcut = ServiceRegistry.get(SecurityPointcutService.class);

        _serverHeader =  "Labkey/" + AppProps.getInstance().getLabKeyVersionString();
    }


    // Would rather let controllers initialize lazily, but this is problematic when code other than the controller refers
    // to an action class (e.g., new ActionURL(Class<? extends Controller>, Container)).  In particular, action classes
    // defined outside the controller have no idea who their controller is until the controller initializes.  Instead of
    // initializing everything at startup we could restrict the use of ActionURL(Class, Container), et al, to methods
    // within a controller... which would guarantee that the controller was initialized before resolving the action class.
    private static void initializeAllSpringControllers()
    {
        CPUTimer timer = new CPUTimer("ViewServlet.initializeAllSpringControllers");
        timer.start();
        for (Module module : ModuleLoader.getInstance().getModules())
        {
            for (Class controllerClass : module.getControllerClassToName().keySet())
            {
                if (Controller.class.isAssignableFrom(controllerClass))
                {
                    try
                    {
                        getController(module, controllerClass);
                    }
                    catch (Throwable t)
                    {
                        ExceptionUtil.logExceptionToMothership(null, t);
                    }
                }
            }
        }
        timer.stop();
        _log.debug(timer.toString());
    }


    private static void initializeControllerMaps()
    {
        _controllerClassToName = new HashMap<>();

        for (Module module : ModuleLoader.getInstance().getModules())
            _controllerClassToName.putAll(module.getControllerClassToName());
    }

    public static int getPendingRequestCount()
    {
        return _pendingRequestCount.get();
    }

    public static void setShuttingDown(long msWaitForRequests)
    {
        _shuttingDown = true;
        while (msWaitForRequests > 0)
        {
            if (0==_pendingRequestCount.get())
                break;
            try {Thread.sleep(100);}catch(InterruptedException x){}
            msWaitForRequests -= 100;
        }
    }

    public static boolean isShuttingDown()
    {
        return _shuttingDown;
    }

    public static void checkShuttingDown()
    {
        if (_shuttingDown)
            throw new ShuttingDownException();
    }

    public static Controller getController(Module module, Class controllerClass) throws IllegalAccessException, InstantiationException
    {
        if (module instanceof SpringModule)
            return ((SpringModule)module).getController(null, controllerClass);
        else
            return (Controller)controllerClass.newInstance();
    }


    private ActionURL requestActionURL(HttpServletRequest request)
            throws ServletException
    {
        // does not reduce need to validate form parameters which may be posted
        if (!validChars(request))
            return null;

        ActionURL url = new ActionURL(request);
        assert validChars(url);

        Container c = canonicalizeContainer(request, url);

        if (null != c)
        {
            url.setContainer(c);
            QueryService.get().setEnvironment(Environment.CONTAINER, c);
        }

        // lastfilter
        for (Pair<String, String> entry : url.getParameters())
        {
            if (entry.getKey().endsWith(DataRegion.LAST_FILTER_PARAM) && ColumnInfo.booleanFromString(entry.getValue()))
            {
                String paramName = entry.getKey();
                ActionURL expand = (ActionURL) request.getSession(true).getAttribute(url.getPath() + "#" + paramName);
                if (null != expand)
                {
                    // any parameters on the URL override those stored in the last filter:
                    for (Pair<String, String> parameter : url.getParameters())
                    {
                        // don't add the .lastFilter parameter (or we'll have an infinite redirect):
                        if (!paramName.equals(parameter.getKey()))
                            expand.replaceParameter(parameter.getKey(), parameter.getValue());
                    }
                    if ("GET".equals(request.getMethod()))
                        throw new RedirectException(expand.getLocalURIString());
                    _log.error(DataRegion.LAST_FILTER_PARAM + " not supported for " + request.getMethod());
                }
            }
        }

        url.setReadOnly();
        return url;
    }

    private Container canonicalizeContainer(HttpServletRequest request, ActionURL url)
    {
        Path path = url.getParsedPath();
        Container c = ContainerManager.getForPath(path);
        if (null == c)
        {
            url.setIsCanonical(false);
            // recover from duplicated controller (e.g. /project/home/announcements-begin.view)
            if (null != url.getController() && path.size() > 0 && null != ModuleLoader.getInstance().getModuleForController(path.get(0).toLowerCase()))
            {
                String controllerPart = path.get(0);
                Path pathFixUp = path.subpath(1,path.size());
                Container cFixUp = ContainerManager.getForPath(pathFixUp);
                if (null != cFixUp && ("GET".equals(request.getMethod()) || controllerPart.equalsIgnoreCase(url.getController())))
                {
                    c = cFixUp;
                    path = pathFixUp;
                    url.setPath(path);
                    if ("GET".equals(request.getMethod()))
                        throw new RedirectException(url.getLocalURIString());
                }
            }
        }

        // We support two types of permanent link encoding for containers:
        // 1) for backwards compatibility, long container ids (37 chars long, including the first "/"
        // 2) shorter row ids, starting with "/__r".
        if (null == c && path.size() == 1)
        {
            String name = path.getName();
            if (name.length()==36)
                c = ContainerManager.getForId(path.getName());
            if (null == c && name.startsWith("__r"))
            {
                try
                {
                    c = ContainerManager.getForRowId(Integer.parseInt(path.getName().substring(3)));
                }
                catch (NumberFormatException e)
                {
                    // Continue to try other ways to look up the container
                }
            }
        }
        if (null == c)
        {
            String strPath = path.toString();
            if (!strPath.startsWith("/"))
                strPath = "/" + strPath;
            if (strPath.endsWith("/"))
                strPath = strPath.substring(0, strPath.length() - 1);
            c = ContainerManager.resolveContainerPathAlias(strPath);
            if (c != null)
            {
                url.setContainer(c);
                if ("GET".equals(request.getMethod()))
                    throw new RedirectException(url.getLocalURIString());
            }
        }
        return c;
    }


    public static HttpServletRequest mockRequest(String method, @Nullable ActionURL url, @Nullable User user, @Nullable Map<String, Object> headers, @Nullable String postData)
    {
        MockRequest request = new MockRequest(getViewServletContext(), method, url);
        UniqueID.initializeRequestScopedUID(request);

        AppProps props = AppProps.getInstance();
        request.setContextPath(props.getContextPath());
        request.setServerPort(props.getServerPort());
        request.setServerName(props.getServerName());
        request.setScheme(props.getScheme());
        request.addHeader(MOCK_REQUEST_HEADER, true);

        // Mock requests are used for things like trigger scripts making client API calls back into the server
        // They're inherently trusted from the CSRF perspective, since they can't originate from external clients
        request.setAttribute(CSRFUtil.csrfName, MOCK_REQUEST_CSRF);
        request.addHeader(CSRFUtil.csrfHeader, MOCK_REQUEST_CSRF);

        if (user != null)
            request.setUserPrincipal(user);

        if (headers != null)
        {
            for (String header : headers.keySet())
            {
                if (header.equals("Content-Type"))
                    request.setContentType((String)headers.get(header));
                request.addHeader(header, headers.get(header));
            }
        }

        if (method.equals("POST") && postData != null)
            request.setContent(postData.getBytes(StringUtilsLabKey.DEFAULT_CHARSET));

        return request;

    }

    private static class MockRequest extends MockHttpServletRequest
    {
        private ActionURL _actionURL;

        private MockRequest(ServletContext servletContext, String method, ActionURL actionURL)
        {
            super(servletContext, method, null != actionURL ? actionURL.getURIString() : null);
            _actionURL = actionURL;
        }

        public ActionURL getActionURL()
        {
            return _actionURL;
        }

        public void setActionURL(ActionURL actionURL)
        {
            setRequestURI(actionURL.getURIString());
            _actionURL = actionURL;
        }

        @Override
        public Map<String, String[]> getParameterMap()
        {
            return _actionURL.getParameterMap();
        }

        @Override
        public String[] getParameterValues(String name)
        {
            List<String> parameters = _actionURL.getParameterValues(name);
            return parameters.toArray(new String[parameters.size()]);
        }

        @Override
        public Enumeration<String> getParameterNames()
        {
            return _actionURL.getParameterNames();
        }
    }


    public static MockHttpServletResponse GET(ActionURL url, User user, Map<String, Object> headers) throws Exception
    {
        HttpServletRequest request = mockRequest("GET", url, user, headers, null);
        return mockDispatch(request, null);
    }

    public static MockHttpServletResponse POST(ActionURL url, User user, Map<String, Object> headers, String postData) throws Exception
    {
        HttpServletRequest request = mockRequest("POST", url, user, headers, postData);
        return mockDispatch(request, null);
    }


    public static MockHttpServletResponse mockDispatch(HttpServletRequest request, @Nullable final String requiredContentType) throws Exception
    {
        if (!("GET".equals(request.getMethod()) || "POST".equals(request.getMethod())))
            throw new IllegalArgumentException(request.getMethod());

        ActionURL url;
        if (request instanceof MockRequest)
            url = ((MockRequest)request).getActionURL().clone();
        else
            url = new ActionURL(request.getRequestURI());

        String path = url.getExtraPath();
        Container c = ContainerManager.getForPath(path);
        if (null == c)
            c = ContainerManager.getForId(StringUtils.strip(path,"/"));
        if (null != c)
            url.setExtraPath(c.getPath());
        url.setReadOnly();

        MockHttpServletResponse mockResponse = new MockHttpServletResponse()
        {
            @Override
            public void setContentType(String s)
            {
                if (null != requiredContentType && !s.startsWith(requiredContentType))
                    throw new IllegalStateException(s);
                super.setContentType(s);
            }
        };

        final Object state =  QueryService.get().cloneEnvironment();
        try (RequestInfo r = MemTracker.get().startProfiler(request, url.getController() + "/" + url.getAction()))
        {
            Module module = ModuleLoader.getInstance().getModuleForController(url.getController());
            if (module == null)
            {
                throw new NotFoundException("Unknown controller: " + url.getController());
            }
            module.dispatch(request, mockResponse, url);
            return mockResponse;
        }
        catch (ServletException | IOException x)
        {
            _logError("error", x);
            throw x;
        }
        catch (Exception x)
        {
            _logError("error", x);
            throw new ServletException(x);
        }
        finally
        {
            QueryService.get().copyEnvironment(state);
        }
    }

    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        _servletContext = config.getServletContext();
        String realPath = config.getServletContext().getRealPath("/");
        _log.info("ViewServlet initialized");
        _log.info("   WEBAPP: " + realPath);
        _log.info("     PATH: " + System.getenv("PATH"));
    }


    public static String getOriginalURL()
    {
        // using HttpView to remember the root request, rather than have our own ThreadLocal
        HttpServletRequest request = HttpView.getRootContext().getRequest();
        if (null == request)
            return null;
        Object url = request.getAttribute(ORIGINAL_URL_STRING);
        if (null == url)
            url = request.getRequestURI() + "?" + StringUtils.trimToEmpty(request.getQueryString());
        return String.valueOf(url);
    }
    

    public static ActionURL getRequestURL()
    {
        // using HttpView to remember the root request, rather than have our own ThreadLocal
        ViewContext ctx = HttpView.getRootContext();
        if (ctx == null)
            return null;

        HttpServletRequest request = ctx.getRequest();
        if (request == null)
            return null;

        return (ActionURL) request.getAttribute(REQUEST_ACTION_URL);
    }


    public static void setAsRequestThread()
    {
        // Would rather not use thread local for this, but other HttpView/ViewServlet settings get set later and are
        //  awkward to use.
        IS_REQUEST_THREAD.set(true);
    }

    // Returns true if the current thread is a request thread, false if it's a background thread
    public static boolean isRequestThread()
    {
        return Boolean.TRUE.equals(IS_REQUEST_THREAD.get());
    }


    public static long getRequestStartTime(HttpServletRequest request)
    {
        Long ms = (Long)request.getAttribute(REQUEST_STARTTIME);
        return ms == null ? 0 : ms.longValue();
    }


    public static void ensureViewServlet(HttpServletResponse response)
    {
        try
        {
            HttpView.getRootContext();
        }
        catch (ArrayIndexOutOfBoundsException x)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            throw new NotFoundException();
        }
    }


    public static ServletContext getViewServletContext()
    {
        return _servletContext;
    }


    public static void _logError(String s, Throwable t)
    {
        _log.error(s, t);
        _servletContext.log(s, t);
    }


    public static void _log(String s, Throwable t)
    {
        _logError(s, t);
    }


    static boolean[] legal = new boolean[256];
    static
    {
        for (char ch=0 ; ch<256 ; ch++)
            legal[ch] = Character.isWhitespace(ch) || !Character.isISOControl(ch);
    }

    public static boolean validChar(int ch)
    {
        boolean allowRepacementChar = true;
        // 0xFFFD is sometimes used as a substitute for an illegal sequence
        return ch < 256 ? legal[ch] : (ch != 0xFFFD || allowRepacementChar) && Character.isDefined(ch) && (Character.isWhitespace(ch) || !Character.isISOControl(ch));
    }

    public static boolean validChars(CharSequence s)
    {
        if (null == s)
            return true;
        for (int i=0 ; i<s.length() ; i++)
            if (!validChar(s.charAt(i)))
                return false;
        return true;
    }

    private boolean validChars(URLHelper url) throws ServletException
    {
        Path path = url.getParsedPath();
        for (int i=0 ; i<path.size() ; i++)
        {
            if (!validChars(path.get(i)))
                return false;
        }
        for (Pair<String,String> p : url.getParameters())
        {
            if (!validChars(p.getKey()))
                return false;
            if (!validChars(p.getValue()))
               return false;
        }
        return true;
    }


    public static boolean validChars(HttpServletRequest r)
    {
        try
        {
            // validate original and decoded
            if (!validChars(r.getRequestURI()))
                return false;
            if (!validChars(r.getServletPath()))
                return false;
            if (!validChars(r.getQueryString()))
                return false;
            if (!validChars(PageFlowUtil.decode(r.getQueryString())))
                return false;

            if ("GET".equals(r.getMethod()))
                return true;

            // NOTE: this doesn't work for multipart/form-data
            // SpringActionController.handleRequest() will call validChars() again if necessary
/*            for (Map.Entry<String,String[]> e : ((Map<String,String[]>)r.getParameterMap()).entrySet())
            {
                if (!ViewServlet.validChars(e.getKey()))
                    return false;
                String[] a = e.getValue();
                if (null == a)
                    continue;
                for (String s : a)
                    if (!ViewServlet.validChars(s))
                        return false;
            }
*/
        }
        catch (IllegalArgumentException x)
        {
            return false;
        }
        return true;
    }

    /**
     * Replace any invalid characters with the unicode inverted question mark.
     * @param s Character sequence to be fixed.
     * @return The fixed String.
     */
    public static String replaceInvalid(CharSequence s)
    {
        int len = s.length();
        StringBuilder sb = new StringBuilder(len);

        for (int i = 0; i < len; i++)
        {
            char c = s.charAt(i);
            if (validChar(c))
                sb.append(c);
            else
                sb.append('\u00BF'); // inverted question mark
        }

        return sb.toString();
    }

    // Adapt Map<String, String[]> returned by getParameterMap() to match InsertView.setInitialValues(Map<String, Object>).
    // This makes it possible to upgrade our version of servlet-api.jar without a major overhaul. See #25941.
    public static Map<String, Object> adaptParameterMap(Map<String, String[]> parameterMap)
    {
        return new HashMap<>(parameterMap);
    }
}
