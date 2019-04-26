package org.labkey.api.jsp;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.jsp.JspApplicationContext;
import javax.servlet.jsp.JspEngineInfo;
import javax.servlet.jsp.JspFactory;
import javax.servlet.jsp.PageContext;

public class LabKeyJspFactory extends JspFactory
{
    private final JspFactory _factory;

    public LabKeyJspFactory(JspFactory factory)
    {
        _factory = factory;
    }

    @Override
    public PageContext getPageContext(Servlet servlet, ServletRequest servletRequest, ServletResponse servletResponse, String s, boolean b, int i, boolean b1)
    {
        PageContext pageContext = _factory.getPageContext(servlet, servletRequest, servletResponse, s, b, i, b1);
        return pageContext;
//        return new LabKeyPageContext(pageContext);
    }

    @Override
    public void releasePageContext(PageContext pageContext)
    {
        _factory.releasePageContext(pageContext);
    }

    @Override
    public JspEngineInfo getEngineInfo()
    {
        return _factory.getEngineInfo();
    }

    @Override
    public JspApplicationContext getJspApplicationContext(ServletContext servletContext)
    {
        return _factory.getJspApplicationContext(servletContext);
    }
}
