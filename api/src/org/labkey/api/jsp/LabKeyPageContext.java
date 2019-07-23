/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.jsp;

import javax.el.ELContext;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.el.ExpressionEvaluator;
import javax.servlet.jsp.el.VariableResolver;
import javax.servlet.jsp.tagext.BodyContent;
import java.io.IOException;
import java.util.Enumeration;

public class LabKeyPageContext extends PageContext
{
    private final PageContext _pageContext;

    private JspWriter _out = null;

    LabKeyPageContext(PageContext pageContext)
    {
        _pageContext = pageContext;
    }

    public void initialize(Servlet servlet, ServletRequest servletRequest, ServletResponse servletResponse, String s, boolean b, int i, boolean b1) throws IOException, IllegalStateException, IllegalArgumentException
    {
        _pageContext.initialize(servlet, servletRequest, servletResponse, s, b, i, b1);
    }

    public void release()
    {
        _pageContext.release();
    }

    public HttpSession getSession()
    {
        return _pageContext.getSession();
    }

    public Object getPage()
    {
        return _pageContext.getPage();
    }

    public ServletRequest getRequest()
    {
        return _pageContext.getRequest();
    }

    public ServletResponse getResponse()
    {
        return _pageContext.getResponse();
    }

    public Exception getException()
    {
        return _pageContext.getException();
    }

    public ServletConfig getServletConfig()
    {
        return _pageContext.getServletConfig();
    }

    public ServletContext getServletContext()
    {
        return _pageContext.getServletContext();
    }

    public void forward(String s) throws ServletException, IOException
    {
        _pageContext.forward(s);
    }

    public void include(String s) throws ServletException, IOException
    {
        _pageContext.include(s);
    }

    public void include(String s, boolean b) throws ServletException, IOException
    {
        _pageContext.include(s, b);
    }

    public void handlePageException(Exception e) throws ServletException, IOException
    {
        _pageContext.handlePageException(e);
    }

    public void handlePageException(Throwable throwable) throws ServletException, IOException
    {
        _pageContext.handlePageException(throwable);
    }

    public void setAttribute(String s, Object o)
    {
        _pageContext.setAttribute(s, o);
    }

    public void setAttribute(String s, Object o, int i)
    {
        _pageContext.setAttribute(s, o, i);
    }

    public Object getAttribute(String s)
    {
        return _pageContext.getAttribute(s);
    }

    public Object getAttribute(String s, int i)
    {
        return _pageContext.getAttribute(s, i);
    }

    public Object findAttribute(String s)
    {
        return _pageContext.findAttribute(s);
    }

    public void removeAttribute(String s)
    {
        _pageContext.removeAttribute(s);
    }

    public void removeAttribute(String s, int i)
    {
        _pageContext.removeAttribute(s, i);
    }

    public int getAttributesScope(String s)
    {
        return _pageContext.getAttributesScope(s);
    }

    public Enumeration<String> getAttributeNamesInScope(int i)
    {
        return _pageContext.getAttributeNamesInScope(i);
    }

    public JspWriter getOut()
    {
        if (null == _out)
            _out = new LabKeyJspWriter(_pageContext.getOut());

        return _out;
    }

    public ExpressionEvaluator getExpressionEvaluator()
    {
        return _pageContext.getExpressionEvaluator();
    }

    public ELContext getELContext()
    {
        return _pageContext.getELContext();
    }

    public VariableResolver getVariableResolver()
    {
        return _pageContext.getVariableResolver();
    }

    @Override
    public BodyContent pushBody()
    {
        return _pageContext.pushBody();
    }

    @Override
    public JspWriter popBody()
    {
        return _pageContext.popBody();
    }
}
