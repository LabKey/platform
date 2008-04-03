package org.labkey.api.view;

import org.labkey.api.util.AppProps;
import org.labkey.api.module.ModuleLoader;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.*;

public class JavaScriptFilter implements Filter
{
    private static boolean _cachingAllowed = AppProps.getInstance().isCachingAllowed();

    public void destroy()
    {
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
    {
        if (!_cachingAllowed && null != AppProps.getInstance().getProjectRoot())
        {
            HttpServletRequest request = (HttpServletRequest)servletRequest;
            File file = ModuleLoader.searchModuleSourceForFile(AppProps.getInstance().getProjectRoot(), "/webapp" + request.getServletPath());

            if (null != file)
            {
                byte[] buf = new byte[4096];
                InputStream is = new FileInputStream(file);
                OutputStream os = servletResponse.getOutputStream();

                for(int len; (len=is.read(buf))!=-1; )
                    os.write(buf,0,len);

                os.close();
                is.close();

                return;
            }
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    public void init(FilterConfig filterConfig) throws ServletException
    {
    }
}
