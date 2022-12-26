package org.mitre.dsmiley.httpproxy;

import org.springframework.web.servlet.mvc.ServletWrappingController;

import javax.servlet.ServletContext;
import java.util.Properties;

public class ProxyServletUtils
{
    public static ServletWrappingController initProxy(ServletContext servletContext, String servletName, Properties properties) throws Exception
    {
        ServletWrappingController proxy = new ServletWrappingController();
        proxy.setServletClass(ProxyServlet.class);
        proxy.setServletName(servletName);
        proxy.setInitParameters(properties);
        proxy.setServletContext(servletContext);
        proxy.afterPropertiesSet();
        return proxy;
    }
}
