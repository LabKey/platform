<%
/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
%>
<%@ page import="org.jetbrains.annotations.NotNull" %>
<%@ page import="org.labkey.api.util.StringUtilsLabKey" %>
<%@ page import="org.springframework.mock.web.MockHttpServletResponse" %>
<%@ page import="java.io.ByteArrayOutputStream" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.io.PrintWriter" %>
<%@ page import="java.io.UnsupportedEncodingException" %>
<%@ page import="java.util.Map" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="org.junit.Test" %>
<%@ page import="org.labkey.api.util.TestContext" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="org.labkey.api.view.ViewServlet" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="java.net.URISyntaxException" %>
<%@ page extends="org.labkey.api.jsp.JspTest.BVT" %>
<%--
This tests uses MockRequest to test some expected Headers and Meta tags for various types of requests.
--%>
<%!
    static class _ForwardWrapper extends ViewServlet.ForwardWrapper
    {
        _ForwardWrapper(URLHelper url)
        {
            super(TestContext.get().getRequest(), url);
            // NOTE : we can't intercept the "org.apache.tomcat.sendfile.filename" functionality, so don't use it for these tests
            TestContext.get().getRequest().setAttribute("avoidSendFile", "true");
        }
    };

    static class _MockHeaderResponse extends MockHttpServletResponse
    {
        PrintWriter printWriter = new PrintWriter(new ByteArrayOutputStream(), true, StringUtilsLabKey.DEFAULT_CHARSET)
        {
            @Override
            public void write(@NotNull char @NotNull [] buf, int off, int len)
            {
            }

            @Override
            public void write(int c)
            {
            }

            @Override
            public void write(@NotNull String s, int off, int len)
            {
            }
        };

        ServletOutputStream servletOutputStream = new ServletOutputStream()
        {
            @Override
            public boolean isReady()
            {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener)
            {
            }

            @Override
            public void write(int b) throws IOException
            {
            }
        };

        @Override
        public @NotNull ServletOutputStream getOutputStream()
        {
            return servletOutputStream;
        }

        @Override
        public @NotNull PrintWriter getWriter() throws UnsupportedEncodingException
        {
            return printWriter;
        }
    }

    Map<String, String> getHeaders(String requestUri) throws ServletException, IOException, URISyntaxException
    {
        URLHelper url = new URLHelper(requestUri);
        var req = new _ForwardWrapper(url);
        var res = new _MockHeaderResponse();
        req.getRequestDispatcher(url.getLocalURIString()).forward(TestContext.get().getRequest(), res);
        assertEquals(200, res.getStatus());
        Map<String, String> headers = new TreeMap<>();
        res.getHeaderNames().forEach(h -> headers.put(h, res.getHeader(h)));
        return headers;
    }

    void assertHeader(Map<String, String> headers, String key)
    {
        assertTrue(headers.containsKey(key));
    }

    void assertNoHeader(Map<String, String> headers, String key)
    {
        assertFalse(headers.containsKey(key));
    }

    void assertHeaderValue(Map<String, String> headers, String key, String value)
    {
        assertHeader(headers, key);
        assertEquals(value, headers.get(key));
    }

    void assertHeaderContains(Map<String, String> headers, String key, String... values)
    {
        assertHeader(headers, key);
        for (var v : values)
            assertTrue(headers.get(key).contains(v));
    }

    @Test
    public void testCacheHeaders() throws Exception
    {
        Map<String, String> headers;

        // regular old module file
        headers = getHeaders("/_.gif");
        assertHeader(headers, "ETag");
        assertHeader(headers, "Last-Modified");
        assertNoHeader(headers, "Pragma");
        // Cache-Control and Expires are not set in devMode

        // module file with content hash
        headers = getHeaders("/_.221d8352905f2c38b3cb.gif");   // this file is in webapp just for this test
        assertHeaderContains(headers, "Cache-Control", "public", "max-age=31536000");
        assertHeader(headers, "ETag");
        assertHeader(headers, "Expires");
        assertHeader(headers, "Last-Modified");
        assertNoHeader(headers, "Pragma");

        // dynamic view
        headers = getHeaders("/home/project-begin.view");
        assertHeaderContains(headers, "Cache-Control", "private", "no-cache", "no-store", "max-age=0", "must-revalidate");
        assertNoHeader(headers, "ETag");
        assertHeaderValue(headers, "Expires", "Sun, 01 Jan 2000 00:00:00 GMT");
        assertNoHeader(headers, "Last-Modified");
        assertHeaderValue(headers, "Pragma", "no-cache");
    }

    @Test
    public void testRobotsHeader() throws Exception
    {
        Map<String, String> headers;

        headers = getHeaders("/home/project-begin.view");
        assertNoHeader(headers, "X-Robots-Tag");

        headers = getHeaders("/home/project-begin.view?query.containerFilterName=AllFolders");
        assertHeaderContains(headers, "X-Robots-Tag", "noindex");

        headers = getHeaders("/home/project-begin.view?_print=1");
        assertHeaderContains(headers, "X-Robots-Tag", "noindex");
    }


%>
