/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

/* This code derived from tomcat WebdavServet */

package org.labkey.core.webdav;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.fileupload.InvalidFileNameException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.IgnoresAllocationTracking;
import org.labkey.api.action.PermissionCheckableAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.Sort;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.RequestInfo;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.premium.PremiumService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.resource.Resource;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DefaultModelAndView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.webdav.DirectRequest;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.core.view.template.bootstrap.AppTemplate;
import org.labkey.core.view.template.bootstrap.PrintTemplate;
import org.labkey.core.webdav.apache.XMLWriter;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.commons.CommonsMultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.beans.Introspector;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutput;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.labkey.api.action.ApiJsonWriter.CONTENT_TYPE_JSON;


/**
 * User: matthewb
 * Date: Oct 3, 2007
 * Time: 3:54:42 PM
 *
 * Derived from Tomcat's WebdavServlet
 */
public class DavController extends SpringActionController
{
    public static final String name = "_dav_";
    public static final String mimeSeparation = "<[[mime " + GUID.makeHash() + "_separator_]]>";
    static final Charset utf8 = Charset.forName("UTF-8");

    static Logger _log = Logger.getLogger(DavController.class);
    static DefaultActionResolver _actionResolver = new DefaultActionResolver(DavController.class);
    static boolean _readOnly = false;
    static boolean _locking = true;
    static boolean _requiresLogin = false;
    static boolean _overwriteCollection = true; // must be true to pass litmus

    WebdavResponse _webdavresponse;
    WebdavResolver _webdavresolver;
    WebdavResolver _userresolver;


    protected Controller resolveHTMLActionName(Controller actionController, String actionName)
    {
        return null;
    }


    HttpServletRequest getRequest()
    {
        return getViewContext().getRequest();
    }

    WebdavResponse getResponse()
    {
        return _webdavresponse;
    }

    void setResolver(WebdavResolver resolver)
    {
        _webdavresolver = resolver;
        _requiresLogin = resolver.requiresLogin();
    }

    void setUserResolver(WebdavResolver resolver)
    {
        _userresolver = resolver;

    }
    
    WebdavResolver getResolver()
    {
        return _webdavresolver;
    }

    // best guess is this a browser vs. a WebDAV client
    boolean isBrowser()
    {
        if ("XMLHttpRequest".equals(getRequest().getHeader("x-requested-with")))
            return true;
        String userAgent = getRequest().getHeader("User-Agent");
        if (null == userAgent)
            return false;
        return userAgent.startsWith("Mozilla/") || userAgent.startsWith("Opera/");
    }

    // best guess is this a browser vs. a WebDAV client
    boolean isMacFinder()
    {
        String userAgent = getRequest().getHeader("User-Agent");
        if (null == userAgent)
            return false;
        return userAgent.startsWith("WebDAVFS/") && userAgent.contains("Darwin/");
    }

    boolean isWindowsExplorer()
    {
        String userAgent = getRequest().getHeader("User-Agent");
        if (null == userAgent)
            return false;
        return userAgent.startsWith("Microsoft-WebDAV");
    }

    boolean isChrome()
    {
        String userAgent = getRequest().getHeader("User-Agent");
        return StringUtils.contains(userAgent, "Chrome/") || StringUtils.contains(userAgent, "Chromium/");
    }

    // clients that support following redirects when getting a resource
    boolean supportsGetRedirect()
    {
        return isBrowser();
    }


    URLHelper getURL()
    {
        return (URLHelper)getRequest().getAttribute(ViewServlet.ORIGINAL_URL_URLHELPER);
    }


    ActionURL getLoginURL()
    {
        return PageFlowUtil.urlProvider(LoginUrls.class).getLoginURL(getContainer(), getURL());
    }


