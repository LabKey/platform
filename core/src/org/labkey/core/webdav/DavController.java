package org.labkey.core.webdav;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.log4j.Category;
import org.apache.log4j.Logger;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.template.PageConfig;
import org.labkey.core.webdav.apache.XMLWriter;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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
    public static final String mimeSeparation = "_dav_" + GUID.makeHash() + "_separator_";

    static Category _log = Logger.getInstance(DavController.class);
    static DefaultActionResolver _actionResolver = new DefaultActionResolver(DavController.class);
    static boolean _readOnly = false;
    static boolean _listings = true;
    static boolean _locking = false;
    static boolean _requiresLogin = true;

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


    String getLoginURL()
    {
        ActionURL redirect = AuthenticationManager.getLoginURL(getURL());
        return redirect.getLocalURIString();
    }


    public DavController()
    {
        super();
        setActionResolver(_actionResolver);
    }


    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws MultipartException
    {
        _webdavresponse = new WebdavResponse(response);
        _webdavresolver = WebdavResolverImpl.get(); 
        Controller action = resolveAction(request.getMethod().toLowerCase());
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

        WebdavStatus sendError(WebdavStatus status, String message)
        {
            assert !_sendError;
            try
            {
                response.sendError(status.code, message);
                _status = status;
                _sendError = false;
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
            if (!_log.isDebugEnabled())
                return response.getWriter();
            final Writer responseWriter = response.getWriter();
            return new Writer()
            {
                public void write(char cbuf[], int off, int len) throws IOException
                {
                    responseWriter.write(cbuf,off,len);
                    sbLogResponse.append(cbuf,off,len);
                }

                public void flush() throws IOException
                {
                    responseWriter.flush();
                }

                public void close() throws IOException
                {
                    responseWriter.close();
                }
            };
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
            _log.debug(request.getMethod() + " " + getResourcePath());
            assertMethod();

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
                    throw new DavException(ex);
                }
            }
            catch (UnauthorizedException uex)
            {
                WebdavResolver.Resource resource = uex.getResource();
                if (!getUser().isGuest())
                {
                    getResponse().sendError(WebdavStatus.SC_FORBIDDEN, resource.getPath());
                }
                else if (resource.isCollection() && isBrowser() && "GET".equals(method))
                {
                    getResponse().setStatus(WebdavStatus.SC_MOVED_PERMANENTLY);
                    response.setHeader("Location", getLoginURL());
                }
                else
                {
                    getResponse().setRealm(AppProps.getInstance().getSystemDescription());
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
            _log.debug("" + (status != null ? status.code : 0) + ": " + message);

            return null;
        }

        WebdavStatus doMethod() throws DavException, IOException
        {
            WebdavResolver.Resource resource = resolvePath();
            if (null != resource)
            {
                StringBuffer methodsAllowed = determineMethodsAllowed(resource);
                getResponse().setMethodsAllowed(methodsAllowed);
            }
            return WebdavStatus.SC_METHOD_NOT_ALLOWED;
        }

        void assertMethod()
        {
            if (!method.equals(getRequest().getMethod()))
                throw new IllegalArgumentException("Expected method " + method);
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
            WebdavResolver.Resource resource = resolvePath();
            if (null == resource)
                return notFound();
            if (!resource.canRead(getUser()))
                return unauthorized(resource);
            if (!resource.exists())
                return notFound(resource.getPath());

            if (resource.isCollection())
                return serveCollection(resource, true);
            else
                return serveResource(resource, true);
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class PostAction extends GetAction
    {
        public PostAction()
        {
            super("POST");
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class PropfindAction extends DavAction
    {
        public PropfindAction()
        {
            super("PROPFIND");
        }
        
        public WebdavStatus doMethod() throws DavException, IOException
        {
            WebdavResolver.Resource resource = resolvePath();
            if (resource == null || !resource.exists())
                return notFound();
            if (!resource.canRead(getUser()))
                return unauthorized(resource);

            List<String> properties = null;
            Find type = Find.FIND_ALL_PROP;
            int depth = getDepthParameter();

            Node propNode = null;

            ReadAheadBufferedInputStream is = new ReadAheadBufferedInputStream(getRequest().getInputStream());
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
                        }
                    }
                }
                catch (Exception e)
                {
                    throw new DavException(WebdavStatus.SC_BAD_REQUEST);
                }
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
                            // href is a live property which is handled differently
                            properties.add(propertyName);
                            break;
                    }
                }
            }

            getResponse().setStatus(WebdavStatus.SC_MULTI_STATUS);
            getResponse().setContentType("text/xml; charset=UTF-8");

            // Create multistatus object
            XMLWriter generatedXML = new XMLWriter(getResponse().getWriter());
            generatedXML.writeXMLHeader();
            generatedXML.writeElement(null, "multistatus"+ generateNamespaceDeclarations(), XMLWriter.OPENING);

            if (depth == 0)
            {
                parseProperties(generatedXML, resource, type, properties);
            }
            else
            {
                // The stack always contains the object of the current level
                Stack<String> stack = new Stack<String>();
                stack.push(resource.getPath());

                // Stack of the objects one level below
                Stack<String> stackBelow = new Stack<String>();

                while ((!stack.isEmpty()) && (depth >= 0))
                {
                    String currentPath = stack.pop();

                    resource = resolvePath(currentPath);
                    if (null == resource || !resource.canRead(getUser()))
                        continue;
                    
                    parseProperties(generatedXML, resource, type, properties);

                    if (resource.isCollection() && depth > 0)
                    {
                        List<String> listPaths = resource.listNames();
                        for (String listPath : listPaths)
                        {
                            String newPath = currentPath;
                            if (!(newPath.endsWith("/")))
                                newPath += "/";
                            newPath += listPath;
                            stackBelow.push(newPath);
                        }

                        // Displaying the lock-null resources present in that
                        // collection
                        String lockPath = currentPath;
                        if (lockPath.endsWith("/"))
                            lockPath = lockPath.substring(0, lockPath.length() - 1);
                        List currentLockNullResources = (List) lockNullResources.get(lockPath);
                        if (currentLockNullResources != null)
                        {
                            for (Object currentLockNullResource : currentLockNullResources)
                            {
                                String lockNullPath = (String) currentLockNullResource;
                                parseLockNullProperties(generatedXML, lockNullPath, type, properties);
                            }
                        }
                    }

                    if (stack.isEmpty())
                    {
                        depth--;
                        stack = stackBelow;
                        stackBelow = new Stack<String>();
                    }

                    generatedXML.sendData();
                }
            }

            generatedXML.writeElement(null, "multistatus", XMLWriter.CLOSING);
            generatedXML.sendData();
            return WebdavStatus.SC_MULTI_STATUS;
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

            String path = getResourcePath();

            WebdavResolver.Resource resource = resolvePath();
            if (null == resource)
                return notFound();
            boolean exists = resource.exists();

            // Can't create a collection if a resource already exists at the given path
            if (exists)
            {
                // Get allowed methods
                StringBuffer methodsAllowed = determineMethodsAllowed(resource);
                getResponse().setMethodsAllowed(methodsAllowed);
                throw new DavException(WebdavStatus.SC_METHOD_NOT_ALLOWED);
            }

            BufferedInputStream is = new ReadAheadBufferedInputStream(getRequest().getInputStream());
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
                    throw new DavException(WebdavStatus.SC_NOT_IMPLEMENTED);
                }
                catch (SAXException saxe)
                {
                    // Parse error - assume invalid content
                    throw new DavException(WebdavStatus.SC_UNSUPPORTED_MEDIA_TYPE);
                }
            }

            if (!resource.canCreate(getUser()))
                return unauthorized(resource);

            boolean result = resource.getFile() != null && resource.getFile().mkdir();

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
        public PutAction()
        {
            super("PUT");
        }

        WebdavStatus doMethod() throws DavException, IOException
        {
            checkReadOnly();
            checkLocked();

            WebdavResolver.Resource resource = resolvePath();
            if (resource == null)
                return notFound();
            boolean exists = resource.exists();

            if (exists && !resource.canWrite(getUser()) || !exists && !resource.canCreate(getUser()))
                return unauthorized(resource);

            Range range = parseContentRange();
            InputStream resourceInputStream = null;
            RandomAccessFile resourceOutputData = null;

            try
            {
                resourceInputStream = getRequest().getInputStream();
                resource.getFile().createNewFile();
                resourceOutputData = new RandomAccessFile(resource.getFile(),"rw");

                // Append data specified in ranges to existing content for this
                // resource - create a temp. file on the local filesystem to
                // perform this operation
                // Assume just one range is specified for now
                if (range != null)
                {
                    if (range.start > resourceOutputData.length())
                        throw new DavException(WebdavStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    resourceOutputData.seek(range.start);
                }
                else
                {
                }

                if (range != null)
                    copyData(resourceInputStream, resourceOutputData, range.end-range.start);
                else
                    copyData(resourceInputStream, resourceOutputData);
            }
            finally
            {
                try
                {
                    if (null != resourceInputStream)
                        resourceInputStream.close();
                }
                catch (Exception e)
                {
                    log("DavController.PutAction: couldn't close InputStream", e);
                }
                try
                {
                    if (null != resourceOutputData)
                        resourceOutputData.close();
                }
                catch (Exception e)
                {
                    log("DavController.PutAction: couldn't close OutputStream", e);
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
    private WebdavStatus deleteResource(String path) throws DavException, IOException
    {
        String ifHeader = getRequest().getHeader("If");
        if (ifHeader == null)
            ifHeader = "";

        String lockTokenHeader = getRequest().getHeader("Lock-Token");
        if (lockTokenHeader == null)
            lockTokenHeader = "";

        if (isLocked(path, ifHeader + lockTokenHeader))
            throw new DavException(WebdavStatus.SC_LOCKED);

        WebdavResolver.Resource resource = resolvePath(path);
        boolean exists = resource != null && resource.exists();
        if (!exists)
            return notFound();

        if (!resource.canDelete(getUser()))
            return unauthorized(resource);

        if (!resource.isCollection())
        {
            if (!resource.getFile().delete())
                throw new DavException(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
            return WebdavStatus.SC_NO_CONTENT;
        }
        else
        {
            Hashtable<String,WebdavStatus> errorList = new Hashtable<String,WebdavStatus>();

            deleteCollection(resource, errorList);
            if (!resource.getFile().delete())
                errorList.put(resource.getPath(), WebdavStatus.SC_INTERNAL_SERVER_ERROR);
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
    private void deleteCollection(WebdavResolver.Resource coll, Hashtable<String,WebdavStatus> errorList)
    {
        HttpServletRequest request = getRequest();
        String path = coll.getPath();

        _log.debug("Delete:" + path);

        String ifHeader = request.getHeader("If");
        if (ifHeader == null)
            ifHeader = "";

        String lockTokenHeader = request.getHeader("Lock-Token");
        if (lockTokenHeader == null)
            lockTokenHeader = "";

        List<WebdavResolver.Resource> children = coll.list();

        for (WebdavResolver.Resource child : children)
        {
            String childName = child.getPath();

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

                if (!child.getFile().delete())
                    errorList.put(childName, WebdavStatus.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    /**
     * Send a multistatus element containing a complete error report to the client.
     *
     * @param errors The errors to be displayed
     */
    private WebdavStatus sendReport(Map<String,WebdavStatus> errors) throws IOException
    {
        WebdavResponse response = getResponse();
        response.setStatus(WebdavStatus.SC_MULTI_STATUS);

        String absoluteUri = getRequest().getRequestURI();
        String relativePath = getResourcePath();

        XMLWriter generatedXML = new XMLWriter();
        generatedXML.writeXMLHeader();

        generatedXML.writeElement(null, "multistatus" + generateNamespaceDeclarations(), XMLWriter.OPENING);

        for (String errorPath : errors.keySet())
        {
            WebdavStatus status = errors.get(errorPath);
            generatedXML.writeElement(null, "response", XMLWriter.OPENING);
            generatedXML.writeElement(null, "href", XMLWriter.OPENING);
            String toAppend = errorPath.substring(relativePath.length());
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
        writer.close();
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

            String destinationPath = getDestinationPath();

            if (destinationPath == null)
                throw new DavException(WebdavStatus.SC_BAD_REQUEST);

            WebdavResolver.Resource dest = resolvePath(destinationPath);
            WebdavResolver.Resource src = resolvePath();

            if (dest.getFile().equals(src.getFile()))
                throw new DavException(WebdavStatus.SC_FORBIDDEN);

            boolean overwrite = getOverwriteParameter();
            boolean exists = dest.exists();

            if (!src.canRead(getUser()))
                return unauthorized(src);
            if (exists && !dest.canWrite(getUser()) || !exists && !dest.canCreate(getUser()))
                return unauthorized(dest);

            if (destinationPath.endsWith("/") && src.isFile())
            {
                return WebdavStatus.SC_NO_CONTENT;
            }

            if (exists)
            {
                if (overwrite)
                {
                    WebdavStatus ret = deleteResource(destinationPath);
                    if (ret != WebdavStatus.SC_NO_CONTENT)
                        return ret;
                }
                else
                {
                    throw new DavException(WebdavStatus.SC_PRECONDITION_FAILED);
                }
            }

            if (!src.getFile().renameTo(dest.getFile()))
                throw new DavException(WebdavStatus.SC_INTERNAL_SERVER_ERROR);

            // Removing any lock-null resource which would be present at
            // the destination path
            lockNullResources.remove(destinationPath);
            
            return exists ? WebdavStatus.SC_OK : WebdavStatus.SC_CREATED;
        }
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
            StringBuffer methodsAllowed = determineMethodsAllowed();
            response.setMethodsAllowed(methodsAllowed);
            return WebdavStatus.SC_OK;
        }
    }


    private WebdavStatus serveCollection(WebdavResolver.Resource resource, boolean content)
            throws DavException
    {
        String contentType = resource.getContentType();

        // Skip directory listings if we have been configured to suppress them
        if (!_listings)
            return notFound();

        // Set the appropriate output headers
        if (contentType != null)
            getResponse().setContentType(contentType);

        // Serve the directory browser
        if (content)
            return listHtml(resource);

        return WebdavStatus.SC_OK;
    }


    private WebdavStatus serveResource(WebdavResolver.Resource resource, boolean content)
            throws DavException, IOException
    {
        // If the resource is not a collection, and the resource path ends with "/" 
        if (resource.getPath().endsWith("/"))
            return notFound(getURL());

        // Check if the conditions specified in the optional If headers are satisfied.
        if (!checkIfHeaders(resource))
            return null;

        // Find content type
        String contentType = resource.getContentType();

        // Parse range specifier
        List ranges = parseRange(resource);

        // ETag header
        getResponse().setEntityTag(resource.getETag());

        // Last-Modified header
        getResponse().setLastModified(resource.getLastModified());

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
                copy(resource.getInputStream(), ostream);
            else
                copy(resource.getInputStream(), writer);

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
                    copy(resource.getInputStream(), ostream, range.start, range.end);
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
            close(istream);
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
            close(istream);
        }
    }


    protected void copy(WebdavResolver.Resource resource, OutputStream ostream, Iterator ranges, String contentType) throws IOException
    {
        while (ranges.hasNext())
        {
            InputStream resourceInputStream = resource.getInputStream();
            InputStream istream = new BufferedInputStream(resourceInputStream, 16*1024);
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
        try
        {
            Reader reader = new InputStreamReader(istream);
            char buffer[] = new char[8*1024];
            int len;
            while (-1 < (len = reader.read(buffer)))
                writer.write(buffer, 0, len);
        }
        finally
        {
            close(istream);
        }
    }


    void close(InputStream istream)
    {
        try
        {
            if (null != istream)
                istream.close();
        }
        catch (IOException x)
        {
        }
    }


    private StringBuffer determineMethodsAllowed() throws DavException
    {
        WebdavResolver.Resource resource = resolvePath();
        if (resource == null)
            return new StringBuffer();
        return determineMethodsAllowed(resource);
    }


    private StringBuffer determineMethodsAllowed(WebdavResolver.Resource resource)
    {
        StringBuffer methodsAllowed = new StringBuffer();
        if (!resource.exists())
        {
            methodsAllowed.append("OPTIONS, MKCOL, PUT");
            if (_locking)
                methodsAllowed.append(", LOCK");
        }
        else
        {
            methodsAllowed.append("OPTIONS, GET, HEAD, POST, DELETE, TRACE, COPY, MOVE"); // PROPPATCH
            if (_locking)
                methodsAllowed.append(", LOCK, UNLOCK");
            if (_listings)
                methodsAllowed.append(", PROPFIND");
            if (resource.isCollection())
                methodsAllowed.append(", PUT");
        }
        return methodsAllowed;
    }


    /**
     * Propfind helper method.
     *
     * @param generatedXML     XML response to the Propfind request
     * @param type             Propfind type
     * @param propertiesVector If the propfind type is find properties by
     *                         name, then this Vector contains those properties
     */
    private void parseProperties(XMLWriter generatedXML, WebdavResolver.Resource resource, Find type,
            List<String> propertiesVector)
    {
        generatedXML.writeElement(null, "response", XMLWriter.OPENING);
        String status = "HTTP/1.1 " + WebdavStatus.SC_OK;

        // Generating href element
        generatedXML.writeElement(null, "href", XMLWriter.OPENING);
        generatedXML.writeText(resource.getHref(getViewContext()));
        generatedXML.writeElement(null, "href", XMLWriter.CLOSING);

        String displayName = resource.getName();

        switch (type)
        {
            case FIND_ALL_PROP:

                generatedXML.writeElement(null, "propstat", XMLWriter.OPENING);
                generatedXML.writeElement(null, "prop", XMLWriter.OPENING);

                generatedXML.writeProperty(null, "creationdate", getISOCreationDate(resource.getCreation()));
                if (null == displayName)
                {
                    generatedXML.writeElement(null, "displayname", XMLWriter.NO_CONTENT);
                }
                else
                {
                    generatedXML.writeElement(null, "displayname", XMLWriter.OPENING);
                    generatedXML.writeData(displayName);
                    generatedXML.writeElement(null, "displayname", XMLWriter.CLOSING);
                }
                if (resource.isFile())
                {
                    generatedXML.writeProperty(null, "getlastmodified", getHttpDateFormat(resource.getLastModified()));
                    generatedXML.writeProperty(null, "getcontentlength",String.valueOf(resource.getContentLength()));
                    String contentType = resource.getContentType();
                    if (contentType != null)
                    {
                        generatedXML.writeProperty(null, "getcontenttype", contentType);
                    }
                    generatedXML.writeProperty(null, "getetag", resource.getETag());
                    generatedXML.writeElement(null, "resourcetype", XMLWriter.NO_CONTENT);
                }
                else
                {
                    generatedXML.writeElement(null, "resourcetype", XMLWriter.OPENING);
                    generatedXML.writeElement(null, "collection", XMLWriter.NO_CONTENT);
                    generatedXML.writeElement(null, "resourcetype", XMLWriter.CLOSING);
                }

                generatedXML.writeProperty(null, "source", "");

                String supportedLocks = "<lockentry>"
                        + "<lockscope><exclusive/></lockscope>"
                        + "<locktype><write/></locktype>"
                        + "</lockentry>" + "<lockentry>"
                        + "<lockscope><shared/></lockscope>"
                        + "<locktype><write/></locktype>"
                        + "</lockentry>";
                generatedXML.writeElement(null, "supportedlock", XMLWriter.OPENING);
                generatedXML.writeText(supportedLocks);
                generatedXML.writeElement(null, "supportedlock", XMLWriter.CLOSING);

                generateLockDiscovery(resource.getPath(), generatedXML);

                generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);
                generatedXML.writeElement(null, "status", XMLWriter.OPENING);
                generatedXML.writeText(status);
                generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
                generatedXML.writeElement(null, "propstat", XMLWriter.CLOSING);

                break;

            case FIND_PROPERTY_NAMES:

                generatedXML.writeElement(null, "propstat", XMLWriter.OPENING);
                generatedXML.writeElement(null, "prop", XMLWriter.OPENING);

                generatedXML.writeElement(null, "creationdate", XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "displayname", XMLWriter.NO_CONTENT);
                if (!resource.exists())
                {
                    generatedXML.writeElement(null, "getcontentlanguage", XMLWriter.NO_CONTENT);
                    generatedXML.writeElement(null, "getcontentlength", XMLWriter.NO_CONTENT);
                    generatedXML.writeElement(null, "getcontenttype", XMLWriter.NO_CONTENT);
                    generatedXML.writeElement(null, "getetag", XMLWriter.NO_CONTENT);
                    generatedXML.writeElement(null, "getlastmodified", XMLWriter.NO_CONTENT);
                }
                generatedXML.writeElement(null, "resourcetype", XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "source", XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "lockdiscovery", XMLWriter.NO_CONTENT);

                generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);
                generatedXML.writeElement(null, "status", XMLWriter.OPENING);
                generatedXML.writeText(status);
                generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
                generatedXML.writeElement(null, "propstat", XMLWriter.CLOSING);

                break;

            case FIND_BY_PROPERTY:

                List<String> propertiesNotFound = new Vector<String>();

                // Parse the list of properties

                generatedXML.writeElement(null, "propstat", XMLWriter.OPENING);
                generatedXML.writeElement(null, "prop", XMLWriter.OPENING);

                for (String property : propertiesVector)
                {
                    if (property.equals("creationdate"))
                    {
                        generatedXML.writeProperty(null, "creationdate", getISOCreationDate(resource.getCreation()));
                    }
                    else if (property.equals("displayname"))
                    {
                        if (null == displayName)
                        {
                            generatedXML.writeElement(null, "displayname", XMLWriter.NO_CONTENT);
                        }
                        else
                        {
                            generatedXML.writeElement(null, "displayname", XMLWriter.OPENING);
                            generatedXML.writeData(displayName);
                            generatedXML.writeElement(null, "displayname", XMLWriter.CLOSING);
                        }
                    }
                    else if (property.equals("getcontentlanguage"))
                    {
                        if (resource.exists())
                        {
                            propertiesNotFound.add(property);
                        }
                        else
                        {
                            generatedXML.writeElement(null, "getcontentlanguage", XMLWriter.NO_CONTENT);
                        }
                    }
                    else if (property.equals("getcontentlength"))
                    {
                        if (resource.exists())
                        {
                            propertiesNotFound.add(property);
                        }
                        else
                        {
                            generatedXML.writeProperty(null, "getcontentlength", (String.valueOf(resource.getContentLength())));
                        }
                    }
                    else if (property.equals("getcontenttype"))
                    {
                        if (resource.exists())
                        {
                            propertiesNotFound.add(property);
                        }
                        else
                        {
                            generatedXML.writeProperty(null, "getcontenttype", resource.getContentType());
                        }
                    }
                    else if (property.equals("getetag"))
                    {
                        if (resource.exists())
                        {
                            propertiesNotFound.add(property);
                        }
                        else
                        {
                            generatedXML.writeProperty(null, "getetag", resource.getETag());
                        }
                    }
                    else if (property.equals("getlastmodified"))
                    {
                        if (resource.exists())
                        {
                            propertiesNotFound.add(property);
                        }
                        else
                        {
                            generatedXML.writeProperty(null, "getlastmodified", getHttpDateFormat(resource.getLastModified()));
                        }
                    }
                    else if (property.equals("resourcetype"))
                    {
                        if (resource.exists())
                        {
                            generatedXML.writeElement(null, "resourcetype", XMLWriter.OPENING);
                            generatedXML.writeElement(null, "collection", XMLWriter.NO_CONTENT);
                            generatedXML.writeElement(null, "resourcetype", XMLWriter.CLOSING);
                        }
                        else
                        {
                            generatedXML.writeElement(null, "resourcetype", XMLWriter.NO_CONTENT);
                        }
                    }
                    else if (property.equals("source"))
                    {
                        generatedXML.writeProperty(null, "source", "");
                    }
                    else if (property.equals("supportedlock"))
                    {
                        supportedLocks = "<lockentry>"
                                + "<lockscope><exclusive/></lockscope>"
                                + "<locktype><write/></locktype>"
                                + "</lockentry>" + "<lockentry>"
                                + "<lockscope><shared/></lockscope>"
                                + "<locktype><write/></locktype>"
                                + "</lockentry>";
                        generatedXML.writeElement(null, "supportedlock", XMLWriter.OPENING);
                        generatedXML.writeText(supportedLocks);
                        generatedXML.writeElement(null, "supportedlock", XMLWriter.CLOSING);
                    }
                    else if (property.equals("lockdiscovery"))
                    {
                        if (!generateLockDiscovery(resource.getPath(), generatedXML))
                            propertiesNotFound.add(property);
                    }
                    else
                    {
                        propertiesNotFound.add(property);
                    }

                }

                generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);
                generatedXML.writeElement(null, "status", XMLWriter.OPENING);
                generatedXML.writeText(status);
                generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
                generatedXML.writeElement(null, "propstat", XMLWriter.CLOSING);

                if (propertiesNotFound.size() > 0)
                {
                    status = "HTTP/1.1 " + WebdavStatus.SC_NOT_FOUND;
                    generatedXML.writeElement(null, "propstat", XMLWriter.OPENING);
                    generatedXML.writeElement(null, "prop", XMLWriter.OPENING);

                    for (String property : propertiesNotFound)
                    {
                        generatedXML.writeElement(null, property, XMLWriter.NO_CONTENT);
                    }

                    generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);
                    generatedXML.writeElement(null, "status", XMLWriter.OPENING);
                    generatedXML.writeText(status);
                    generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
                    generatedXML.writeElement(null, "propstat", XMLWriter.CLOSING);

                }

                break;

        }

        generatedXML.writeElement(null, "response", XMLWriter.CLOSING);
    }
    

    /**
     * Propfind helper method. Dispays the properties of a lock-null resource.
     *
     * @param generatedXML     XML response to the Propfind request
     * @param path             Path of the current resource
     * @param type             Propfind type
     * @param propertiesVector If the propfind type is find properties by
     *                         name, then this Vector contains those properties
     */
    private void parseLockNullProperties(
            XMLWriter generatedXML,
            String path, Find type,
            List<String> propertiesVector) throws DavException
    {
        // Exclude any resource in the /WEB-INF and /META-INF subdirectories
        // (the "toUpperCase()" avoids problems on Windows systems)
        if (path.toUpperCase().startsWith("/WEB-INF") || path.toUpperCase().startsWith("/META-INF"))
            return;

        // Retrieving the lock associated with the lock-null resource
        LockInfo lock = (LockInfo) resourceLocks.get(path);
        if (lock == null)
            return;

        WebdavResolver.Resource resource = resolvePath(path);

        generatedXML.writeElement(null, "response", XMLWriter.OPENING);
        String status = "HTTP/1.1 " + WebdavStatus.SC_OK;

        // Generating href element
        generatedXML.writeElement(null, "href", XMLWriter.OPENING);
        generatedXML.writeText(resource.getHref(getViewContext()));
        generatedXML.writeElement(null, "href", XMLWriter.CLOSING);

        switch (type)
        {
            case FIND_ALL_PROP:

                generatedXML.writeElement(null, "propstat", XMLWriter.OPENING);
                generatedXML.writeElement(null, "prop", XMLWriter.OPENING);

                generatedXML.writeProperty(null, "creationdate", getISOCreationDate(lock.creationDate.getTime()));
                generatedXML.writeElement(null, "displayname", XMLWriter.OPENING);
                generatedXML.writeData(resource.getName());
                generatedXML.writeElement(null, "displayname", XMLWriter.CLOSING);
                generatedXML.writeProperty(null, "getlastmodified", getHttpDateFormat(lock.creationDate.getTime()));
                generatedXML.writeProperty(null, "getcontentlength", String.valueOf(0));
                generatedXML.writeProperty(null, "getcontenttype", "");
                generatedXML.writeProperty(null, "getetag", "");
                generatedXML.writeElement(null, "resourcetype", XMLWriter.OPENING);
                generatedXML.writeElement(null, "lock-null", XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "resourcetype",XMLWriter.CLOSING);

                generatedXML.writeProperty(null, "source", "");

                String supportedLocks = "<lockentry>"
                        + "<lockscope><exclusive/></lockscope>"
                        + "<locktype><write/></locktype>"
                        + "</lockentry>" + "<lockentry>"
                        + "<lockscope><shared/></lockscope>"
                        + "<locktype><write/></locktype>"
                        + "</lockentry>";
                generatedXML.writeElement(null, "supportedlock", XMLWriter.OPENING);
                generatedXML.writeText(supportedLocks);
                generatedXML.writeElement(null, "supportedlock", XMLWriter.CLOSING);

                generateLockDiscovery(path, generatedXML);

                generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);
                generatedXML.writeElement(null, "status", XMLWriter.OPENING);
                generatedXML.writeText(status);
                generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
                generatedXML.writeElement(null, "propstat", XMLWriter.CLOSING);

                break;

            case FIND_PROPERTY_NAMES:

                generatedXML.writeElement(null, "propstat", XMLWriter.OPENING);
                generatedXML.writeElement(null, "prop", XMLWriter.OPENING);

                generatedXML.writeElement(null, "creationdate", XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "displayname", XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "getcontentlanguage", XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "getcontentlength", XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "getcontenttype", XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "getetag", XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "getlastmodified", XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "resourcetype", XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "source", XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "lockdiscovery", XMLWriter.NO_CONTENT);

                generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);
                generatedXML.writeElement(null, "status", XMLWriter.OPENING);
                generatedXML.writeText(status);
                generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
                generatedXML.writeElement(null, "propstat", XMLWriter.CLOSING);

                break;

            case FIND_BY_PROPERTY:

                List<String> propertiesNotFound = new Vector<String>();

                // Parse the list of properties

                generatedXML.writeElement(null, "propstat", XMLWriter.OPENING);
                generatedXML.writeElement(null, "prop", XMLWriter.OPENING);

                for (String property : propertiesVector)
                {
                    if (property.equals("creationdate"))
                    {
                        generatedXML.writeProperty(null, "creationdate",getISOCreationDate(lock.creationDate.getTime()));
                    }
                    else if (property.equals("displayname"))
                    {
                        generatedXML.writeElement(null, "displayname", XMLWriter.OPENING);
                        generatedXML.writeData(resource.getName());
                        generatedXML.writeElement(null, "displayname", XMLWriter.CLOSING);
                    }
                    else if (property.equals("getcontentlanguage"))
                    {
                        generatedXML.writeElement(null, "getcontentlanguage", XMLWriter.NO_CONTENT);
                    }
                    else if (property.equals("getcontentlength"))
                    {
                        generatedXML.writeProperty(null, "getcontentlength", (String.valueOf(0)));
                    }
                    else if (property.equals("getcontenttype"))
                    {
                        generatedXML.writeProperty(null, "getcontenttype", "");
                    }
                    else if (property.equals("getetag"))
                    {
                        generatedXML.writeProperty(null, "getetag", "");
                    }
                    else if (property.equals("getlastmodified"))
                    {
                        generatedXML.writeProperty(null, "getlastmodified", getHttpDateFormat(lock.creationDate.getTime()));
                    }
                    else if (property.equals("resourcetype"))
                    {
                        generatedXML.writeElement(null, "resourcetype", XMLWriter.OPENING);
                        generatedXML.writeElement(null, "lock-null", XMLWriter.NO_CONTENT);
                        generatedXML.writeElement(null, "resourcetype", XMLWriter.CLOSING);
                    }
                    else if (property.equals("source"))
                    {
                        generatedXML.writeProperty(null, "source", "");
                    }
                    else if (property.equals("supportedlock"))
                    {
                        supportedLocks = "<lockentry>"
                                + "<lockscope><exclusive/></lockscope>"
                                + "<locktype><write/></locktype>"
                                + "</lockentry>" + "<lockentry>"
                                + "<lockscope><shared/></lockscope>"
                                + "<locktype><write/></locktype>"
                                + "</lockentry>";
                        generatedXML.writeElement(null, "supportedlock", XMLWriter.OPENING);
                        generatedXML.writeText(supportedLocks);
                        generatedXML.writeElement(null, "supportedlock", XMLWriter.CLOSING);
                    }
                    else if (property.equals("lockdiscovery"))
                    {
                        if (!generateLockDiscovery(path, generatedXML))
                            propertiesNotFound.add(property);
                    }
                    else
                    {
                        propertiesNotFound.add(property);
                    }

                }

                generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);
                generatedXML.writeElement(null, "status", XMLWriter.OPENING);
                generatedXML.writeText(status);
                generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
                generatedXML.writeElement(null, "propstat", XMLWriter.CLOSING);

                if (propertiesNotFound.size() > 0)
                {
                    status = "HTTP/1.1 " + WebdavStatus.SC_NOT_FOUND;
                    generatedXML.writeElement(null, "propstat", XMLWriter.OPENING);
                    generatedXML.writeElement(null, "prop", XMLWriter.OPENING);

                    for (String aPropertiesNotFound : propertiesNotFound)
                        generatedXML.writeElement(null, aPropertiesNotFound, XMLWriter.NO_CONTENT);

                    generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);
                    generatedXML.writeElement(null, "status", XMLWriter.OPENING);
                    generatedXML.writeText(status);
                    generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
                    generatedXML.writeElement(null, "propstat", XMLWriter.CLOSING);
                }
                break;
        }

        generatedXML.writeElement(null, "response", XMLWriter.CLOSING);
    }


    private boolean generateLockDiscovery
            (String path, XMLWriter generatedXML)
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


    String getResourcePath()
    {
        ActionURL url = getViewContext().getActionURL();
        //String path = StringUtils.trimToEmpty(().getRequest().getParameter("path"));
        String path = StringUtils.trimToEmpty(url.getParameter("path"));
        if (path == null || path.length() == 0)
            path = "/";
        if (!path.startsWith("/"))
            return null;
        return path;
    }


    String getDestinationPath()
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

        destinationPath = FileUtil.normalize(destinationPath);

        log("Dest path: " + destinationPath);
        return destinationPath;
    }


    // UNDONE: normalize path
    WebdavResolver.Resource resolvePath() throws DavException
    {
        // NOTE: security is enforced via WebFolderInfo, however we expect the  container to be a parent of the path
        Container c = getViewContext().getContainer();
        String path = getResourcePath();
        if (null == path)
            return null;
        // fullPath should start with container path
        if (!pathStartsWith(c.getPath(), path))
            return null;
        return resolvePath(path);
    }


    // per request cache
    Map<String, WebdavResolver.Resource> resourceCache = new HashMap<String, WebdavResolver.Resource>();
    WebdavResolver.Resource nullDavFileInfo = (WebdavResolver.Resource)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{WebdavResolver.Resource.class}, new InvocationHandler(){public Object invoke(Object proxy, Method method, Object[] args) throws Throwable{return null;}});

    WebdavResolver.Resource resolvePath(String fullPath) throws DavException
    {
        WebdavResolver.Resource resource = resourceCache.get(fullPath);
        
        if (resource != null)
        {
            if (resource == nullDavFileInfo)
                resource = null;
        }
        else
        {
            resource = getResolver().lookup(fullPath);
            resourceCache.put(fullPath, resource == null ? nullDavFileInfo : resource);
        }
        if (null == resource)
            throw new DavException(WebdavStatus.SC_FORBIDDEN);
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
    private boolean checkIfHeaders(WebdavResolver.Resource resource)
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
     * @param davInfo
     * @return boolean true if the resource meets the specified condition,
     *         and false if the condition is not satisfied, in which case request
     *         processing is stopped
     */
    private boolean checkIfMatch(WebdavResolver.Resource davInfo)
            throws DavException
    {

        String eTag = davInfo.getETag();
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
    private boolean checkIfModifiedSince(WebdavResolver.Resource resource)
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
    private boolean checkIfNoneMatch(WebdavResolver.Resource resource) throws DavException
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
    private boolean checkIfUnmodifiedSince(WebdavResolver.Resource resource) throws DavException
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


    int getDepthParameter()
    {
        try
        {
            String depthStr = getRequest().getHeader("Depth");
            int depth = null == depthStr || "infinity".equals(depthStr) ? INFINITY : Integer.parseInt(depthStr);
            return depth < 0 ? INFINITY : Math.min(depth,INFINITY);
        }
        catch (NumberFormatException x)
        {
        }
        return INFINITY;
    }


    boolean getOverwriteParameter()
    {
        String overwriteHeader = getRequest().getHeader("Overwrite");
        return overwriteHeader == null || overwriteHeader.equalsIgnoreCase("T");
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
    private List<Range> parseRange(WebdavResolver.Resource resource) throws DavException
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

        long fileLength = resource.getContentLength();

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
        public WebdavResolver.Resource resource;
        public String loginUrl;
    }

    WebdavStatus listHtml(WebdavResolver.Resource resource)
    {
        try
        {
            ListPage page = new ListPage();
            page.resource = resource;
            page.loginUrl = getLoginURL();
            
            HttpView<ListPage> v = new JspView<ListPage>(DavController.class,  "list.jsp", page);
            v.render(getViewContext().getRequest(), getViewContext().getResponse());
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


    private static final FastDateFormat creationDateFormat = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("GMT"));

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
        String path = getResourcePath();

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
    private boolean isLocked(String path, String ifHeader)
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
        String destinationPath = getDestinationPath();
        if (destinationPath == null)
            throw new DavException(WebdavStatus.SC_BAD_REQUEST);

        _log.debug("Dest path :" + destinationPath);

        // Parsing overwrite header
        boolean overwrite = getOverwriteParameter();

        // Overwriting the destination

        WebdavResolver.Resource resource = resolvePath();
        if (null == resource || !resource.exists())
           throw new DavException(WebdavStatus.SC_NOT_FOUND);
        
        WebdavResolver.Resource destination = resolvePath(destinationPath);
        if (null == destination)
            throw new DavException(WebdavStatus.SC_FORBIDDEN);
        boolean exists = destination.exists();

        if (!exists)
        {
            // does parent exist?
            WebdavResolver.Resource parent = destination.parent();
            if (null == parent || !parent.exists())
                throw new DavException(WebdavStatus.SC_CONFLICT);
        }
        else if (overwrite)
        {
            WebdavStatus ret = deleteResource(destinationPath);
            if (ret != WebdavStatus.SC_NO_CONTENT)
                return ret;
        }
        else
        {
            throw new DavException(WebdavStatus.SC_PRECONDITION_FAILED);
        }

        // Copying source to destination
        Hashtable<String,WebdavStatus> errorList = new Hashtable<String,WebdavStatus>();
        WebdavStatus ret = copyResource(resource, errorList, destinationPath);
        boolean result = ret == null;

        if ((!result) || (!errorList.isEmpty()))
        {
            return sendReport(errorList);
        }

        // Removing any lock-null resource which would be present at
        // the destination path
        lockNullResources.remove(destinationPath);

        return exists ? WebdavStatus.SC_NO_CONTENT : WebdavStatus.SC_CREATED;
    }


    /**
     * Copy a collection.
     *
     * @param src resources to be copied
     * @param errorList Hashtable containing the list of errors which occurred
     * during the copy operation
     * @param destPath Destination path
     */
    private WebdavStatus copyResource(WebdavResolver.Resource src, Map<String,WebdavStatus> errorList, String destPath) throws DavException
    {
        _log.debug("Copy: " + src.getPath() + " To: " + destPath);

        WebdavResolver.Resource dest = resolvePath(destPath);
        
        if (src.isCollection())
        {
            if (!dest.getFile().mkdir())
            {
                errorList.put(dest.getPath(), WebdavStatus.SC_CONFLICT);
                return WebdavStatus.SC_CONFLICT;
            }

            try
            {
                List<WebdavResolver.Resource> children = src.list();
                for (WebdavResolver.Resource child : children)
                {
                    String childDest = dest.getPath();
                    if (!childDest.equals("/"))
                        childDest += "/";
                    childDest += child.getName();
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
                FileUtil.copyFile(src.getFile(), dest.getFile());
            }
            catch (IOException ex)
            {
                WebdavResolver.Resource parent = dest.parent();
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

    
    // move to FileUtil
    void copyData(InputStream is, OutputStream os) throws IOException
    {
        byte[] buf = new byte[16*1024];
        int r;
        while (0 <= (r = is.read(buf)))
            os.write(buf,0,r);
    }


    // move to FileUtil
    void copyData(InputStream is, DataOutput os, long len) throws IOException
    {
        byte[] buf = new byte[16*1024];
        long remaining = len;
        do
        {
            int r = (int)Math.min(buf.length, remaining);
            r = is.read(buf, 0, r);
            os.write(buf,0,r);
            remaining -= r;
        } while (0 < remaining);
    }


    // move to FileUtil
    void copyData(InputStream is, DataOutput os) throws IOException
    {
        byte[] buf = new byte[16*1024];
        int r;
        while (0 < (r = is.read(buf)))
            os.write(buf,0,r);
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

        String path = "/";
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
            generatedXML.writeText(owner);
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


    private class ReadAheadBufferedInputStream extends BufferedInputStream
    {
        ReadAheadBufferedInputStream(InputStream is) throws IOException
        {
            super(is);
            assert markSupported();
            byte[] buf = new byte[1024];
            mark(1025);
            int r = read(buf);
            reset();
            assert r <= 0 || available() > 0;
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
        protected WebdavResolver.Resource resource;

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

        public WebdavResolver.Resource getResource()
        {
            return resource;
        }
    }

    class UnauthorizedException extends DavException
    {
        UnauthorizedException(WebdavResolver.Resource resource)
        {
            super(WebdavStatus.SC_UNAUTHORIZED);
            this.resource = resource;
        }
    }

    WebdavStatus unauthorized(WebdavResolver.Resource resource) throws DavException
    {
        throw new UnauthorizedException(resource);
    }

    WebdavStatus notFound() throws DavException
    {
        return notFound(getResourcePath());
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
}


// UNDONE LOCK, UNLOCK
// UNDONE: Windows Explorer (needs dav binding for entire path (e.g. / and /labkey/)
// [x] litmus.basic
// [x] litmus.copymove
// [ ] litmus.props
// [ ] litmus.locks
// [x] litmus.http
// TODO: FileContent module filesets