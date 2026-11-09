package org.mitre.dsmiley.httpproxy;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.Strings;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.util.PageFlowUtil;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.HttpCookie;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public class LabKeyProxyServlet extends ProxyServlet
{
    private static final Logger LOG = LogManager.getLogger(ProxyServlet.class);

    /** The source URI to proxy from , defaults to /{contextpath}/{servletpath}  */
    protected static final String P_SOURCE_PATH = "sourcePath";
    protected static final String FLEX_REDIRECT_PROTOCOL = "flexRedirectProtocol";
    public static final String ALLOW_PROTOCOL_UPGRADE_PARAM_NAME = "LKS-protocolUpgradeEnabled";
    private static final String ATTR_ASSERTED_HEADERS = LabKeyProxyServlet.class.getName() + "$assertedHeaders";

    protected String sourcePath;
    private boolean flexRedirectProtocol;
    private boolean _allowProtocolUpgrade;

    @Override
    protected void initTarget() throws ServletException
    {
        super.initTarget();

        sourcePath = getConfigParam(P_SOURCE_PATH);

        String flexRedirectProtocolString = getConfigParam(FLEX_REDIRECT_PROTOCOL);
        if (flexRedirectProtocolString != null)
        {
            this.flexRedirectProtocol = Boolean.parseBoolean(flexRedirectProtocolString);
        }

        // httpclient5 v5.4 protocolUpgradeEnabled default to true
        // https://github.com/apache/httpcomponents-client/commit/3235f00
        this._allowProtocolUpgrade = readBooleanConfigParam(ALLOW_PROTOCOL_UPGRADE_PARAM_NAME, true);
    }

    /**
     * Assert a header on the caller's behalf: the proxy sets it on the forwarded request rather than passing through
     * whatever the client sent, so any client-supplied copy is stripped first. A null value strips without asserting.
     */
    public static void assertHeader(HttpServletRequest request, String name, @Nullable String value)
    {
        @SuppressWarnings("unchecked")
        Map<String, String> asserted = (Map<String, String>) request.getAttribute(ATTR_ASSERTED_HEADERS);

        if (null == asserted)
        {
            asserted = new CaseInsensitiveHashMap<>();
            request.setAttribute(ATTR_ASSERTED_HEADERS, asserted);
        }

        asserted.put(name, value);
    }

    @Override
    protected void copyRequestHeaders(HttpServletRequest servletRequest, HttpRequest proxyRequest)
    {
        super.copyRequestHeaders(servletRequest, proxyRequest);

        @SuppressWarnings("unchecked")
        Map<String, String> asserted = (Map<String, String>) servletRequest.getAttribute(ATTR_ASSERTED_HEADERS);

        if (null != asserted)
        {
            asserted.forEach((name, value) -> {
                proxyRequest.removeHeaders(name); // case-insensitive, drops every copy the client sent
                if (null != value)
                    proxyRequest.addHeader(name, value);
            });
        }
    }

    protected String getSourcePath(HttpServletRequest request)
    {
        return Objects.toString(sourcePath, request.getContextPath() + request.getServletPath());
    }

    private void appendPath(StringBuilder sb, CharSequence path) // LKS override
    {
        appendPath(sb, path, false);
    }

    private StringBuilder appendPath(StringBuilder sb, CharSequence path, boolean trimTrailingSlash) // LKS override
    {
        if (sb.isEmpty() || '/' != sb.charAt(sb.length()-1))
            sb.append('/');
        if (!path.isEmpty())
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
    protected void copyResponseHeader(HttpServletRequest servletRequest, HttpServletResponse servletResponse, Header header)
    {
        // In Apache HttpClient <5.6.4, these headers were automatically removed after the client transparently
        // decompressed the entity. Now we need to remove them manually, or the stale values reach the browser
        // alongside the already-decompressed body.
        if (doHandleCompression)
        {
            String headerName = header.getName();
            if (headerName.equalsIgnoreCase(HttpHeaders.CONTENT_ENCODING) ||
                    headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH) ||
                    headerName.equalsIgnoreCase(HttpHeaders.CONTENT_MD5))
                return;
        }
        super.copyResponseHeader(servletRequest, servletResponse, header);
    }

    @Override
    protected String getCookieNamePrefix(String cName)
    {
        String name = Strings.CS.replace(getServletName()," ","-");
        // make sure this is a legal cookie name
        return "!Proxy!" + PageFlowUtil.encodeURIComponent(name) + "!";
    }

    @Override
    protected void setCookiePath(Cookie servletCookie, HttpServletRequest servletRequest, HttpCookie cookie)
    {
        String path = getSourcePath(servletRequest);
        String proxyCookiePath = Strings.CS.replace(trimToEmpty(cookie.getPath()),"//","/");
        if (Strings.CS.startsWith(proxyCookiePath, path))
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

        LOG.info("theUrl: {}", theUrl);
        LOG.info("getTargetUri(servletRequest): {}", getTargetUri(servletRequest));
        LOG.info("getTargetHost(servletRequest): {}", getTargetHost(servletRequest));
        LOG.info("servletRequest.getRequestURL(): {}", servletRequest.getRequestURL());

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
            LOG.info("getSourcePath(servletRequest): {}", getSourcePath(servletRequest));
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

    @Override
    protected boolean allowProtocolUpgrade()
    {
        return _allowProtocolUpgrade;
    }


    public static class TestCase extends Assert
    {
        private static final String ASSERTED_HEADER = "X-Proxy-Asserted";

        /** Every header on the forwarded request, so a stray passthrough under another name fails the test. */
        private List<String> forwarded(BasicHttpRequest proxyRequest)
        {
            return Arrays.stream(proxyRequest.getHeaders()).map(h -> h.getName() + "=" + h.getValue()).toList();
        }

        private List<String> forward(@Nullable String clientHeaderName, @Nullable String assertedValue)
        {
            MockHttpServletRequest servletRequest = new MockHttpServletRequest();
            if (null != clientHeaderName)
                servletRequest.addHeader(clientHeaderName, "fromClient");
            if (null != assertedValue)
                assertHeader(servletRequest, ASSERTED_HEADER, assertedValue);

            BasicHttpRequest proxyRequest = new BasicHttpRequest("GET", "/");
            new LabKeyProxyServlet().copyRequestHeaders(servletRequest, proxyRequest);

            return forwarded(proxyRequest);
        }

        @Test
        public void testAssertedHeaderReplacesClientCopy()
        {
            assertEquals(List.of(ASSERTED_HEADER + "=fromProxy"), forward(ASSERTED_HEADER, "fromProxy"));
        }

        @Test
        public void testAssertedHeaderReplacesClientCopyRegardlessOfCase()
        {
            assertEquals(List.of(ASSERTED_HEADER + "=fromProxy"), forward(ASSERTED_HEADER.toLowerCase(), "fromProxy"));
            assertEquals(List.of(ASSERTED_HEADER + "=fromProxy"), forward(ASSERTED_HEADER.toUpperCase(), "fromProxy"));
        }

        @Test
        public void testAssertedHeaderForwardedWhenClientSentNone()
        {
            assertEquals(List.of(ASSERTED_HEADER + "=fromProxy"), forward(null, "fromProxy"));
        }

        @Test
        public void testClientCopyForwardedWhenNothingAsserted()
        {
            assertEquals(List.of(ASSERTED_HEADER + "=fromClient"), forward(ASSERTED_HEADER, null));
        }

        @Test
        public void testNullAssertedValueStripsClientCopy()
        {
            MockHttpServletRequest servletRequest = new MockHttpServletRequest();
            servletRequest.addHeader(ASSERTED_HEADER, "fromClient");
            assertHeader(servletRequest, ASSERTED_HEADER, null);

            BasicHttpRequest proxyRequest = new BasicHttpRequest("GET", "/");
            new LabKeyProxyServlet().copyRequestHeaders(servletRequest, proxyRequest);

            assertEquals(List.of(), forwarded(proxyRequest));
        }

        @Test
        public void testLaterAssertionWinsRegardlessOfCase()
        {
            MockHttpServletRequest servletRequest = new MockHttpServletRequest();
            assertHeader(servletRequest, ASSERTED_HEADER, "first");
            assertHeader(servletRequest, ASSERTED_HEADER.toLowerCase(), "second");

            BasicHttpRequest proxyRequest = new BasicHttpRequest("GET", "/");
            new LabKeyProxyServlet().copyRequestHeaders(servletRequest, proxyRequest);

            assertEquals(1, proxyRequest.getHeaders(ASSERTED_HEADER).length);
            assertEquals("second", proxyRequest.getHeaders(ASSERTED_HEADER)[0].getValue());
        }

        @Test
        public void testOtherHeadersUntouched()
        {
            MockHttpServletRequest servletRequest = new MockHttpServletRequest();
            servletRequest.addHeader("Accept", "text/html");
            servletRequest.addHeader("Accept", "text/plain");
            assertHeader(servletRequest, ASSERTED_HEADER, "fromProxy");

            BasicHttpRequest proxyRequest = new BasicHttpRequest("GET", "/");
            new LabKeyProxyServlet().copyRequestHeaders(servletRequest, proxyRequest);

            assertEquals(List.of("Accept=text/html", "Accept=text/plain", ASSERTED_HEADER + "=fromProxy"), forwarded(proxyRequest));
        }
    }
}