    public DavController()
    {
        setActionResolver(_actionResolver);
    }


    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response)
    {
        response.setCharacterEncoding("UTF-8");
        _webdavresponse = new WebdavResponse(response);

        MultipartHttpServletRequest multipartRequest = null;

        try
        {
            String contentType = request.getContentType();
            if (null != contentType && contentType.startsWith("multipart"))
            {
                try
                {
                    multipartRequest = (new CommonsMultipartResolver()).resolveMultipart(request);
                    request = multipartRequest;
                }
                catch (MultipartException x)
                {
                    _webdavresponse.sendError(WebdavStatus.SC_BAD_REQUEST, x);
                    return null;
                }
            }

            ViewContext context = getViewContext();
            context.setRequest(request);
            context.setResponse(response);

            String method = getViewContext().getActionURL().getAction();
            Controller action = resolveAction(method.toLowerCase());

            if (null == action)
            {
                _webdavresponse.sendError(WebdavStatus.SC_METHOD_NOT_ALLOWED);
                return null;
            }

            Class<? extends Controller> actionClass = action.getClass();
            if (actionClass.isAnnotationPresent(IgnoresAllocationTracking.class) || "true".equals(request.getParameter("skip-profiling")))
            {
                MemTracker.get().ignore();
            }
            else
            {
                // Don't send back mini-profiler id if the user won't be able to get the profiler info
                if (MiniProfiler.isEnabled(getViewContext()))
                {
                    RequestInfo req = MemTracker.get().current();
                    if (req != null)
                    {
                        LinkedHashSet<Long> ids = new LinkedHashSet<>();
                        ids.add(req.getId());
                        ids.addAll(MemTracker.get().getUnviewed(context.getUser()));

                        response.setHeader("X-MiniProfiler-Ids", ids.toString());
                    }
                }
            }

            try
            {
                if (action instanceof HasViewContext)
                    ((HasViewContext) action).setViewContext(context);
                action.handleRequest(request, response);
            }
            catch (RedirectException ex)
            {
                ExceptionUtil.doErrorRedirect(response, ex.getURL());
            }
            catch (ConfigurationException ex)
            {
                _log.error("Unexpected exception, might be related to server configuration problems", ex);
                _webdavresponse.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR, ex);
            }
            catch (Exception ex)
            {
                _log.error("unexpected exception", ex);
                ExceptionUtil.logExceptionToMothership(request, ex);
                _webdavresponse.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR, ex);
            }
        }
        finally
        {
            if (multipartRequest != null)
            {
                // Be sure that temp files are deleted. This typically is handled via garbage collection
                // but there is no guarantee that it will actually run in a timely fashion, or ever depending
                // on how the server is shut down
                for (List<MultipartFile> multipartFiles : multipartRequest.getMultiFileMap().values())
                {
                    for (MultipartFile multipartFile : multipartFiles)
                    {
                        if (multipartFile instanceof CommonsMultipartFile)
                        {
                            ((CommonsMultipartFile)multipartFile).getFileItem().delete();
                        }
                    }
                }
            }
        }
        for (Map.Entry<Closeable, Throwable> e : closables.entrySet())
        {
            Closeable c = e.getKey();
            Throwable t = e.getValue();
            _log.warn(c.getClass().getName() + " not closed", t);
        }
        return null;
    }


    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig config = super.defaultPageConfig();
        config.setTemplate(PageConfig.Template.None);
        return config;
    }


    class WebdavResponse
    {
        HttpServletResponse response;
        
        WebdavStatus _status = null;
        String _message = null;
        boolean _sendError = false;

        WebdavResponse(HttpServletResponse response)
        {
            this.response = response;
        }

        WebdavStatus sendError(WebdavStatus status)
        {
            return sendError(status, status.message);
        }

        WebdavStatus sendError(WebdavStatus status, Exception x)
        {
            _log.error(x instanceof ConfigurationException ? "Configuration Exception" : "Unexpected exception", x);
            String message = x.getMessage() != null ? x.getMessage() : status.message;
            if (x instanceof ConfigurationException)
                message += "\nThis may be a server configuration problem.  Contact the site administrator.";
            return sendError(status, message);
        }

        WebdavStatus sendError(WebdavStatus status, Path path)
        {
            return sendError(status, String.valueOf(path));
        }

        WebdavStatus sendError(WebdavStatus status, String message)
        {
            assert !_sendError;
            try
            {
                if (null == StringUtils.trimToNull(message))
                    response.sendError(status.code);
                else
                {
                    String accept = StringUtils.join(getRequest().getHeader("Accept"), "," , getRequest().getParameter("Accept"));
                    if(CONTENT_TYPE_JSON.equals(getRequest().getHeader("Content-Type")) ||
                            accept.contains(CONTENT_TYPE_JSON))
                    {
                        JSONObject o = new JSONObject();
                        o.put("success", false);
                        o.put("status", status.code);
                        o.put("exception", message);
                        // if this is a multi-part post, it's probably really a background ext form, respond in an ext compatible way
                        if (!"XMLHttpRequest".equals(getRequest().getHeader("X-Requested-With")) &&
                                "post".equals(getViewContext().getActionURL().getAction()) &&
                                getRequest() instanceof MultipartHttpServletRequest)
                        {
                            response.setHeader("Content-Type", "text/html");
                            response.getWriter().write("<html><body><textarea>" + o.toString() + "</textarea></body></html>");
                        }
                        else
                        {
                            response.setHeader("Content-Type", CONTENT_TYPE_JSON);
                            response.getWriter().write(o.toString());
                            response.setStatus(HttpServletResponse.SC_OK);
                        }
                    }
                    else
                    {
                        response.sendError(status.code, message);
                    }
                }
                _status = status;
                _sendError = true;
            }
            catch (Exception x)
            {
                _log.error("unexpected error", x);
            }
            return _status;
        }

        WebdavStatus setStatus(WebdavStatus status)
        {
            assert _status == null || (200 <= _status.code && _status.code < 300);
            try
            {
                response.setStatus(status.code);
            }
            catch (Exception x)
            {
                _log.error("unexpected error", x);
            }
            _status = status;
            return status;
        }

        WebdavStatus getStatus()
        {
            return _status;
        }

        String getMessage()
        {
            return _message;
        }

        void setExpires(long expires, String cache)
        {
            response.setDateHeader("Expires", expires);
            response.setHeader("Cache-Control", cache);
        }

        void setContentEncoding(String value)
        {
            response.setHeader("Content-Encoding", value);
            response.addHeader("Vary", "Accept-Encoding");
        }

        void setCacheForUserOnly()
        {
            response.addHeader("Vary", "Cookie");
        }

        void setContentDisposition(String value)
        {
            response.setHeader("Content-Disposition", value);
        }

        void  setContentType(String contentType)
        {
            response.setContentType(contentType);
        }

        void setContentLength(long contentLength)
        {
            if (contentLength < Integer.MAX_VALUE)
            {
                response.setContentLength((int)contentLength);
            }
            else
            {
                // Set the content-length as String to be able to use a long
                response.setHeader("content-length", "" + contentLength);
            }
        }

        void setContentRange(long fileLength)
        {
            response.addHeader("Content-Range", "bytes */" + fileLength);
        }

        void addContentRange(Range range)
        {
            response.addHeader("Content-Range", "bytes "+ range.start + "-" + range.end + "/" + range.length);
        }


        void setEntityTag(String etag)
        {
            response.setHeader("ETag", etag);
        }

        void setLastModified(long d)
        {
            response.setHeader("Last-Modified", getHttpDateFormat(d));
        }

        void addLockToken(String lockToken)
        {
            response.addHeader("Lock-Token", "<opaquelocktoken:" + lockToken + ">");
        }

        void setMethodsAllowed(CharSequence methods)
        {
            response.addHeader("Allow", methods.toString());
        }

        void addOptionsHeaders()
        {
            response.addHeader("DAV", "1,2");
            response.addHeader("MS-Author-Via", "DAV");
        }

        void setRealm(String realm)
        {
            response.setHeader("WWW-Authenticate", "Basic realm=\"" + realm  + "\"");
        }

        void setLocation(String value)
        {
            response.setHeader("Location", value);
        }

        ServletOutputStream getOutputStream() throws IOException
        {
            return response.getOutputStream();
        }
        
        StringBuilder sbLogResponse = new StringBuilder();

        Writer getWriter() throws IOException
        {
            Writer responseWriter = response.getWriter();
            assert track(responseWriter);

            if (!_log.isDebugEnabled())
                return responseWriter;

            FilterWriter f = new java.io.FilterWriter(responseWriter)
            {
                @Override
                public void write(int c) throws IOException
                {
                    super.write(c);
                    sbLogResponse.append(c);
                }

                @Override
                public void write(String str, int off, int len) throws IOException
                {
                    super.write(str, off, len);
                    sbLogResponse.append(str, off, len);
                }

                @Override
                public void write(char cbuf[]) throws IOException
                {
                    super.write(cbuf);
                    sbLogResponse.append(cbuf);
                }

                @Override
                public void write(String str) throws IOException
                {
                    write(str, 0, str.length());
                }

                public void write(char cbuf[], int off, int len) throws IOException
                {
                    super.write(cbuf,off,len);
                    sbLogResponse.append(cbuf,off,len);
                }

                @Override
                public void close() throws IOException
                {
                    super.close();
                    assert untrack(out);
                }
            };
            assert track(f);
            return f;
        }
    }


    @RequiresNoPermission
    private abstract class DavAction extends PermissionCheckableAction
    {
        final String method;
        final boolean allowDuringUpgrade;

        protected DavAction(String method)
        {
            this.method = method;
            this.allowDuringUpgrade = false;
        }

        protected DavAction(String method, boolean allow)
        {
            this.method = method;
            this.allowDuringUpgrade = allow;
        }

        public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            clearLastError();

            long start=0;
            if (_log.isEnabledFor(Level.DEBUG))
            {
                long modified = getRequest().getDateHeader("If-Modified-Since");
                boolean isBasicAuthentication = SecurityManager.isBasicAuthentication(getRequest());
                String username = getUser().getName();
                String auth = username + (isBasicAuthentication ? ":basic" : !getUser().isGuest() ? ":session" : "");
                _log.debug(">>>> " + request.getMethod() + " " + getResourcePath() + " (" + auth + ") " + (modified==-1? "" : "   (If-Modified-Since:" + DateUtil.toISO(modified) + ")"));
                if (1==0) // verbose
                {
                    IteratorUtils.asIterator(request.getHeaderNames()).forEachRemaining(name -> {
                        _log.debug(name + ": " + request.getHeader(name));
                    });
                }
                start = System.currentTimeMillis();
            }

            try
            {
                try
                {
                    if (!allowDuringUpgrade && (ModuleLoader.getInstance().isUpgradeRequired() || !ModuleLoader.getInstance().isStartupComplete()))
                    {
                        throw new DavException(WebdavStatus.SC_SERVICE_UNAVAILABLE, "Server has not completed startup.");
                    }

                    if (_requiresLogin && getUser().isGuest())
                        throw new UnauthorizedException(resolvePath());

                    WebdavStatus ret = doMethod();
                    assert null != ret || getResponse().getStatus() != null;
                    if (null != ret && 200 <= ret.code && ret.code < 300)
                        getResponse().setStatus(ret);
                }
                catch (SocketException ex)
                {
                    return null; // ignore
                }
                catch (IOException ex)
                {
                    if (ExceptionUtil.isClientAbortException(ex))
                        return null; // ignore
                    DavException dex = new DavException(ex);
                    getResponse().sendError(dex.getStatus(), dex.getMessage());
                    ExceptionUtil.logExceptionToMothership(request, ex);
                }
            }
            catch (UnauthorizedException uex)
            {
                setLastError(uex);
                WebdavResource resource = uex.getResource();
                Path resourcePath = uex.getResourcePath();
                if (!getUser().isGuest())
                {
                    getResponse().sendError(WebdavStatus.SC_FORBIDDEN, resourcePath);
                }
                else if ("GET".equals(method) && isBrowser())
                {
                    getResponse().setStatus(WebdavStatus.SC_MOVED_TEMPORARILY);
                    getResponse().setLocation(getLoginURL().getEncodedLocalURIString());
                }
                else
                {
                    getResponse().setRealm(LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getDescription());
                    getResponse().sendError(WebdavStatus.SC_UNAUTHORIZED, resourcePath);
                }
            }
            catch (ConfigurationException ex)
            {
                _log.error("Unexpected exception, might be related to server configuration problems", ex);
                getResponse().sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR, ex);
            }
            catch (DavException dex)
            {
                setLastError(dex);
                if (dex.getStatus().equals(WebdavStatus.SC_NOT_FOUND))
                {
                    SearchService ss = SearchService.get();
                    if (null != ss)
                        ss.notFound((URLHelper)getRequest().getAttribute(ViewServlet.ORIGINAL_URL_URLHELPER));
                }
                getResponse().sendError(dex.getStatus(), dex.getMessage());
                if (dex.getStatus() != null && dex.getStatus().code == HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                {
                    ExceptionUtil.logExceptionToMothership(request, dex);
                }
            }

            if (_log.isDebugEnabled())
            {
                if (getResponse().sbLogResponse.length() > 0)
                    _log.debug(getResponse().sbLogResponse);
                WebdavStatus status = getResponse().getStatus();
                String message = getResponse().getMessage();
                _log.debug("<<<< " + (status != null ? status.code : 0) + " " +
                        StringUtils.defaultString(message, null != status ? status.message : "") + " " +
                        DateUtil.formatDuration(System.currentTimeMillis()-start));
            }

            return null;
        }

        WebdavStatus doMethod() throws DavException, IOException, RedirectException
        {
            WebdavResource resource = resolvePath();
            if (null != resource)
            {
                StringBuilder methodsAllowed = determineMethodsAllowed(resource);
                getResponse().setMethodsAllowed(methodsAllowed);
            }
            WebdavStatus ret = WebdavStatus.SC_METHOD_NOT_ALLOWED;
            getResponse().setStatus(ret);
            return ret;
        }
    }


    @RequiresNoPermission
    public class GetAction extends DavAction
    {
        public GetAction()
        {
            super("GET", true);
        }

        protected GetAction(String method)
        {
            super(method, true);
        }

        public final WebdavStatus doMethod() throws DavException, IOException
        {
            try
            {
                WebdavStatus status = _doMethod();
                return status;
            }
            catch (DavException x)
            {
                // last ditch effort to find matching container on GET, e.g. /labkey/home/ --> /labkey/home/project-begin.view
                // might be a little cleaner to register a javax.servlet.Filter
                if (x.getStatus() != WebdavStatus.SC_NOT_FOUND || !"GET".equals(method))
                    throw x;
                Container c = ContainerManager.getForPath(getResourcePath());
                if (null == c)
                    throw x;
                throw new RedirectException(c.getStartURL(getUser()));
            }
        }

        protected WebdavStatus _doMethod() throws DavException, IOException
        {
            WebdavResource resource;
            try
            {
                resource = resolvePath();
            }
            catch (DavException x)
            {
                if (x.getStatus() == WebdavStatus.SC_FORBIDDEN)
                    return notFound();
                throw x;
            }
            if (null == resource || resource instanceof WebdavResolverImpl.UnboundResource)
                return notFound();
            if (resource.isCollection() && !allowHtmlListing(getResourcePath()))
            {
                WebdavResource welcome = welcomePage(getResourcePath());
                if (null == welcome)
                    return notFound(resource.getPath());
                if (null != welcome && welcome.isFile())
                    resource = welcome;
                else
                    return notFound(resource.getPath());
            }
            if (!(resource.isCollection() ? resource.canList(getUser(), true) : resource.canRead(getUser(), true)))
                return unauthorized(resource);
            if (!resource.exists())
                return notFound(resource.getPath());

            // http://www.ietf.org/rfc/rfc4709.txt
            if (resource.isCollection())
                return serveCollection(resource, !"HEAD".equals(method));
            else
                return serveResource(resource, !"HEAD".equals(method));
        }
    }

    @RequiresNoPermission
    public class Md5sumAction extends GetAction
    {
        public Md5sumAction()
        {
            super("MD5SUM");
        }

        protected WebdavStatus _doMethod() throws DavException, IOException
        {
            WebdavResource resource;
            try
            {
                resource = resolvePath();
            }
            catch (DavException x)
            {
                if (x.getStatus() == WebdavStatus.SC_FORBIDDEN)
                    return notFound();
                throw x;
            }
            if (null == resource || resource instanceof WebdavResolverImpl.UnboundResource)
                return notFound();
            if (!resource.exists())
                return notFound(resource.getPath());

            if (resource.isCollection() && !resource.canList(getUser(), true) || !resource.canRead(getUser(), true))
                return unauthorized(resource);

            Collection<? extends WebdavResource> resources = null;
            if (resource.isCollection())
                resources = resource.list();
            else
                resources = Collections.singletonList(resource);

            getResponse().setContentType("text/plain");
            Writer out = getResponse().getWriter();
            for (WebdavResource r : resources)
            {
                String md5;
                try
                {
                    // CONSIDER: replace with json response with file name
                    if (r.isFile())
                        out.write(r.getMD5(getUser()) + " *" + r.getName() + "\n");
                }
                catch (Exception x)
                {
                    out.write("ERROR: " + r.getName() + ": " + x.getMessage() + "\n");
                }
            }
            out.flush();
            return WebdavStatus.SC_OK;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ZipAction extends DavAction
    {
        public static final String PARAM_ZIPNAME = "zipName";
        public static final String PARAM_DEPTH = "depth";

        public ZipAction()
        {
            super("ZIP");
        }

        @Override
        WebdavStatus doMethod() throws DavException, IOException, RedirectException
        {
            User user = getUser();
            WebdavResource resource = resolvePath();
            if (null == resource)
                return notFound();

            //check for zipName parameter
            HttpServletRequest request = getViewContext().getRequest();
            String zipName = request.getParameter(PARAM_ZIPNAME);
            if (null == zipName)
            {
                Resource namingResource = resource;
                while (namingResource != null && namingResource.getName().startsWith("@"))
                {
                    namingResource = namingResource.parent();
                }
                if (namingResource != null)
                {
                    zipName = namingResource.getName() + " files";
                }
                else
                {
                    zipName = "Files";
                }
            }

            int depth = 1;
            try
            {
                depth = Integer.parseInt(request.getParameter(PARAM_DEPTH));
            }
            catch (NumberFormatException ignore) {}

            Set<String> includeNames = null;
            if (request.getParameterValues("file") != null)
            {
                includeNames = new HashSet<>(Arrays.asList(request.getParameterValues("file")));
            }

            //-1 means infinite (...well, as deep as max int)
            if (-1 == depth)
                depth = Integer.MAX_VALUE;

            HttpServletResponse response = getViewContext().getResponse();
            response.reset();
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + zipName + ".zip\"");

            try (ZipOutputStream out = new ZipOutputStream(response.getOutputStream()))
            {
                addResource(resource, out, user, resource, depth, includeNames);
            }
            return WebdavStatus.SC_OK;
        }

        /** @param includeNames if non-null, the set of children to include in the zip. If null, all are included */
        private void addResource(WebdavResource resource, ZipOutputStream out, User user, WebdavResource rootResource, int depth, Set<String> includeNames)
                throws IOException, DavException
        {
            if (!resource.canRead(user, true))
                return;

            if (resource.isCollection())
            {
                if (depth > 0)
                {
                    for (WebdavResource child : resource.list())
                    {
                        if (includeNames == null || includeNames.contains(child.getName()))
                        {
                            addResource(child, out, user, rootResource, depth - 1, null);
                        }
                    }
                }
            }
            else
            {
                String entryName = rootResource.getPath().equals(resource.getPath())
                        ? resource.getName()
                        : rootResource.getPath().relativize(resource.getPath()).toString();

                ZipEntry entry = new ZipEntry(entryName);
                out.putNextEntry(entry);

                try (InputStream in = getResourceInputStream(resource, user))
                {
                    FileUtil.copyData(in, out);
                }
            }
        }
    }


    @RequiresNoPermission
    public class HeadAction extends GetAction
    {
        public HeadAction()
        {
            super("HEAD");
        }

        @Override
        protected WebdavStatus _doMethod() throws DavException, IOException
        {
            // CYBERDUCK uses head to probe permissions
            checkRequireLogin(resolvePath());
            return super._doMethod();
        }
    }


    private class MountAction extends DavAction
    {
        public MountAction()
        {
            super("--MOUNT--"); // make spring happy?  why do I need a zero-constructor arg
        }

        public MountAction(String method)
        {
            super(method);
        }

        @Override
        protected WebdavStatus doMethod() throws DavException, IOException
        {
            WebdavResource resource = resolvePath();
            if (null == resource || !resource.exists())
                return notFound();
            if (resource.isFile())
                resource = (WebdavResource)resource.parent();
            if (!resource.canList(getUser(), true))
                return unauthorized(resource);

            String root = resolvePath("/").getHref(getViewContext());
            if (!root.endsWith("/")) root += "/";
            String path = resource.getHref(getViewContext());
            if (!path.endsWith("/")) path += "/";
            String open = path.substring(root.length());
            URLHelper url;
            try
            {
                url = new URLHelper(path);
            }
            catch (URISyntaxException x)
            {
                throw new RuntimeException(x);
            }

            if (method.equals("DAVMOUNT"))
            {

                StringBuilder sb = new StringBuilder();
                sb.append("<dm:mount xmlns:dm=\"http://purl.org/NET/webdav/mount\">\n");
                sb.append("  <dm:url>").append(PageFlowUtil.filter(root)).append("</dm:url>\n");
                if (open.length() > 0)
                    sb.append("  <dm:open>").append(PageFlowUtil.filter(open)).append("</dm:open>\n");
                sb.append("</dm:mount>\n");

                getResponse().setContentType("application/davmount+xml");
                getResponse().setContentDisposition("attachment; filename=\"" + resource.getName() + ".davmount\"");
                Writer w = getResponse().getWriter();
                w.write(sb.toString());
                close(w, "response writer");
            }
            else if (method.equals("CYBERDUCK"))
            {
                StringBuilder sb = new StringBuilder();
                sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                        "<plist version=\"1.0\">\n" +
                        "<dict>\n" +
                        "<key>Hostname</key>\n" +
                        "<string>" + PageFlowUtil.filter(AppProps.getInstance().getServerName()) + "</string>\n" +
                        "<key>Nickname</key>\n" +
                        "<string>" + PageFlowUtil.filter(LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getShortName()) + "</string>\n" +
                        "<key>Path</key>\n" +
                        "<string>" + url.getEncodedLocalURIString().replace("%40","@") + "</string>\n" +
                        "<key>Port</key>\n" +
                        "<string>" + AppProps.getInstance().getServerPort() + "</string>\n" +
                        "<key>Protocol</key>\n" +
                        "<string>" + (AppProps.getInstance().isSSLRequired() ? "davs" : "dav") + "</string>\n" +
                        "<key>Username</key>\n" +
                        "<string>" + PageFlowUtil.filter(getUser().isGuest() ? getUser().getName() : getUser().getEmail()) + "</string>\n" +
                        "</dict>\n" +
                        "</plist>\n");

                getResponse().setContentType("application/x-cyberduck+xml");
                getResponse().setContentDisposition("attachment; filename=\"" + resource.getName() + ".duck\"");
                Writer w = getResponse().getWriter();
                w.write(sb.toString());
                close(w, "response writer");
            }

            return WebdavStatus.SC_OK;
        }
    }


    @RequiresNoPermission
    public class DavmountAction extends MountAction
    {
        public DavmountAction()
        {
            super("DAVMOUNT");
        }
    }

    public class CyberduckAction extends MountAction
    {
        public CyberduckAction()
        {
            super("CYBERDUCK");
        }
    }





    @RequiresNoPermission
    public class PostAction extends PutAction
    {
        public PostAction()
        {
            super("POST");
        }

        @Override
        public WebdavStatus doMethod() throws DavException, IOException, RedirectException
        {
            if (PremiumService.get().isFileUploadDisabled())
                return WebdavStatus.SC_METHOD_NOT_ALLOWED;

            WebdavResource resource = resolvePath();
            if (null == resource)
                return notFound();

            Boolean createIntermediates = getBooleanParameter("createIntermediates");
            if (!resource.exists() && Boolean.TRUE != createIntermediates)
                return notFound();

            // Assume resource is a collection if we are creating intermediates
            boolean isCollection = resource.isCollection() || Boolean.TRUE == createIntermediates;

            if (isCollection)
            {
                String filename = getFilenameParameter();
                FileStream stream;
                
                if (getRequest() instanceof MultipartHttpServletRequest)
                {
                    MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest)getRequest();
                    if (multipartRequest.getFileMap().size() > 1)
                        return WebdavStatus.SC_NOT_IMPLEMENTED;
                    if (multipartRequest.getFileMap().isEmpty())
                        return WebdavStatus.SC_METHOD_NOT_ALLOWED;

                    Map.Entry<String, MultipartFile> entry = multipartRequest.getFileMap().entrySet().iterator().next();
                    MultipartFile file = entry.getValue();
                    if (null == filename)
                    {
                        try
                        {
                            filename = file.getOriginalFilename();
                        }
                        catch (InvalidFileNameException ex)
                        {
                            return WebdavStatus.SC_BAD_REQUEST;
                        }
                    }
                    stream = new SpringAttachmentFile(file);
                }
                else
                {
                    // CONSIDER: enforce ContentType=text/plain?
                    String content = StringUtils.defaultString(getRequest().getParameter("content"),"");
                    stream = new FileStream.ByteArrayFileStream(content.getBytes(StringUtilsLabKey.DEFAULT_CHARSET));
                }

                if (StringUtils.isEmpty(filename) || filename.contains("/"))
                    return WebdavStatus.SC_METHOD_NOT_ALLOWED;
                WebdavResource dest = resource.find(filename);
                if (null == dest)
                    return WebdavStatus.SC_METHOD_NOT_ALLOWED;
                if (!dest.exists())
                    checkAllowedFileName(dest.getName());
                setFileStream(stream);

                setResource(dest);
                WebdavStatus status = super.doMethod();

                // if _returnUrl then redirect, else respond as if PROPFIND
                String returnUrl = getRequest().getParameter(ActionURL.Param.returnUrl.name());
                if (null != StringUtils.trimToNull(returnUrl))
                {
                    String url = returnUrl + (returnUrl.indexOf('?')==-1 ? '?' : '&') + "status=" + status;
                    throw new RedirectException(url);
                }

                if (status == WebdavStatus.SC_CREATED)
                {
                    WebdavResource newDest = resource.find(filename);       // #30569: get newly created resource object that now has metadata
                    PropfindAction action = new PropfindAction()
                    {
                        @Override
                        protected InputStream getInputStream() throws IOException
                        {
                            return new ByteArrayInputStream(new byte[0]);
                        }

                        @Override
                        protected Pair<Integer, Boolean> getDepthParameter()
                        {
                            return new Pair<>(0,Boolean.FALSE);
                        }
                    };
                    action.setResource(newDest);
                    return action.doMethod();
                }
                return status;
            }
            else
            {
                return new GetAction().doMethod();
            }
        }
    }


    class ResourceFilter
    {
        boolean accept(WebdavResource r)
        {
            return true;
        }
    }

    Boolean getBooleanParameter(String name)
    {
        String v = getRequest().getParameter(name);
        if (StringUtils.isEmpty(v))
            return null;
        try
        {
            // handle webdav style
            if (StringUtils.equals("T",v))
                return Boolean.TRUE;
            if (StringUtils.equals("F",v))
                return Boolean.FALSE;
            return ConvertHelper.convert(v, Boolean.class);
        }
        catch (Exception x) {}
        return null;
    }


    @RequiresNoPermission
    public class PropfindAction extends DavAction
    {
        WebdavResource _resource = null;
        boolean defaultListRoot = true; // return root node when depth>0?
        int defaultDepth = 1;
        
        public PropfindAction()
        {
            super("PROPFIND");
        }

        public PropfindAction(String method)
        {
            super(method);
        }

        protected void setResource(WebdavResource r)
        {
            _resource = r;
        }

        WebdavResource getResource() throws DavException
        {
            if (null == _resource)
                _resource = resolvePath();
            return _resource;
        }

        protected InputStream getInputStream() throws IOException
        {
            return getRequest().getInputStream();
        }

        protected Pair<Integer, Boolean> getDepthParameter()
        {
            String depthStr = getRequest().getHeader("Depth");
            if (null == depthStr)
                depthStr = getRequest().getParameter("depth");
            if (null == depthStr)
                return new Pair<>(defaultDepth, defaultListRoot);
            int depth = defaultDepth;
            boolean noroot = depthStr.endsWith(",noroot");
            if (noroot)
                depthStr = depthStr.substring(0,depthStr.length()-",noroot".length());
            try
            {
                depth = Math.min(INFINITY, Math.max(0,Integer.parseInt(depthStr.trim())));
            }
            catch (NumberFormatException x)
            {
            }
            return new Pair<>(depth, depth>0 && noroot);
        }


        protected ResourceFilter getResourceFilter()
        {
            Boolean isCollection = getBooleanParameter("isCollection");
            // Other possible filters???

            if (null == isCollection)
                return new ResourceFilter();
            if (isCollection)
            {
                return new ResourceFilter()
                    {
                        @Override
                        boolean accept(WebdavResource r)
                        {
                            return null != r && r.isCollection();
                        }
                    };
            }
            else
            {
                return new ResourceFilter()
                    {
                        @Override
                        boolean accept(WebdavResource r)
                        {
                            return null != r && r.isFile();
                        }
                    };
            }
        }


        public WebdavStatus doMethod() throws DavException, IOException
        {
            WebdavResource root = getResource();
            checkRequireLogin(root);
            if (root == null || !root.exists())
                return notFound();

            if (!root.canList(getUser(), true))
                return unauthorized(root);

            List<String> properties = null;
            Find type = null;
            Pair<Integer, Boolean> depthParam = getDepthParameter();
            int depth = depthParam.first;
            boolean noroot = depthParam.second;

            Node propNode = null;

            if ("PROPFIND".equals(method))
            {
                ReadAheadInputStream is = new ReadAheadInputStream(getInputStream());
                try
                {
                    if (is.available() > 0)
                    {
                        DocumentBuilder documentBuilder = getDocumentBuilder();
                        Document document = documentBuilder.parse(is);

                        // Get the root element of the document
                        Element rootElement = document.getDocumentElement();
                        NodeList childList = rootElement.getChildNodes();

                        for (int i = 0; i < childList.getLength(); i++)
                        {
                            Node currentNode = childList.item(i);
                            switch (currentNode.getNodeType())
                            {
                                case Node.TEXT_NODE:
                                    break;
                                case Node.ELEMENT_NODE:
                                    if (currentNode.getNodeName().endsWith("prop"))
                                    {
                                        type = Find.FIND_BY_PROPERTY;
                                        propNode = currentNode;
                                    }
                                    if (currentNode.getNodeName().endsWith("propname"))
                                    {
                                        type = Find.FIND_PROPERTY_NAMES;
                                    }
                                    if (currentNode.getNodeName().endsWith("allprop"))
                                    {
                                        type = Find.FIND_ALL_PROP;
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    throw new DavException(WebdavStatus.SC_BAD_REQUEST);
                }
                finally
                {
                    close(is, "propfind request stream");
                }
            }

            if (type == null)
            {
                // No XML posted, check for HTTP parameters
                String typeParam = getRequest().getParameter("type");
                if ("propname".equalsIgnoreCase(typeParam))
                {
                    type = Find.FIND_PROPERTY_NAMES;
                }
                else
                {
                    String[] propNames = getRequest().getParameterValues("propname");
                    if ("prop".equalsIgnoreCase(typeParam) || (propNames != null && propNames.length > 0))
                    {
                        type = Find.FIND_BY_PROPERTY;
                        if (propNames != null && propNames.length > 0)
                        {
                            properties = new Vector<>();
                            properties.addAll(Arrays.asList(getRequest().getParameterValues("propname")));
                        }
                    }
                    else
                    {
                        type = Find.FIND_ALL_PROP;
                    }
                }
            }
            else if (Find.FIND_BY_PROPERTY == type && null != propNode)
            {
                properties = new Vector<>();
                NodeList childList = propNode.getChildNodes();

                for (int i = 0; i < childList.getLength(); i++)
                {
                    Node currentNode = childList.item(i);
                    switch (currentNode.getNodeType())
                    {
                        case Node.TEXT_NODE:
                            break;
                        case Node.ELEMENT_NODE:
                            String nodeName = currentNode.getNodeName();
                            String propertyName;
                            if (nodeName.indexOf(':') != -1)
                            {
                                propertyName = nodeName.substring(nodeName.indexOf(':') + 1);
                            }
                            else
                            {
                                propertyName = nodeName;
                            }
                            properties.add(propertyName);
                            break;
                    }
                }
            }


            ResourceFilter f = getResourceFilter();

            // Create multistatus object
            Writer writer = getResponse().getWriter();
            assert track(writer);
            ResourceWriter resourceWriter = null;
            try
            {
                resourceWriter = getResourceWriter(writer);
                resourceWriter.beginResponse(getResponse());

                if (depth == 0)
                {
                    // probably not useful to apply filter here
                    resourceWriter.writeProperties(root, type, properties);
                }
                else
                {
                    // The stack always contains the object of the current level
                    LinkedList<Path> stack = new LinkedList<>();
                    stack.addLast(root.getPath());

                    // Stack of the objects one level below
                    boolean skipFirst = noroot;
                    WebdavResource resource;
                    LinkedList<Path> stackBelow = new LinkedList<>();

                    while ((!stack.isEmpty()) && (depth >= 0))
                    {
                        Path currentPath = stack.removeFirst();
                        resource = resolvePath(currentPath);

                        if (null == resource || !resource.canList(getUser(), true))
                            continue;

                        if (isTempFile(resource))
                            continue;

                        if (skipFirst)
                            skipFirst = false;
                        else if (f.accept(resource))
                            resourceWriter.writeProperties(resource, type, properties);

                        if (resource.isCollection() && depth > 0)
                        {
                            Collection<String> listPaths = resource.listNames();
                            for (String listPath : listPaths)
                            {
                                Path newPath = currentPath.append(listPath);
                                stackBelow.addLast(newPath);
                            }

                            // Displaying the lock-null resources present in that
                            // collection
                            List<Path> currentLockNullResources = lockNullResources.get(currentPath);
                            if (currentLockNullResources != null)
                            {
                                for (Path currentLockNullResource : currentLockNullResources)
                                {
                                    Path lockNullPath = currentLockNullResource;
                                    resourceWriter.writeLockNullProperties(lockNullPath, type, properties);
                                }
                            }
                        }

                        if (stack.isEmpty())
                        {
                            depth--;
                            stack = stackBelow;
                            stackBelow = new LinkedList<>();
                        }

                        resourceWriter.sendData();
                    }
                }
            }
            catch (IOException|DavException|ConfigurationException x)
            {
                throw x;
            }
            catch (Exception x)
            {
                throw new DavException(x);    
            }
            finally
            {
                if (resourceWriter != null)
                {
                    try {
                        resourceWriter.endResponse();
                        resourceWriter.sendData();
                    }
                    catch (Exception e) { }
                }
            }

            close(writer, "response writer");
            return WebdavStatus.SC_MULTI_STATUS;
        }

        protected ResourceWriter getResourceWriter(Writer writer)
        {
            return new XMLResourceWriter(writer);
        }
    }

    public static class JsonForm
    {
        private int _limit = -1;
        private int _start = -1;
        private boolean _collections = true;
        private boolean _paging = false;
        private Map<String, String> _sort = Collections.emptyMap();

        public static final String SORT_PROP = "property";
        public static final String SORT_DIR = "direction";

        public JsonForm(PropertyValues props)
        {
            BaseViewAction.springBindParameters(this, "form", props);

            if (props.contains("sort"))
            {
                Object sort = props.getPropertyValue("sort").getValue();
                if (sort instanceof String[])
                {
                    String[] sortArray = (String[])sort;
                    assert sortArray.length == 1 : "Unsupported sort array length";

                    JSONArray jsonArray = new JSONArray(sortArray[0]);
                    JSONObject sortObj = jsonArray.getJSONObject(0);

                    _sort = new HashMap<>();

                    _sort.put(SORT_PROP, sortObj.get(SORT_PROP).toString());
                    _sort.put(SORT_DIR, sortObj.get(SORT_DIR).toString());
                }
            }
        }

        public void setLimit(int limit)
        {
            _limit = limit;
        }

        public int getLimit()
        {
            return _limit;
        }

        public void setStart(int start)
        {
            _start = start;
        }

        public int getStart()
        {
            return _start;
        }

        public boolean getPaging()
        {
            return _paging;
        }

        public void setPaging(boolean paging)
        {
            _paging = paging;
        }

        public Map<String, String> getSort()
        {
            return _sort;
        }

        public void setCollections(boolean collections)
        {
            _collections = collections;
        }

        public boolean includeCollections()
        {
            return _collections;
        }
    }

    @RequiresNoPermission
    public class JsonAction extends PropfindAction
    {
        JsonForm form;

        // depth > 1 NYI
        public JsonAction()
        {
            super("JSON");
            defaultListRoot = false;
            defaultDepth = 1;

            // Map Bind Parameters
            form = new JsonForm(new MutablePropertyValues(getRequest().getParameterMap()));
        }

        @Override
        public WebdavStatus doMethod() throws DavException, IOException
        {
            WebdavResource root = getResource();
            if (root == null || !root.exists())
                return notFound();

            if (!root.canList(getUser(), true))
                return unauthorized(root);

            Writer writer = getResponse().getWriter();
            assert track(writer);
            ResourceWriter resourceWriter = null;

            try
            {
                resourceWriter = getResourceWriter(writer);
                resourceWriter.beginResponse(getResponse());

                WebdavResource resource = root;
                if (resource.isCollection())
                {
                    Collection<String> listPaths = resource.listNames();  // 17749
                    ArrayList<WebdavResource> resources = new ArrayList<>();

                    // Build resource set
                    for (String p : listPaths)
                    {
                        if (p.startsWith("."))
                            continue;
                        resource = resolvePath(root.getPath().append(p));
                        if (resource != null && resource.canList(getUser(), true))
                        {
                            if (resource.isCollection())
                            {
                                if (form.includeCollections())
                                    resources.add(resource);
                            }
                            else
                            {
                                resources.add(resource);
                            }
                        }
                    }

                    // Establish size
                    resourceWriter.writeProperty("fileCount", resources.size());

                    // Fix for Issue 22598
                    // these comparisons on the attributes of a file (last modified, size, created by, description) runs the risk
                    // of the file being deleted or its attribute being modified by some other process while this sort is going on.
                    // this may lead to: IllegalArgumentException: Comparison method violates its general contract!
                    // shouldn't happen too often, so when it does we will stop and then try the entire sort again a maximum of 5 times
                    boolean sortComplete = false;
                    int numAttempts = 0;
                    while (!sortComplete)
                    {
                        try
                        {
                            // Sort
                            resources.sort((o1, o2) ->
                            {
                                if (o1 == null && o2 == null) return 0;
                                if (o1 == null) return 1;
                                if (o2 == null) return -1;

                                boolean o1Collection = o1.isCollection();
                                boolean o2Collection = o2.isCollection();

                                if (o1Collection && o2Collection || (!o1Collection && !o2Collection))
                                {
                                    try
                                    {
                                        return doCompare(o1, o2);
                                    }
                                    catch (IOException e)
                                    {
                                        throw new RuntimeException(e);
                                    }
                                }
                                if (o1Collection)
                                    return -1;
                                else
                                    return 1;
                            });
                            // made it to the end of the sort ok
                            sortComplete=true;
                        }
                        catch (IllegalArgumentException e)
                        {
                            sortComplete = false;
                            numAttempts++;
                            if(numAttempts > 4)
                            {
                                throw e;
                            }
                        }
                    }

                    // Support for Limits
                    int limitCount = 0;
                    int limitMax = form.getPaging() ? form.getLimit()-1 : resources.size();

                    // Support for Indexing
                    for (int i=Math.max(0,form.getStart()) ; i < resources.size() ; i++)
                    {
                        if (limitCount > limitMax)
                            break;

                        resourceWriter.writeProperties(resources.get(i), Find.FIND_ALL_PROP, new ArrayList<String>());

                        if (limitMax > 0)
                            limitCount++;
                    }
                }

                resourceWriter.sendData();
            }
            catch (ConfigurationException x)
            {
                throw x;
            }
            catch (IOException x)
            {
                throw x;
            }
            catch (DavException x)
            {
                throw x;
            }
            catch (Exception x)
            {
                throw new DavException(x);
            }
            finally
            {
                if (resourceWriter != null)
                {
                    try {
                        resourceWriter.endResponse();
                        resourceWriter.sendData();
                    }
                    catch (Exception e) { }
                }
            }

            close(writer, "response writer");
            return WebdavStatus.SC_MULTI_STATUS;
        }

        @Override
        protected WebdavResource getResource() throws DavException
        {
            String node = getRequest().getParameter("node");
            if (null != node)
                return resolvePath(node);
            return super.getResource();
        }

        @Override
        protected ResourceWriter getResourceWriter(Writer writer)
        {
            return new JSONResourceWriter(writer);
        }

        private int doCompare(WebdavResource o1, WebdavResource o2) throws IOException
        {
            String sortDir = form.getSort().get(JsonForm.SORT_DIR);
            Sort.SortDirection direction = Sort.SortDirection.fromString(sortDir != null ? sortDir : Sort.SortDirection.ASC.name());

            return getCompareValue(o1, o2, direction, form.getSort().get(JsonForm.SORT_PROP));
        }

        private int getCompareValue(WebdavResource resource1, WebdavResource resource2, Sort.SortDirection direction, @Nullable String prop) throws IOException
        {
            if ("lastmodified".equalsIgnoreCase(prop))
            {
                if (resource1.isFile() && resource2.isFile())
                    return compareDate(new Date(resource1.getLastModified()), new Date(resource2.getLastModified()), direction);
                else
                    return 0;
            }
            else if ("size".equalsIgnoreCase(prop))
            {
                if (resource1.isFile() && resource2.isFile())
                    return compareLong(resource1.getContentLength(), resource2.getContentLength(), direction);
                else
                    return 0;
            }
            else if ("createdby".equalsIgnoreCase(prop))
            {
                User createdBy1 = resource1.getCreatedBy();
                User createdBy2 = resource2.getCreatedBy();
                if (createdBy1 != null && createdBy2 != null)
                    return compareString(createdBy1.getDisplayName(getUser()), createdBy2.getDisplayName(getUser()), direction, false);
                else
                    return 0;
            }
            else if ("description".equalsIgnoreCase(prop))
            {
                return compareString(resource1.getDescription(), resource2.getDescription(), direction, false);
            }

            // default to name
            return compareString(resource1.getName(), resource2.getName(), direction, true);
        }

        private int compareString(String str1, String str2, Sort.SortDirection direction, boolean ignoreCase)
        {
            String first = direction == Sort.SortDirection.ASC ? str1 : str2;
            String second = direction == Sort.SortDirection.ASC ? str2 : str1;

            if (first == null && second == null) return 0;
            if (first == null) return 1;
            if (second == null) return -1;

            if (ignoreCase)
                return first.compareToIgnoreCase(second);
            else
                return first.compareTo(second);
        }

        private int compareLong(long num1, long num2, Sort.SortDirection direction)
        {
            long first = direction == Sort.SortDirection.ASC ? num1 : num2;
            long second = direction == Sort.SortDirection.ASC ? num2 : num1;

            return (int)(first - second);
        }

        private int compareDate(Date date1, Date date2, Sort.SortDirection direction)
        {
            Date first = direction == Sort.SortDirection.ASC ? date1 : date2;
            Date second = direction == Sort.SortDirection.ASC ? date2 : date1;

            if (first == null && second == null) return 0;
            if (first == null) return 1;
            if (second == null) return -1;

            return first.compareTo(second);
        }
    }


    interface ResourceWriter
    {
        void beginResponse(WebdavResponse response) throws Exception;
        void endResponse() throws Exception;

        void writeProperty(String propertyName, Object propertyValue);

        /**
         * @param type             Propfind type
         * @param propertiesVector If the propfind type is find properties by
         *                         name, then this Vector contains those properties
         */
        void writeProperties(WebdavResource resource, Find type, List<String> propertiesVector) throws Exception;

        /**
         * @param path             Path of the current resource
         * @param type             Propfind type
         * @param propertiesVector If the propfind type is find properties by
         *                         name, then this Vector contains those properties
         */
        void writeLockNullProperties(Path path, Find type, List<String> propertiesVector) throws Exception;

        void sendData() throws Exception;
    }


    long _contentLength(WebdavResource r)
    {
        try
        {
            return r.getContentLength();
        }
        catch (IOException x)
        {
        return 0;
        }
    }


    class XMLResourceWriter implements ResourceWriter
    {
        XMLWriter xml;
        boolean gvfs = false;

        XMLResourceWriter(Writer writer)
        {
            xml = new XMLWriter(writer);
            String userAgent = getRequest().getHeader("User-Agent");
            if (null != userAgent && -1 != userAgent.indexOf("gvfs"))
                gvfs = true;
        }

        public void beginResponse(WebdavResponse response)
        {
            response.setStatus(WebdavStatus.SC_MULTI_STATUS);
            response.setContentType("text/xml; charset=UTF-8");

            xml.writeXMLHeader();
            xml.writeElement(null, "multistatus" + generateNamespaceDeclarations(), XMLWriter.OPENING);
        }

        public void endResponse()
        {
            xml.writeElement(null, "multistatus", XMLWriter.CLOSING);
        }

        public void writeProperty(String propertyName, Object propertyValue)
        {
            /* NYI */
        }

        @Override
        public void writeProperties(WebdavResource resource, Find type, List<String> propertiesVector)
        {
            boolean exists = resource.exists();
            boolean isFile = exists && resource.isFile();

            xml.writeElement(null, "response", XMLWriter.OPENING);
            String status = "HTTP/1.1 " + WebdavStatus.SC_OK;

            xml.writeElement(null, "href", XMLWriter.OPENING);
            String href = resource.getLocalHref(getViewContext());
            if (gvfs)
                href = href.replace("%40","@"); // gvfs workaround
            xml.writeText(h(href));
            xml.writeElement(null, "href", XMLWriter.CLOSING);

            String displayName = resource.getPath().equals("/") ? "/" : resource.getName();

            switch (type)
            {
                case FIND_ALL_PROP:
                {
                    xml.writeElement(null, "propstat", XMLWriter.OPENING);
                    xml.writeElement(null, "prop", XMLWriter.OPENING);

                    Path path = resource.getPath();
                    String pathStr = path.toString();
                    if (!isFile && !pathStr.endsWith("/"))
                        pathStr = pathStr + "/";
                    xml.writeProperty(null, "path", h(pathStr));

                    long created = resource.getCreated();
                    xml.writeProperty(null, "creationdate", created == Long.MIN_VALUE ? timestampZERO_iso : getISOCreationDate(created));

                    long modified = resource.getLastModified();
                    if (modified == Long.MIN_VALUE)
                        modified = created;

                    // Why does this use getHttpDateFormat() instead of getISOCreationDate()?
                    // http://www.webdav.org/specs/rfc4918.html#PROPERTY_creationdate
                    // http://www.webdav.org/specs/rfc4918.html#PROPERTY_getlastmodified
                    xml.writeProperty(null, "getlastmodified", modified == Long.MIN_VALUE ? timestampZERO_http : getHttpDateFormat(modified));
                    if (null == displayName)
                    {
                        xml.writeElement(null, "displayname", XMLWriter.NO_CONTENT);
                    }
                    else
                    {
                        xml.writeProperty(null, "displayname", h(displayName));
                    }
                    if (exists)
                    {
                        User createdby = resource.getCreatedBy();
                        if (null != createdby)
                            xml.writeProperty(null, "createdby", h(UserManager.getDisplayName(createdby.getUserId(), getUser())));

                        if (isFile)
                        {
                            User modifiedby = resource.getModifiedBy();
                            if (null != modifiedby)
                                xml.writeProperty(null, "modifiedby", UserManager.getDisplayName(modifiedby.getUserId(), getUser()));
                            xml.writeProperty(null, "getcontentlength", String.valueOf(_contentLength(resource)));
                            String contentType = resource.getContentType();
                            if (contentType != null)
                            {
                                xml.writeProperty(null, "getcontenttype", contentType);
                            }
                            xml.writeProperty(null, "getetag", resource.getETag());
                            xml.writeElement(null, "resourcetype", XMLWriter.NO_CONTENT);
                        }
                        else
                        {
                            xml.writeElement(null, "resourcetype", XMLWriter.OPENING);
                            xml.writeProperty(null, "collection", "1");
                            xml.writeElement(null, "resourcetype", XMLWriter.CLOSING);
                        }

                        if (isFileSystemFileOrDirectory(resource))
                        {
                            String absolutePath = resource.getAbsolutePath(getUser());
                            if (null != absolutePath)
                                xml.writeProperty(null, "absolutePath", absolutePath);
                        }
                    }

                    StringBuilder methodsAllowed = determineMethodsAllowed(resource);
                    xml.writeProperty(null, "options", methodsAllowed.toString());
                
                    xml.writeProperty(null, "iconHref", h(resource.getIconHref()));

                    xml.writeProperty(null, "iconFontCls", h(resource.getIconFontCls()));

                    xml.writeProperty(null, "source", "");

//					 String supportedLocks = "<lockentry>"
//								+ "<lockscope><exclusive/></lockscope>"
//								+ "<locktype><write/></locktype>"
//								+ "</lockentry>" + "<lockentry>"
//								+ "<lockscope><shared/></lockscope>"
//								+ "<locktype><write/></locktype>"
//								+ "</lockentry>";
//					 generatedXML.writeElement(null, "supportedlock", XMLWriter.OPENING);
//					 generatedXML.writeText(supportedLocks);
//					 generatedXML.writeElement(null, "supportedlock", XMLWriter.CLOSING);
//					 generateLockDiscovery(resource.getPath(), generatedXML);

                    xml.writeElement(null, "prop", XMLWriter.CLOSING);
                    xml.writeElement(null, "status", XMLWriter.OPENING);
                    xml.writeText(status);
                    xml.writeElement(null, "status", XMLWriter.CLOSING);
                    xml.writeElement(null, "propstat", XMLWriter.CLOSING);

                    break;
                }

                case FIND_PROPERTY_NAMES:

                    xml.writeElement(null, "propstat", XMLWriter.OPENING);
                    xml.writeElement(null, "prop", XMLWriter.OPENING);

                    xml.writeElement(null, "path", XMLWriter.NO_CONTENT);
                    xml.writeElement(null, "creationdate", XMLWriter.NO_CONTENT);
                    xml.writeElement(null, "displayname", XMLWriter.NO_CONTENT);
                    if (exists)
                    {
                        xml.writeElement(null, "createdby", XMLWriter.NO_CONTENT);
                        xml.writeElement(null, "getcontentlanguage", XMLWriter.NO_CONTENT);
                        xml.writeElement(null, "getcontentlength", XMLWriter.NO_CONTENT);
                        xml.writeElement(null, "getcontenttype", XMLWriter.NO_CONTENT);
                        xml.writeElement(null, "getetag", XMLWriter.NO_CONTENT);
                        xml.writeElement(null, "getlastmodified", XMLWriter.NO_CONTENT);
                        xml.writeElement(null, "modifiedby", XMLWriter.NO_CONTENT);
                        if (isFileSystemFileOrDirectory(resource))
                            xml.writeElement(null, "absolutePath", XMLWriter.NO_CONTENT);
                        //xml.writeElement(null, "directget", XMLWriter.NO_CONTENT);
                    }
                    //xml.writeElement(null, "directput", XMLWriter.NO_CONTENT);
					xml.writeElement(null, "actions", XMLWriter.NO_CONTENT);
					xml.writeElement(null, "description", XMLWriter.NO_CONTENT);
					xml.writeElement(null, "iconHref", XMLWriter.NO_CONTENT);
                    xml.writeElement(null, "iconFontCls", XMLWriter.NO_CONTENT);
					xml.writeElement(null, "history", XMLWriter.NO_CONTENT);
					xml.writeElement(null, "md5sum", XMLWriter.NO_CONTENT);
					xml.writeElement(null, "href", XMLWriter.NO_CONTENT);
					xml.writeElement(null, "ishidden", XMLWriter.NO_CONTENT);
					xml.writeElement(null, "isreadonly", XMLWriter.NO_CONTENT);
                    xml.writeElement(null, "resourcetype", XMLWriter.NO_CONTENT);
                    xml.writeElement(null, "source", XMLWriter.NO_CONTENT);
                    xml.writeElement(null, "options", XMLWriter.NO_CONTENT);
//					 generatedXML.writeElement(null, "lockdiscovery", XMLWriter.NO_CONTENT);

                    xml.writeElement(null, "prop", XMLWriter.CLOSING);
                    xml.writeElement(null, "status", XMLWriter.OPENING);
                    xml.writeText(status);
                    xml.writeElement(null, "status", XMLWriter.CLOSING);
                    xml.writeElement(null, "propstat", XMLWriter.CLOSING);

                    break;

                case FIND_BY_PROPERTY:

                    List<String> propertiesNotFound = new Vector<>();

                    // Parse the list of properties

                    xml.writeElement(null, "propstat", XMLWriter.OPENING);
                    xml.writeElement(null, "prop", XMLWriter.OPENING);

                    for (String property : propertiesVector)
                    {
                        if (property.equals("path"))
                        {
                            Path path = resource.getPath();
                            String pathStr = path.toString();
                            if (!isFile && !pathStr.endsWith("/"))
                                pathStr = pathStr + "/";
                            xml.writeProperty(null, "path", h(pathStr));
                        }
                        else if (property.equals("actions"))
                        {
                            Collection<NavTree> actions = resource.getActions(getUser());
                            xml.writeElement(null, "actions", XMLWriter.OPENING);
                            for (NavTree action : actions)
                            {
                                xml.writeElement(null, "action", XMLWriter.OPENING);
                                if (action.getText() != null)
                                {
                                    xml.writeProperty(null, "message", PageFlowUtil.filter(action.getText()));
                                }
                                if (action.getHref() != null)
                                {
                                    xml.writeProperty(null, "href", PageFlowUtil.filter(action.getHref()));
                                }
                                xml.writeElement(null, "action", XMLWriter.CLOSING);
                            }
                            xml.writeElement(null, "actions", XMLWriter.CLOSING);
                        }
                        else if (property.equals("creationdate"))
                        {
                            long created = resource.getCreated();
                            if (created == Long.MIN_VALUE)
                                xml.writeElement(null, "creationdate", XMLWriter.NO_CONTENT);
                            else
                                xml.writeProperty(null, "creationdate", getISOCreationDate(resource.getCreated()));
                        }
                        else if (property.equals("createdby"))
                        {
                            User createdby = resource.getCreatedBy();
                            if (null != createdby)
                                xml.writeProperty(null, "createdby", h(UserManager.getDisplayName(createdby.getUserId(), getUser())));
                            else
                                xml.writeElement(null, "createdby", XMLWriter.NO_CONTENT);
                        }
                        else if (property.equals("modifiedby"))
                        {
                            User modifiedBy = resource.getModifiedBy();
                            if (null != modifiedBy)
                                xml.writeProperty(null, "modifiedby", h(UserManager.getDisplayName(modifiedBy.getUserId(), getUser())));
                            else
                                xml.writeElement(null, "modifiedby", XMLWriter.NO_CONTENT);
                        }
                        else if (property.equals("description"))
                        {
                            String description = resource.getDescription();
                            if (null != description)
                                xml.writeProperty(null, "description", h(description));
                            else
                                xml.writeElement(null, "description", XMLWriter.NO_CONTENT);
                        }
                        else if (property.equals("displayname"))
                        {
                            if (null == displayName)
                            {
                                xml.writeElement(null, "displayname", XMLWriter.NO_CONTENT);
                            }
                            else
                            {
                                xml.writeElement(null, "displayname", XMLWriter.OPENING);
                                xml.writeText(h(displayName));
                                xml.writeElement(null, "displayname", XMLWriter.CLOSING);
                            }
                        }
                        else if (property.equals("getcontentlanguage"))
                        {
                            if (!exists)
                            {
                                propertiesNotFound.add(property);
                            }
                            else
                            {
                                xml.writeElement(null, "getcontentlanguage", XMLWriter.NO_CONTENT);
                            }
                        }
                        else if (property.equals("getcontentlength"))
                        {
                            if (!exists)
                            {
                                propertiesNotFound.add(property);
                            }
                            else
                            {
                                xml.writeProperty(null, "getcontentlength", (String.valueOf(_contentLength(resource))));
                            }
                        }
                        else if (property.equals("getcontenttype"))
                        {
                            if (!exists)
                            {
                                propertiesNotFound.add(property);
                            }
                            else
                            {
                                xml.writeProperty(null, "getcontenttype", resource.getContentType());
                            }
                        }
                        else if (property.equals("absolutePath"))
                        {
                            if (!exists)
                            {
                                propertiesNotFound.add(property);
                            }
                            else if (isFileSystemFileOrDirectory(resource))
                            {
                                String absolutePath = resource.getAbsolutePath(getUser());
                                if (null != absolutePath)
                                    xml.writeProperty(null, "absolutePath", absolutePath);
                            }
                        }
                        else if (property.equals("getetag"))
                        {
                            if (!exists)
                            {
                                propertiesNotFound.add(property);
                            }
                            else
                            {
                                xml.writeProperty(null, "getetag", resource.getETag());
                            }
                        }
                        else if (property.equals("getlastmodified"))
                        {
                            if (!exists)
                            {
                                propertiesNotFound.add(property);
                            }
                            else
                            {
                                long modified = resource.getLastModified();
                                if (modified == Long.MIN_VALUE)
                                    xml.writeProperty(null, "getlastmodified", "");
                                else
                                    xml.writeProperty(null, "getlastmodified", getHttpDateFormat(modified));
                            }
                        }
						else if (property.equals("href"))
						{
							xml.writeElement(null, "href", XMLWriter.OPENING);
							xml.writeText(h(resource.getLocalHref(getViewContext())));
							xml.writeElement(null, "href", XMLWriter.CLOSING);
						}
						else if (property.equals("iconHref"))
						{
                            xml.writeProperty(null, "iconHref", h(resource.getIconHref()));
						}
                        else if (property.equals("iconFontCls"))
                        {
                            xml.writeProperty(null, "iconFontCls", h(resource.getIconFontCls()));
                        }
						else if (property.equals("ishidden"))
						{
							xml.writeElement(null, "ishidden", XMLWriter.OPENING);
							xml.writeText("0");
							xml.writeElement(null, "ishidden", XMLWriter.CLOSING);
						}
						else if (property.equals("isreadonly"))
						{
							xml.writeElement(null, "isreadonly", XMLWriter.OPENING);
							xml.writeText(resource.canWrite(getUser(),false) ? "0" : "1");
							xml.writeElement(null, "isreadonly", XMLWriter.CLOSING);
						}
                        else if (property.equals("resourcetype"))
                        {
                            if (resource.isCollection())
                            {
                                xml.writeElement(null, "resourcetype", XMLWriter.OPENING);
                                xml.writeProperty(null, "collection", "1");
                                xml.writeElement(null, "resourcetype", XMLWriter.CLOSING);
                            }
                            else
                            {
                                xml.writeElement(null, "resourcetype", XMLWriter.NO_CONTENT);
                            }
                        }
                        else if (property.equals("source"))
                        {
                            xml.writeProperty(null, "source", "");
                        }
                        else if (property.equals("md5sum"))
                        {
                            String md5sum = null;
                            try
                            {
                                md5sum = resource.getMD5(getUser());
                            }
                            catch (IOException x)
                            {
                                /* */
                            }
                            if (null == md5sum)
                            {
                                xml.writeElement(null, "md5sum", XMLWriter.NO_CONTENT);
                            }
                            else
                            {
                                xml.writeElement(null, "md5sum", XMLWriter.OPENING);
                                xml.writeText(md5sum);
                                xml.writeElement(null, "md5sum", XMLWriter.CLOSING);
                            }
                        }
                        else if (property.equals("history"))
                        {
                            xml.writeElement(null, "history", XMLWriter.OPENING);
                            Collection<WebdavResolver.History> list = resource.getHistory();
                            for (WebdavResolver.History history : list)
                            {
                                xml.writeElement(null, "entry", XMLWriter.OPENING);
                                xml.writeElement(null, "date", XMLWriter.OPENING);
                                  xml.writeText(DateUtil.toISO(history.getDate()));
                                xml.writeElement(null, "date", XMLWriter.CLOSING);
                                xml.writeElement(null, "user", XMLWriter.OPENING);
                                  xml.writeText(h(history.getUser().getDisplayName(null)));
                                xml.writeElement(null, "user", XMLWriter.CLOSING);
                                xml.writeElement(null, "message", XMLWriter.OPENING);
                                  xml.writeText(h(history.getMessage()));
                                xml.writeElement(null, "message", XMLWriter.CLOSING);
                                if (null != history.getHref())
                                {
                                    xml.writeElement(null, "href", XMLWriter.OPENING);
                                      xml.writeText(h(history.getHref()));
                                    xml.writeElement(null, "href", XMLWriter.CLOSING);
                                }
                                xml.writeElement(null, "entry", XMLWriter.CLOSING);
                            }
                            xml.writeElement(null, "history", XMLWriter.CLOSING);
                        }
                        else if (property.equals("options"))
                        {
                            StringBuilder methodsAllowed = determineMethodsAllowed(resource);
                            xml.writeProperty(null, "options", methodsAllowed.toString());
                        }
                        else if (property.equals("custom"))
                        {
                            xml.writeElement(null, "custom", XMLWriter.OPENING);
                            for (Map.Entry<String, String> entry : resource.getCustomProperties(getUser()).entrySet())
                            {
                                xml.writeProperty(null, entry.getKey(), h(entry.getValue()));
                            }
                            xml.writeElement(null, "custom", XMLWriter.CLOSING);
                        }
                        // UNDONE: Direct get/put properties are not currently used by client
//                        else if (property.equals("directget"))
//                        {
//                            if (!exists)
//                                propertiesNotFound.add(property);
//                            else
//                                writeDirectRequest("directget", resource.getDirectGetRequest(getViewContext(), null));
//                        }
//                        else if (property.equals("directput"))
//                        {
//                            writeDirectRequest("directput", resource.getDirectPutRequest(getViewContext()));
//                        }
                        else
                        {
                            propertiesNotFound.add(property);
                        }
                    }

                    xml.writeElement(null, "prop", XMLWriter.CLOSING);
                    xml.writeElement(null, "status", XMLWriter.OPENING);
                    xml.writeText(status);
                    xml.writeElement(null, "status", XMLWriter.CLOSING);
                    xml.writeElement(null, "propstat", XMLWriter.CLOSING);

                    if (propertiesNotFound.size() > 0)
                    {
                        xml.writeElement(null, "propstat", XMLWriter.OPENING);
                        xml.writeElement(null, "prop", XMLWriter.OPENING);
                        for (String property : propertiesNotFound)
                        {
                            xml.writeElement(null, property, XMLWriter.NO_CONTENT);
                        }
                        xml.writeElement(null, "prop", XMLWriter.CLOSING);
                        xml.writeElement(null, "status", XMLWriter.OPENING);
                        xml.writeText("HTTP/1.1 " + WebdavStatus.SC_NOT_FOUND);
                        xml.writeElement(null, "status", XMLWriter.CLOSING);
                        xml.writeElement(null, "propstat", XMLWriter.CLOSING);
                    }
                    break;
            }

            xml.writeElement(null, "response", XMLWriter.CLOSING);
        }

        /**
         * Writes direct request xml as:
         * <pre>
         *     <directget>
         *         <method>GET</method>
         *         <endpoint>url</endpoint>
         *         <headers>
         *             <header>
         *                 <name>Authorization</name>
         *                 <value>AWS ...</value>
         *             </header>
         *         </headers>
         *     </directget>
         * </pre>
         */
        private void writeDirectRequest(@NotNull String nodeName, @Nullable DirectRequest request)
        {
            if (request == null)
            {
                xml.writeElement(null, nodeName, XMLWriter.NO_CONTENT);
                return;
            }

            xml.writeElement(null, nodeName, XMLWriter.OPENING);
            xml.writeProperty(null, "method", h(request.getMethod()));
            xml.writeProperty(null, "endpoint", h(request.getEndpoint().toASCIIString()));
            xml.writeElement(null, "headers", XMLWriter.OPENING);
            for (String header : request.getHeaders().keySet())
            {
                xml.writeElement(null, "header", XMLWriter.OPENING);
                xml.writeProperty(null, "name", h(header));

                Collection<String> col = request.getHeaders().get(header);
                String firstValue = col.iterator().next();
                xml.writeProperty(null, "value", h(firstValue));
                xml.writeElement(null, "header", XMLWriter.CLOSING);
            }
            xml.writeElement(null, "headers", XMLWriter.CLOSING);
            xml.writeElement(null, nodeName, XMLWriter.CLOSING);
        }


        @Override
        public void writeLockNullProperties(Path path, Find type, List<String> propertiesVector) throws DavException
        {
            // Retrieving the lock associated with the lock-null resource
            LockInfo lock = resourceLocks.get(path);
            if (lock == null)
                return;

            WebdavResource resource = resolvePath(path);
            if (null == resource)
                return;

            xml.writeElement(null, "response", XMLWriter.OPENING);
            String status = "HTTP/1.1 " + WebdavStatus.SC_OK;

            // Generating href element
            xml.writeElement(null, "href", XMLWriter.OPENING);
            xml.writeText(h(resource.getHref(getViewContext())));
            xml.writeElement(null, "href", XMLWriter.CLOSING);

            switch (type)
            {
                case FIND_ALL_PROP:

                    xml.writeElement(null, "propstat", XMLWriter.OPENING);
                    xml.writeElement(null, "prop", XMLWriter.OPENING);

                    xml.writeProperty(null, "creationdate", getISOCreationDate(lock.creationDate.getTime()));
                    xml.writeElement(null, "displayname", XMLWriter.OPENING);
                    xml.writeText(h(resource.getName()));
                    xml.writeElement(null, "displayname", XMLWriter.CLOSING);
                    xml.writeProperty(null, "getlastmodified", getHttpDateFormat(lock.creationDate.getTime()));
                    xml.writeProperty(null, "getcontentlength", String.valueOf(0));
                    xml.writeProperty(null, "getcontenttype", "");
                    xml.writeProperty(null, "getetag", "");
                    xml.writeElement(null, "resourcetype", XMLWriter.OPENING);
                    xml.writeElement(null, "lock-null", XMLWriter.NO_CONTENT);
                    xml.writeElement(null, "resourcetype", XMLWriter.CLOSING);

                    xml.writeProperty(null, "source", "");

                    xml.writeElement(null, "prop", XMLWriter.CLOSING);
                    xml.writeElement(null, "status", XMLWriter.OPENING);
                    xml.writeText(status);
                    xml.writeElement(null, "status", XMLWriter.CLOSING);
                    xml.writeElement(null, "propstat", XMLWriter.CLOSING);

                    break;

                case FIND_PROPERTY_NAMES:

                    xml.writeElement(null, "propstat", XMLWriter.OPENING);
                    xml.writeElement(null, "prop", XMLWriter.OPENING);

                    xml.writeElement(null, "creationdate", XMLWriter.NO_CONTENT);
                    xml.writeElement(null, "displayname", XMLWriter.NO_CONTENT);
                    xml.writeElement(null, "getcontentlanguage", XMLWriter.NO_CONTENT);
                    xml.writeElement(null, "getcontentlength", XMLWriter.NO_CONTENT);
                    xml.writeElement(null, "getcontenttype", XMLWriter.NO_CONTENT);
                    xml.writeElement(null, "getetag", XMLWriter.NO_CONTENT);
                    xml.writeElement(null, "getlastmodified", XMLWriter.NO_CONTENT);
                    xml.writeElement(null, "resourcetype", XMLWriter.NO_CONTENT);
                    xml.writeElement(null, "source", XMLWriter.NO_CONTENT);

                    xml.writeElement(null, "prop", XMLWriter.CLOSING);
                    xml.writeElement(null, "status", XMLWriter.OPENING);
                    xml.writeText(status);
                    xml.writeElement(null, "status", XMLWriter.CLOSING);
                    xml.writeElement(null, "propstat", XMLWriter.CLOSING);

                    break;

                case FIND_BY_PROPERTY:

                    List<String> propertiesNotFound = new Vector<>();

                    // Parse the list of properties

                    xml.writeElement(null, "propstat", XMLWriter.OPENING);
                    xml.writeElement(null, "prop", XMLWriter.OPENING);

                    for (String property : propertiesVector)
                    {
                        if (property.equals("creationdate"))
                        {
                            xml.writeProperty(null, "creationdate", getISOCreationDate(lock.creationDate.getTime()));
                        }
                        else if (property.equals("displayname"))
                        {
                            xml.writeElement(null, "displayname", XMLWriter.OPENING);
                            xml.writeText(h(resource.getName()));
                            xml.writeElement(null, "displayname", XMLWriter.CLOSING);
                        }
                        else if (property.equals("getcontentlanguage"))
                        {
                            xml.writeElement(null, "getcontentlanguage", XMLWriter.NO_CONTENT);
                        }
                        else if (property.equals("getcontentlength"))
                        {
                            xml.writeProperty(null, "getcontentlength", (String.valueOf(0)));
                        }
                        else if (property.equals("getcontenttype"))
                        {
                            xml.writeProperty(null, "getcontenttype", "");
                        }
                        else if (property.equals("getetag"))
                        {
                            xml.writeProperty(null, "getetag", "");
                        }
                        else if (property.equals("getlastmodified"))
                        {
                            xml.writeProperty(null, "getlastmodified", getHttpDateFormat(lock.creationDate.getTime()));
                        }
                        else if (property.equals("resourcetype"))
                        {
                            xml.writeElement(null, "resourcetype", XMLWriter.OPENING);
                            xml.writeElement(null, "lock-null", XMLWriter.NO_CONTENT);
                            xml.writeElement(null, "resourcetype", XMLWriter.CLOSING);
                        }
                        else if (property.equals("source"))
                        {
                            xml.writeProperty(null, "source", "");
                        }
                        else
                        {
                            propertiesNotFound.add(property);
                        }

                    }

                    xml.writeElement(null, "prop", XMLWriter.CLOSING);
                    xml.writeElement(null, "status", XMLWriter.OPENING);
                    xml.writeText(status);
                    xml.writeElement(null, "status", XMLWriter.CLOSING);
                    xml.writeElement(null, "propstat", XMLWriter.CLOSING);

                    if (propertiesNotFound.size() > 0)
                    {
                        status = "HTTP/1.1 " + WebdavStatus.SC_NOT_FOUND;
                        xml.writeElement(null, "propstat", XMLWriter.OPENING);
                        xml.writeElement(null, "prop", XMLWriter.OPENING);

                        for (String aPropertiesNotFound : propertiesNotFound)
                            xml.writeElement(null, aPropertiesNotFound, XMLWriter.NO_CONTENT);

                        xml.writeElement(null, "prop", XMLWriter.CLOSING);
                        xml.writeElement(null, "status", XMLWriter.OPENING);
                        xml.writeText(status);
                        xml.writeElement(null, "status", XMLWriter.CLOSING);
                        xml.writeElement(null, "propstat", XMLWriter.CLOSING);
                    }
                    break;
            }

            xml.writeElement(null, "response", XMLWriter.CLOSING);
        }

        public void sendData() throws IOException
        {
            xml.sendData();
        }
    }


    class JSONResourceWriter implements ResourceWriter
    {
        BufferedWriter out;
        JSONWriter json;
        Map<String, Object> extraProps;

        JSONResourceWriter(Writer writer)
        {
            if (writer instanceof BufferedWriter)
                out = (BufferedWriter)writer;
            else
                out = new BufferedWriter(writer);
            json = new JSONWriter(out);
            extraProps = new HashMap<>();
        }

        public void beginResponse(WebdavResponse response) throws Exception
        {
            response.setContentType("application/json; charset=UTF-8");
            json.object();
            json.key("files");
            json.array();
        }

        public void endResponse() throws Exception
        {
            json.endArray();

            for (Map.Entry<String, Object> entry : extraProps.entrySet())
            {
                json.key(entry.getKey()).value(entry.getValue());
            }

            json.endObject();
        }

        public void writeProperty(String propertyName, Object propertyValue)
        {
            extraProps.put(propertyName, propertyValue);
        }

        public void writeProperties(WebdavResource resource, Find type, List<String> propertiesVector) throws Exception
        {
            json.object();
            json.key("id").value(resource.getPath());
            String displayName = resource.getPath().equals("/") ? "/" : resource.getName();
            json.key("href").value(resource.getLocalHref(getViewContext()));
            json.key("text").value(displayName);
            json.key("iconHref").value(resource.getIconHref());
            json.key("iconFontCls").value(resource.getIconFontCls());
            json.key("options").value(determineMethodsAllowed(resource));

            long created = resource.getCreated();
            if (Long.MIN_VALUE != created)
                json.key("creationdate").value(new Date(created));
            User createdby = resource.getCreatedBy();
            if (null != createdby)
                json.key("createdby").value(h(UserManager.getDisplayName(createdby.getUserId(), getUser())));
            String description = resource.getDescription();
            if (null != description)
                json.key("description").value(description);
            json.key("collection").value(resource.isCollection());
            if (resource.isFile())
            {
                long lastModified = resource.getLastModified();
                if (Long.MIN_VALUE != lastModified)
                    json.key("lastmodified").value(new Date(lastModified));
                long length = resource.getContentLength();
                json.key("contentlength").value(length);
                if (length >= 0)
                    json.key("size").value(length);
                String contentType = resource.getContentType();
                if (contentType != null)
                    json.key("contenttype").value(contentType);
                json.key("etag").value(resource.getETag());

                // UNDONE: Don't calculate directget URL for every item -- it may be expensive to generate.
                // UNDONE: Client doesn't support handling directget yet
                //if (propertiesVector.contains("directget"))
                    //json.key("directget").value(writeDirectRequest(resource.getDirectGetRequest(getViewContext())));
                //if (propertiesVector.contains("directput"))
                    //json.key("directput").value(writeDirectRequest(resource.getDirectPutRequest(getViewContext())));

                json.key("leaf").value(true);
            }
            else
            {
                json.key("leaf").value(false);
            }

            if (isFileSystemFileOrDirectory(resource))
            {
                String absolutePath = resource.getAbsolutePath(getUser());
                if (null != absolutePath)
                    json.key("absolutePath").value(absolutePath);
            }

            Collection<NavTree> actions = resource.getActions(getUser());
            if (!actions.isEmpty())
            {
                JSONArray actionArr = new JSONArray();
                for (NavTree action : actions)
                {
                    JSONObject actionObj = new JSONObject();
                    if (action.getText() != null)
                    {
                        actionObj.put("message", PageFlowUtil.filter(action.getText()));
                    }
                    if (action.getHref() != null)
                    {
                        actionObj.put("href", PageFlowUtil.filter(action.getHref()));
                    }
                    actionArr.put(actionObj);
                }
                json.key("actions").value(actionArr);
            }

            json.endObject();
            out.newLine();
        }

        /**
         * Creates JSONObject of the form:
         * <pre>
         * {
         *   "method": "[http verb]",
         *   "endpoint": "[url]",
         *   "headers": {
         *       "header1": value,
         *       "header2": value,
         *   }
         * }
         * </pre>
         */
        private JSONObject writeDirectRequest(@Nullable DirectRequest request)
        {
            if (request == null)
                return null;

            JSONObject obj = new JSONObject();
            obj.put("method", request.getMethod());
            obj.put("endpoint", request.getEndpoint());

            JSONObject headers = new JSONObject();
            for (String header : request.getHeaders().keySet())
            {
                Collection<String> col = request.getHeaders().get(header);
                String firstValue = col.iterator().next();
                headers.put(header, firstValue);
            }
            obj.put("headers", headers);
            return obj;
        }

        @Override
        public void writeLockNullProperties(Path path, Find type, List<String> propertiesVector) throws Exception
        {
            // Retrieving the lock associated with the lock-null resource
            LockInfo lock = resourceLocks.get(path);
            if (lock == null)
                return;

            WebdavResource resource = resolvePath(path);
            if (null == resource)
                return;

            json.object();
            json.key("id").value(resource.getPath());
            json.key("href").value(resource.getHref(getViewContext()));
            json.key("text").value(resource.getName());
            json.key("creationdate").value(lock.creationDate);
            json.key("lastmodified").value(lock.creationDate);
            json.endObject();
        }

        public void sendData() throws IOException
        {
            out.flush();
        }
    }

    private static boolean isFileSystemFileOrDirectory(WebdavResource resource)
    {
        if (resource.isFile())
            return true;
        File file = resource.getFile();
        return null != file && file.isDirectory();
    }



    @RequiresNoPermission
    public class MkcolAction extends DavAction
    {
        public MkcolAction()
        {
            super("MKCOL");
        }

        @Override
        WebdavStatus doMethod() throws DavException, IOException
        {
            checkReadOnly();
            checkLocked();

            Path path = getResourcePath();
            WebdavResource resource = resolvePath();
            if (null == resource || path.size()==0)
                throw new DavException(WebdavStatus.SC_FORBIDDEN, String.valueOf(path));

            boolean exists = resource.exists();

            // Can't create a collection if a resource already exists at the given path
            if (exists)
            {
                // Get allowed methods
                StringBuilder methodsAllowed = determineMethodsAllowed(resource);
                getResponse().setMethodsAllowed(methodsAllowed);
                throw new DavException(WebdavStatus.SC_METHOD_NOT_ALLOWED);
            }

            checkAllowedFileName(resource.getName());

            try (InputStream is = new ReadAheadInputStream(getRequest().getInputStream()))
            {
                if (is.available() > 0)
                {
                    DocumentBuilder documentBuilder = getDocumentBuilder();
                    try
                    {
                        // TODO : Process this request body
                        documentBuilder.parse(new InputSource(is));
                        throw new DavException(WebdavStatus.SC_NOT_IMPLEMENTED);
                    }
                    catch (SAXException saxe)
                    {
                        // Parse error - assume invalid content
                        throw new DavException(WebdavStatus.SC_UNSUPPORTED_MEDIA_TYPE);
                    }
                }
            }

            // MKCOL with missing intermediate should fail (RFC2518:8.3.1)
            Resource parent = resource.parent();
            if (null == parent || !parent.isCollection())
                throw new DavException(WebdavStatus.SC_CONFLICT, String.valueOf(path.getParent()) + " is not a collection");

            if (!resource.canCreate(getUser(),true))
                return unauthorized(resource);

            boolean result = resource.createCollection(getUser());

            if (!result)
            {
                throw new DavException(WebdavStatus.SC_CONFLICT);
            }
            else
            {
                updateDataObject(resource);
                resource.notify(getViewContext(), "folder created");
                // Removing any lock-null resource which would be present
                lockNullResources.remove(path);
                return WebdavStatus.SC_CREATED;
            }
        }
    }


    @RequiresNoPermission
    public class PutAction extends DavAction
    {
        // this is a member so PostAction() can set it
        WebdavResource _resource;
        FileStream _fis;

        public PutAction()
        {
            super("PUT");
        }

        protected PutAction(String method)
        {
            super(method);
        }

        protected void setResource(WebdavResource r)
        {
            _resource = r;
        }
        
        WebdavResource getResource() throws DavException
        {
            if (null == _resource)
                _resource = resolvePath();
            return _resource;
        }

        protected void setFileStream(final FileStream fis)
        {
            _fis = new FileStream()
            {
                @Override
                public long getSize() throws IOException
                {
                    return fis.getSize();
                }

                @Override
                public InputStream openInputStream() throws IOException
                {
                    return SessionKeepAliveFilter.wrap(fis.openInputStream(), getRequest());
                }

                @Override
                public void closeInputStream() throws IOException
                {
                    fis.closeInputStream();
                }
            };
        }

        FileStream getFileStream() throws DavException, IOException
        {
            if (null == _fis)
            {
                final InputStream is = getRequest().getInputStream();
                String contentLength = getRequest().getHeader("Content-Length");
                long size = -1;
                try
                {
                    if (null != contentLength)
                        size = Long.parseLong(contentLength);
                }
                catch (NumberFormatException x)
                {
                    throw new DavException(WebdavStatus.SC_BAD_REQUEST, "Content-Length: " + contentLength);
                }
                // NOTE Mac Finder does not set content-length, but we don't really need it
                final long _size = size;
                FileStream fis = new FileStream()
                {
                    public long getSize()
                    {
                        return _size;
                    }
                    public InputStream openInputStream() throws IOException
                    {
                        return is;
                    }
                    public void closeInputStream() throws IOException
                    {
                        /* */
                    }
                };
                setFileStream(fis);
            }
            return _fis;
        }

        WebdavStatus doMethod() throws DavException, IOException, RedirectException
        {
            checkReadOnly();
            checkLocked();

            WebdavResource resource = getResource();
            if (resource == null)
                return notFound();
            checkAllowedFileName(resource.getName());

            boolean exists = resource.exists();
            boolean overwrite = getOverwriteParameter(true);

            boolean deleteFileOnFail = false;
            boolean temp = false;

            if (exists && !resource.canWrite(getUser(),true) || !exists && !resource.canCreate(getUser(),true))
                return unauthorized(resource);
            checkLocked();
            if (resource.isCollectionType() || exists && resource.isCollection())
                throw new DavException(WebdavStatus.SC_METHOD_NOT_ALLOWED, "Cannot overwrite folder");

            if (exists)
            {
                if (!overwrite)
                {
                    // allow finder to overwrite zero byte files without overwrite header
                    boolean finderException = isMacFinder() && 0 == resource.getContentLength();
                    if (!finderException)
                        throw new DavException(WebdavStatus.SC_FILE_MATCH, "Cannot overwrite file");
                }
            }

            Range range = parseContentRange();
            RandomAccessFile raf = null;
            OutputStream os = null;

            try
            {
                if (!exists)
                {
                    temp = getTemporary();
                    if (temp)
                        markTempFile(resource);
                    deleteFileOnFail = true;
                }

                File file = resource.getFile();
                if (range != null)
                {
                    if (resource.getContentType().startsWith("text/html") && !getUser().isDeveloper())
                        throw new DavException(WebdavStatus.SC_FORBIDDEN, "Partial writing of html files is not allowed");
                    if (range.start > raf.length() || (range.end - range.start) > Integer.MAX_VALUE)
                        throw new DavException(WebdavStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    // CONSIDER: use temp file
                    _ByteArrayOutputStream bos = new _ByteArrayOutputStream((int)(range.end-range.start));
                    FileUtil.copyData(getFileStream().openInputStream(), bos);
                    if (bos.size() != range.end-range.start)
                        throw new DavException(WebdavStatus.SC_BAD_REQUEST);
                    if (null == file)
                        return WebdavStatus.SC_NOT_IMPLEMENTED;
                    raf = new RandomAccessFile(file,"rw");
                    assert track(raf);
                    raf.seek(range.start);
                    bos.writeTo(raf);
                    raf.getFD().sync();
                    resource.notify(getViewContext(), "modified range " + range.toString());
                }
                else
                {
                    if (resource.getContentType().startsWith("text/html") && !getUser().isDeveloper())
                    {
                        _ByteArrayOutputStream bos = new _ByteArrayOutputStream(4*1025);
                        FileUtil.copyData(getFileStream().openInputStream(), bos);
                        byte[] buf = bos.toByteArray();
                        String html = new String(buf, StringUtilsLabKey.DEFAULT_CHARSET);
                        List<String> errors = new ArrayList<>();
                        List<String> script = new ArrayList<>();
                        PageFlowUtil.validateHtml(html, errors, script);
                        if (!script.isEmpty())
                            throw new DavException(WebdavStatus.SC_FORBIDDEN, "User is not allowed to save script in html files.");
                        resource.copyFrom(getUser(), new FileStream.ByteArrayFileStream(buf));
                    }
                    else
                    {
                        try
                        {
                            resource.copyFrom(getUser(), getFileStream());
                        }
                        catch (IOException io)
                        {
                            if (null != resource.getFile() && !resource.getFile().exists())
                                throw new DavException(WebdavStatus.SC_INTERNAL_SERVER_ERROR, "Can not create resource: " + resource.getPath(), io);
                            throw io;
                        }
                    }

                    if (!temp)
                    {
                        if (exists)
                        {
                            fireFileReplacedEvent(resource);
                        }
                        else
                        {
                            fireFileCreatedEvent(resource);
                        }
                    }
                }

                // if we got here then we succeeded
                deleteFileOnFail = false;
            }
            finally
            {
                getFileStream().closeInputStream();
                close(os, "put action outputstream");
                close(raf, "put action outputdata");
                if (deleteFileOnFail)
                {
                    resource.delete(getUser());
                }

                if (_log.isDebugEnabled())
                {
                    File f = resource.getFile();
                    if (null != f && f.exists())
                        _log.debug(f.getName() + " length=" + f.length());
                }
            }

            lockNullResources.remove(resource.getPath());
            if (exists && !overwrite) //TODO: review this
                return WebdavStatus.SC_OK;
            else
                return WebdavStatus.SC_CREATED;
        }
    }

    @RequiresNoPermission
    public class DeleteAction extends DavAction
    {
        public DeleteAction()
        {
            super("DELETE");
        }

        WebdavStatus doMethod() throws DavException, IOException
        {
            checkReadOnly();
            checkLocked();

            return deleteResource(getResourcePath());
        }
    }


    /**
     * Delete a resource.
     *
     * @param path      Path of the resource which is to be deleted
     * @return SC_NO_CONTENT indicates success
     * @throws java.io.IOException
     * @throws org.labkey.core.webdav.DavController.DavException
     */
    private WebdavStatus deleteResource(Path path) throws DavException, IOException
    {
        checkLocked(path);

        WebdavResource resource = resolvePath(path);
        boolean exists = resource != null && resource.exists();
        if (!exists)
            return notFound();

        List<String> messages = new ArrayList<>();
        if (!resource.canDelete(getUser(),true,messages))
            return unauthorized(resource, messages);

        if (!resource.isCollection())
        {
            if (!resource.delete(getUser()))
            {
                resource.notify(getViewContext(), "fileDeleteFailed");
                if (null != resource.getFile())
                    throw new ConfigurationException("Unable to delete resource: " + resource.getPath().toString());
                else
                    throw new DavException(WebdavStatus.SC_INTERNAL_SERVER_ERROR, "Unable to delete resource: " + resource.getPath().toString());
            }

            boolean temp = rmTempFile(resource);
            if (!temp)
            {
                fireFileDeletedEvent(resource);
            }
            return WebdavStatus.SC_NO_CONTENT;
        }
        else
        {
            LinkedHashMap<Path,WebdavStatus> errorList = new LinkedHashMap<>();

            deleteCollection(resource, errorList);
            removeFromDataObject(resource);
            if (!resource.delete(getUser()))
            {
                resource.notify(getViewContext(), "dirDeleteFailed");
                errorList.put(resource.getPath(), WebdavStatus.SC_INTERNAL_SERVER_ERROR);
            }
            else
                resource.notify(getViewContext(), "deleted");
            if (!errorList.isEmpty())
                return sendReport(errorList);
            return WebdavStatus.SC_NO_CONTENT;
        }
    }

    // The following fire*Event() methods should be replaced with a full subscribe-notify implementation, but for now
    // just add to the method bodies
    private void fireFileReplacedEvent(WebdavResource resource)
    {
        long start = System.currentTimeMillis();
        resource.notify(getViewContext(), "replaced");
        updateIndexAndDataObject(resource);
        _log.debug("fireFileReplaceEvent: " + DateUtil.formatDuration(System.currentTimeMillis() - start));
    }

    private void fireFileCreatedEvent(WebdavResource resource)
    {
        long start = System.currentTimeMillis();
        resource.notify(getViewContext(), "created");
        updateIndexAndDataObject(resource);

        Container srcContainer = resource.getContainerId() == null ? null : ContainerManager.getForId(resource.getContainerId());
        File file = resource.getFile();
        if (null != file)
            FileContentService.get().fireFileCreateEvent(file, getUser(), srcContainer);
        _log.debug("fireFileCreatedEvent: " + DateUtil.formatDuration(System.currentTimeMillis() - start));
    }

    private void updateIndexAndDataObject(WebdavResource resource)
    {
        addToIndex(resource);
        updateDataObject(resource);
    }

    private void updateDataObject(WebdavResource resource)
    {
        File file = resource.getFile();
        if (file != null)
        {
            Container c = resource.getContainerId() == null ? null : ContainerManager.getForId(resource.getContainerId());
            if (c != null)
            {
                ExpData data = ExperimentService.get().getExpDataByURL(file, c);

                if (data == null)
                {
                    data = ExperimentService.get().createData(c, new DataType("UploadedFile"));
                    data.setName(file.getName());
                    data.setDataFileURI(file.toURI());
                }
                if (data.getDataFileUrl() != null && data.getDataFileUrl().length() > ExperimentService.get().getTinfoData().getColumn("DataFileURL").getScale())
                {
                    // If the path is too long to store, bail out without creating an exp.data row
                    return;
                }
                data.save(getUser());

                if (getRequest().getParameter("description") != null)
                {
                    try
                    {
                        data.setComment(getUser(), getRequest().getParameter("description"));
                    }
                    catch (ValidationException e) {}
                }
            }
        }
    }

    private void fireFileDeletedEvent(WebdavResource resource)
    {
        long start = System.currentTimeMillis();
        resource.notify(getViewContext(), "deleted");
        removeFromIndex(resource);
        removeFromDataObject(resource);
        _log.debug("fireFileDeletedEvent: " + DateUtil.formatDuration(System.currentTimeMillis() - start));
    }

    private void removeFromDataObject(WebdavResource resource)
    {
        Container c = resource.getContainerId() == null ? null : ContainerManager.getForId(resource.getContainerId());
        File file = resource.getFile();
        if (c != null && file != null)
        {
            ExpData data = ExperimentService.get().getExpDataByURL(file, c);
            if (data != null && ExperimentService.get().getRunsUsingDatas(Arrays.asList(data)).isEmpty())
            {
                data.delete(getUser());
            }
        }
    }


    /**
     * Deletes a collection.
     *
     * @param coll collection to be deleted
     * @param errorList Contains the list of the errors which occurred
     */
    private void deleteCollection(WebdavResource coll, Map<Path,WebdavStatus> errorList)
    {
        HttpServletRequest request = getRequest();
        Path path = coll.getPath();

        _log.debug("Delete:" + path);

        String ifHeader = request.getHeader("If");
        if (ifHeader == null)
            ifHeader = "";

        String lockTokenHeader = request.getHeader("Lock-Token");
        if (lockTokenHeader == null)
            lockTokenHeader = "";

        Collection<? extends WebdavResource> children = coll.list();
        List<String> outMessage = new ArrayList<>();

        for (WebdavResource child : children)
        {
            Path childName = child.getPath();

            if ( LockResult.LOCKED == isLocked(childName, ifHeader + lockTokenHeader))
            {
                errorList.put(childName, WebdavStatus.SC_LOCKED);
            }
            else
            {
                outMessage.clear();
                if (!child.canDelete(getUser(),true,outMessage))
                {
                    child.notify(getViewContext(), "fileDeleteFailed");
                    setLastError(new DavException(WebdavStatus.SC_FORBIDDEN, outMessage.isEmpty() ? null : outMessage.get(0), child.getPath()));
                    errorList.put(childName, WebdavStatus.SC_FORBIDDEN);
                    continue;
                }
                
                if (child.isCollection())
                    deleteCollection(child, errorList);

                try
                {
                    removeFromDataObject(child);
                    if (!child.delete(getUser()))
                        errorList.put(childName, WebdavStatus.SC_INTERNAL_SERVER_ERROR);
                }
                catch (IOException x)
                {
                    errorList.put(childName, WebdavStatus.SC_INTERNAL_SERVER_ERROR);
                }
                
                boolean temp = rmTempFile(child);
                if (!temp)
                {
                    child.notify(getViewContext(), "deleted");
                    removeFromIndex(child);
                }
                else
                {
                    child.notify(getViewContext(), "fileDeleteFailed");
                }
            }
        }
    }

    /**
     * Send a multistatus element containing a complete error report to the client.
     *
     * @param errors The errors to be displayed
     */
    private WebdavStatus sendReport(Map<Path,WebdavStatus> errors) throws IOException
    {
        WebdavResponse response = getResponse();
        response.setStatus(WebdavStatus.SC_MULTI_STATUS);

        String absoluteUri = getRequest().getRequestURI();
        Path relativePath = getResourcePath();

        XMLWriter generatedXML = new XMLWriter();
        generatedXML.writeXMLHeader();

        generatedXML.writeElement(null, "multistatus" + generateNamespaceDeclarations(), XMLWriter.OPENING);

        for (Path errorPath : errors.keySet())
        {
            WebdavStatus status = errors.get(errorPath);
            generatedXML.writeElement(null, "response", XMLWriter.OPENING);
            generatedXML.writeElement(null, "href", XMLWriter.OPENING);
            String toAppend = relativePath.relativize(errorPath).toString();
            if (!toAppend.startsWith("/"))
                toAppend = "/" + toAppend;
            generatedXML.writeText(absoluteUri + toAppend);
            generatedXML.writeElement(null, "href", XMLWriter.CLOSING);
            generatedXML.writeElement(null, "status", XMLWriter.OPENING);
            if (status == WebdavStatus.SC_CONFLICT)
                generatedXML.writeText("HTTP/1.1 " + status.code);    // litmus doesn't want the string...
            else
                generatedXML.writeText("HTTP/1.1 " + status.toString());
            generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
            generatedXML.writeElement(null, "response", XMLWriter.CLOSING);
        }

        generatedXML.writeElement(null, "multistatus", XMLWriter.CLOSING);

        Writer writer = response.getWriter();
        writer.write(generatedXML.toString());
        close(writer, "response writer");
        return WebdavStatus.SC_MULTI_STATUS;
    }
    

    @RequiresNoPermission
    public class TraceAction extends DavAction
    {
        public TraceAction()
        {
            super("TRACE");
        }
    }


    @RequiresNoPermission
    public class PropPatchAction extends DavAction
    {
        public PropPatchAction()
        {
            super("PROPPATCH");
        }

        WebdavStatus doMethod() throws DavException, IOException
        {
            checkReadOnly();
            checkLocked();

            if (!isWindowsExplorer())
            {
                throw new DavException(WebdavStatus.SC_METHOD_NOT_ALLOWED);
            }

            // Windows insists on calling PROPPATCH despite being told that we don't support it. It will consider
            // uploads unsuccessful if this doesn't return a minimal response. See issue 33197
            Writer writer = getResponse().getWriter();
            assert track(writer);
            try
            {
                WebdavResource resource = resolvePath();
                if (resource == null)
                {
                    throw new DavException(WebdavStatus.SC_NOT_FOUND);
                }

                XMLResourceWriter resourceWriter = new XMLResourceWriter(writer);
                resourceWriter.beginResponse(getResponse());

                resourceWriter.xml.writeElement(null, "response", XMLWriter.OPENING);
                String status = "HTTP/1.1 " + WebdavStatus.SC_OK;

                resourceWriter.xml.writeElement(null, "href", XMLWriter.OPENING);
                resourceWriter.xml.writeText(h(resource.getLocalHref(getViewContext())));
                resourceWriter.xml.writeElement(null, "href", XMLWriter.CLOSING);

                resourceWriter.xml.writeElement(null, "propstat", XMLWriter.OPENING);
                resourceWriter.xml.writeElement(null, "status", XMLWriter.OPENING);
                resourceWriter.xml.writeText(h(status));
                resourceWriter.xml.writeElement(null, "status", XMLWriter.CLOSING);
                resourceWriter.xml.writeElement(null, "propstat", XMLWriter.CLOSING);
                resourceWriter.xml.writeElement(null, "response", XMLWriter.CLOSING);

                resourceWriter.endResponse();
                resourceWriter.sendData();

                return WebdavStatus.SC_MULTI_STATUS;
            }
            catch (Exception e)
            {
                throw new DavException(e);
            }
            finally
            {
                close(writer, "response writer");
            }
        }
    }


    @RequiresNoPermission
    public class CopyAction extends DavAction
    {
        public CopyAction()
        {
            super("COPY");
        }

        @Override
        WebdavStatus doMethod() throws DavException, IOException
        {
            checkReadOnly();
//            checkLocked();
            checkLocked(getDestinationPath());

            return copyResource();
        }
    }


    @RequiresNoPermission
    public class MoveAction extends DavAction
    {
        public MoveAction()
        {
            super("MOVE");
        }

        @Override
        WebdavStatus doMethod() throws DavException, IOException
        {
            checkReadOnly();
            checkLocked();

            Path destinationPath = getDestinationPath();
            if (destinationPath == null)
                throw new DavException(WebdavStatus.SC_BAD_REQUEST);

            WebdavResource src = resolvePath();
            if (null == src || !src.exists())
                notFound();

            WebdavResource dest = resolvePath(destinationPath);
            if (null == dest || dest.getPath().equals(src.getPath()))
                throw new DavException(WebdavStatus.SC_FORBIDDEN);
            checkAllowedFileName(dest.getName());

            boolean overwrite = getOverwriteParameter(false);
            boolean exists = dest.exists();

            if (!src.canRead(getUser(), true))
                return unauthorized(src);
            if (exists && !dest.canWrite(getUser(),true) || !exists && !dest.canCreate(getUser(),true))
                return unauthorized(dest);

            if (destinationPath.isDirectory() && src.isFile())
            {
                return WebdavStatus.SC_NO_CONTENT;
            }

            // Don't allow creating text/html via rename (circumventing script checking)
            if (!isSafeCopy(src,dest))
                throw new DavException(WebdavStatus.SC_FORBIDDEN, "Cannot create 'text/html' file using move.");

            if (exists)
            {
                if (!overwrite)
                    throw new DavException(WebdavStatus.SC_PRECONDITION_FAILED);
                if (dest.isCollection())
                {
                    if (!_overwriteCollection)
                        throw new DavException(WebdavStatus.SC_FORBIDDEN, "Cannot overwrite folder");
                    WebdavStatus ret = deleteResource(destinationPath);
                    if (ret != WebdavStatus.SC_NO_CONTENT)
                        return ret;
                }
            }

            // File based
            File srcFile = src.getFile();
            File destFile = dest.getFile();
            if (srcFile != null && destFile != null)
            {
                File tmp = null;
                try
                {
                    if (destFile.exists())
                    {
                        WebdavResource parent = (WebdavResource)dest.parent();
                        tmp = new File(parent.getFile(), "~rename" + GUID.makeHash() + "~" + dest.getName());
                        markTempFile(tmp);
                        if (!destFile.renameTo(tmp))
                            throw new ConfigurationException("Could not remove destination: " + dest.getPath());
                    }
                    // NOTE: destFile get's marked temp even if renameTo fails, this is OK for our uses or Temporary=T (always uniquified names)
                    if (getTemporary())
                        markTempFile(dest);
                    try
                    {
                        if (srcFile.isDirectory())
                        {
                            FileUtils.moveDirectory(srcFile, destFile);
                        }
                        else
                        {
                            FileUtils.moveFile(srcFile, destFile);
                        }
                    }
                    catch (IOException ex)
                    {
                        if (null != tmp)
                            tmp.renameTo(destFile);
                        throw new ConfigurationException("Could not move source:" + src.getPath(), ex);
                    }
                }
                finally
                {
                    if (null != tmp)
                    {
                        tmp.delete();
                        rmTempFile(tmp);
                    }
                }
            }
            // Stream based
            else
            {
                if (getTemporary())
                    markTempFile(dest);
                dest.moveFrom(getUser(),src);
            }

            // FileSystemResource caches file/directory/exists status, we need to reload the object
            WebdavResource destReload = resolvePath(destinationPath, true);
            if (null != destReload)
                dest = destReload;

            if (rmTempFile(src))
            {
                fireFileCreatedEvent(dest);
            }
            else
            {
                fireFileMovedEvent(dest, src);
            }

            // Removing any lock-null resource which would be present at
            // the destination path
            lockNullResources.remove(destinationPath);

            return exists ? WebdavStatus.SC_OK : WebdavStatus.SC_CREATED;
        }
    }

    private void fireFileMovedEvent(WebdavResource dest, WebdavResource src)
    {
        long start = System.currentTimeMillis();
        src.notify(getViewContext(), null == dest.getFile() ? "deleted" : "deleted: moved to " + dest.getFile().getPath());
        dest.notify(getViewContext(), null == src.getFile() ? "created" : "created: moved from " + src.getFile().getPath());

        Container srcContainer = src.getContainerId() == null ? null : ContainerManager.getForId(src.getContainerId());

        if (src.getFile() != null && dest.getFile() != null)
        {
            FileContentService.get().fireFileMoveEvent(src.getFile(), dest.getFile(), getUser(), srcContainer);
        }

        removeFromIndex(src);
        addToIndex(dest);
        _log.debug("fireFileMovedEvent: " + DateUtil.formatDuration(System.currentTimeMillis() - start));
    }


    boolean isSafeCopy(WebdavResource src, WebdavResource dest)
    {
        // Don't allow creating text/html via rename (circumventing script checking)
        if (src.isFile() && !getUser().isDeveloper())
        {
            String contentTypeSrc = StringUtils.defaultString(src.getContentType(),"");
            String contentTypeDest = StringUtils.defaultString(dest.getContentType(),"");
            if (contentTypeDest.startsWith("text/html") && !contentTypeDest.equals(contentTypeSrc))
                return false;
        }
        return true;
    }


    // should probably be scoped to resolver?
    // each collection is syncrhonized which is good for isLocked()
    // however, modifying locks should use the lockingLock
    private static final Object lockingLock = new Object();
    private static final Map<Path,List<Path>> lockNullResources = Collections.synchronizedMap(new HashMap<Path,List<Path>>());
    private static final Map<Path,LockInfo> resourceLocks = Collections.synchronizedMap(new HashMap<Path,LockInfo>());
    private static final List<LockInfo> collectionLocks = Collections.synchronizedList(new ArrayList<LockInfo>());
    private static final AtomicInteger lockCounter = new AtomicInteger(); // helps with debugging
    private static final int maxDepth = 3;


    @RequiresNoPermission
    public class LockAction extends DavAction
    {
        public LockAction()
        {
            super("LOCK");
        }

        @Override
        WebdavStatus doMethod() throws DavException, IOException, RedirectException
        {
            if (isStaticContent(getResourcePath()))
            {
                return getResponse().sendError(WebdavStatus.SC_FORBIDDEN);
            }

            if (LockResult.LOCKED == isLocked())
            {
                return getResponse().sendError(WebdavStatus.SC_LOCKED);
            }

            LockInfo lock = new LockInfo();

            // Parsing lock request

            // Parsing depth header

            String depthStr = getRequest().getHeader("Depth");

            if (depthStr == null)
            {
                lock.depth = maxDepth;
            }
            else
            {
                if (depthStr.equals("0"))
                {
                    lock.depth = 0;
                }
                else
                {
                    lock.depth = maxDepth;
                }
            }

            // Parsing timeout header

            int lockDuration = DEFAULT_TIMEOUT;
            String lockDurationStr = getRequest().getHeader("Timeout");
            if (lockDurationStr == null)
            {
                lockDuration = DEFAULT_TIMEOUT;
            }
            else
            {
                int commaPos = lockDurationStr.indexOf(",");
                // If multiple timeouts, just use the first
                if (commaPos != -1)
                {
                    lockDurationStr = lockDurationStr.substring(0, commaPos);
                }
                if (lockDurationStr.startsWith("Second-"))
                {
                    lockDuration = (new Integer(lockDurationStr.substring(7))).intValue();
                }
                else
                {
                    if (lockDurationStr.equalsIgnoreCase("infinity"))
                    {
                        lockDuration = MAX_TIMEOUT;
                    }
                    else
                    {
                        try
                        {
                            lockDuration = (new Integer(lockDurationStr)).intValue();
                        }
                        catch (NumberFormatException e)
                        {
                            lockDuration = MAX_TIMEOUT;
                        }
                    }
                }
                if (lockDuration == 0)
                {
                    lockDuration = DEFAULT_TIMEOUT;
                }
                if (lockDuration > MAX_TIMEOUT)
                {
                    lockDuration = MAX_TIMEOUT;
                }
            }
            lock.expiresAt = System.currentTimeMillis() + (lockDuration * 1000);

            int lockRequestType = LOCK_CREATION;

            Node lockInfoNode = null;

            DocumentBuilder documentBuilder = getDocumentBuilder();

            try
            {
                Document document = documentBuilder.parse(new InputSource(getRequest().getInputStream()));

                // Get the root element of the document
                Element rootElement = document.getDocumentElement();
                lockInfoNode = rootElement;
            }
            catch (IOException e)
            {
                lockRequestType = LOCK_REFRESH;
            }
            catch (SAXException e)
            {
                lockRequestType = LOCK_REFRESH;
            }

            if (lockInfoNode != null)
            {
                // Reading lock information

                NodeList childList = lockInfoNode.getChildNodes();

                Node lockScopeNode = null;
                Node lockTypeNode = null;
                Node lockOwnerNode = null;

                for (int i = 0; i < childList.getLength(); i++)
                {
                    Node currentNode = childList.item(i);
                    switch (currentNode.getNodeType())
                    {
                        case Node.TEXT_NODE:
                            break;
                        case Node.ELEMENT_NODE:
                            String nodeName = currentNode.getNodeName();
                            if (nodeName.endsWith("lockscope"))
                            {
                                lockScopeNode = currentNode;
                            }
                            if (nodeName.endsWith("locktype"))
                            {
                                lockTypeNode = currentNode;
                            }
                            if (nodeName.endsWith("owner"))
                            {
                                lockOwnerNode = currentNode;
                            }
                            break;
                    }
                }

                if (lockScopeNode != null)
                {

                    childList = lockScopeNode.getChildNodes();
                    for (int i = 0; i < childList.getLength(); i++)
                    {
                        Node currentNode = childList.item(i);
                        switch (currentNode.getNodeType())
                        {
                            case Node.TEXT_NODE:
                                break;
                            case Node.ELEMENT_NODE:
                                String tempScope = currentNode.getNodeName();
                                if (tempScope.indexOf(':') != -1)
                                {
                                    lock.scope = tempScope.substring
                                            (tempScope.indexOf(':') + 1);
                                }
                                else
                                {
                                    lock.scope = tempScope;
                                }
                                break;
                        }
                    }

                    if (lock.scope == null)
                    {
                        // Bad request
                        getResponse().setStatus(WebdavStatus.SC_BAD_REQUEST);
                    }
                }
                else
                {
                    // Bad request
                    getResponse().setStatus(WebdavStatus.SC_BAD_REQUEST);
                }

                if (lockTypeNode != null)
                {

                    childList = lockTypeNode.getChildNodes();
                    for (int i = 0; i < childList.getLength(); i++)
                    {
                        Node currentNode = childList.item(i);
                        switch (currentNode.getNodeType())
                        {
                            case Node.TEXT_NODE:
                                break;
                            case Node.ELEMENT_NODE:
                                String tempType = currentNode.getNodeName();
                                if (tempType.indexOf(':') != -1)
                                {
                                    lock.type = tempType.substring(tempType.indexOf(':') + 1);
                                }
                                else
                                {
                                    lock.type = tempType;
                                }
                                break;
                        }
                    }

                    if (lock.type == null)
                    {
                        // Bad request
                        getResponse().setStatus(WebdavStatus.SC_BAD_REQUEST);
                    }

                }
                else
                {
                    // Bad request
                    getResponse().setStatus(WebdavStatus.SC_BAD_REQUEST);
                }

                if (lockOwnerNode != null)
                {
                    childList = lockOwnerNode.getChildNodes();
                    for (int i = 0; i < childList.getLength(); i++)
                    {
                        Node currentNode = childList.item(i);
                        switch (currentNode.getNodeType())
                        {
                            case Node.TEXT_NODE:
                                lock.owner += currentNode.getNodeValue();
                                break;
                            case Node.ELEMENT_NODE:
                            {
                                XMLWriter domWriter = new XMLWriter();
//                                domWriter.setQualifiedNames(false);
                                domWriter.print(currentNode);
                                domWriter.sendData();
                                lock.owner += domWriter.toString();
                                break;
                            }
                        }
                    }

                    if (lock.owner == null)
                    {
                        // Bad request
                        getResponse().setStatus(WebdavStatus.SC_BAD_REQUEST);
                    }
                }
                else
                {
                    lock.owner = "";
                }
            }

            Path path = getResourcePath();
            lock.path = path;

            WebdavResource resource = resolvePath();

            if (lockRequestType == LOCK_CREATION)
            {
                // Generating lock id, lockCounter is just for human debugging
                // the { } are just for quick validation of lock-token header
                String lockToken = "{" + lockCounter.incrementAndGet() + "_" + GUID.makeHash(path.toString()) + "}";

                if (resource.isCollection() && (lock.depth == maxDepth))
                {
                    // Locking a collection (and all its member resources)

                    // Checking if a child resource of this collection is
                    // already locked
                    ArrayList<Path> lockPaths = new ArrayList<>();
                    for (LockInfo currentLock : collectionLocks.toArray(new LockInfo[0]))
                    {
                        if (currentLock.hasExpired())
                        {
                            removeLock(currentLock.path);
                            continue;
                        }
                        if ((currentLock.path.startsWith(lock.path)) &&
                                ((currentLock.isExclusive()) ||
                                        (lock.isExclusive())))
                        {
                            // A child collection of this collection is locked
                            lockPaths.add(currentLock.path);
                        }
                    }
                    for (LockInfo currentLock : resourceLocks.entrySet().toArray(new LockInfo[0]))
                    {
                        if (currentLock.hasExpired())
                        {
                            removeLock(currentLock.path);
                            continue;
                        }
                        if ((currentLock.path.startsWith(lock.path)) &&
                                ((currentLock.isExclusive()) ||
                                        (lock.isExclusive())))
                        {
                            // A child resource of this collection is locked
                            lockPaths.add(currentLock.path);
                        }
                    }

                    if (!lockPaths.isEmpty())
                    {
                        // One of the child paths was locked
                        // We generate a multistatus error report

                        getResponse().setStatus(WebdavStatus.SC_CONFLICT);

                        XMLWriter generatedXML = new XMLWriter();
                        generatedXML.writeXMLHeader();

                        generatedXML.writeElement("D", DEFAULT_NAMESPACE,
                                "multistatus", XMLWriter.OPENING);

                        for (Path lockPath : lockPaths)
                        {
                            generatedXML.writeElement("D", "response", XMLWriter.OPENING);
                            generatedXML.writeElement("D", "href", XMLWriter.OPENING);
                            generatedXML.writeText(getRequest().getContextPath() + lockPath.encode());
                            generatedXML.writeElement("D", "href", XMLWriter.CLOSING);
                            generatedXML.writeElement("D", "status", XMLWriter.OPENING);
                            generatedXML.writeText("HTTP/1.1 " + WebdavStatus.SC_LOCKED + " " + WebdavStatus.SC_LOCKED.message);
                            generatedXML.writeElement("D", "status", XMLWriter.CLOSING);
                            generatedXML.writeElement("D", "response", XMLWriter.CLOSING);
                        }

                        generatedXML.writeElement("D", "multistatus", XMLWriter.CLOSING);

                        Writer writer = getResponse().getWriter();
                        writer.write(generatedXML.toString());
                        close(writer, "lock writer");

                        return WebdavStatus.SC_CONFLICT;
                    }

                    boolean addLock = true;


                    synchronized (lockingLock)
                    {
                        // Checking if there is already a shared lock on this path
                        for (LockInfo currentLock : collectionLocks.toArray(new LockInfo[0]))
                        {
                            if (currentLock.path.equals(lock.path))
                            {
                                if (currentLock.isExclusive())
                                {
                                    return getResponse().sendError(WebdavStatus.SC_LOCKED);
                                }
                                else
                                {
                                    if (lock.isExclusive())
                                    {
                                        return getResponse().sendError(WebdavStatus.SC_LOCKED);
                                    }
                                }

                                currentLock.tokens.add(lockToken);
                                lock = currentLock;
                                addLock = false;
                            }
                        }

                        if (addLock)
                        {
                            lock.tokens.add(lockToken);
                            collectionLocks.add(lock);
                        }
                    } // synchronized
                }
                else
                {
                    // Locking a single resource

                    synchronized (lockingLock)
                    {
                        // Retrieving an already existing lock on that resource
                        LockInfo presentLock = resourceLocks.get(lock.path);
                        if (presentLock != null)
                        {

                            if ((presentLock.isExclusive()) || (lock.isExclusive()))
                            {
                                // If either lock is exclusive, the lock can't be
                                // granted
                                return getResponse().sendError(WebdavStatus.SC_PRECONDITION_FAILED);
                            }
                            else
                            {
                                presentLock.tokens.add(lockToken);
                                lock = presentLock;
                            }
                        }
                        else
                        {

                            lock.tokens.add(lockToken);
                            resourceLocks.put(lock.path, lock);
                            resource = resolvePath();

                            if (null == resource || !resource.exists())
                            {

                                // "Creating" a lock-null resource
                                Path parentPath = lock.path.getParent();

                                List<Path> lockNulls = lockNullResources.get(parentPath);
                                if (lockNulls == null)
                                {
                                    lockNulls = Collections.synchronizedList(new ArrayList<Path>());
                                    lockNullResources.put(parentPath, lockNulls);
                                }

                                lockNulls.add(lock.path);

                            }
                            // Add the Lock-Token header as by RFC 2518 8.10.1
                            // - only do this for newly created locks
                            getResponse().addLockToken(lockToken);
                        }
                    } // synchronized
                }
            }

            if (lockRequestType == LOCK_REFRESH)
            {
                String ifHeader = getRequest().getHeader("If");
                if (ifHeader == null)
                    ifHeader = "";

                synchronized (lockingLock)
                {
                    // Checking resource locks
                    LockInfo toRenew = resourceLocks.get(path);

                    if (toRenew != null)
                    {
                        // At least one of the tokens of the locks must have been given
                        for (String token : toRenew.tokens.toArray(new String[0]))
                        {
                            if (ifHeader.indexOf(token) != -1)
                            {
                                toRenew.expiresAt = lock.expiresAt;
                                lock = toRenew;
                            }
                        }
                    }

                    // Checking inheritable collection locks

                    for (LockInfo toRenewColl : collectionLocks.toArray(new LockInfo[0]))
                    {
                        if (path.equals(toRenewColl.path))
                        {
                            for (String token : toRenewColl.tokens.toArray(new String[0]))
                            {
                                if (ifHeader.indexOf(token) != -1)
                                {
                                    toRenewColl.expiresAt = lock.expiresAt;
                                    lock = toRenewColl;
                                }
                            }
                        }
                    }
                }
            }

            // Set the status, then generate the XML response containing
            // the lock information
            XMLWriter generatedXML = new XMLWriter();
            generatedXML.writeXMLHeader();
            generatedXML.writeElement("D", DEFAULT_NAMESPACE, "prop",
                    XMLWriter.OPENING);

            generatedXML.writeElement("D", "lockdiscovery", XMLWriter.OPENING);

            lock.toXML(generatedXML);

            generatedXML.writeElement("D", "lockdiscovery", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "prop", XMLWriter.CLOSING);

            getResponse().setStatus(WebdavStatus.SC_OK);
            getResponse().setContentType("text/xml; charset=UTF-8");
            Writer writer = getResponse().getWriter();
            writer.write(generatedXML.toString());
            close(writer, "lock writer");

            if (_log.isDebugEnabled())
                _log.debug("lock: " + String.valueOf(lock));

            if (null != resource && resource.exists())
                return WebdavStatus.SC_OK;
            else
                return WebdavStatus.SC_CREATED;
        }
    }


    @RequiresNoPermission
    public class UnlockAction extends DavAction
    {
        public UnlockAction()
        {
            super("UNLOCK");
        }

        @Override
        WebdavStatus doMethod() throws IOException, DavException
        {
            if (isStaticContent(getResourcePath()))
            {
                return getResponse().sendError(WebdavStatus.SC_FORBIDDEN);
            }

            // litmus test hack..., consider actually parsing and checking validity
            if (LockResult.LOCKED == isLocked())
            {
                return getResponse().sendError(WebdavStatus.SC_LOCKED);
            }

            Path path = getResourcePath();

            String lockTokenHeader = getRequest().getHeader("Lock-Token");
            if (lockTokenHeader == null)
                lockTokenHeader = "";

            _log.debug("lock token: " + lockTokenHeader);

            // Checking resource locks

            synchronized (lockingLock)
            {
                LockInfo lock = resourceLocks.get(path);
                if (lock != null)
                {
                    // At least one of the tokens of the locks must have been given
                    for (String token : lock.tokens.toArray(new String[0]))
                    {
                        if (lockTokenHeader.indexOf(token) != -1)
                        {
                            lock.tokens.remove(token);
                        }
                    }

                    if (lock.tokens.isEmpty())
                    {
                        resourceLocks.remove(path);
                        // Removing any lock-null resource which would be present
                        lockNullResources.remove(path);
                    }

                }

                // Checking inheritable collection locks

                for (LockInfo lockColl : collectionLocks.toArray(new LockInfo[0]))
                {
                    if (path.equals(lockColl.path))
                    {
                        for (String token : lockColl.tokens.toArray(new String[0]))
                        {
                            if (lockTokenHeader.indexOf(token) != -1)
                            {
                                lockColl.tokens.remove(token);
                                break;
                            }
                        }

                        if (lockColl.tokens.isEmpty())
                        {
                            collectionLocks.remove(lock);
                            // Removing any lock-null resource which would be present
                            lockNullResources.remove(path);
                        }

                    }
                }
            }// syncronized

            return getResponse().setStatus(WebdavStatus.SC_NO_CONTENT);
        }
    }


    enum LockResult
    {
        NOT_LOCKED,
        TOKEN_NOT_FOUND,
        HAS_LOCK,
        LOCKED
    }


    /**
     * Check to see if a resource is currently write locked. The method
     * will look at the "If" header to make sure the client
     * has give the appropriate lock tokens.
     *
     * @return boolean true if the resource is locked (and no appropriate
     * lock token has been found for at least one of the non-shared locks which
     * are present on the resource).
     */
    private LockResult isLocked() throws DavException
    {
        String ifHeader = getRequest().getHeader("If");
        if (ifHeader == null)
            ifHeader = "";

        boolean lockExpected = false;
        String lockTokenHeader = getRequest().getHeader("Lock-Token");
        if (lockTokenHeader == null)
            lockTokenHeader = "";
        else if (lockTokenHeader.contains("<opaquelocktoken:"))
        {
            lockExpected = true;
            // verify format looks reasonable
            if (!lockTokenHeader.contains("{") && !lockTokenHeader.contains("}"))
                throw new DavException(WebdavStatus.SC_BAD_REQUEST);
        }

        LockResult result = isLocked(getResourcePath(), ifHeader + lockTokenHeader);
        if (lockExpected && result==LockResult.NOT_LOCKED)
            throw new DavException(WebdavStatus.SC_BAD_REQUEST);
        return result;
    }


    /**
     * Check to see if a resource is currently write locked.
     *
     * @param path Path of the resource
     * @param ifHeader "If" HTTP header which was included in the request
     * @return boolean true if the resource is locked (and no appropriate
     * lock token has been found for at least one of the non-shared locks which
     * are present on the resource).
     */
    private LockResult isLocked(Path path, String ifHeader)
    {
        LockResult locked = _isLocked(path,ifHeader);
        if (_log.isDebugEnabled() && (locked!=LockResult.NOT_LOCKED || !StringUtils.isEmpty(ifHeader)))
        {
            _log.debug(locked.name() + " " + path.toString() + " If:" + ifHeader);
        }
        return locked;
    }


    private LockResult _isLocked(Path path, String ifHeader)
    {
        LockResult ret = LockResult.NOT_LOCKED;

        // TODO real "If" parsing, this is mostly to pass litmus
        if (ifHeader.contains("(Not <DAV:no-lock>)"))
            ifHeader = ifHeader.replace("(Not <DAV:no-lock>)","");
        if (ifHeader.contains("Not <DAV:no-lock>"))
            return LockResult.NOT_LOCKED;   //e.g. statement is always true
        else if (ifHeader.contains("<DAV:no-lock>"))
            return LockResult.TOKEN_NOT_FOUND;  //e.g. statement is always false

        // Checking resource locks
        LockInfo lock = resourceLocks.get(path);
        if ((lock != null) && (lock.hasExpired()))
        {
            removeLock(path);
        }
        else if (lock != null)
        {
            // At least one of the tokens of the locks must have been given
            boolean tokenMatch = false;
            for (String token : lock.tokens)
            {
                if (ifHeader.indexOf(token) != -1)
                {
                    tokenMatch = true;
                    ret = LockResult.HAS_LOCK;
                }
            }
            if (!tokenMatch)
                return LockResult.LOCKED;
        }

        // Checking inheritable collection locks
        for (LockInfo lockColl : collectionLocks.toArray(new LockInfo[0]))
        {
            if (lockColl.hasExpired())
            {
                collectionLocks.remove(lockColl);
            }
            else if (path.startsWith(lockColl.path))
            {
                boolean tokenMatch = false;
                for (String token : lockColl.tokens)
                {
                    if (ifHeader.indexOf(token) != -1)
                    {
                        tokenMatch = true;
                        ret = LockResult.HAS_LOCK;
                    }
                }
                if (!tokenMatch)
                    return LockResult.LOCKED;
            }
        }

        // TODO actually parse the headers instead of using ifHeader.contains(token)
        boolean tokenSpecified = StringUtils.contains(ifHeader,"<opaquelocktoken:") && StringUtils.contains(ifHeader,"{") && StringUtils.contains(ifHeader,"}");
        if (tokenSpecified && ret == LockResult.NOT_LOCKED)
            return LockResult.TOKEN_NOT_FOUND;
        return ret;
    }


    private void removeLock(Path path)
    {
        synchronized (lockingLock)
        {
            LockInfo lock = resourceLocks.get(path);
            if ((lock != null) && (lock.hasExpired()))
                resourceLocks.remove(path);
        }
    }


    private void checkRequireLogin(WebdavResource r) throws DavException
    {
        // by default require login for OPTIONS and PROPFIND.
        // this helps many clients that won't prompt for credentials
        // if the initial OPTIONS request works.
        // AllowNoLogin header returns to normal behavior (simple permission check)
        boolean isBasicAuthentication = SecurityManager.isBasicAuthentication(getRequest());
        boolean isGuest = getUser().isGuest();

        // if user is authenticated or is trying to authenticate we're OK
        if (!isGuest || isBasicAuthentication)
            return;
        // force non browsers to log-in, except windowsexplorer over http (avoid cryptic "appears to be invalid" message)
        //if (isWindowsExplorer() && "http".equals(getRequest().getScheme()))
        //    return;
        if (!isBrowser())
            throw new UnauthorizedException(r);
    }
    
    
    @RequiresNoPermission
    public class OptionsAction extends DavAction
    {
        public OptionsAction()
        {
            super("OPTIONS");
        }

        public WebdavStatus doMethod() throws DavException
        {
            checkRequireLogin(null);
            WebdavResponse response = getResponse();
            response.addOptionsHeaders();
            StringBuilder methodsAllowed = determineMethodsAllowed();
            response.setMethodsAllowed(methodsAllowed);
            return WebdavStatus.SC_OK;
        }
    }


    static Cache<Path,JSONObject> exceptionCache = CacheManager.getCache(1000, 5*CacheManager.MINUTE, "webdav errors");

    private Path getErrorCacheKey()
    {
        Path path = getResourcePath();
        HttpServletRequest request = getRequest();
        HttpSession session =  request.getSession(true);
        if (null == session)
            return null;
        String sessionId = session.getId();
        String pageId = StringUtils.defaultIfBlank(request.getParameter("pageId"), "-");
        Path p = new Path(sessionId).append(String.valueOf(pageId));
        if (null != path)
            p = p.append(path);
        return p;
    }


    private void setLastError(@NotNull DavException error)
    {
        if (WebdavStatus.SC_NOT_FOUND == error.status)
            return;
        Path key = getErrorCacheKey();
        if (null != key)
            exceptionCache.put(key, error.toJSON());
    }


    private void clearLastError()
    {
        Path key = getErrorCacheKey();
        if (null != key)
            exceptionCache.remove(key);
    }

    /*
     * JSON clients can use this API for more robust/easier error description than might be available over web-dav
     */
    @RequiresNoPermission
    public class LastErrorAction extends ApiAction
    {
        @Override
        public Object execute(Object o, BindException bindErrors) throws Exception
        {
            Path key = getErrorCacheKey();

            JSONObject x = exceptionCache.get(key);
            if (null != x)
                exceptionCache.remove(key);

            JSONObject ret = new JSONObject();
            ret.put("success",true);
            JSONArray errors = new JSONArray();
            ret.put("errors", errors);
            if (null != x)
            {
                errors.put(x);
            }
            return ret;
        }
    }

    /** allow html listing of this resource */
    private boolean allowHtmlListing(Path path) throws DavException
    {
        // ask the resolver to answer this
        WebdavResolver.LookupResult result = resolvePathResult(path,false);
        if (null != result && null != result.resolver)
            return result.resolver.allowHtmlListing();
        return false;
    }


    /** if !allowHtmlListing() is there a default page? */
    private WebdavResource welcomePage(Path path) throws DavException
    {
        // ask the resolver to answer this
        WebdavResolver.LookupResult result = resolvePathResult(path, false);
        if (null != result && null != result.resolver)
        {
            String name = result.resolver.defaultWelcomePage();
            if (null == name)
                return null;
            Path welcomePath = path.append(name);
            return resolvePath(welcomePath);
        }
        return null;
    }


    private WebdavStatus serveCollection(WebdavResource resource, boolean content)
            throws DavException
    {
        String contentType = resource.getContentType();

        // Skip directory listings if we have been configured to suppress them
        if (!allowHtmlListing(resource.getPath()))
            return notFound();

        // Set the appropriate output headers
        if (contentType != null)
            getResponse().setContentType(contentType);

        // Serve the directory browser
        if (content)
            return listHtml(resource);

        return WebdavStatus.SC_OK;
    }



    static final boolean supportStaticGzFiles = true;

    boolean isStaticContent(Path path)
    {
        final Boolean[] isStatic = {true};
        WebdavService.get().getEnabledRootResolvers().forEach(webdavResolver -> {
            if (!webdavResolver.isStaticContent() && path.startsWith(webdavResolver.getRootPath()))
                isStatic[0] = false;
        });
        return isStatic[0];
    }


    private static final Map<Path, Boolean> hasZipMap = new ConcurrentHashMap<>();

/*
    InputStream getGzipStream(WebdavResource r) throws DavException, IOException
    {
        // kinda hacky, but good enough for now
        assert isStaticContent(r.getPath());
        if (!supportStaticGzFiles || !isStaticContent(r.getPath()))
            return null;
        Boolean hasZip = hasZipMap.get(r.getPath());
        if (Boolean.FALSE == hasZip)
            return null;

        File file = r.getFile();
        if (null == file)
            return null;
        File fileGz = new File(file.getPath() + ".gz");

        try
        {
            if (Boolean.TRUE == hasZip)
                return new FileInputStream(fileGz);

            if (file.exists() && fileGz.exists() && file.lastModified() <= fileGz.lastModified())
            {
                InputStream ret = new FileInputStream(fileGz);
                hasZipMap.put(r.getPath(), Boolean.TRUE);
                return ret;
            }
        }
        catch (FileNotFoundException x)
        {
        }
        hasZipMap.put(r.getPath(), Boolean.FALSE);
        return null;
    }
*/

    WebdavResource getGzipResource(WebdavResource r) throws DavException, IOException
    {
        // kinda hacky, but good enough for now
        assert isStaticContent(r.getPath());
        if (!supportStaticGzFiles || !isStaticContent(r.getPath()))
            return null;
        Boolean hasZip = hasZipMap.get(r.getPath());
        if (Boolean.FALSE == hasZip)
            return null;

        // hasZip could be null or TRUE, either way look for .gz
        // NOTE if hasZip ever becomes false, it stays that way. which is only weird for extrawebapp
        WebdavResource gz = (WebdavResource)r.parent().find(r.getName() + ".gz");
        if (null != gz && gz.exists())
        {
            File rFile = r.getFile();
            File gzFile = gz.getFile();
            if (null != rFile && null != gzFile)
            {
                String rParent = rFile.getParent();
                String gzParent = gzFile.getParent();
                if (null != rParent && null != gzParent && rParent.equals(gzParent))
                {
                    if (null == hasZip)
                        hasZipMap.put(r.getPath(), Boolean.TRUE);
                    return gz;
                }
            }
        }

        hasZipMap.put(r.getPath(), Boolean.FALSE);
        return null;
    }


    Path extPath = new Path(PageFlowUtil.extJsRoot());
    Path mcePath = new Path("timymce");
    
    boolean alwaysCacheFile(Path p)
    {
        return p.startsWith(extPath) || p.startsWith(mcePath);
    }
    

    private WebdavStatus serveResource(WebdavResource resource, boolean content)
            throws DavException, IOException
    {
        boolean isStatic = isStaticContent(resource.getPath());

        if (!isStatic && !ModuleLoader.getInstance().isStartupComplete())
            throw new DavException(WebdavStatus.SC_SERVICE_UNAVAILABLE, "Server has not completed startup.");

        // If the resource is not a collection, and the resource path ends with "/"
        if (resource.getPath().isDirectory())
            return notFound(resource.getPath());

        // Parse range specifier
        List ranges = parseRange(resource);

        // ETag header
        // NOTE it is better to use an older etag and newer content, than vice-versa
        getResponse().setEntityTag(resource.getETag(true));

        // Last-Modified header
        long modified = resource.getLastModified();
        if (modified != Long.MIN_VALUE)
            getResponse().setLastModified(modified);

        if (isStatic)
        {
            assert resource.canRead(User.guest,true);

            if (!resource.getName().contains(".nocache."))
            {
                boolean isPerfectCache = resource.getName().contains(".cache.");
                boolean allowCaching = AppProps.getInstance().isCachingAllowed();

                if (allowCaching || isPerfectCache || alwaysCacheFile(resource.getPath()))
                {
                    int expireInDays = isPerfectCache ? 365 : 35;
                    getResponse().setExpires(HeartBeat.currentTimeMillis() + TimeUnit.DAYS.toMillis(expireInDays), "public");
                }
            }
        }
        else
        {
            if (!resource.canRead(UserManager.getGuestUser(),false))
                getResponse().setCacheForUserOnly();
        }

        // Check if the conditions specified in the optional If headers are satisfied.
        if (!checkIfHeaders(resource))
            return null;

        String contentDisposition = getRequest().getParameter("contentDisposition");
        if (!StringUtils.equals("attachment",contentDisposition) && !StringUtils.equals("inline",contentDisposition))
            contentDisposition = null;

        if (!StringUtils.isEmpty(contentDisposition))
        {
            getResponse().setContentDisposition(contentDisposition);
            try
            {
                // https://bugs.chromium.org/p/chromium/issues/detail?id=1503
                if (isChrome())
                {
                    Path requestPath = new URLHelper(getRequest().getRequestURI()).getParsedPath();
                    getResponse().setContentDisposition(contentDisposition + "; filename=" + requestPath.getName());
                }
            }
            catch (URISyntaxException x)
            {
               // pass
            }
        }

        // Find content type
        String contentType = resource.getContentType();

        // Get content length
        long contentLength = resource.getContentLength();

        // Special case for zero length files, which would cause a
        // (silent) IllegalStateException when setting the output buffer size
        if (contentLength == 0L)
            content = false;

        boolean fullContent = (((ranges == null) || (ranges.isEmpty())) && (getRequest().getHeader("Range") == null)) || (ranges == FULL);
        OutputStream ostream = null;
        Writer writer = null;

        if (fullContent)
        {
            // Set the appropriate output headers
            if (contentType != null)
            {
                getResponse().setContentType(contentType);
            }

            WebdavResource gz = null;

            // if static content look for gzip version
            if (isStatic && !AppProps.getInstance().isDevMode())
            {
                String accept = getRequest().getHeader("accept-encoding");
                if (null != accept && accept.contains("gzip"))
                {
                    gz = getGzipResource(resource);
                    if (null != gz)
                        getResponse().setContentEncoding("gzip");
                }
            }

            if (content)
            {
                if (supportsGetRedirect())
                {
                    // Use redirect to the alternate get location
                    DirectRequest req = resource.getDirectGetRequest(getViewContext(), contentDisposition);
                    if (req != null)
                    {
                        String uri = req.getEndpoint().toASCIIString();
                        getResponse().setStatus(WebdavStatus.SC_MOVED_TEMPORARILY);
                        getResponse().setLocation(uri);
                        return null;
                    }
                }

                File file = null==gz ? resource.getFile() : gz.getFile();
                if (file != null && WebdavService.get().getPreGzippedExtensions().contains(FileUtil.getExtension(file)))
                {
                    String accept = getRequest().getHeader("accept-encoding");
                    if (null != accept && accept.contains("gzip"))
                    {
                        getResponse().setContentEncoding("gzip");
                    }
                }

                HttpServletRequest request = getRequest();
                if (null != file && Boolean.TRUE == request.getAttribute("org.apache.tomcat.sendfile.support"))
                {
                    request.setAttribute("org.apache.tomcat.sendfile.filename", file.getAbsolutePath());
                    request.setAttribute("org.apache.tomcat.sendfile.start", new Long(0L));
                    request.setAttribute("org.apache.tomcat.sendfile.end", new Long(file.length()));
                    request.setAttribute("org.apache.tomcat.sendfile.token", this);
                    getResponse().setContentLength(file.length());
                    _log.debug("sendfile: " + file.getAbsolutePath());
                }
                else
                {
                    try
                    {
                        ostream = getResponse().getOutputStream();
                    }
                    catch (IllegalStateException e)
                    {
                        // If it fails, we try to get a Writer instead if we're trying to serve a text file
                        if (fullContent && ((contentType == null) || (contentType.startsWith("text")) || (contentType.endsWith("xml"))))
                        {
                            writer = getResponse().getWriter();
                        }
                        else
                        {
                            throw e;
                        }
                    }

                    InputStream is = getResourceInputStream(gz==null?resource:gz,getUser());
                    if (ostream != null)
                        copy(is, ostream);
                    else if (writer != null)
                        copy(is, writer);
                }
            }
        }
        else
        {
            if ((ranges == null) || (ranges.isEmpty()))
                return null;

            // Partial content response.
            getResponse().setStatus(WebdavStatus.SC_PARTIAL_CONTENT);

            ostream = getResponse().getOutputStream();

            if (ranges.size() == 1)
            {

                Range range = (Range) ranges.get(0);
                getResponse().addContentRange(range);
                long length = range.end - range.start + 1;
                getResponse().setContentLength(length);

                if (contentType != null)
                    getResponse().setContentType(contentType);

                // UNDONE: allow resource subclass to handle ranges.  See org.jclouds.blobstore.options.GetOptions
                if (content)
                    copy(getResourceInputStream(resource, getUser()), ostream, range.start, range.end);
            }
            else
            {
                // UNDONE: allow resource subclass to handle ranges.  See org.jclouds.blobstore.options.GetOptions
                getResponse().setContentType("multipart/byteranges; boundary=" + mimeSeparation);
                if (content)
                    copy(resource, ostream, ranges.iterator(), contentType);
            }
        }
        return WebdavStatus.SC_OK;
    }


    protected void copy(InputStream istream, OutputStream ostream) throws IOException
    {
        try
        {
            // Copy the input stream to the output stream
            byte buffer[] = new byte[16*1024];
            int len;
            while (-1 < (len = istream.read(buffer)))
                ostream.write(buffer,0,len);
        }
        finally
        {
            close(istream, "copy InputStream");
        }
    }


    protected void copy(InputStream istream, OutputStream ostream, long start, long end) throws IOException
    {
        try
        {
            long skip = istream.skip(start);
            if (skip < start)
                throw new IOException();
            long remaining = end - start + 1;
            byte buffer[] = new byte[16*1024];
            int len;
            while (remaining > 0 && -1 < (len = istream.read(buffer)))
            {
                long copy = Math.min(len,remaining);
                ostream.write(buffer,0,(int)copy);
                remaining -= copy;
            }
        }
        finally
        {
            close(istream, "copy InputStream");
        }
    }


    protected void copy(WebdavResource resource, OutputStream ostream, Iterator ranges, String contentType) throws IOException, DavException
    {
        while (ranges.hasNext())
        {
            InputStream resourceInputStream = getResourceInputStream(resource,getUser());
            assert track(resourceInputStream);
            InputStream istream = new BufferedInputStream(resourceInputStream, 16*1024);
            assert track(istream);

            Range currentRange = (Range) ranges.next();

            // Writing MIME header.
            println(ostream);
            println(ostream, "--" + mimeSeparation);
            if (contentType != null)
                println(ostream, "Content-Type: " + contentType);
            println(ostream, "Content-Range: bytes " + currentRange.start + "-" + currentRange.end + "/" + currentRange.length);
            println(ostream);

            copy(istream, ostream, currentRange.start, currentRange.end);
        }
        println(ostream);
        print(ostream, "--" + mimeSeparation + "--");
    }

    void println(OutputStream ostream) throws IOException
    {
        print(ostream, "\n");
    }

    void println(OutputStream ostream, String s) throws IOException
    {
        print(ostream, s);
        print(ostream, "\n");
    }

    void print(OutputStream stream, String s) throws IOException
    {
        stream.write(s.getBytes(utf8));
    }


    protected void copy(InputStream istream, Writer writer) throws IOException
    {
        Reader reader = new InputStreamReader(istream, utf8);
        try
        {
            assert track(reader);
            char buffer[] = new char[8*1024];
            int len;
            while (-1 < (len = reader.read(buffer)))
                writer.write(buffer, 0, len);
        }
        finally
        {
            close(reader, "reader");
            close(istream, "input stream");
        }
    }


    private StringBuilder determineMethodsAllowed() throws DavException
    {
        WebdavResource resource = resolvePath();
        if (resource == null)
            return new StringBuilder();
        return determineMethodsAllowed(resource);
    }


    private StringBuilder determineMethodsAllowed(WebdavResource resource)
    {
        User user = getUser();
        StringBuilder methodsAllowed = new StringBuilder("OPTIONS");

        boolean createResource = resource.canCreate(user,false) && !PremiumService.get().isFileUploadDisabled();
        boolean createCollection = resource.canCreateCollection(user,false);

        if (!resource.exists())
        {
            if (createResource)
                methodsAllowed.append(", PUT");
            if (createCollection)
                methodsAllowed.append(", MKCOL");
            if (_locking)
                methodsAllowed.append(", LOCK");
        }
        else
        {
            boolean read = resource.canRead(user,false);
            boolean delete = resource.canDelete(user,false,null);

            if (read)
                methodsAllowed.append(", GET, HEAD, COPY");
            if (delete)
                methodsAllowed.append(", DELETE");
            if (delete && read)
                methodsAllowed.append(", MOVE");
            if (_locking)
                methodsAllowed.append(", LOCK, UNLOCK");
            methodsAllowed.append(", PROPFIND");

            if (resource.isCollection())
            {
                if (createResource)
                    methodsAllowed.append(", POST");
                // PUT and MKCOL actually apply to _children_ of this resource
                if (createResource)
                    methodsAllowed.append(", PUT");
                if (createCollection)
                    methodsAllowed.append(", MKCOL");
            }
        }
        return methodsAllowed;
    }

    private String _urlResourcePathStr = null;
    private Path _resourcePath = null;


    public void setUrlResourcePath(String path)
    {
        _urlResourcePathStr = path;
    }


    @NotNull
    String getUrlResourcePathStr()
    {
        if (_urlResourcePathStr == null)
        {
            ActionURL url = getViewContext().getActionURL();
            String path = trimToEmpty(url.getParameter("path"));
            if (path == null || path.length() == 0)
                path = "/";
            if (path.equals(""))
                path = "/";
            _urlResourcePathStr = path;
        }
        if (!_urlResourcePathStr.startsWith("/"))
            return "/" + _urlResourcePathStr;
        return _urlResourcePathStr;
    }


    Path getResourcePath()
    {
        if (null == _resourcePath)
        {
            String str = getUrlResourcePathStr();
            if (null == str)
                return Path.rootPath;
            Path p = Path.parse(str).normalize();
            Path urlDirectory = p.isDirectory() ? p : p.getParent();
            if (StringUtils.equalsIgnoreCase("GET",getViewContext().getActionURL().getAction()))
            {
            String filename = StringUtils.trimToNull(getRequest().getParameter("filename"));
            if (null != filename)
                {
                    if (!p.isDirectory())
                    {
                        String oldname = p.getName();
                        String oldExt = oldname.substring(oldname.lastIndexOf('.')+1);
                        String newExt = filename.substring(filename.lastIndexOf('.')+1);
                        if (!StringUtils.equalsIgnoreCase(oldExt,newExt))
                            filename = null;
                    }
                    if (null != filename)
                        p = urlDirectory.append(filename);
                }
            }
            _resourcePath = p;
        }
        return _resourcePath;
    }

    @Nullable String getFilenameParameter()
    {
        String filename = StringUtils.trimToNull(getRequest().getParameter("filename"));
        if (null == filename)
            return null;
        int slash = Math.max(filename.lastIndexOf("/"), filename.lastIndexOf("\\"));
        filename = filename.substring(slash+1);
        return filename;
    }

    Path getDestinationPath()
    {
        HttpServletRequest request = getRequest();
        
        String destinationPath = request.getHeader("Destination");
        if (destinationPath == null)
            return null;

        // Remove url encoding from destination
        destinationPath = PageFlowUtil.decode(destinationPath);

        int protocolIndex = destinationPath.indexOf("://");
        if (protocolIndex >= 0)
        {
            // if the Destination URL contains the protocol, we can safely
            // trim everything upto the first "/" character after "://"
            int firstSeparator = destinationPath.indexOf("/", protocolIndex + 4);
            if (firstSeparator < 0)
            {
                destinationPath = "/";
            }
            else
            {
                destinationPath = destinationPath.substring(firstSeparator);
            }
        }
        else
        {
            String hostName = request.getServerName();
            if ((hostName != null) && (destinationPath.startsWith(hostName)))
            {
                destinationPath = destinationPath.substring(hostName.length());
            }

            int portIndex = destinationPath.indexOf(":");
            if (portIndex >= 0)
            {
                destinationPath = destinationPath.substring(portIndex);
            }

            if (destinationPath.startsWith(":"))
            {
                int firstSeparator = destinationPath.indexOf("/");
                if (firstSeparator < 0)
                {
                    destinationPath = "/";
                }
                else
                {
                    destinationPath = destinationPath.substring(firstSeparator);
                }
            }
        }

        // Normalise destination path (remove '.' and '..')
        destinationPath = FileUtil.normalize(destinationPath);

        String contextPath = request.getContextPath();
        if ((contextPath != null) && (destinationPath.startsWith(contextPath)))
        {
            destinationPath = destinationPath.substring(contextPath.length());
        }

/*
        This is kinda correct, but really need to have access to the servet config parameters or something,
        for now we don't need this since we always resolve starting from "/"

        String pathInfo = request.getPathInfo();
        if (pathInfo != null)
        {
            String servletPath = request.getServletPath();
            if ((servletPath != null) && (destinationPath.startsWith(servletPath)))
            {
                destinationPath = destinationPath.substring(servletPath.length());
            }
        }
*/

        Path path = Path.parse(destinationPath).normalize();
        log("Dest path: " + String.valueOf(path));

        // some user agents incorrectly double encode!  check here
        if (destinationPath.contains("%") && StringUtils.contains(request.getHeader("User-Agent"),"cadaver"))
        {
            Resource r = null;
            try { r = resolvePath(path); } catch (DavException x) {};
            if (null == r || r instanceof WebdavResolverImpl.UnboundResource)
            {
                String decodeAgain = PageFlowUtil.decode(destinationPath);
                if (!decodeAgain.equals(destinationPath))
                {
                    Path pathDecodeAgain = Path.parse(decodeAgain).normalize();
                    try { r = resolvePath(pathDecodeAgain); } catch (DavException x) {};
                }
            }
        }

        return path;
    }
                                                             

    static Pattern nameVersionExtension = Pattern.compile("(.*)\\{.*\\}(\\.[^\\.]*)");

    @Nullable WebdavResource resolvePath() throws DavException
    {
        // NOTE: security is enforced via WebFolderInfo, however we expect the container to be a parent of the path
        Container c = getContainer();
        Path path = getResourcePath();
        if (null == path)
            return null;
        // fullPath should start with container path
        Path containerPath = c.getParsedPath();
        if (!path.startsWith(containerPath))
            return null;

        // check for version info in name e.g. stylesheet{47}.css
        if (isStaticContent(path))
        {
            // TODO 12.3  path = ResourceURL.stripVersion(path);
            Matcher m = nameVersionExtension.matcher(path.getName());
            if (m.matches())
                path = path.getParent().append(m.group(1) + m.group(2));
        }
        return resolvePath(path);
    }

    
    // per request cache
    Map<Path, WebdavResolver.LookupResult> resourceCache = new HashMap<>();
    WebdavResolver.LookupResult nullDavFileInfo = new WebdavResolver.LookupResult(null,null);

    @Nullable WebdavResource resolvePath(String path) throws DavException
    {
        return resolvePath(Path.parse(path));
    }


    @Nullable WebdavResource resolvePath(Path path) throws DavException
    {
        return resolvePath(path,false);
    }

    @Nullable WebdavResource resolvePath(Path path, boolean reload) throws DavException
    {
        WebdavResolver.LookupResult result = resolvePathResult(path, reload);
        return null==result ? null : result.resource;
    }

    @Nullable WebdavResolver.LookupResult resolvePathResult(Path path, boolean reload) throws DavException
    {
        WebdavResolver.LookupResult result = reload ? null : resourceCache.get(path);

        if (result == null)
        {
            result = getResolver().lookupEx(path);
            resourceCache.put(path, result == null || result.resource == null ? nullDavFileInfo : result);
        }

        if (null == result || nullDavFileInfo == result || null == result.resource)
            return null;

        boolean isRoot = path.size() == 0;
        if (!isRoot && path.isDirectory() && result.resource.isFile())
            return null;
        return result;
    }


    /**
     * Check if the conditions specified in the optional If headers are
     * satisfied.
     *
     * @param resource
     * @return boolean true if the resource meets all the specified conditions,
     *         and false if any of the conditions is not satisfied, in which case
     *         request processing is stopped
     */
    private boolean checkIfHeaders(WebdavResource resource)
            throws DavException
    {
        return checkIfMatch(resource)
                && checkIfModifiedSince(resource)
                && checkIfNoneMatch(resource)
                && checkIfUnmodifiedSince(resource);
    }

    /**
     * Check if the if-match condition is satisfied.
     *
     * @param resource
     * @return boolean true if the resource meets the specified condition,
     *         and false if the condition is not satisfied, in which case request
     *         processing is stopped
     */
    private boolean checkIfMatch(WebdavResource resource)
            throws DavException
    {
        String headerValue = getRequest().getHeader("If-Match");
        if (headerValue != null)
        {
            String eTag = resource.getETag();
            if (headerValue.indexOf('*') == -1)
            {
                StringTokenizer commaTokenizer = new StringTokenizer
                        (headerValue, ",");
                boolean conditionSatisfied = false;

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens())
                {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }

                // If none of the given ETags match, 412 Precondition failed is
                // sent back
                if (!conditionSatisfied)
                {
                    getResponse().sendError(WebdavStatus.SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * Check if the if-modified-since condition is satisfied.
     *
     * @return boolean true if the resource meets the specified condition,
     *         and false if the condition is not satisfied, in which case request
     *         processing is stopped
     */
    private boolean checkIfModifiedSince(WebdavResource resource)
            throws DavException
    {
        try
        {
            long headerValue = getRequest().getDateHeader("If-Modified-Since");
            if (headerValue != -1)
            {
                // If an If-None-Match header has been specified, if modified since
                // is ignored.
                if ((getRequest().getHeader("If-None-Match") == null))
                {
                    long lastModified = resource.getLastModified();
                    if (lastModified < headerValue + 1000)
                    {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    getResponse().setEntityTag(resource.getETag());
                    getResponse().setStatus(WebdavStatus.SC_NOT_MODIFIED);
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


    /**
     * Check if the if-none-match condition is satisfied.
     *
     * @return boolean true if the resource meets the specified condition,
     *         and false if the condition is not satisfied, in which case request
     *         processing is stopped
     */
    private boolean checkIfNoneMatch(WebdavResource resource) throws DavException
    {
        String headerValue = getRequest().getHeader("If-None-Match");
        if (headerValue != null)
        {
            boolean conditionSatisfied = false;

            if (!headerValue.equals("*"))
            {
                String eTag = resource.getETag();
                StringTokenizer commaTokenizer = new StringTokenizer(headerValue, ",");
                while (!conditionSatisfied && commaTokenizer.hasMoreTokens())
                {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }
            }
            else
            {
                conditionSatisfied = true;
            }

            if (conditionSatisfied)
            {
                // For GET and HEAD, we should respond with
                // 304 Not Modified.
                // For every other method, 412 Precondition Failed is sent
                // back.
                if (("GET".equals(getRequest().getMethod())) || ("HEAD".equals(getRequest().getMethod())))
                {
                    getResponse().setStatus(WebdavStatus.SC_NOT_MODIFIED);
                    getResponse().setEntityTag(resource.getETag());
                    return false;
                }
                else
                {
                    throw new DavException(WebdavStatus.SC_PRECONDITION_FAILED);
                }
            }
        }
        return true;
    }


    /**
     * Check if the if-unmodified-since condition is satisfied.
     *
     * @return boolean true if the resource meets the specified condition,
     *         and false if the condition is not satisfied, in which case request
     *         processing is stopped
     */
    private boolean checkIfUnmodifiedSince(WebdavResource resource) throws DavException
    {
        try
        {
            long headerValue = getRequest().getDateHeader("If-Unmodified-Since");
            if (headerValue != -1)
            {
                long lastModified = resource.getLastModified();
                if (lastModified >= (headerValue + 1000))   // UNDONE: why the +1000???
                    throw new DavException(WebdavStatus.SC_PRECONDITION_FAILED);
            }
        }
        catch (IllegalArgumentException illegalArgument)
        {
            return true;
        }
        return true;
    }


    boolean getOverwriteParameter(boolean defaultOverwrite)
    {
        String overwrite = getRequest().getHeader("Overwrite");
        if (null == overwrite)
            overwrite = getRequest().getParameter("overwrite");
        if (null == overwrite)
            return defaultOverwrite;
        return overwrite.equalsIgnoreCase("T");
    }


    /** this is not part of the DAV protocol, but is used by the DropApplet to indicate this file is temporary */
    boolean getTemporary()
    {
        String overwriteHeader = getRequest().getHeader("Temporary");
        return overwriteHeader != null && overwriteHeader.equalsIgnoreCase("T");
    }


    /**
     * Parse the content-range header.
     *
     * @return Range
     */
    private Range parseContentRange()
    {
        // Retrieving the content-range header (if any is specified
        String rangeHeader = getRequest().getHeader("Content-Range");

        if (rangeHeader == null)
            return null;

        // bytes is the only range unit supported
        if (!rangeHeader.startsWith("bytes"))
        {
            getResponse().sendError(WebdavStatus.SC_BAD_REQUEST);
            return null;
        }

        rangeHeader = rangeHeader.substring(6).trim();

        int dashPos = rangeHeader.indexOf('-');
        int slashPos = rangeHeader.indexOf('/');

        if (dashPos == -1)
        {
            getResponse().sendError(WebdavStatus.SC_BAD_REQUEST);
            return null;
        }

        if (slashPos == -1)
        {
            getResponse().sendError(WebdavStatus.SC_BAD_REQUEST);
            return null;
        }

        Range range = new Range();

        try
        {
            range.start = Long.parseLong(rangeHeader.substring(0, dashPos));
            range.end = Long.parseLong(rangeHeader.substring(dashPos + 1, slashPos));
            if (!"*".equals(rangeHeader.substring(slashPos + 1, rangeHeader.length())))
                range.length = Long.parseLong(rangeHeader.substring(slashPos + 1, rangeHeader.length()));
        }
        catch (NumberFormatException e)
        {
            getResponse().sendError(WebdavStatus.SC_BAD_REQUEST);
            return null;
        }

        if (!range.validate())
        {
            getResponse().sendError(WebdavStatus.SC_BAD_REQUEST);
            return null;
        }

        return range;
    }



    /**
     * Full range marker.
     */
    private static final List<Range> FULL = Collections.emptyList();

    /**
     * Parse the range header.
     *
     * @return Vector of ranges
     */
    private List<Range> parseRange(WebdavResource resource) throws DavException
    {
        // Checking If-Range
        String headerValue = getRequest().getHeader("If-Range");

        if (headerValue != null)
        {

            long headerValueTime = (-1L);
            try
            {
                headerValueTime = getRequest().getDateHeader("If-Range");
            }
            catch (Exception e)
            {
                // fall through
            }

            String eTag = resource.getETag();

            if (headerValueTime == (-1L))
            {

                // If the ETag the client gave does not match the entity
                // etag, then the entire entity is returned.
                if (!eTag.equals(headerValue.trim()))
                    return FULL;
            }
            else
            {
                // If the timestamp of the entity the client got is older than
                // the last modification date of the entity, the entire entity
                // is returned.
                long lastModified = resource.getLastModified();
                if (lastModified > (headerValueTime + 1000))
                    return FULL;
            }
        }

        long fileLength = _contentLength(resource);

        if (fileLength == 0)
            return null;

        // Retrieving the range header (if any is specified
        String rangeHeader = getRequest().getHeader("Range");

        if (rangeHeader == null)
            return null;
        // bytes is the only range unit supported (and I don't see the point
        // of adding new ones).
        if (!rangeHeader.startsWith("bytes"))
        {
            getResponse().setContentRange(fileLength);
            throw new DavException(WebdavStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
        }

        rangeHeader = rangeHeader.substring(6);

        // Vector which will contain all the ranges which are successfully
        // parsed.
        ArrayList<Range> result = new ArrayList<>();
        StringTokenizer commaTokenizer = new StringTokenizer(rangeHeader, ",");

        // Parsing the range list
        while (commaTokenizer.hasMoreTokens())
        {
            String rangeDefinition = commaTokenizer.nextToken().trim();

            Range currentRange = new Range();
            currentRange.length = fileLength;

            int dashPos = rangeDefinition.indexOf('-');

            if (dashPos == -1)
            {
                getResponse().setContentRange(fileLength);
                throw new DavException(WebdavStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            }

            if (dashPos == 0)
            {

                try
                {
                    long offset = Long.parseLong(rangeDefinition);
                    currentRange.start = fileLength + offset;
                    currentRange.end = fileLength - 1;
                }
                catch (NumberFormatException e)
                {
                    getResponse().setContentRange(fileLength);
                    throw new DavException(WebdavStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                }

            }
            else
            {

                try
                {
                    currentRange.start = Long.parseLong(rangeDefinition.substring(0, dashPos));
                    if (dashPos < rangeDefinition.length() - 1)
                        currentRange.end = Long.parseLong(rangeDefinition.substring(dashPos + 1, rangeDefinition.length()));
                    else
                        currentRange.end = fileLength - 1;
                }
                catch (NumberFormatException e)
                {
                    getResponse().setContentRange(fileLength);
                    throw new DavException(WebdavStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                }
            }

            if (!currentRange.validate())
            {
                getResponse().setContentRange(fileLength);
                throw new DavException(WebdavStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            }

            result.add(currentRange);
        }

        return result;
    }


    public class ListPage
    {
        public Path root = Path.emptyPath;
        public WebdavResource resource;
        public URLHelper loginURL;
        public Path getDirectory()
        {
            Path path = resource.getPath();
            assert path.startsWith(root);
            return root.relativize(path);
        }
    }

    public Path getResourceRootPath(WebdavResource resource)
    {
        final Path[] rootPath = new Path[1];
        WebdavService.get().getEnabledRootResolvers().forEach(webdavResolver -> {
            if (resource.getPath().startsWith(webdavResolver.getRootPath()))
                rootPath[0] = webdavResolver.getRootPath();
        });
        return rootPath[0];
    }

    WebdavStatus listHtml(WebdavResource resource)
    {
        if (!ModuleLoader.getInstance().isStartupComplete())
            throw new RedirectException(getUpgradeMaintenanceRedirect(getRequest(), null));

        try
        {
            ListPage page = new ListPage();
            page.resource = resource;
            page.loginURL = getLoginURL();
            Path resourceRootPath = getResourceRootPath(resource);
            if (resourceRootPath != null)
                page.root = resourceRootPath;

            PageConfig config = new PageConfig(resource.getPath() + "-- webdav");

            if ("html".equals(getViewContext().getRequest().getParameter("listing")))
            {
                JspView<ListPage> v = new JspView<>(DavController.class, "list.jsp", page);
                v.setFrame(WebPartView.FrameType.NONE);
                PrintTemplate print = new PrintTemplate(getViewContext(), v, config);
                print.render(getViewContext().getRequest(), getViewContext().getResponse());
            }
            else
            {
                JspView<ListPage> v = new JspView<>(DavController.class, "davListing.jsp", page);
                config.addClientDependencies(v.getClientDependencies());

                DefaultModelAndView template = new AppTemplate(getViewContext(), v, config);
                template.render(getViewContext().getRequest(), getViewContext().getResponse());
            }
            return WebdavStatus.SC_OK;
        }
        catch (Exception x)
        {
            return getResponse().sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR, x);
        }
    }

    void log(String message)
    {
        _log.debug(message);
    }

    void log(String message, Exception x)
    {
        _log.debug(message, x);
    }


//    private static final FastDateFormat creationDateFormat = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("GMT"));
    private static final FastDateFormat creationDateFormat = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss'-00:00'", TimeZone.getTimeZone("GMT"));

    private String getISOCreationDate(long creationDate)
    {
        return creationDateFormat.format(new Date(creationDate));
    }

    private static final FastDateFormat httpDateFormat = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz", TimeZone.getTimeZone("GMT"), Locale.US);
    
    private String getHttpDateFormat(long date)
    {
        return httpDateFormat.format(new Date(date));
    }

    String timestampZERO_iso = getISOCreationDate(DateUtil.parseISODateTime("1900-01-01 00:00:00"));
    String timestampZERO_http = getHttpDateFormat(DateUtil.parseISODateTime("1900-01-01 00:00:00"));


    /**
     * Generate the namespace declarations.
     */
    private String generateNamespaceDeclarations()
    {
        return " xmlns=\"" + DEFAULT_NAMESPACE + "\"";
    }



    /**
     * Copy a resource.
     *
     * @return boolean true if the copy is successful
     */
    private WebdavStatus copyResource() throws DavException, IOException
    {
        boolean overwrite = getOverwriteParameter(false);
        Path destinationPath = getDestinationPath();
        if (destinationPath == null)
            throw new DavException(WebdavStatus.SC_BAD_REQUEST);
        _log.debug("Dest path :" + destinationPath);

        WebdavResource resource = resolvePath();
        if (null != resource && !resource.canRead(getUser(),true))
           unauthorized(resource);
        if (null == resource || !resource.exists())
            throw new DavException(WebdavStatus.SC_NOT_FOUND);

        WebdavResource destination = resolvePath(destinationPath);
        if (null == destination)
            throw new DavException(WebdavStatus.SC_FORBIDDEN);
        checkAllowedFileName(destination.getName());
        WebdavStatus successStatus = destination.exists() ? WebdavStatus.SC_NO_CONTENT : WebdavStatus.SC_CREATED;

        if (null != resource.getFile() && null != destination.getFile())
            if (resource.getFile().getAbsolutePath().equals(destination.getFile().getAbsolutePath()))
                throw new DavException(WebdavStatus.SC_CONFLICT);
        Resource parent = destination.parent();
        if (parent == null || !parent.exists())
            throw new DavException(WebdavStatus.SC_CONFLICT);
        if (destination.exists() && !destination.canWrite(getUser(),true))
            return unauthorized(destination);

        if (destination.exists())
        {
            if (!overwrite)
                throw new DavException(WebdavStatus.SC_PRECONDITION_FAILED);
            if (destination.isCollection())
            {
                if (!_overwriteCollection)
                    throw new DavException(WebdavStatus.SC_FORBIDDEN, "Can't overwrite existing directory: " + destination.getPath());
                WebdavStatus ret = deleteResource(destinationPath);
                if (ret != WebdavStatus.SC_NO_CONTENT)
                    return ret;
            }
        }

        // Don't allow creating text/html via copy/rename (circumventing script checking)
        if (!isSafeCopy(resource,destination))
            throw new DavException(WebdavStatus.SC_FORBIDDEN, "Cannot create 'text/html' file using copy.");

        // Copying source to destination
        LinkedHashMap<Path,WebdavStatus> errorList = new LinkedHashMap<>();
        WebdavStatus ret = copyResource(resource, errorList, destinationPath);
        boolean result = ret == null;

        if ((!result) || (!errorList.isEmpty()))
            return sendReport(errorList);

        // Removing any lock-null resource which would be present at the destination path
        lockNullResources.remove(destinationPath);

        return successStatus;
    }


    /**
     * Copy a collection.
     *
     * @param src resources to be copied
     * @param errorList Hashtable containing the list of errors which occurred
     * during the copy operation
     * @param destPath Destination path
     */
    private WebdavStatus copyResource(WebdavResource src, Map<Path,WebdavStatus> errorList, Path destPath) throws DavException
    {
        _log.debug("Copy: " + src.getPath() + " To: " + destPath);

        WebdavResource dest = resolvePath(destPath);
        
        if (src.isCollection())
        {
            if (!dest.getFile().mkdir())
            {
                errorList.put(dest.getPath(), WebdavStatus.SC_CONFLICT);
                return WebdavStatus.SC_CONFLICT;
            }

            try
            {
                Collection<? extends WebdavResource> children = src.list();
                for (WebdavResource child : children)
                {
                    Path childDest = dest.getPath().append(child.getName());
                    copyResource(child, errorList, childDest);
                }
            }
            catch (Exception e)
            {
                errorList.put(dest.getPath(), WebdavStatus.SC_INTERNAL_SERVER_ERROR);
                return WebdavStatus.SC_INTERNAL_SERVER_ERROR;
            }
        }
        else
        {
            try
            {
                boolean exists = dest.getFile().exists();
                FileUtil.copyFile(src.getFile(), dest.getFile());
                if (exists)
                    dest.notify(getViewContext(), "overwrite: copied from " + src.getFile().getPath());
                else
                    dest.notify(getViewContext(), "create: copied from " + src.getFile().getPath());
                addToIndex(dest);
            }
            catch (IOException ex)
            {
                Resource parent = dest.parent();
                if (null != parent && !parent.exists())
                {
                    errorList.put(src.getPath(), WebdavStatus.SC_CONFLICT);
                    return WebdavStatus.SC_CONFLICT;
                }
                else
                {
                    errorList.put(src.getPath(), WebdavStatus.SC_INTERNAL_SERVER_ERROR);
                    return WebdavStatus.SC_INTERNAL_SERVER_ERROR;
                }
            }
        }

        return null;
    }


    /**
     * Return JAXP document builder instance.
     */
    private DocumentBuilder getDocumentBuilder()
            throws DavException
    {
        DocumentBuilder documentBuilder;
        DocumentBuilderFactory documentBuilderFactory;
        try
        {
            documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
        }
        catch (ParserConfigurationException e)
        {
            throw new DavException(e);
        }
        return documentBuilder;
    }



    /**
     * Holds a lock information.
     */
    /**
     * Holds a lock information.
     */
    private class LockInfo
    {
        public LockInfo()
        {
            // Ignore
        }


        // ------------------------------------------------- Instance Variables


        Path path = Path.rootPath;
        String type = "write";
        String scope = "exclusive";
        int depth = 0;
        String owner = "";
        final List<String> tokens = Collections.synchronizedList(new ArrayList<String>());
        long expiresAt = 0;
        final Date creationDate = new Date();


        // ----------------------------------------------------- Public Methods


        /**
         * Get a String representation of this lock token.
         */
        @Override
        public String toString()
        {
            StringBuilder result =  new StringBuilder("Type:");
            result.append(type);
            result.append("\nScope:");
            result.append(scope);
            result.append("\nDepth:");
            result.append(depth);
            result.append("\nOwner:");
            result.append(owner);
            result.append("\nExpiration:");
            result.append(new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).format(expiresAt));
            for (String token : tokens.toArray(new String[0]))
            {
                result.append("\nToken:");
                result.append(token);
            }
            result.append("\n");
            return result.toString();
        }


        /**
         * Return true if the lock has expired.
         */
        public boolean hasExpired()
        {
            return (System.currentTimeMillis() > expiresAt);
        }


        /**
         * Return true if the lock is exclusive.
         */
        public boolean isExclusive()
        {
            return (scope.equals("exclusive"));
        }


        /**
         * Get an XML representation of this lock token. This method will
         * append an XML fragment to the given XML writer.
         */
        public void toXML(XMLWriter generatedXML)
        {
            generatedXML.writeElement("D", "activelock", XMLWriter.OPENING);

            generatedXML.writeElement("D", "locktype", XMLWriter.OPENING);
            generatedXML.writeElement("D", type, XMLWriter.NO_CONTENT);
            generatedXML.writeElement("D", "locktype", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "lockscope", XMLWriter.OPENING);
            generatedXML.writeElement("D", scope, XMLWriter.NO_CONTENT);
            generatedXML.writeElement("D", "lockscope", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "depth", XMLWriter.OPENING);
            if (depth == maxDepth) {
                generatedXML.writeText("Infinity");
            } else {
                generatedXML.writeText("0");
            }
            generatedXML.writeElement("D", "depth", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "owner", XMLWriter.OPENING);
            generatedXML.writeText(owner);
            generatedXML.writeElement("D", "owner", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "timeout", XMLWriter.OPENING);
            long timeout = (expiresAt - System.currentTimeMillis()) / 1000;
            generatedXML.writeText("Second-" + timeout);
            generatedXML.writeElement("D", "timeout", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "locktoken", XMLWriter.OPENING);
            for (String token : tokens.toArray(new String[0]))
            {
                generatedXML.writeElement("D", "href", XMLWriter.OPENING);
                generatedXML.writeText("opaquelocktoken:" + token);
                generatedXML.writeElement("D", "href", XMLWriter.CLOSING);
            }
            generatedXML.writeElement("D", "locktoken", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "activelock", XMLWriter.CLOSING);
        }
    }


    private class Range
    {
        long start;
        long end;
        long length;

        /**
         * Validate range.
         */
        boolean validate()
        {
            if (end >= length)
                end = length - 1;
            return ((start >= 0) && (end >= 0) && (start <= end) && (length > 0));
        }

        void recycle()
        {
            start = 0;
            end = 0;
            length = 0;
        }

        @Override
        public String toString()
        {
            return "[" + start + "-" + end + "," + length + "]";
        }
    }


    /**
     * Default depth is infinite.
     */
    private static final int INFINITY = 3; // To limit tree browsing a bit


    enum Find
    {
        FIND_BY_PROPERTY,
        FIND_ALL_PROP,
        FIND_PROPERTY_NAMES
    }


    /**
     * Create a new lock.
     */
    private static final int LOCK_CREATION = 0;


    /**
     * Refresh lock.
     */
    private static final int LOCK_REFRESH = 1;


    /**
     * Default lock timeout value.
     */
    private static final int DEFAULT_TIMEOUT = 3600;


    /**
     * Maximum lock timeout.
     */
    private static final int MAX_TIMEOUT = 604800;

    /**
     * Default namespace.
     */
    private static final String DEFAULT_NAMESPACE = "DAV:";


    /** This class is just to make .available() work as expected above */
    private class ReadAheadInputStream extends FilterInputStream
    {
        ByteArrayOutputStream bos = null;
        InputStream is = null;
        
        ReadAheadInputStream(InputStream is) throws IOException
        {
            super(is instanceof BufferedInputStream ? is : new BufferedInputStream(is));
            this.is = is;
            assert super.markSupported();
            byte[] buf = new byte[1];
            super.mark(1025);
            int r = super.read(buf);
            super.reset();
            assert r <= 0 || available() > 0;
            if (_log.isDebugEnabled())
                bos = new ByteArrayOutputStream();

            assert track(this.is);  // InputStream
            assert track(in);       // BufferedInputStream
            assert track(this);
        }

        @Override
        public int available() throws IOException
        {
            String method = getRequest().getMethod();
            // GET, HEAD, and MKCOL request have no inputstream and are correctly 'available=0' in http, but in https were showing 'available>0' (fix for ISSUE: 25318 and 25437)
            if ( !"GET".equals(method) && !"HEAD".equals(method) && !"MKCOL".equals(method)) {
                return super.available();
            }
            return 0;
        }

        @Override
        public int read() throws IOException
        {
            int i = super.read();
            if (null != bos)
                bos.write(i);
            return i;
        }

        @Override
        public int read(byte b[]) throws IOException
        {
            int r = super.read(b);
            if (null != bos && r > 0)
                bos.write(b, 0, r);
            return r;
        }

        @Override
        public int read(byte b[], int off, int len) throws IOException
        {
            int r = super.read(b, off, len);
            if (null != bos && r > 0)
                bos.write(b, off, r);
            return r;
        }

        @Override
        public void close() throws IOException
        {
            super.close();
            if (null != bos)
                _log.debug(bos.toString());
            bos = null;

            assert untrack(in);     // BufferedInputStream
            assert untrack(is);     // InputStream
            assert untrack(this);   // FilterStream
        }
    }


    enum WebdavStatus
    {
        SC_CONTINUE(HttpServletResponse.SC_CONTINUE, "Continue"),
        //101=Switching Protocols
        //102=Processing
        SC_OK(HttpServletResponse.SC_OK, "OK"),
        SC_CREATED(HttpServletResponse.SC_CREATED, "Created"),
        SC_ACCEPTED(HttpServletResponse.SC_ACCEPTED, "Accepted"),
        //203=Non-Authoritative Information
        SC_NO_CONTENT(HttpServletResponse.SC_NO_CONTENT, "No Content"),
        //205=Reset Content
        SC_PARTIAL_CONTENT(HttpServletResponse.SC_PARTIAL_CONTENT, "Partial Content"),
        SC_MULTI_STATUS(207, "Multi-Status"),
        SC_FILE_MATCH(208, "File Conflict"),
        //300=Multiple Choices
        SC_MOVED_PERMANENTLY(HttpServletResponse.SC_MOVED_PERMANENTLY, "Moved Permanently"),
        SC_MOVED_TEMPORARILY(HttpServletResponse.SC_MOVED_TEMPORARILY, "Moved Temporarily"),    // Found
        SC_SEE_OTHER(HttpServletResponse.SC_SEE_OTHER, "See Other"),    // Found
        SC_NOT_MODIFIED(HttpServletResponse.SC_NOT_MODIFIED, "Not Modified"),
        //305=Use Proxy
        SC_TEMPORARY_REDIRECT(HttpServletResponse.SC_TEMPORARY_REDIRECT, "Temporary Redirect"),
        SC_BAD_REQUEST(HttpServletResponse.SC_BAD_REQUEST, "Bad Request"),
        SC_UNAUTHORIZED(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"),
        //402=Payment Required
        SC_FORBIDDEN(HttpServletResponse.SC_FORBIDDEN, "Forbidden"),
        SC_NOT_FOUND(HttpServletResponse.SC_NOT_FOUND, "Not Found"),
        SC_METHOD_NOT_ALLOWED(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method Not Allowed"),
        //406=Not Acceptable
        //407=Proxy Authentication Required
        //408=Request Time-out
        SC_CONFLICT(HttpServletResponse.SC_CONFLICT, "Conflict"),
        //410=Gone
        //411=Length Required
        SC_PRECONDITION_FAILED(HttpServletResponse.SC_PRECONDITION_FAILED, "Precondition Failed"),
        SC_REQUEST_TOO_LONG(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request Too Long"),
        //414=Request-URI Too Large
        SC_UNSUPPORTED_MEDIA_TYPE(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type"),
        SC_REQUESTED_RANGE_NOT_SATISFIABLE(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE, "Requested Range Not Satisfiable"),
        //417=Expectation Failed
        SC_UNPROCESSABLE_ENTITY(418, "Unprocessable Entity"),
        SC_INSUFFICIENT_SPACE_ON_RESOURCE(419, "Insufficient Space On Resource"),
        SC_METHOD_FAILURE(420, "Method Failure"),
        //422=Unprocessable Entity
        SC_LOCKED(423, "Locked"),
        //424=Failed Dependency
        SC_INTERNAL_SERVER_ERROR(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error"),
        SC_NOT_IMPLEMENTED(HttpServletResponse.SC_NOT_IMPLEMENTED, "Not Implemented"),
        SC_BAD_GATEWAY(HttpServletResponse.SC_BAD_GATEWAY, "Bad Gateway"),
        SC_SERVICE_UNAVAILABLE(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Service Unavailable")
        //504=Gateway Time-out
        //505=HTTP Version not supported
        //507=Insufficient Storage
        ;

        final int code;
        final String message;

        WebdavStatus(int code, String text)
        {
            this.code = code;
            this.message = text;
        }

        public String toString()
        {
            return "" + code + " " + message;
        }
    }


    class DavException extends Exception
    {
        protected WebdavStatus status;
        protected String message;
        protected WebdavResource resource;
        protected Path resourcePath;

        DavException(WebdavStatus status)
        {
            this.status = status;
        }

        DavException(WebdavStatus status, String message)
        {
            this.status = status;
            this.message = message;
        }

//        DavException(WebdavStatus status, String message, String path)
//        {
//            this.status = status;
//            this.message = message;
//            if (null != path)
//                this.resourcePath = Path.parse(path);
//        }

        DavException(WebdavStatus status, String message, Path path)
        {
            this.status = status;
            this.message = message;
            this.resourcePath = path;
        }

        DavException(WebdavStatus status, String message, Throwable t)
        {
            this.status = status;
            this.message = message;
            initCause(t);
        }

        DavException(Throwable x)
        {
            this.status = WebdavStatus.SC_INTERNAL_SERVER_ERROR;
            initCause(x);
        }

        public WebdavStatus getStatus()
        {
            return status;
        }

        public int getCode()
        {
            return status.code;
        }

        public String getMessage()
        {
            return StringUtils.defaultIfEmpty(message, status.message);
        }

        public WebdavResource getResource()
        {
            return resource;
        }

        public Path getResourcePath()
        {
            if (null != resource)
                return resource.getPath();
            else if (null != resourcePath)
                return resourcePath;
            return null;
        }

        JSONObject toJSON()
        {
            JSONObject o = new JSONObject();
            o.put("status", status.code);
            o.put("message", getMessage());
            Path p = getResourcePath();
            if (null != p)
            {
                o.put("resourcePath", p.toString());
                o.put("resourceName", p.getName());
            }
            // check for interesting annotations
            Map<Enum,String> map = ExceptionUtil.getExceptionDecorations(this);
            for (Map.Entry<Enum,String> e : map.entrySet())
            {
                if (e.getKey() == ExceptionUtil.ExceptionInfo.ResolveURL ||
                    e.getKey() == ExceptionUtil.ExceptionInfo.ResolveText ||
                    e.getKey() == ExceptionUtil.ExceptionInfo.HelpURL ||
                    e.getKey() == ExceptionUtil.ExceptionInfo.ExtraMessage)
                {
                    o.put(Introspector.decapitalize(e.getKey().name()), e.getValue());
                }
            }
            return o;
        }
    }

    class UnauthorizedException extends DavException
    {
        UnauthorizedException(WebdavResource resource)
        {
            super(WebdavStatus.SC_UNAUTHORIZED);
            this.resource = resource;
        }
        UnauthorizedException(WebdavResource resource, String message)
        {
            super(WebdavStatus.SC_UNAUTHORIZED);
            this.resource = resource;
            this.message = message;
        }
    }

    WebdavStatus unauthorized(WebdavResource resource) throws DavException
    {
        throw new UnauthorizedException(resource);
    }

    WebdavStatus unauthorized(WebdavResource resource, List<String> messages) throws DavException
    {
        String message = null!=messages && !messages.isEmpty() ? messages.get(0) : null;
        throw new UnauthorizedException(resource, message);
    }

    WebdavStatus notFound() throws DavException
    {
        return notFound(getResourcePath());
    }

    WebdavStatus notFound(Path path) throws DavException
    {
        throw new DavException(WebdavStatus.SC_NOT_FOUND, null, path);
    }
    
    private void checkReadOnly() throws DavException
    {
        if (_readOnly)
            throw new DavException(WebdavStatus.SC_FORBIDDEN);
    }


    private void checkLocked() throws DavException
    {
        checkLocked(getResourcePath());
    }


    private void checkLocked(Path path) throws DavException
    {
        String ifHeader = getRequest().getHeader("If");
        if (ifHeader == null)
            ifHeader = "";

        String lockTokenHeader = getRequest().getHeader("Lock-Token");
        if (lockTokenHeader == null)
            lockTokenHeader = "";

        LockResult result = isLocked(path, ifHeader + lockTokenHeader);
        switch (result)
        {
            case NOT_LOCKED:
                return;
            case HAS_LOCK:
                return;
            case TOKEN_NOT_FOUND:
                throw new DavException(WebdavStatus.SC_PRECONDITION_FAILED, path.toString());
            case LOCKED:
                throw new DavException(WebdavStatus.SC_LOCKED, path.toString());
        }
    }


    /** Converts FileNotFound into SC_NOT_FOUND */
    private InputStream getResourceInputStream(WebdavResource r, User user) throws IOException, DavException
    {
        try
        {
            InputStream result = r.getInputStream(user);
            if (result == null)
            {
                throw new DavException(WebdavStatus.SC_NOT_FOUND, String.valueOf(r.getPath()));
            }
            return result;
        }
        catch (FileNotFoundException fnfe)
        {
            throw new DavException(WebdavStatus.SC_NOT_FOUND, String.valueOf(r.getPath()), fnfe);
        }
    }



    Map<Closeable,Throwable> closables = new IdentityHashMap<>();

    boolean track(Closeable c)
    {
        closables.put(c, new Throwable());
        return true;
    }

    boolean untrack(Closeable c)
    {
        if (null != c)
            closables.remove(c);
        return true;
    }

    private void close(Closeable c, String msg)
    {
        try
        {
            if (null != c)
            {
                c.close();
                assert untrack(c);
            }
        }
        catch (Exception e)
        {
            log(msg, e);
        }
    }

    class _ByteArrayOutputStream extends ByteArrayOutputStream
    {
        _ByteArrayOutputStream(int len)
        {
            super(len);
        }

        public synchronized void writeTo(DataOutput out) throws IOException
        {
            out.write(buf);
        }
    }

    private String h(String s)
    {
        return PageFlowUtil.filter(s);
    }


    private static final Set<String> _tempFiles = new ConcurrentSkipListSet<>();
    private static final Set<Path> _tempResources = new ConcurrentHashSet<>();

    private void markTempFile(WebdavResource r)
    {
        _tempResources.add(r.getPath());
        markTempFile(r.getFile());
    }

    public static void markTempFile(File f)
    {
        if (null != f)
            _tempFiles.add(f.getPath());
    }
    
    public static boolean rmTempFile(WebdavResource r)
    {
        rmTempFile(r.getFile());
        return _tempResources.remove(r.getPath());
    }

    public static boolean rmTempFile(File f)
    {
        if (null != f)
            return _tempFiles.remove(f.getPath());
        return false;
    }

    public static boolean isTempFile(File f)
    {
        if (f != null)
            return _tempFiles.contains(f.getPath());
        return false;
    }

    private boolean isTempFile(WebdavResource r)
    {
        return _tempResources.contains(r.getPath());
    }

    public static ShutdownListener getShutdownListener()
    {
        return new ShutdownListener()
        {
            @Override
            public String getName()
            {
                return "Temp file deletion";
            }

            @Override
            public void shutdownPre()
            {
            }

            @Override
            public void shutdownStarted()
            {
                for (Object path : _tempFiles.toArray())
                    new File((String)path).delete();
            }
        };
    }


    private void addToIndex(WebdavResource r)
    {
        _log.debug("addToIndex: " + r.getPath());

        try
        {
            // a lot of tools up load temporary 0 length files, let's not index them
            if (!r.shouldIndex() || r.getContentLength() == 0 || r.getName().startsWith("._"))
            {
                _log.debug("!shouldIndex(): " + r.getPath());
                return;
            }
        }
        catch (IOException x)
        {
            _log.debug("!shouldIndex(): " + r.getPath(), x);
            return;
        }


        boolean isFile = r.isFile();
        // UNDONE: FileSystemResource.isFile() may not be correct after MoveAction
        // UNDONE: fix FileSystemResource or at least move this hack into MoveAction (CopyAction?)
        if (!isFile && null != r.getFile())
        {
            isFile = r.getFile().isFile();
            if (isFile)
            {
                try
                {
                    resourceCache.remove(r.getPath());
                    r = resolvePath(r.getPath());
                    isFile = r.isFile();
                }
                catch (DavException x)
                {
                    return;
                }
            }
        }
        if (!isFile)
            return;

        if (isTempFile(r))
            return;
        
        SearchService ss = SearchService.get();
        if (null != ss)
            ss.defaultTask().addResource(r, SearchService.PRIORITY.item);
    }


    private void removeFromIndex(WebdavResource r)
    {
        _log.debug("removeFromIndex: " + r.getPath());
        SearchService ss = SearchService.get();
        if (null != ss)
            ss.deleteResource(r.getDocumentId());
    }


    /**
     * There seems to be no 'legal' way to update the lastAccessedTime for a session
     * However, catalina.Session has an access() endAccess() method used when a request starts/ends
     */
    public static class SessionKeepAliveFilter extends FilterInputStream
    {
        static InputStream wrap(InputStream in, HttpServletRequest request)
        {
            HttpSession facade = request.getSession(true);
            HttpSession inner = null;
            try
            {
                // org.apache.catalina.Session inner = ((org.apache.catalina.session.StandardSessionFacade)facade).session;
                Field f = facade.getClass().getDeclaredField("session");
                f.setAccessible(true);
                inner = (HttpSession)f.get(facade);
            }
            catch (NoSuchFieldException x)
            {
            }
            catch (IllegalAccessException x)
            {
            }
            if (null == inner)
                inner = facade;
            Method access = null;
            Method endAccess = null;
            try
            {
                access = inner.getClass().getMethod("access");
                endAccess = inner.getClass().getMethod("endAccess");
            }
            catch (NoSuchMethodException x)
            {
            }
            if (null == access || null == endAccess)
                return in;
            return new SessionKeepAliveFilter(in, inner, access, endAccess);
        }

        final HttpSession session;
        final Method accessMethod;
        final Method endAccessMethod;
        long time;

        SessionKeepAliveFilter(InputStream in, HttpSession session, Method access, Method endAccess)
        {
            super(in);
            this.session = session;
            this.accessMethod = access;
            this.endAccessMethod = endAccess;
            // set up so that access() gets called immediately, for easier testing
            this.time = 0; // HeartBeat.currentTimeMillis();
            access();
        }

        private void access()
        {
            long now = HeartBeat.currentTimeMillis();
            if (now - time >= 60*1000)
            {
                time = now;
                try
                {
                    accessMethod.invoke(session);
                    endAccessMethod.invoke(session);
                }
                catch (IllegalAccessException x)
                {

                }
                catch (InvocationTargetException x)
                {

                }
            }
        }

        @Override
        public int read() throws IOException
        {
            access();
            return super.read();
        }

        @Override
        public int read(byte[] bytes) throws IOException
        {
            access();
            return super.read(bytes, 0, bytes.length);
        }

        @Override
         public int read(byte[] bytes, int i, int i1) throws IOException
        {
            access();
            return super.read(bytes, i, i1);
        }
    }


    void checkAllowedFileName(String s) throws DavException
    {
        String msg = isAllowedFileName(s);
        if (null == msg)
            return;
        throw new DavException(WebdavStatus.SC_BAD_REQUEST, msg);
    }


    static private final String windowsRestricted = "\\/:*?\"<>|`";
    // and ` seems like a bad idea for linux?
    static private final String linuxRestricted = "`";
    static private final String restrictedPrintable = windowsRestricted + linuxRestricted;

    public static String isAllowedFileName(String s)
    {
        if (StringUtils.isBlank(s))
            return "Filename must not be blank";
        if (!ViewServlet.validChars(s))
            return "Filename must contain only valid unicode characters.";
        if (StringUtils.containsAny(s, restrictedPrintable))
            return "Filename may not contain any of these characters: " + restrictedPrintable;
        if (StringUtils.containsAny(s, "\t\n\r"))
            return "Filename may not contain 'tab', 'new line', or 'return' characters.";
        if (StringUtils.contains("-$%", s.charAt(0)))
            return "Filename may not begin with any of these characters: -$%";
        if (Pattern.matches(".*\\s-[^ ].*",s))
            return "Filename may not contain space followed by dash.";
        return null;
    }


    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        @Test
        public void testAllowedFileName()
        {
            assertNull(isAllowedFileName("a"));
            assertNull(isAllowedFileName("a-b"));
            assertNull(isAllowedFileName("a b"));
            assertNull(isAllowedFileName("a%b"));
            assertNull(isAllowedFileName("a$b"));

            assertNotNull(isAllowedFileName(null));
            assertNotNull(isAllowedFileName(""));
            assertNotNull(isAllowedFileName(" "));
            assertNotNull(isAllowedFileName("a\tb"));
            assertNotNull(isAllowedFileName("-a"));
            assertNotNull(isAllowedFileName("a -b"));
            assertNotNull(isAllowedFileName("a/b"));
            assertNotNull(isAllowedFileName("a\b"));
            assertNotNull(isAllowedFileName("a:b"));
            assertNotNull(isAllowedFileName("a*b"));
            assertNotNull(isAllowedFileName("a?b"));
            assertNotNull(isAllowedFileName("a<b"));
            assertNotNull(isAllowedFileName("a>b"));
            assertNotNull(isAllowedFileName("a\"b"));
            assertNotNull(isAllowedFileName("a|b"));
            assertNotNull(isAllowedFileName("a`b"));
            assertNotNull(isAllowedFileName("$ab"));
            assertNotNull(isAllowedFileName("-ab"));
            assertNotNull(isAllowedFileName("%a`b"));
        }
    }
}
