package org.mitre.dsmiley.httpproxy;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.util.PageFlowUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.HttpCookie;

import static org.apache.commons.lang3.StringUtils.replace;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public class LabKeyProxyServlet extends ProxyServlet
{
    private static final Logger LOG = LogManager.getLogger(ProxyServlet.class);

    /** The source URI to proxy from , defaults to /{contextpath}/{servletpath}  */
    protected static final String P_SOURCE_PATH = "sourcePath";
    protected static final String FLEX_REDIRECT_PROTOCOL = "flexRedirectProtocol";

    protected String sourcePath;
    private boolean flexRedirectProtocol;

    protected void initTarget() throws ServletException
    {
        super.initTarget();

        sourcePath = getConfigParam(P_SOURCE_PATH);

        String flexRedirectProtocolString = getConfigParam(FLEX_REDIRECT_PROTOCOL);
        if (flexRedirectProtocolString != null)
        {
            this.flexRedirectProtocol = Boolean.parseBoolean(flexRedirectProtocolString);
        }
    }

    protected String getSourcePath(HttpServletRequest request)
    {
        return StringUtils.defaultString(sourcePath, request.getContextPath() + request.getServletPath());
    }

    private void appendPath(StringBuilder sb, CharSequence path) // LKS override
    {
        appendPath(sb, path, false);
    }

    private StringBuilder appendPath(StringBuilder sb, CharSequence path, boolean trimTrailingSlash) // LKS override
    {
        if (sb.length() < 1 || '/' != sb.charAt(sb.length()-1))
            sb.append('/');
        if (path.length() > 0)
            sb.append(path, '/'==path.charAt(0)?1:0 , path.length());
        // trim trailing slash (unless sb is "/")
        if (trimTrailingSlash && sb.length() > 1 && '/' == sb.charAt(sb.length()-1))
            sb.setLength(sb.length()-1);
        return sb;
    }

    protected String getPathInfo(HttpServletRequest request)
    {
        if (null != sourcePath) // LKS override
        {
            String requestURI = request.getRequestURI();
            if (requestURI.startsWith(sourcePath))
                return requestURI.substring(sourcePath.length() - (sourcePath.endsWith("/")?1:0));
        }
        return request.getPathInfo();
    }

    @Override
    protected boolean skipXForwardedProto()
    {
        return true;
    }

    @Override
    protected String getCookieNamePrefix(String cName)
    {
        String name = StringUtils.replace(getServletName()," ","-");
        // make sure this is a legal cookie name
        return "!Proxy!" + PageFlowUtil.encodeURIComponent(name) + "!";
    }

    @Override
    protected void setCookiePath(Cookie servletCookie, HttpServletRequest servletRequest, HttpCookie cookie)
    {
        String path = getSourcePath(servletRequest);
        String proxyCookiePath = replace(trimToEmpty(cookie.getPath()),"//","/");
        if (StringUtils.startsWith(proxyCookiePath, path))
            servletCookie.setPath( appendPath(new StringBuilder(proxyCookiePath),"",true).toString() );
        else
            servletCookie.setPath( appendPath(new StringBuilder(path),proxyCookiePath,true).toString() );
    }

    @Override
    protected void appendRequestPath(StringBuilder uri, HttpServletRequest servletRequest)
    {
        String pathInfo = getPathInfo(servletRequest); // LKS override
        appendPath(uri, encodeUriQuery(trimToEmpty(pathInfo), true));
    }

    @Override
    protected String rewriteUrlFromResponse(HttpServletRequest servletRequest, String theUrl)  // LKS override
    {
        //TODO document example paths
        String targetUri = getTargetUri(servletRequest);

        LOG.info("theUrl: " + theUrl);
        LOG.info("getTargetUri(servletRequest): " + getTargetUri(servletRequest));
        LOG.info("getTargetHost(servletRequest): " + getTargetHost(servletRequest));
        LOG.info("servletRequest.getRequestURL(): " + servletRequest.getRequestURL());

        if (flexRedirectProtocol && theUrl.startsWith("https://localhost:") && targetUri.startsWith("http://localhost:"))
        {
            // Issue 47596: Make Dockerized version of rstudio server work in LabKey's AWS environment
            // For unknown reason, with AWS ALB, docker instance uses inconsistent http vs https protocol
            theUrl = theUrl.replace("https://localhost:", "http://localhost:");
        }

        if (theUrl.startsWith("/") || theUrl.startsWith(targetUri))
        {
            /*-
             * The URL points back to the back-end server.
             * Instead of returning it verbatim we replace the target path with our
             * source path in a way that should instruct the original client to
             * request the URL pointed through this Proxy.
             * We do this by taking the current request and rewriting the path part
             * using this servlet's absolute path and the path from the returned URL
             * after the base target URL.
             */
            StringBuilder curUrl = new StringBuilder(servletRequest.getRequestURL());//no query
            int pos;
            // Skip the protocol part
            if ((pos = curUrl.indexOf("://")) >= 0)
            {
                // Skip the authority part
                // + 3 to skip the separator between protocol and authority
                if ((pos = curUrl.indexOf("/", pos + 3)) >= 0)
                {
                    // Trim everything after the authority part.
                    curUrl.setLength(pos);
                }
            }

            //Issue 42677: 404 Error when initiating a Jupyter Notebook session from RStudio Pro when integrated with LabKey
            // jupyter notebook redirect url contains context and servlet path, resulting in duplicate path
            String sourcePath = getSourcePath(servletRequest);
            LOG.info("getSourcePath(servletRequest): " + getSourcePath(servletRequest));
            if (!theUrl.startsWith(sourcePath))
                curUrl.append(sourcePath);

            if (theUrl.startsWith("/"))
                appendPath(curUrl, theUrl);
            else
                appendPath(curUrl, theUrl.substring(targetUri.length()));
            theUrl = curUrl.toString();

        }
        return theUrl;
    }

}