package org.labkey.api.jsp;

import org.labkey.api.view.GroovyView;
import org.labkey.api.view.ViewContext;
import org.labkey.common.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.AppProps;
import org.apache.log4j.Logger;

import javax.servlet.jsp.HttpJspPage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import java.util.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.io.File;

public class JspLoader
{
    private static Logger _log = Logger.getLogger(JspLoader.class);
    private static final String JSP_PACKAGE = "org.labkey.jsp.compiled";
    private static final String JSP_CLASSLOADER_KEY = JspLoader.class.getName() + ".CLASSLOADER";
    static public final boolean CHECK_RELOAD = AppProps.getInstance().isDevMode();

    static class JspClassLoader
    {
        transient ClassLoader _loader;
        Collection<Map.Entry<File, Long>> _jarFiles;
        long lastCheck;

        public Class loadClass(ServletContext context, String className) throws ClassNotFoundException
        {
            ClassLoader loader = initLoader(context);
            return loader.loadClass(className);
        }

        synchronized private ClassLoader initLoader(ServletContext context)
        {
            checkReload(context);
            if (_loader == null)
            {
                Set<String> paths = context.getResourcePaths("/WEB-INF/jsp/");
                _jarFiles = new ArrayList();
                List<URL> urls = new ArrayList();
                for (String path : paths)
                {
                    File file = new File(context.getRealPath(path));
                    try
                    {
                        urls.add(file.toURL());
                        _jarFiles.add(new Pair(file, file.lastModified()));
                    }
                    catch (MalformedURLException mURLe)
                    {
                        _log.error("initLoader exception", mURLe);
                    }
                }
                _loader = new URLClassLoader(urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
            }
            return _loader;
        }

        synchronized private void checkReload(ServletContext context)
        {
            if (!CHECK_RELOAD)
                return;
            if (_loader == null || _jarFiles == null)
                return;
            for (Map.Entry<File, Long> entry : _jarFiles)
            {
                long current = entry.getKey().lastModified();
                if (current > entry.getValue())
                {
                    _loader = null;
                    return;
                }
            }
        }
    }


    static String getJspClassName(ServletContext context, String packageName, String jspFile)
    {
        StringBuilder ret = new StringBuilder(JSP_PACKAGE);
        if (packageName != null)
        {
            ret.append("." + packageName + ".");
        }
        else
        {
            if (!jspFile.startsWith("/"))
                throw new IllegalArgumentException("Path must start with '/' if no package defined.");
        }
        ret.append(jspFile.replaceAll("\\.", "_").replaceAll("/", "\\."));
        return ret.toString();
    }

    static JspClassLoader getClassLoader(ServletContext context, String classname)
    {
        JspClassLoader loader = (JspClassLoader) context.getAttribute(JSP_CLASSLOADER_KEY);
        if (loader == null)
        {
            synchronized (JspLoader.class)
            {
                loader = new JspClassLoader();
                context.setAttribute(JSP_CLASSLOADER_KEY, loader);
            }
        }
        return loader;
    }

    /**
     * Create a new JSP page.
     *
     * @param request Used to get the ServletContext
     * @param packageName Dot separated package where the JSP file was in the source tree.  May be null, in which case
     * "jspFile" should be a full path to the page, starting with "/"
     * @param jspFile Path to the JSP from the package in which it resides.  For JSP files that are in the same
     * directory as their controller, pass the package name of the controller, and the filename of the JSP with no
     * "/" 
     * @return inited page
     */
    static public HttpJspPage createPage(HttpServletRequest request, String packageName, String jspFile)
    {
        try
        {
            ServletContext context = request.getSession().getServletContext();
            String className = getJspClassName(context, packageName, jspFile);
            JspClassLoader loader = getClassLoader(context, className);
            Class clazz = loader.loadClass(context, className);
            HttpJspPage ret = (HttpJspPage) clazz.newInstance();
            ret.init(new JspServletConfig(context));
            return ret;
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    /**
     * Instantiates a JSP class in a particular directory.
     *
     * @param clazz   A class which is in the same folder as the JSP folder.
     * @param jspFile Name of the JSP file, without the path.
     */
    static public HttpJspPage createPage(HttpServletRequest request, Class clazz, String jspFile)
    {
        return createPage(request, clazz.getPackage().getName(), jspFile);
    }

    static public HttpJspPage createPage(ViewContext context, Class clazz, String jspFile)
    {
        return createPage(context.getRequest(), clazz.getPackage().getName(), jspFile);
    }

    static private class JspServletConfig implements ServletConfig
    {
        ServletContext _context;

        public JspServletConfig(ServletContext context)
        {
            _context = context;
        }

        public String getServletName()
        {
            return "jsp";
        }

        public ServletContext getServletContext()
        {
            return _context;
        }

        public String getInitParameter(String name)
        {
            return null;
        }

        public Enumeration getInitParameterNames()
        {
            return null;
        }
    }

}
