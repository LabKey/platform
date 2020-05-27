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

    @Override
    public void initialize(Servlet servlet, ServletRequest servletRequest, ServletResponse servletResponse, String s, boolean b, int i, boolean b1) throws IOException, IllegalStateException, IllegalArgumentException
    {
        _pageContext.initialize(servlet, servletRequest, servletResponse, s, b, i, b1);
    }

    @Override
    public void release()
    {
        _pageContext.release();
    }

    @Override
    public HttpSession getSession()
    {
        return _pageContext.getSession();
    }

    @Override
    public Object getPage()
    {
        return _pageContext.getPage();
    }

    @Override
    public ServletRequest getRequest()
    {
        return _pageContext.getRequest();
    }

    @Override
    public ServletResponse getResponse()
    {
        return _pageContext.getResponse();
    }

    @Override
    public Exception getException()
    {
        return _pageContext.getException();
    }

    @Override
    public ServletConfig getServletConfig()
    {
        return _pageContext.getServletConfig();
    }

    @Override
    public ServletContext getServletContext()
    {
        return _pageContext.getServletContext();
    }

    @Override
    public void forward(String s) throws ServletException, IOException
    {
        _pageContext.forward(s);
    }

    @Override
    public void include(String s) throws ServletException, IOException
    {
        _pageContext.include(s);
    }

    @Override
    public void include(String s, boolean b) throws ServletException, IOException
    {
        _pageContext.include(s, b);
    }

    @Override
    public void handlePageException(Exception e) throws ServletException, IOException
    {
        _pageContext.handlePageException(e);
    }

    @Override
    public void handlePageException(Throwable throwable) throws ServletException, IOException
    {
        _pageContext.handlePageException(throwable);
    }

    @Override
    public void setAttribute(String s, Object o)
    {
        _pageContext.setAttribute(s, o);
    }

    @Override
    public void setAttribute(String s, Object o, int i)
    {
        _pageContext.setAttribute(s, o, i);
    }

    @Override
    public Object getAttribute(String s)
    {
        return _pageContext.getAttribute(s);
    }

    @Override
    public Object getAttribute(String s, int i)
    {
        return _pageContext.getAttribute(s, i);
    }

    @Override
    public Object findAttribute(String s)
    {
        return _pageContext.findAttribute(s);
    }

    @Override
    public void removeAttribute(String s)
    {
        _pageContext.removeAttribute(s);
    }

    @Override
    public void removeAttribute(String s, int i)
    {
        _pageContext.removeAttribute(s, i);
    }

    @Override
    public int getAttributesScope(String s)
    {
        return _pageContext.getAttributesScope(s);
    }

    @Override
    public Enumeration<String> getAttributeNamesInScope(int i)
    {
        return _pageContext.getAttributeNamesInScope(i);
    }

    @Override
    public JspWriter getOut()
    {
        if (null == _out)
            _out = new LabKeyJspWriter(_pageContext.getOut());

        return _out;
    }

    @Override
    public ExpressionEvaluator getExpressionEvaluator()
    {
        return _pageContext.getExpressionEvaluator();
    }

    @Override
    public ELContext getELContext()
    {
        return _pageContext.getELContext();
    }

    @Override
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
