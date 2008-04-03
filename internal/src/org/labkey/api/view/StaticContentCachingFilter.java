package org.labkey.api.view;

import org.labkey.api.util.AppProps;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class StaticContentCachingFilter implements Filter
{
    private static boolean _cachingAllowed = AppProps.getInstance().isCachingAllowed();

    public void destroy()
    {
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
    {
        if (_cachingAllowed)
        {
            HttpServletResponse response = (HttpServletResponse)servletResponse;
            response.setDateHeader("Expires", System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 5);
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    public void init(FilterConfig filterConfig) throws ServletException
    {
    }
}
