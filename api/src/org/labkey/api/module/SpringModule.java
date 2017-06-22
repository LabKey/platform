/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.api.module;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.URIUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.support.XmlWebApplicationContext;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: matthewb
 * Date: May 24, 2007
 * Time: 9:00:37 AM
 *
 * SpringModule knows how to load spring application context information (applicationContext.xml etc)
 */
public abstract class SpringModule extends DefaultModule
{                              
    private static final Logger _log = Logger.getLogger(SpringModule.class);

    /**
     * The name of the init parameter on the <code>ServletContext</code> specifying
     * the path where Spring configuration files may be found.
     */
    public static final String INIT_PARAMETER_CONFIG_PATH = "org.labkey.api.pipeline.config";

    /**
     * Types of Spring context supported by a <code>SpringModule</code>
     * <ul>
     *  <li>none - no context associated with this module</li>
     *  <li>context - context XML describing beans inside module only</li>
     *  <li>config - context may be overridden by bean XML on the config path</li>
     * </ul>
     */
    public enum ContextType { none, context, config }


    public SpringModule()
    {
        _moduleServletContext = createModuleServletContext();
    }


    @Override
    public Controller getController(@Nullable HttpServletRequest request, Class controllerClass)
    {
        try
        {
            // try spring configuration first
            Controller con = (Controller)getBean(controllerClass);
            if (null == con)
            {
                con = (Controller)controllerClass.newInstance();
                if (con instanceof ApplicationContextAware)
                    ((ApplicationContextAware)con).setApplicationContext(getApplicationContext());
            }
            return con;
        }
        catch (IllegalAccessException | InstantiationException x)
        {
            throw new RuntimeException(x);
        }
    }


    /** Do not override this method, instead implement startupAfterSpringConfig(), which will be invoked for you */
    @Override
    public final void doStartup(ModuleContext moduleContext)
    {
        initWebApplicationContext();
        startupAfterSpringConfig(moduleContext);
    }

    
    /** Invoked after Spring has been configured as part of the startup() method */
    protected abstract void startupAfterSpringConfig(ModuleContext moduleContext);

    /**
     * A module may define a pipeline configuration file in the deployed webapp at /WEB-INF/[lower_case_module_name]Context.xml.
     * Context may be overridden outside the the module after installation, by specifying a path to
     * the configuration files in the <code>ServletContext</code> parameter
     * <code>INIT_PARAMETER_CONFIG_PATH</code>.
     * <p/>
     * Module context files must be placed in the source tree:<br/>
     * /WEB-INF/[lower_case_module_name]/[lower_case_module_name]Context.xml.
     * <p/>
     * Post-installation config files must be placed in:<br/>
     * [CONFIG_PATH]/[lower_case_module_name]/[lower_case_module_name]Config.xml
     * <p/>
     * By specifying beans in the config XML of the same ID as those in the
     * module's context XML, Spring will use the external versions over those
     * in the module.  The pipeline also uses a registration model to allow
     * elements to be overridden by TaskId.
     *
     * @return path to the context XML file within the webapp, if present, or null if no file is present
     */
    protected String getContextXMLFilePath()
    {
        String potentialPath = "/WEB-INF/" + getName().toLowerCase() + "Context.xml";

        // Look for a context file
        try (InputStream is = ModuleLoader.getServletContext().getResourceAsStream(potentialPath))
        {
            if (is != null && is.read() != -1)
            {
                return potentialPath;
            }
        }
        catch (IOException e) { /* Just return */ }

        return null;
    }


    // see contextConfigLocation parameter
    protected List<String> getContextConfigLocation()
    {
        String contextXMLFilePath = getContextXMLFilePath();
        if (contextXMLFilePath == null)
            return Collections.emptyList();

        String prefix = getName().toLowerCase();

        List<String> result = new ArrayList<>();
        // Add the location of the context XML inside the module
        result.add(contextXMLFilePath);

        // Look for post-installation config outside the module
        File dirConfig = getSpringConfigDir(getModuleServletContext().getInitParameter(INIT_PARAMETER_CONFIG_PATH));

        File fileConfig = new File(dirConfig, prefix + "Config.xml");
        if (fileConfig.isFile())
        {
            result.add(fileConfig.toString());
        }

        return result;
    }

    public static File getSpringConfigDir(String configPath)
    {
        File dirConfig;
        // First check for an explicit pointer to some other location
        if (configPath != null)
        {
            dirConfig = new File(configPath);
        }
        else
        {
            // Fall back on a directory that's a peer to the webapp directory
            dirConfig = new File(ModuleLoader.getInstance().getWebappDir().getParent(), "config");
        }
        return dirConfig;
    }


