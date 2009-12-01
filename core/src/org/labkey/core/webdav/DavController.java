/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

package org.labkey.core.webdav;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.log4j.Category;
import org.apache.log4j.Logger;
import org.json.JSONWriter;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.*;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.template.PrintTemplate;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.webdav.Resource;
import org.labkey.core.webdav.apache.XMLWriter;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;


/**
 * Created by IntelliJ IDEA.
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

    static Category _log = Logger.getInstance(DavController.class);
    static DefaultActionResolver _actionResolver = new DefaultActionResolver(DavController.class);
    static boolean _readOnly = false;
    static boolean _locking = false;
    static boolean _requiresLogin = true;
    static boolean _overwriteCollection = true; // must be true to pass litmus

    WebdavResponse _webdavresponse;
    WebdavResolver _webdavresolver;


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
    
    WebdavResolver getResolver()
    {
        return _webdavresolver;
    }

    // best guess is this a browser vs. a WebDAV client
    boolean isBrowser()
    {
        String userAgent = getRequest().getHeader("user-agent");
        if (null == userAgent)
            return false;
        return userAgent.startsWith("Mozilla/") || userAgent.startsWith("Opera/");
    }


    String getURL()
    {
        return (String)getRequest().getAttribute(ViewServlet.ORIGINAL_URL);
    }


    ActionURL getLoginURL()
    {
        return PageFlowUtil.urlProvider(LoginUrls.class).getLoginURL(getContainer(), getURL());
    }


    public DavController()
    {
        setActionResolver(_actionResolver);
    }


    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws MultipartException
    {
        _webdavresponse = new WebdavResponse(response);

        String contentType = request.getContentType();
        if (null != contentType && contentType.startsWith("multipart"))
            request = (new CommonsMultipartResolver()).resolveMultipart(request);

        ViewContext context = getViewContext();
        context.setRequest(request);
        context.setResponse(response);

        String method = getViewContext().getActionURL().getAction();
        Controller action = resolveAction(method.toLowerCase());
        try
        {
            if (null == action)
            {
                _webdavresponse.sendError(WebdavStatus.SC_NOT_IMPLEMENTED);
                return null;
            }
            action.handleRequest(request, response);
        }
        catch (Exception e)
        {
            _log.error("unexpected exception", e);
            _webdavresponse.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR, e);
        }
        for (Map.Entry e : closables.entrySet())
        {
            Closeable c = (Closeable) e.getKey();
            Throwable t = (Throwable)e.getValue();
            _log.warn(c.getClass().getName(), t);
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
            _log.error("unexpected error", x);
            return sendError(status, x.getMessage() != null ? x.getMessage() : status.message);
        }

        WebdavStatus sendError(WebdavStatus status, Path path)
        {
            return sendError(status, path.toString());
        }

        WebdavStatus sendError(WebdavStatus status, String message)
        {
            assert !_sendError;
            try
            {
                if (null == StringUtils.trimToNull(message))
                    response.sendError(status.code);
                else
                    response.sendError(status.code, message);
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
                    super.write(str);
                    sbLogResponse.append(str);
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


    @RequiresPermission(ACL.PERM_NONE)
    private abstract class DavAction implements Controller
    {
        final String method;

        protected DavAction(String method)
        {
            this.method = method;
        }

        public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            _log.debug(">>>> " + request.getMethod() + " " + getResourcePath());

            try
            {
                try
                {
                    if (_requiresLogin && getUser().isGuest())
                        throw new UnauthorizedException(resolvePath());

                    WebdavStatus ret = doMethod();
                    assert null != ret || getResponse().getStatus() != null;
                    if (null != ret && 200 <= ret.code && ret.code < 300)
                        getResponse().setStatus(ret);
                }
                catch (IOException ex)
                {
                    if (ExceptionUtil.isClientAbortException(ex))
                        return null; // ignore
                    throw new DavException(ex);
                }
            }
            catch (UnauthorizedException uex)
            {
                Resource resource = uex.getResource();
                if (!getUser().isGuest())
                {
                    getResponse().sendError(WebdavStatus.SC_FORBIDDEN, resource.getPath());
                }
                else if (resource.isCollection() && isBrowser() && "GET".equals(method))
                {
                    getResponse().setStatus(WebdavStatus.SC_MOVED_PERMANENTLY);
                    response.setHeader("Location", getLoginURL().getEncodedLocalURIString());
                }
                else
                {
                    getResponse().setRealm(LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getDescription());
                    getResponse().sendError(WebdavStatus.SC_UNAUTHORIZED, resource.getPath());
                }
            }
            catch (DavException dex)
            {
                getResponse().sendError(dex.getStatus(), dex.getMessage());
            }

            if (getResponse().sbLogResponse.length() > 0)
                _log.debug(getResponse().sbLogResponse);
            WebdavStatus status = getResponse().getStatus();
            String message = getResponse().getMessage();
            _log.debug("<<<< " + (status != null ? status.code : 0) + (message == null ? "" : (": " + message)));

            return null;
        }

        WebdavStatus doMethod() throws DavException, IOException, RedirectException
        {
            Resource resource = resolvePath();
            if (null != resource)
            {
                StringBuilder methodsAllowed = determineMethodsAllowed(resource);
                getResponse().setMethodsAllowed(methodsAllowed);
            }
            return WebdavStatus.SC_METHOD_NOT_ALLOWED;
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class GetAction extends DavAction
    {
        public GetAction()
        {
            super("GET");
        }

        protected GetAction(String method)
        {
            super(method);
        }

        public WebdavStatus doMethod() throws DavException, IOException
        {
            Resource resource = null;
            if ("GET".equals(method) && getResourcePath().size() == 0)
                resource = getResolver().welcome();
            if (null == resource)
                resource = resolvePath();
            if (null == resource)
                return notFound();
            if (resource.isCollection() && !allowHtmlListing(resource))
                return notFound(resource.getPath());
            if (!(resource.isCollection() ? resource.canList(getUser()) : resource.canRead(getUser())))
                return unauthorized(resource);
            if (!resource.exists())
                return notFound(resource.getPath());

            // http://www.ietf.org/rfc/rfc4709.txt
            if ("DAVMOUNT".equals(method))
                return mountResource(resource);
            else if (resource.isCollection())
                return serveCollection(resource, !"HEAD".equals(method));
            else
                return serveResource(resource, !"HEAD".equals(method));
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class DavmountAction extends GetAction
    {
        public DavmountAction()
        {
            super("DAVMOUNT");
        }
    }

    
    @RequiresPermission(ACL.PERM_NONE)
    public class HeadAction extends GetAction
    {
        public HeadAction()
        {
            super("HEAD");
        }
    }


    WebdavStatus mountResource(Resource resource) throws DavException, IOException
    {
        StringBuilder sb = new StringBuilder();
        String root = resolvePath("/").getHref(getViewContext());
        if (!root.endsWith("/")) root += "/";
        if (resource.isFile())
            resource = resource.parent();
        String path = resource.getHref(getViewContext());
        if (!path.endsWith("/")) path += "/";
        String open = path.substring(root.length());

        sb.append("<dm:mount xmlns:dm=\"http://purl.org/NET/webdav/mount\">\n");
        sb.append("  <dm:url>").append(PageFlowUtil.filter(root)).append("</dm:url>\n");
        if (open.length() > 0)
        sb.append("  <dm:open>").append(PageFlowUtil.filter(open)).append("</dm:open>\n");
        sb.append("</dm:mount>");


        getResponse().setContentType("application/davmount+xml");
        getResponse().setContentDisposition("attachment; filename=\"" + resource.getName() + ".davmount\"");
        getResponse().getWriter().write(sb.toString());
        return WebdavStatus.SC_OK;
    }


    /*
     * CONSIDER: make this work like a form post
     * <form method="post" action="" enctype="multipart/form-data">
     * <input type="file" name="file">
     * </form>
     */
    @RequiresPermission(ACL.PERM_NONE)
    public class PostAction extends PutAction
    {
        public PostAction()
        {
            super("POST");
        }

        @Override
        public WebdavStatus doMethod() throws DavException, IOException, RedirectException
        {
            Resource resource = resolvePath();
            if (null == resource)
                return notFound();
            boolean isCollection = resource.isCollection();

            if (isCollection && getRequest() instanceof MultipartHttpServletRequest)
            {
                MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest)getRequest();
                if (multipartRequest.getFileMap().size() > 1)
                    return WebdavStatus.SC_NOT_IMPLEMENTED;

                for (Object o : multipartRequest.getFileMap().entrySet())
                {
                    Map.Entry<String, MultipartFile> entry = (Map.Entry<String, MultipartFile>) o;
                    MultipartFile file = entry.getValue();
                    String fileName = file.getOriginalFilename();
                    if (file.isEmpty())
                        return WebdavStatus.SC_METHOD_NOT_ALLOWED;
                    Resource dest = resource.find(fileName);
                    if (null == dest)
                        return WebdavStatus.SC_METHOD_NOT_ALLOWED;
                    // CONSIDER: support multi-file POST
                    setResource(dest);
                    setFileStream(new SpringAttachmentFile(file));
                    WebdavStatus status = super.doMethod();

                    // if _returnUrl then redirect, else respond as if PROPFIND
                    String returnUrl = getRequest().getParameter(ReturnUrlForm.Params.returnUrl.toString());
                    if (null != StringUtils.trimToNull(returnUrl))
                    {
                        HttpView.throwRedirect(returnUrl);
                        return WebdavStatus.SC_OK;
                    }

                    if (status == WebdavStatus.SC_CREATED)
                    {
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
                                return new Pair<Integer,Boolean>(0,Boolean.FALSE);
                            }
                        };
                        action.setResource(dest);
                        return action.doMethod();
                    }
                    return status;
                }
            }
            return new GetAction().doMethod();
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class PropfindAction extends DavAction
    {
        Resource _resource = null;
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

        protected void setResource(Resource r)
        {
            _resource = r;
        }

        Resource getResource() throws DavException
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
                return new Pair<Integer, Boolean>(defaultDepth, defaultListRoot);
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
            return new Pair<Integer,Boolean>(depth, depth>0 && noroot);
        }

        public WebdavStatus doMethod() throws DavException, IOException
        {
            Resource root = getResource();
            if (root == null || !root.exists())
                return notFound();
            
            if (!root.canList(getUser()))
                return unauthorized(root);

            List<String> properties = null;
            Find type = Find.FIND_ALL_PROP;
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
                        DocumentBuilder documentBuilder;
                        try
                        {
                            documentBuilder = getDocumentBuilder();
                        }
                        catch (ServletException ex)
                        {
                            throw new DavException(ex.getCause());
                        }

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

                if (type == Find.FIND_BY_PROPERTY)
                {
                    properties = new Vector<String>();
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
            }

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
                    resourceWriter.writeProperties(root, type, properties);
                }
                else
                {
                    // The stack always contains the object of the current level
                    LinkedList<Path> stack = new LinkedList<Path>();
                    stack.addLast(root.getPath());

                    // Stack of the objects one level below
                    boolean skipFirst = noroot;
                    Resource resource;
                    LinkedList<Path> stackBelow = new LinkedList<Path>();

                    while ((!stack.isEmpty()) && (depth >= 0))
                    {
                        Path currentPath = stack.removeFirst();
                        resource = resolvePath(currentPath);

                        if (null == resource || !resource.canList(getUser()))
                            continue;

                        if (isTempFile(resource))
                            continue;

                        if (skipFirst)
                            skipFirst = false;
                        else
                            resourceWriter.writeProperties(resource, type, properties);

                        if (resource.isCollection() && depth > 0)
                        {
                            List<String> listPaths = resource.listNames();
                            for (String listPath : listPaths)
                            {
                                Path newPath = currentPath.append(listPath);
                                stackBelow.addLast(newPath);
                            }

                            // Displaying the lock-null resources present in that
                            // collection
                            Path lockPath = currentPath;
                            List currentLockNullResources = (List) lockNullResources.get(lockPath);
                            if (currentLockNullResources != null)
                            {
                                for (Object currentLockNullResource : currentLockNullResources)
                                {
                                    String lockNullPath = (String) currentLockNullResource;
                                    resourceWriter.writeLockNullProperties(lockNullPath, type, properties);
                                }
                            }
                        }

                        if (stack.isEmpty())
                        {
                            depth--;
                            stack = stackBelow;
                            stackBelow = new LinkedList<Path>();
                        }

                        resourceWriter.sendData();
                    }
                }
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

        protected ResourceWriter getResourceWriter(Writer writer)
        {
            return new XMLResourceWriter(writer);
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class JsonAction extends PropfindAction
    {
        // depth > 1 NYI
        public JsonAction()
        {
            super("JSON");
            defaultListRoot = false;
            defaultDepth = 1;
        }

        @Override
        protected Resource getResource() throws DavException
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
    }


    interface ResourceWriter
    {
        void beginResponse(WebdavResponse response) throws Exception;
        void endResponse() throws Exception;
 
        /**
         * @param type             Propfind type
         * @param propertiesVector If the propfind type is find properties by
         *                         name, then this Vector contains those properties
         */
        public void writeProperties(Resource resource, Find type, List<String> propertiesVector) throws Exception;

        /**
         * @param path             Path of the current resource
         * @param type             Propfind type
         * @param propertiesVector If the propfind type is find properties by
         *                         name, then this Vector contains those properties
         */
        public void writeLockNullProperties(String path, Find type, List<String> propertiesVector) throws Exception;

        void sendData() throws Exception;
    }


    long _contentLength(Resource r)
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

        XMLResourceWriter(Writer writer)
        {
            xml = new XMLWriter(writer);
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


        public void writeProperties(Resource resource, Find type, List<String> propertiesVector)
        {
            boolean exists = resource.exists();
            boolean isFile = exists && resource.isFile();

            xml.writeElement(null, "response", XMLWriter.OPENING);
            String status = "HTTP/1.1 " + WebdavStatus.SC_OK;

            xml.writeElement(null, "href", XMLWriter.OPENING);
            xml.writeText(h(resource.getLocalHref(getViewContext())));
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
                    if (created != Long.MIN_VALUE)
                        xml.writeProperty(null, "creationdate", getISOCreationDate(created));
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
                        String createdby = resource.getCreatedBy();
                        if (null != createdby)
                            xml.writeProperty(null, "createdby", createdby);

                        if (isFile)
                        {
                            long modified = resource.getLastModified();
                            if (modified != Long.MIN_VALUE)
                                xml.writeProperty(null, "getlastmodified", getHttpDateFormat(modified));
                            String modifiedby = resource.getModifiedBy();
                            if (null != modifiedby)
                                xml.writeProperty(null, "modifiedby", modifiedby);
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
                    }

                    {
                    StringBuilder methodsAllowed = determineMethodsAllowed(resource);
                    xml.writeProperty(null, "options", methodsAllowed.toString());
                    }
                
                    xml.writeProperty(null, "iconHref", h(resource.getIconHref()));

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
                    }
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

                    List<String> propertiesNotFound = new Vector<String>();

                    // Parse the list of properties

                    xml.writeElement(null, "propstat", XMLWriter.OPENING);
                    xml.writeElement(null, "prop", XMLWriter.OPENING);

                    for (String property : propertiesVector)
                    {
                        if (property.equals("creationdate"))
                        {
                            long created = resource.getCreated();
                            if (created == Long.MIN_VALUE)
                                xml.writeElement(null, "creationdate", XMLWriter.NO_CONTENT);
                            else
                                xml.writeProperty(null, "creationdate", getISOCreationDate(resource.getCreated()));
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
						else if (property.equals("ishidden"))
						{
							xml.writeElement(null, "ishidden", XMLWriter.OPENING);
							xml.writeText("0");
							xml.writeElement(null, "ishidden", XMLWriter.CLOSING);
						}
						else if (property.equals("isreadonly"))
						{
							xml.writeElement(null, "isreadonly", XMLWriter.OPENING);
							xml.writeText(resource.canWrite(getUser()) ? "0" : "1");
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
//						  else if (property.equals("supportedlock"))
//						  {
//								supportedLocks = "<lockentry>"
//										  + "<lockscope><exclusive/></lockscope>"
//										  + "<locktype><write/></locktype>"
//										  + "</lockentry>" + "<lockentry>"
//										  + "<lockscope><shared/></lockscope>"
//										  + "<locktype><write/>meth</locktype>"
//										  + "</lockentry>";
//								generatedXML.writeElement(null, "supportedlock", XMLWriter.OPENING);
//								generatedXML.writeText(supportedLocks);
//								generatedXML.writeElement(null, "supportedlock", XMLWriter.CLOSING);
//						  }
//						  else if (property.equals("lockdiscovery"))
//						  {
//								if (!generateLockDiscovery(resource.getPath(), generatedXML))
//									 propertiesNotFound.add(property);
//						  }
                        else if (property.equals("md5sum"))
                        {
                            String md5sum = null;
                            try
                            {
                                md5sum = FileUtil.md5sum(resource.getInputStream(getUser()));
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
                            List<WebdavResolver.History> list = resource.getHistory();
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


        public void writeLockNullProperties(String path, Find type, List<String> propertiesVector) throws DavException
        {
            // Retrieving the lock associated with the lock-null resource
            LockInfo lock = (LockInfo) resourceLocks.get(path);
            if (lock == null)
                return;

            Resource resource = resolvePath(path);

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

//                String supportedLocks = "<lockentry>"
//                        + "<lockscope><exclusive/></lockscope>"
//                        + "<locktype><write/></locktype>"
//                        + "</lockentry>" + "<lockentry>"
//                        + "<lockscope><shared/></lockscope>"
//                        + "<locktype><write/></locktype>"
//                        + "</lockentry>";
//                generatedXML.writeElement(null, "supportedlock", XMLWriter.OPENING);
//                generatedXML.writeText(supportedLocks);
//                generatedXML.writeElement(null, "supportedlock", XMLWriter.CLOSING);

//                generateLockDiscovery(path, generatedXML);

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
//                generatedXML.writeElement(null, "lockdiscovery", XMLWriter.NO_CONTENT);

                    xml.writeElement(null, "prop", XMLWriter.CLOSING);
                    xml.writeElement(null, "status", XMLWriter.OPENING);
                    xml.writeText(status);
                    xml.writeElement(null, "status", XMLWriter.CLOSING);
                    xml.writeElement(null, "propstat", XMLWriter.CLOSING);

                    break;

                case FIND_BY_PROPERTY:

                    List<String> propertiesNotFound = new Vector<String>();

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
//                    else if (property.equals("supportedlock"))
//                    {
//                        supportedLocks = "<lockentry>"
//                                + "<lockscope><exclusive/></lockscope>"
//                                + "<locktype><write/></locktype>"
//                                + "</lockentry>" + "<lockentry>"
//                                + "<lockscope><shared/></lockscope>"
//                                + "<locktype><write/></locktype>"
//                                + "</lockentry>";
//                        generatedXML.writeElement(null, "supportedlock", XMLWriter.OPENING);
//                        generatedXML.writeText(supportedLocks);
//                        generatedXML.writeElement(null, "supportedlock", XMLWriter.CLOSING);
//                    }
//                    else if (property.equals("lockdiscovery"))
//                    {
//                        if (!generateLockDiscovery(path, generatedXML))
//                            propertiesNotFound.add(property);
//                    }
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

        JSONResourceWriter(Writer writer)
        {
            if (writer instanceof BufferedWriter)
                out = (BufferedWriter)writer;
            out = new BufferedWriter(writer);
            json = new JSONWriter(writer);
        }

        public void beginResponse(WebdavResponse response) throws Exception
        {
            response.setContentType("text/plain; charset=UTF-8");
            json.array();
        }

        public void endResponse() throws Exception
        {
            json.endArray();
        }

        public void writeProperties(Resource resource, Find type, List<String> propertiesVector) throws Exception
        {
            json.object();
            json.key("id").value(resource.getPath());
            String displayName = resource.getPath().equals("/") ? "/" : resource.getName();
            json.key("href").value(resource.getHref(getViewContext()));
            json.key("text").value(displayName);

            json.key("creationdate").value(new Date(resource.getCreated()));
            if (resource.isFile())
            {
                json.key("lastmodified").value(new Date(resource.getLastModified()));
                long length = resource.getContentLength();
                json.key("contentlength").value(length);
                if (length >= 0)
                    json.key("size").value(length);
                String contentType = resource.getContentType();
                if (contentType != null)
                    json.key("contenttype").value(contentType);
                json.key("etag").value(resource.getETag());
                json.key("leaf").value(true);
            }
            else
            {
                json.key("leaf").value(false);
            }

            json.endObject();
            out.newLine();
        }

        public void writeLockNullProperties(String path, Find type, List<String> propertiesVector) throws Exception
        {
            // Retrieving the lock associated with the lock-null resource
            LockInfo lock = (LockInfo) resourceLocks.get(path);
            if (lock == null)
                return;

            Resource resource = resolvePath(path);

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



    @RequiresPermission(ACL.PERM_NONE)
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

            Resource resource = resolvePath();
            if (null == resource)
                return notFound();
            boolean exists = resource.exists();

            // Can't create a collection if a resource already exists at the given path
            if (exists)
            {
                // Get allowed methods
                StringBuilder methodsAllowed = determineMethodsAllowed(resource);
                getResponse().setMethodsAllowed(methodsAllowed);
                throw new DavException(WebdavStatus.SC_METHOD_NOT_ALLOWED);
            }

            InputStream is = null;
            try
            {
                is = new ReadAheadInputStream(getRequest().getInputStream());
                if (is.available() > 0)
                {
                     DocumentBuilder documentBuilder;
                    try
                    {
                        documentBuilder = getDocumentBuilder();
                    }
                    catch (ServletException ex)
                    {
                        throw new DavException(ex.getCause());
                    }
                    try
                    {
                        // TODO : Process this request body
                        documentBuilder.parse(new InputSource(is));
                        is = null;  // stream is closed
                        throw new DavException(WebdavStatus.SC_NOT_IMPLEMENTED);
                    }
                    catch (SAXException saxe)
                    {
                        // Parse error - assume invalid content
                        throw new DavException(WebdavStatus.SC_UNSUPPORTED_MEDIA_TYPE);
                    }
                }
            }
            finally
            {
                IOUtils.closeQuietly(is);
            }

            if (!resource.canCreate(getUser()))
                return unauthorized(resource);

            boolean result = resource.getFile() != null && resource.getFile().mkdirs();

            if (!result)
            {
                throw new DavException(WebdavStatus.SC_CONFLICT);
            }
            else
            {
                // Removing any lock-null resource which would be present
                lockNullResources.remove(path);
                return WebdavStatus.SC_CREATED;
            }
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class PutAction extends DavAction
    {
        // this is a member so PostAction() can set it
        Resource _resource;
        FileStream _fis;

        public PutAction()
        {
            super("PUT");
        }

        protected PutAction(String method)
        {
            super(method);
        }

        protected void setResource(Resource r)
        {
            _resource = r;
        }
        
        Resource getResource() throws DavException
        {
            if (null == _resource)
                _resource = resolvePath();
            return _resource;
        }

        protected void setFileStream(FileStream fis)
        {
            _fis = fis;
        }

        FileStream getFileStream() throws DavException, IOException
        {
            if (null == _fis)
            {
                final InputStream is = getRequest().getInputStream();
                final long _size = Long.parseLong(getRequest().getHeader("Content-Length"));
                _fis = new FileStream()
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
            }
            return _fis;
        }

        WebdavStatus doMethod() throws DavException, IOException, RedirectException
        {
            checkReadOnly();
            checkLocked();

            Resource resource = getResource();
            if (resource == null)
                return notFound();
            boolean exists = resource.exists();
            boolean deleteFileOnFail = false;
            boolean temp = false;

            if (exists && !resource.canWrite(getUser()) || !exists && !resource.canCreate(getUser()))
                return unauthorized(resource);

            Range range = parseContentRange();
            RandomAccessFile raf = null;
            OutputStream os = null;

            try
            {
                if (!resource.exists())
                {
                    temp = getTemporary();
                    if (temp)
                        markTempFile(resource);
                    deleteFileOnFail = true;
                }

                if (range != null)
                {
                    if (resource.getContentType().startsWith("text/html") && !UserManager.mayWriteScript(getUser()))
                        throw new DavException(WebdavStatus.SC_FORBIDDEN, "Partial writing of html files is not allowed");
                    if (range.start > raf.length() || (range.end - range.start) > Integer.MAX_VALUE)
                        throw new DavException(WebdavStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    // CONSIDER: use temp file
                    _ByteArrayOutputStream bos = new _ByteArrayOutputStream((int)(range.end-range.start));
                    FileUtil.copyData(getFileStream().openInputStream(), bos);
                    if (bos.size() != range.end-range.start)
                        throw new DavException(WebdavStatus.SC_BAD_REQUEST);
                    File f = resource.getFile();
                    if (null == f)
                        return WebdavStatus.SC_NOT_IMPLEMENTED;
                    raf = new RandomAccessFile(resource.getFile(),"rw");
                    assert track(raf);
                    raf.seek(range.start);
                    bos.writeTo(raf);
                    raf.getFD().sync();
                    audit(resource, "modified range " + range.toString());
                }
                else
                {
                    if (resource.getContentType().startsWith("text/html") && !UserManager.mayWriteScript(getUser()))
                    {
                        _ByteArrayOutputStream bos = new _ByteArrayOutputStream(4*1025);
                        FileUtil.copyData(getFileStream().openInputStream(), bos);
                        byte[] buf = bos.toByteArray();
                        String html = new String(buf, "UTF-8");
                        List<String> errors = new ArrayList<String>();
                        List<String> script = new ArrayList<String>();
                        PageFlowUtil.validateHtml(html, errors, script);
                        if (!script.isEmpty())
                            throw new DavException(WebdavStatus.SC_FORBIDDEN, "User is not allowed to save script in html files.");
                        resource.copyFrom(getUser(), new FileStream.ByteArrayFileStream(buf));
                    }
                    else
                    {
                        resource.copyFrom(getUser(), getFileStream());
                    }

                    if (!temp)
                    {
                        if (exists)
                            audit(resource, "replaced");
                        else
                            audit(resource, "created");
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
            }

            lockNullResources.remove(resource.getPath());
            if (exists)
                return WebdavStatus.SC_NO_CONTENT;
            else
                return WebdavStatus.SC_CREATED;
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
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
        String ifHeader = getRequest().getHeader("If");
        if (ifHeader == null)
            ifHeader = "";

        String lockTokenHeader = getRequest().getHeader("Lock-Token");
        if (lockTokenHeader == null)
            lockTokenHeader = "";

        if (isLocked(path, ifHeader + lockTokenHeader))
            throw new DavException(WebdavStatus.SC_LOCKED);

        Resource resource = resolvePath(path);
        boolean exists = resource != null && resource.exists();
        if (!exists)
            return notFound();

        if (!resource.canDelete(getUser()))
            return unauthorized(resource);

        if (!resource.isCollection())
        {
            if (!resource.delete(getUser()))
                throw new DavException(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
            boolean temp = rmTempFile(resource);
            if (!temp)
                audit(resource, "deleted");
            return WebdavStatus.SC_NO_CONTENT;
        }
        else
        {
            LinkedHashMap<Path,WebdavStatus> errorList = new LinkedHashMap<Path,WebdavStatus>();

            deleteCollection(resource, errorList);
            if (!resource.delete(getUser()))
                errorList.put(resource.getPath(), WebdavStatus.SC_INTERNAL_SERVER_ERROR);
            audit(resource, "deleted");
            if (!errorList.isEmpty())
                return sendReport(errorList);
            return WebdavStatus.SC_NO_CONTENT;
        }
    }


    /**
     * Deletes a collection.
     *
     * @param coll collection to be deleted
     * @param errorList Contains the list of the errors which occurred
     */
    private void deleteCollection(Resource coll, Map<Path,WebdavStatus> errorList)
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

        List<Resource> children = coll.list();

        for (Resource child : children)
        {
            Path childName = child.getPath();

            if (isLocked(childName, ifHeader + lockTokenHeader))
            {
                errorList.put(childName, WebdavStatus.SC_LOCKED);
            }
            else
            {
                if (!child.canDelete(getUser()))
                {
                    errorList.put(childName, WebdavStatus.SC_FORBIDDEN);
                    continue;
                }
                
                if (child.isCollection())
                    deleteCollection(child, errorList);

                try
                {
                    if (!child.delete(getUser()))
                        errorList.put(childName, WebdavStatus.SC_INTERNAL_SERVER_ERROR);
                }
                catch (IOException x)
                {
                    errorList.put(childName, WebdavStatus.SC_INTERNAL_SERVER_ERROR);
                }
                
                boolean temp = rmTempFile(child);
                if (!temp)
                    audit(child, "deleted");
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
    

    @RequiresPermission(ACL.PERM_NONE)
    public class TraceAction extends DavAction
    {
        public TraceAction()
        {
            super("TRACE");
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
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
            throw new DavException(WebdavStatus.SC_NOT_IMPLEMENTED);
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
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
            return copyResource();
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
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

            Resource dest = resolvePath(destinationPath);
            Resource src = resolvePath();

            if (dest.getPath().equals(src.getPath()))
                throw new DavException(WebdavStatus.SC_FORBIDDEN);

            boolean overwrite = getOverwriteParameter();
            boolean exists = dest.exists();

            if (!src.canRead(getUser()))
                return unauthorized(src);
            if (exists && !dest.canWrite(getUser()) || !exists && !dest.canCreate(getUser()))
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
            if (src.getFile() != null || dest.getFile() != null)
            {
                File tmp = null;
                try
                {
                    if (dest.getFile().exists())
                    {
                        tmp = new File(dest.parent().getFile(), "~rename" + GUID.makeHash() + "~" + dest.getName());
                        markTempFile(tmp);
                        if (!dest.getFile().renameTo(tmp))
                            throw new DavException(WebdavStatus.SC_INTERNAL_SERVER_ERROR, "Could not remove destination: " + dest.getPath());
                    }

                    if (!src.getFile().renameTo(dest.getFile()))
                    {
                        if (null != tmp)
                            tmp.renameTo(dest.getFile());
                        throw new DavException(WebdavStatus.SC_INTERNAL_SERVER_ERROR, "Could not move source:" + src.getPath());
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
                dest.moveFrom(getUser(),src);
            }

            if (rmTempFile(src))
            {
                audit(dest, "created");
            }
            else
            {
                audit(src, null == dest.getFile() ? "deleted" : "deleted: moved to " + dest.getFile().getPath());
                audit(dest, null == src.getFile() ? "created" : "created: moved from " + src.getFile().getPath());
            }

            // Removing any lock-null resource which would be present at
            // the destination path
            lockNullResources.remove(destinationPath);
            
            return exists ? WebdavStatus.SC_OK : WebdavStatus.SC_CREATED;
        }
    }


    boolean isSafeCopy(Resource src, Resource dest)
    {
        // Don't allow creating text/html via rename (circumventing script checking)
        if (src.isFile() && !UserManager.mayWriteScript(getUser()))
        {
            String contentTypeSrc = StringUtils.defaultString(src.getContentType(),"");
            String contentTypeDest = StringUtils.defaultString(dest.getContentType(),"");
            if (contentTypeDest.startsWith("text/html") && !contentTypeDest.equals(contentTypeSrc))
                return false;
        }
        return true;
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class LockAction extends DavAction
    {
        public LockAction()
        {
            super("LOCK");
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class UnlockAction extends DavAction
    {
        public UnlockAction()
        {
            super("UNLOCK");
        }
    }

    
    @RequiresPermission(ACL.PERM_NONE)
    public class OptionsAction extends DavAction
    {
        public OptionsAction()
        {
            super("OPTIONS");
        }

        public WebdavStatus doMethod() throws DavException
        {
            WebdavResponse response = getResponse();
            response.addOptionsHeaders();
            StringBuilder methodsAllowed = determineMethodsAllowed();
            response.setMethodsAllowed(methodsAllowed);
            return WebdavStatus.SC_OK;
        }
    }


    static Path servletPrefix = new Path(WebdavService.getServletPath());
    
    /** allow html listing of this resource */
    private boolean allowHtmlListing(Resource resource)
    {
        return resource.getPath().startsWith(servletPrefix);
    }
    

    private WebdavStatus serveCollection(Resource resource, boolean content)
            throws DavException
    {
        String contentType = resource.getContentType();

        // Skip directory listings if we have been configured to suppress them
        if (!allowHtmlListing(resource))
            return notFound();

        // Set the appropriate output headers
        if (contentType != null)
            getResponse().setContentType(contentType);

        // Serve the directory browser
        if (content)
            return listHtml(resource);

        return WebdavStatus.SC_OK;
    }


    private WebdavStatus serveResource(Resource resource, boolean content)
            throws DavException, IOException
    {
        // If the resource is not a collection, and the resource path ends with "/"
        if (resource.getPath().isDirectory())
            return notFound(getURL());

        // Check if the conditions specified in the optional If headers are satisfied.
        if (!checkIfHeaders(resource))
            return null;

        String contentDisposition = getRequest().getParameter("contentDisposition");
        if ("attachment".equals(contentDisposition) || "inline".equals(contentDisposition))
            getResponse().setContentDisposition(contentDisposition);

        // Find content type
        String contentType = resource.getContentType();

        // Parse range specifier
        List ranges = parseRange(resource);

        // ETag header
        getResponse().setEntityTag(resource.getETag());

        // Last-Modified header
        long modified = resource.getLastModified();
        if (modified != Long.MIN_VALUE)
            getResponse().setLastModified(modified);

        // Get content length
        long contentLength = resource.getContentLength();

        // Special case for zero length files, which would cause a
        // (silent) ISE when setting the output buffer size
        if (contentLength == 0L)
            content = false;

        boolean fullContent = (((ranges == null) || (ranges.isEmpty())) && (getRequest().getHeader("Range") == null)) || (ranges == FULL);
        OutputStream ostream = null;
        Writer writer = null;

        if (content)
        {
            // Trying to retrieve the servlet output stream
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
        }

        if (fullContent)
        {
            // Set the appropriate output headers
            if (contentType != null)
            {
                getResponse().setContentType(contentType);
            }
            if (ostream != null)
                copy(resource.getInputStream(getUser()), ostream);
            else
                copy(resource.getInputStream(getUser()), writer);

        }
        else
        {
            if ((ranges == null) || (ranges.isEmpty()))
                return null;

            // Partial content response.
            getResponse().setStatus(WebdavStatus.SC_PARTIAL_CONTENT);

            if (ranges.size() == 1)
            {

                Range range = (Range) ranges.get(0);
                getResponse().addContentRange(range);
                long length = range.end - range.start + 1;
                getResponse().setContentLength(length);

                if (contentType != null)
                    getResponse().setContentType(contentType);

                if (content)
                    copy(resource.getInputStream(getUser()), ostream, range.start, range.end);
            }
            else
            {
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


    protected void copy(Resource resource, OutputStream ostream, Iterator ranges, String contentType) throws IOException
    {
        while (ranges.hasNext())
        {
            InputStream resourceInputStream = resource.getInputStream(getUser());
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
        stream.write(s.getBytes());
    }


    protected void copy(InputStream istream, Writer writer) throws IOException
    {
        Reader reader = new InputStreamReader(istream);
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
        Resource resource = resolvePath();
        if (resource == null)
            return new StringBuilder();
        return determineMethodsAllowed(resource);
    }


    private StringBuilder determineMethodsAllowed(Resource resource)
    {
        User user = getUser();
        StringBuilder methodsAllowed = new StringBuilder("OPTIONS");

        boolean createResource = resource.canCreate(user);
        boolean createCollection = resource.canCreateCollection(user);

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
            boolean read = resource.canRead(user);
            boolean delete = resource.canDelete(user);

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


    private boolean generateLockDiscovery(Path path, XMLWriter generatedXML)
    {
        boolean wroteStart = false;

        LockInfo resourceLock = (LockInfo) resourceLocks.get(path);
        if (resourceLock != null)
        {
            wroteStart = true;
            generatedXML.writeElement(null, "lockdiscovery", XMLWriter.OPENING);
            resourceLock.toXML(generatedXML);
        }

        for (LockInfo currentLock : collectionLocks)
        {
            if (path.startsWith(currentLock.path))
            {
                if (!wroteStart)
                {
                    wroteStart = true;
                    generatedXML.writeElement(null, "lockdiscovery", XMLWriter.OPENING);
                }
                currentLock.toXML(generatedXML);
            }
        }

        if (wroteStart)
        {
            generatedXML.writeElement(null, "lockdiscovery", XMLWriter.CLOSING);
        }
        else
        {
            return false;
        }

        return true;
    }


    boolean pathStartsWith(String dir, String filePath)
    {
        // check filePath == dir || filepath.startsWith(dir + "/")
        // special case dir == "/"
        if (!filePath.toLowerCase().startsWith(dir.toLowerCase()))
            return false;
        return dir.equals("/") || filePath.length() == dir.length() || filePath.charAt(dir.length()) == '/';
    }


    private String _resourcePathStr = null;


    public void setResourcePath(String path)
    {
        _resourcePathStr = path;
    }


    String getResourcePathStr()
    {
        if (_resourcePathStr == null)
        {
            ActionURL url = getViewContext().getActionURL();
            String path = StringUtils.trimToEmpty(url.getParameter("path"));
            if (path == null || path.length() == 0)
                path = "/";
            if (path.equals(""))
                path = "/";
            _resourcePathStr = path;
        }
        if (!_resourcePathStr.startsWith("/"))
            return null;
        return _resourcePathStr;
    }


    Path getResourcePath()
    {
        String p = getResourcePathStr();
        if (null == p) return null;
        return Path.parse(p).normalize();
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

        String pathInfo = request.getPathInfo();
        if (pathInfo != null)
        {
            String servletPath = request.getServletPath();
            if ((servletPath != null) && (destinationPath.startsWith(servletPath)))
            {
                destinationPath = destinationPath.substring(servletPath.length());
            }
        }

        Path path = Path.parse(destinationPath).normalize();
        log("Dest path: " + path.toString());
        return path;
    }
                                                             

    // UNDONE: normalize path
    Resource resolvePath() throws DavException
    {
        // NOTE: security is enforced via WebFolderInfo, however we expect the container to be a parent of the path
        Container c = getViewContext().getContainer();
        Path path = getResourcePath();
        if (null == path)
            return null;
        // fullPath should start with container path
        Path containerPath = c.getParsedPath();
        if (!path.startsWith(containerPath))
            return null;
        return resolvePath(path);
    }

    // per request cache
    Map<Path, Resource> resourceCache = new HashMap<Path, Resource>();
    Resource nullDavFileInfo = (Resource)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Resource.class}, new InvocationHandler(){public Object invoke(Object proxy, Method method, Object[] args) throws Throwable{return null;}});

    Resource resolvePath(String path) throws DavException
    {
        return resolvePath(Path.parse(path));
    }


    Resource resolvePath(Path path) throws DavException
    {
        Resource resource = resourceCache.get(path);

        if (resource != null)
        {
            if (resource == nullDavFileInfo)
                resource = null;
        }
        else
        {
            resource = getResolver().lookup(path);
            resourceCache.put(path, resource == null ? nullDavFileInfo : resource);
            return resource;
        }

        if (null == resource)
            throw new DavException(WebdavStatus.SC_FORBIDDEN);

        boolean isRoot = path.size() == 0;
        if ( !isRoot && path.isDirectory() && resource.isFile())
            return null;
        return resource;
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
    private boolean checkIfHeaders(Resource resource)
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
    private boolean checkIfMatch(Resource resource)
            throws DavException
    {

        String eTag = resource.getETag();
        String headerValue = getRequest().getHeader("If-Match");
        if (headerValue != null)
        {
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

                // If none of the given ETags match, 412 Precodition failed is
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
    private boolean checkIfModifiedSince(Resource resource)
            throws DavException
    {
        try
        {
            long headerValue = getRequest().getDateHeader("If-Modified-Since");
            long lastModified = resource.getLastModified();
            if (headerValue != -1)                              
            {

                // If an If-None-Match header has been specified, if modified since
                // is ignored.
                if ((getRequest().getHeader("If-None-Match") == null) && (lastModified < headerValue + 1000))
                {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    getResponse().setEntityTag(resource.getETag());
                    getResponse().setStatus(WebdavStatus.SC_NOT_MODIFIED);
                    return false;
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
    private boolean checkIfNoneMatch(Resource resource) throws DavException
    {
        String eTag = resource.getETag();
        String headerValue = getRequest().getHeader("If-None-Match");
        if (headerValue != null)
        {
            boolean conditionSatisfied = false;

            if (!headerValue.equals("*"))
            {

                StringTokenizer commaTokenizer =
                        new StringTokenizer(headerValue, ",");

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
    private boolean checkIfUnmodifiedSince(Resource resource) throws DavException
    {
        try
        {
            long lastModified = resource.getLastModified();
            long headerValue = getRequest().getDateHeader("If-Unmodified-Since");
            if (headerValue != -1)
            {
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


    boolean getOverwriteParameter()
    {
        String overwriteHeader = getRequest().getHeader("Overwrite");
        return overwriteHeader == null || overwriteHeader.equalsIgnoreCase("T");
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
    private List<Range> parseRange(Resource resource) throws DavException
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
            long lastModified = resource.getLastModified();

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
        ArrayList<Range> result = new ArrayList<Range>();
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
        public Resource resource;
        public ActionURL loginURL;
        public Path getDirectory()
        {
            Path path = resource.getPath();
            assert path.startsWith(root);
            return root.relativize(path);
        }
    }

    WebdavStatus listHtml(Resource resource)
    {
        try
        {
            ListPage page = new ListPage();
            page.resource = resource;
            page.loginURL = getLoginURL();
            if (resource.getPath().startsWith(WebdavResolverImpl.get().getRootPath()))
                page.root = WebdavResolverImpl.get().getRootPath();

            JspView<ListPage> v = new JspView<ListPage>(DavController.class, "list.jsp", page);
            v.setFrame(WebPartView.FrameType.NONE);
            PageConfig config = new PageConfig();
            config.setTitle(resource.getPath() + "-- webdav");
            PrintTemplate print = new PrintTemplate(v, config);
            print.render(getViewContext().getRequest(), getViewContext().getResponse());
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


    /**
     * Generate the namespace declarations.
     */
    private String generateNamespaceDeclarations()
    {
        return " xmlns=\"" + DEFAULT_NAMESPACE + "\"";
    }


    /**
     * Check to see if a resource is currently write locked. The method
     * will look at the "If" header to make sure the client
     * has give the appropriate lock tokens.
     *
     * @param req Servlet request
     * @return boolean true if the resource is locked (and no appropriate
     *         lock token has been found for at least one of the non-shared locks which
     *         are present on the resource).
     */
    private boolean isLocked(HttpServletRequest req)
    {
        Path path = getResourcePath();

        String ifHeader = req.getHeader("If");
        if (ifHeader == null)
        {
            ifHeader = "";
        }

        String lockTokenHeader = req.getHeader("Lock-Token");
        if (lockTokenHeader == null)
        {
            lockTokenHeader = "";
        }

        return isLocked(path, ifHeader + lockTokenHeader);
    }


    /**
     * Check to see if a resource is currently write locked.
     *
     * @param path     Path of the resource
     * @param ifHeader "If" HTTP header which was included in the request
     * @return boolean true if the resource is locked (and no appropriate
     *         lock token has been found for at least one of the non-shared locks which
     *         are present on the resource).
     */
    private boolean isLocked(Path path, String ifHeader)
    {
        // Checking resource locks
        LockInfo lock = (LockInfo) resourceLocks.get(path);
        if ((lock != null) && (lock.hasExpired()))
        {
            resourceLocks.remove(path);
        }
        else if (lock != null)
        {
            // At least one of the tokens of the locks must have been given
            Iterator iter = lock.tokens.iterator();
            boolean tokenMatch = false;
            while (iter.hasNext())
            {
                String token = (String) iter.next();
                if (ifHeader.indexOf(token) != -1)
                {
                    tokenMatch = true;
                }
            }

            if (!tokenMatch)
            {
                return true;
            }
        }

        // Checking inheritable collection locks
        Iterator iter = collectionLocks.iterator();
        while (iter.hasNext())
        {
            lock = (LockInfo) iter.next();
            if (lock.hasExpired())
            {
                iter.remove();
            }
            else if (path.startsWith(lock.path))
            {
                Iterator tokenIter = lock.tokens.iterator();
                boolean tokenMatch = false;
                while (tokenIter.hasNext())
                {
                    String token = (String) tokenIter.next();
                    if (ifHeader.indexOf(token) != -1)
                    {
                        tokenMatch = true;
                    }
                }

                if (!tokenMatch)
                {
                    return true;
                }
            }
        }

        return false;
    }


    /**
     * Copy a resource.
     *
     * @return boolean true if the copy is successful
     */
    private WebdavStatus copyResource() throws DavException, IOException
    {
        boolean overwrite = getOverwriteParameter();
        Path destinationPath = getDestinationPath();
        if (destinationPath == null)
            throw new DavException(WebdavStatus.SC_BAD_REQUEST);
        _log.debug("Dest path :" + destinationPath);

        Resource resource = resolvePath();
        if (null == resource || !resource.exists())
           throw new DavException(WebdavStatus.SC_NOT_FOUND);
        if (!resource.canRead(getUser()))
           unauthorized(resource);

        Resource destination = resolvePath(destinationPath);
        if (null == destination)
            throw new DavException(WebdavStatus.SC_FORBIDDEN);
        WebdavStatus successStatus = destination.exists() ? WebdavStatus.SC_NO_CONTENT : WebdavStatus.SC_CREATED;

        if (resource.getFile().getCanonicalPath().equals(destination.getFile().getCanonicalPath())) 
            throw new DavException(WebdavStatus.SC_CONFLICT);
        Resource parent = destination.parent();
        if (parent == null || !parent.exists())
            throw new DavException(WebdavStatus.SC_CONFLICT);
        if (destination.exists() && !destination.canWrite(getUser()))
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
        LinkedHashMap<Path,WebdavStatus> errorList = new LinkedHashMap<Path,WebdavStatus>();
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
    private WebdavStatus copyResource(Resource src, Map<Path,WebdavStatus> errorList, Path destPath) throws DavException
    {
        _log.debug("Copy: " + src.getPath() + " To: " + destPath);

        Resource dest = resolvePath(destPath);
        
        if (src.isCollection())
        {
            if (!dest.getFile().mkdir())
            {
                errorList.put(dest.getPath(), WebdavStatus.SC_CONFLICT);
                return WebdavStatus.SC_CONFLICT;
            }

            try
            {
                List<Resource> children = src.list();
                for (Resource child : children)
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
                    audit(dest, "overwrite: copied from " + src.getFile().getPath());
                else
                    audit(dest, "create: copied from " + src.getFile().getPath());
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


    // UNDONE: move auditing into the Resource
    void audit(Resource resource, String message)
    {
        String dir;
        String name;
        File f = resource.getFile();
        if (f != null)
        {
            dir = f.getParent();
            name = f.getName();
        }
        else
        {
            Resource parent = resource.parent();
            dir = parent == null ? "" : parent.getPath().toString();
            name = resource.getName();
        }
        AuditLogService.get().addEvent(getViewContext(), FileSystemAuditViewFactory.EVENT_TYPE, dir, name, message);
    }


    /**
     * Return JAXP document builder instance.
     */
    private DocumentBuilder getDocumentBuilder()
            throws ServletException
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
            throw new ServletException("Unexpected Error", e);
        }
        return documentBuilder;
    }



    /**
     * Holds a lock information.
     */
    private class LockInfo
    {
        /**
         * Constructor.
         */
        LockInfo()
        {

        }

        Path path = Path.emptyPath;
        String type = "write";
        String scope = "exclusive";
        int depth = 0;
        String owner = "";
        List tokens = new Vector();
        long expiresAt = 0;
        Date creationDate = new Date();

        /**
         * Get a String representation of this lock token.
         */
        public String toString()
        {
            String result = "Type:" + type + "\n";
            result += "Scope:" + scope + "\n";
            result += "Depth:" + depth + "\n";
            result += "Owner:" + owner + "\n";
            result += "Expiration:" + getHttpDateFormat(expiresAt) + "\n";
            for (Object token : tokens)
                result += "Token:" + token + "\n";
            return result;
        }


        /**
         * Return true if the lock has expired.
         */
        boolean hasExpired()
        {
            return (System.currentTimeMillis() > expiresAt);
        }


        /**
         * Return true if the lock is exclusive.
         */
        boolean isExclusive()
        {

            return (scope.equals("exclusive"));

        }


        /**
         * Get an XML representation of this lock token. This method will
         * append an XML fragment to the given XML writer.
         */
         void toXML(XMLWriter generatedXML)
        {

            generatedXML.writeElement(null, "activelock", XMLWriter.OPENING);

            generatedXML.writeElement(null, "locktype", XMLWriter.OPENING);
            generatedXML.writeElement(null, type, XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "locktype", XMLWriter.CLOSING);

            generatedXML.writeElement(null, "lockscope", XMLWriter.OPENING);
            generatedXML.writeElement(null, scope, XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "lockscope", XMLWriter.CLOSING);

            generatedXML.writeElement(null, "depth", XMLWriter.OPENING);
            if (depth == INFINITY)
            {
                generatedXML.writeText("Infinity");
            }
            else
            {
                generatedXML.writeText("0");
            }
            generatedXML.writeElement(null, "depth", XMLWriter.CLOSING);

            generatedXML.writeElement(null, "owner", XMLWriter.OPENING);
            generatedXML.writeText(h(owner));
            generatedXML.writeElement(null, "owner", XMLWriter.CLOSING);

            generatedXML.writeElement(null, "timeout", XMLWriter.OPENING);
            long timeout = (expiresAt - System.currentTimeMillis()) / 1000;
            generatedXML.writeText("Second-" + timeout);
            generatedXML.writeElement(null, "timeout", XMLWriter.CLOSING);

            generatedXML.writeElement(null, "locktoken", XMLWriter.OPENING);

            for (Object token : tokens)
            {
                generatedXML.writeElement(null, "href", XMLWriter.OPENING);
                generatedXML.writeText("opaquelocktoken:" + token);
                generatedXML.writeElement(null, "href", XMLWriter.CLOSING);
            }
            generatedXML.writeElement(null, "locktoken", XMLWriter.CLOSING);

            generatedXML.writeElement(null, "activelock", XMLWriter.CLOSING);
        }
    }


//    private class Property
//    {
//        String name;
//        String value;
//        String namespace;
//        String namespaceAbbrev;
//        int status = WebdavStatus.SC_OK.code;
//    }


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

    
    // UNDONE: what is the scope of these variables???
    private Map lockNullResources = new Hashtable();
    private Map resourceLocks = new Hashtable();
    private List<LockInfo> collectionLocks = new Vector<LockInfo>();
             


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
        //300=Multiple Choices
        SC_MOVED_PERMANENTLY(HttpServletResponse.SC_MOVED_PERMANENTLY, "Moved Permanently"),
        SC_MOVED_TEMPORARILY(HttpServletResponse.SC_MOVED_TEMPORARILY, "Moved Temporarily"),    // Found
        //303=See Other
        SC_NOT_MODIFIED(HttpServletResponse.SC_NOT_MODIFIED, "Not Modified"),
        //305=Use Proxy
        //307=Temporary Redirect
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
        protected Resource resource;

        DavException(WebdavStatus status)
        {
            this.status = status;
        }

        DavException(WebdavStatus status, String message)
        {
            this.status = status;
            this.message = message;
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

        public Resource getResource()
        {
            return resource;
        }
    }

    class UnauthorizedException extends DavException
    {
        UnauthorizedException(Resource resource)
        {
            super(WebdavStatus.SC_UNAUTHORIZED);
            this.resource = resource;
        }
    }

    WebdavStatus unauthorized(Resource resource) throws DavException
    {
        throw new UnauthorizedException(resource);
    }

    WebdavStatus notFound() throws DavException
    {
        return notFound(getResourcePath());
    }

    WebdavStatus notFound(Path path) throws DavException
    {
        throw new DavException(WebdavStatus.SC_NOT_FOUND, path.toString());
    }
    
    WebdavStatus notFound(String path) throws DavException
    {
        throw new DavException(WebdavStatus.SC_NOT_FOUND, path);
    }

    private void checkReadOnly() throws DavException
    {
        if (_readOnly)
            throw new DavException(WebdavStatus.SC_FORBIDDEN);
    }

    private void checkLocked() throws DavException
    {
        if (isLocked(getRequest()))
            throw new DavException(WebdavStatus.SC_LOCKED);
    }


    Map<Closeable,Throwable> closables = new IdentityHashMap<Closeable,Throwable>();

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


    static Set<String> _tempFiles = Collections.synchronizedSet(new TreeSet<String>());
    static Set<Path> _tempResources = Collections.synchronizedSet(new HashSet<Path>());

    private void markTempFile(Resource r)
    {
        _tempResources.add(r.getPath());
        markTempFile(r.getFile());
    }

    public static void markTempFile(File f)
    {
        if (null != f)
            _tempFiles.add(f.getPath());
    }
    
    public static boolean rmTempFile(Resource r)
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

    private boolean isTempFile(Resource r)
    {
        return _tempResources.contains(r.getPath());
    }

    public static ShutdownListener getShutdownListener()
    {
        return new ShutdownListener()
        {
            public void shutdownStarted(ServletContextEvent servletContextEvent)
            {
                for (Object path : _tempFiles.toArray())
                    new File((String)path).delete();
            }
        };
    }
}