    protected void initWebApplicationContext()
    {
        final List<String> contextConfigFiles = getContextConfigLocation();
        if (!contextConfigFiles.isEmpty())
        {
            ApplicationContext parentApplicationContext = getParentApplicationContext();

            _log.info("Loading Spring configuration for the " + getName() + " module from " + contextConfigFiles);

            try
            {
                XmlWebApplicationContext xml = new XmlWebApplicationContext()
                {
                    @Override
                    protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) throws IOException
                    {
                        beanFactory.registerSingleton("module", SpringModule.this);
                        super.loadBeanDefinitions(beanFactory);
                    }
                };
                xml.setParent(parentApplicationContext);
                xml.setConfigLocations(contextConfigFiles.toArray(new String[contextConfigFiles.size()]));
                xml.setServletContext(getModuleServletContext());
                xml.setDisplayName(getName() + " WebApplicationContext");
                xml.refresh();
                _applicationContext = xml;
            }
            catch (Exception x)
            {
                _log.error("Failed to load spring application context for module: " + getName(), x);
                ModuleLoader.getInstance().addModuleFailure(getName(), x);
            }
        }
    }


    // See FrameworkServlet
    // CONSIDER: have SpringModule implement getServlet(), and use that to dispatch requests
    // that could remove some spring config code from SpringActionController

    protected ServletContext getParentServletContext()
    {
        return ModuleLoader.getServletContext();
    }

    ServletContext _moduleServletContext = null;
    
    protected ServletContext createModuleServletContext()
    {
        return new ModuleServletContextWrapper(getParentServletContext());
    }

    protected ServletContext getModuleServletContext()
    {
        return _moduleServletContext;
    }

    public String getInitParameter(String s)
    {
        return getModuleServletContext().getInitParameter(s);
    }


    //
    // ServletContext (for per module web applications)
    //

    public static class ModuleServletContextWrapper implements ServletContext
    {
        HashMap<String, Object> _attributes = new HashMap<>();
        HashMap<String, String> _initParameters = new HashMap<>();

        final ServletContext _wrapped;

        ModuleServletContextWrapper(ServletContext wrapped)
        {
            _wrapped = wrapped;    
        }

        public ServletContext getContext(String string)
        {
            return null;
        }

        public int getMajorVersion()
        {
            return 0;
        }

        public int getMinorVersion()
        {
            return 0;
        }

        public String getMimeType(String string)
        {
            return "text/html";
        }

        public Set<String> getResourcePaths(String string)
        {
            return _wrapped.getResourcePaths(string);
        }

        public URL getResource(String string) throws MalformedURLException
        {
            return _wrapped.getResource(string);
        }

        public InputStream getResourceAsStream(String string)
        {
            InputStream is = _wrapped.getResourceAsStream(string);
            if (is == null)
            {
                // If the path starts with the config root, try creating
                // a raw FileInputStream for it.
                File configRoot = getSpringConfigDir(getInitParameter(INIT_PARAMETER_CONFIG_PATH));
                File configFile = new File(string);
                if (!URIUtil.isDescendant(configRoot.toURI(), configFile.toURI()))
                    return null;

                try
                {
                    is = new FileInputStream(configFile);
                }
                catch (FileNotFoundException e)
                {
                    _log.debug("Could not find config override " + string);
                }
            }

            return is;
        }

        public RequestDispatcher getRequestDispatcher(String string)
        {
            return _wrapped.getRequestDispatcher(string);
        }

        public RequestDispatcher getNamedDispatcher(String string)
        {
            return _wrapped.getNamedDispatcher(string);
        }

        public Servlet getServlet(String string) throws ServletException
        {
            return _wrapped.getServlet(string);
        }

        public Enumeration<Servlet> getServlets()
        {
            return _wrapped.getServlets();
        }

        public Enumeration<String> getServletNames()
        {
            return _wrapped.getServletNames();
        }

        public void log(String string)
        {
            _log.info(string);
        }

        public void log(Exception exception, String string)
        {
            _log.error(string, exception);
        }

        public void log(String string, Throwable throwable)
        {
            _log.error(string, throwable);
        }

        public String getRealPath(String string)
        {
            return _wrapped.getRealPath(string);
        }

        public String getServerInfo()
        {
            return _wrapped.getServerInfo();
        }

        public String getInitParameter(String string)
        {
            String param = _initParameters.get(string);
            if (param == null)
                param = _wrapped.getInitParameter(string);
            return param;
        }

        public Enumeration<String> getInitParameterNames()
        {
            return null;
        }

        public Object getAttribute(String string)
        {
            return _attributes.get(string);
        }

        public Enumeration<String> getAttributeNames()
        {
            return null;
        }

        public void setAttribute(String string, Object object)
        {
            _attributes.put(string, object);
        }

        public void removeAttribute(String string)
        {
            _attributes.remove(string);
        }

        public String getServletContextName()
        {
            return null;
        }

        public String getContextPath()
        {
            return AppProps.getInstance().getContextPath();
        }

        @Override
        public int getEffectiveMajorVersion()
        {
            return 0;
        }

        @Override
        public int getEffectiveMinorVersion()
        {
            return 0;
        }

        @Override
        public boolean setInitParameter(String s, String s1)
        {
            return false;
        }

        @Override
        public ServletRegistration.Dynamic addServlet(String s, String s1)
        {
            return null;
        }

        @Override
        public ServletRegistration.Dynamic addServlet(String s, Servlet servlet)
        {
            return null;
        }

        @Override
        public ServletRegistration.Dynamic addServlet(String s, Class<? extends Servlet> aClass)
        {
            return null;
        }

        @Override
        public <T extends Servlet> T createServlet(Class<T> aClass) throws ServletException
        {
            return null;
        }

        @Override
        public ServletRegistration getServletRegistration(String s)
        {
            return null;
        }

        @Override
        public Map<String, ? extends ServletRegistration> getServletRegistrations()
        {
            return null;
        }

        @Override
        public FilterRegistration.Dynamic addFilter(String s, String s1)
        {
            return null;
        }

        @Override
        public FilterRegistration.Dynamic addFilter(String s, Filter filter)
        {
            return null;
        }

        @Override
        public FilterRegistration.Dynamic addFilter(String s, Class<? extends Filter> aClass)
        {
            return null;
        }

        @Override
        public <T extends Filter> T createFilter(Class<T> aClass) throws ServletException
        {
            return null;
        }

        @Override
        public FilterRegistration getFilterRegistration(String s)
        {
            return null;
        }

        @Override
        public Map<String, ? extends FilterRegistration> getFilterRegistrations()
        {
            return null;
        }

        @Override
        public SessionCookieConfig getSessionCookieConfig()
        {
            return null;
        }

        @Override
        public void setSessionTrackingModes(Set<SessionTrackingMode> set) throws IllegalStateException, IllegalArgumentException
        {

        }

        @Override
        public Set<SessionTrackingMode> getDefaultSessionTrackingModes()
        {
            return null;
        }

        @Override
        public Set<SessionTrackingMode> getEffectiveSessionTrackingModes()
        {
            return null;
        }

        @Override
        public void addListener(String s)
        {

        }

        @Override
        public <T extends EventListener> void addListener(T t)
        {

        }

        @Override
        public void addListener(Class<? extends EventListener> aClass)
        {

        }

        @Override
        public <T extends EventListener> T createListener(Class<T> aClass) throws ServletException
        {
            return null;
        }

        @Override
        public void declareRoles(String... strings)
        {

        }

        @Override
        public ClassLoader getClassLoader()
        {
            return null;
        }

        @Override
        public JspConfigDescriptor getJspConfigDescriptor()
        {
            return null;
        }
    }


    /** Spring BeanFactory likes strings, I don't like strings, so use this as a go-between */
    public <T> T getBean(Class<T> cls)
    {
        try
        {
            BeanFactory bf = getApplicationContext();
            if (null == bf)
                return null;
            String name = cls.getSimpleName();
            name = name.substring(0, 1).toLowerCase() + name.substring(1);
            Object o = bf.getBean(name, cls);
            return (T)o;
        }
        catch (NoSuchBeanDefinitionException x)
        {
            return null;
        }
        catch (RuntimeException x)
        {
            _log.error("Error loading object for class " + cls.getName(), x);
            throw x;
        }
    }


    ApplicationContext _parentApplicationContext = null;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException
    {
        super.setApplicationContext(applicationContext);    //To change body of overridden methods use File | Settings | File Templates.
        _parentApplicationContext = applicationContext;
    }

    private ApplicationContext getParentApplicationContext()
    {
        // if we were created by an applicationContext, use that as our parent
        if (null == _parentApplicationContext)
            _parentApplicationContext = ServiceRegistry.get().getApplicationContext();
        return _parentApplicationContext;
    }
}