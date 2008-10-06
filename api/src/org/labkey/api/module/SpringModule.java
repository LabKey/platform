/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import org.labkey.api.action.SpringActionController;
import org.labkey.api.util.URIUtil;
import org.labkey.api.view.WebPartFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.beans.BeansException;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 24, 2007
 * Time: 9:00:37 AM
 *
 * SpringModule knows how to load spring application context information (applicationContext.xml etc)
 */
public class SpringModule extends DefaultModule implements ServletContext
{
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
     *  <li>config - context may be overriden by bean XML on the config path</li>
     * </ul>
     */
    public enum ContextType { none, context, config }
    
    public SpringModule(String name, double version, String resourcePath, boolean shouldRunScripts, WebPartFactory... webParts)
    {
        super(name, version, resourcePath, shouldRunScripts, webParts);
    }

    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);    //To change body of overridden methods use File | Settings | File Templates.

        // each module has its own WebApplicationContext();
        initWebApplicationContext();
    }

    public Controller getController(Class<? extends Controller> controllerClass)
    {
        try
        {
            Controller con = controllerClass.newInstance();
            if (con instanceof SpringActionController)
                ((SpringActionController)con).setWebApplicationContext(getWebApplicationContext());
            return con;
        }
        catch (InstantiationException e1)
        {
        }
        catch (IllegalAccessException e1)
        {
        }
        return null;
    }

    /**
     * Override and return 'context' or 'config' to specify Spring context
     * for this module. If 'config' is specified, context may be overriden
     * outside the the module after installation, by specifying a path to
     * the configuration files in the <code>ServletContext</code> parameter
     * <code>INIT_PARAMETER_CONFIG_PATH</code>.
     * <p/>
     * Module context files must be placed in:<br/>
     * /WEB-INF/&lt;module name>/&lt;module name>Context.xml.
     * <p/>
     * Post-installation config files must be placed in:<br/>
     * &lt;CONFIG_PATH>/&lt;module name>/&lt;module name>Config.xml
     * <p/>
     * By specifying beans in the config XML of the same ID as those in the
     * module's context XML, Spring will use the external versions over those
     * in the module.  The pipeline also uses a registration model to allow
     * elements to be overriden by TaskId.
     *
     * @return context type supported by this module
     */
    protected ContextType getContextType()
    {
        return ContextType.none;
    }


    // see contextCongfigLocation parameter
    protected List<String> getContextConfigLocation()
    {
        if (ContextType.none.equals(getContextType()))
            return Collections.emptyList();

        String prefix = getName().toLowerCase();

        List<String> result = new ArrayList<String>();
        // Add the location of the context XML inside the module
        result.add("/WEB-INF/" + prefix + "/" + prefix + "Context.xml");

        if (ContextType.config.equals(getContextType()))
        {
            // Look for post-installation config outside the module
            String configPath = getInitParameter(INIT_PARAMETER_CONFIG_PATH);
            if (configPath != null)
            {
                File dirConfig = new File(configPath);
                String configRelPath = prefix + "Config.xml";
                File fileConfig = new File(URIUtil.resolve(dirConfig.toURI(), configRelPath));
                if (fileConfig.exists())
                {
                    result.add(fileConfig.toString());
                }
            }
        }

        return result;
    }

    ContextLoader _contextLoader;
    WebApplicationContext _wac;

    void initWebApplicationContext()
    {
        _parentContext = ModuleLoader.getServletContext();
        final WebApplicationContext rootWebApplicationContext = WebApplicationContextUtils.getWebApplicationContext(ModuleLoader.getServletContext());

        final List<String> contextConfigFiles = getContextConfigLocation();
        if (!contextConfigFiles.isEmpty())
        {
            _log.info("Loading Spring configuration for the " + getName() + " module from " + contextConfigFiles);

            try
            {
                _contextLoader = new ContextLoader()
                {
                    @Override
                    protected ApplicationContext loadParentContext(ServletContext servletContext) throws BeansException
                    {
                        return rootWebApplicationContext;
                    }

                    protected void customizeContext(ServletContext servletContext, ConfigurableWebApplicationContext applicationContext)
                    {
                        applicationContext.setConfigLocations(contextConfigFiles.toArray(new String[contextConfigFiles.size()]));
                    }
                };
                _contextLoader.initWebApplicationContext(this);
                _wac = (WebApplicationContext)getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
                ((AbstractApplicationContext)_wac).setDisplayName(getName() + " WebApplicationContext");
            }
            catch (Exception x)
            {
                _log.error("Failed to load spring application context for module: " + getName(), x);
                ModuleLoader.getInstance().setModuleFailure(getName(), x);
            }
        }
    }


    // See FrameworkServlet
    // CONSIDER: have SpringModule implement getServlet(), and use that to dispatch requests
    // that could remove some spring config code from SpringActionController

    public WebApplicationContext getWebApplicationContext()
    {
        return _wac;
    }


    //
    // ServletContext (for per module web applications)
    //

    ServletContext _parentContext = null;
    HashMap<String,Object> _attributes = new HashMap<String,Object>();
    HashMap<String,String> _initParameters = new HashMap<String,String>();
    Logger _log = Logger.getLogger(this.getClass());

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

    public Set getResourcePaths(String string)
    {
        return _parentContext.getResourcePaths(string);
    }

    public URL getResource(String string) throws MalformedURLException
    {
        return _parentContext.getResource(string);
    }

    public InputStream getResourceAsStream(String string)
    {
        InputStream is = _parentContext.getResourceAsStream(string);
        if (is == null)
        {
            // If the path starts with the config root, try creating
            // a raw FileInputStream for it.
            String configPath = getInitParameter(INIT_PARAMETER_CONFIG_PATH);
            if (configPath == null)
                return null;

            File configRoot = new File(configPath);
            File configFile = new File(string);
            if (!URIUtil.isDescendent(configRoot.toURI(), configFile.toURI()))
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
        return _parentContext.getRequestDispatcher(string);
    }

    public RequestDispatcher getNamedDispatcher(String string)
    {
        return _parentContext.getNamedDispatcher(string);
    }

    public Servlet getServlet(String string) throws ServletException
    {
        return _parentContext.getServlet(string);
    }

    public Enumeration getServlets()
    {
        return _parentContext.getServlets();
    }

    public Enumeration getServletNames()
    {
        return _parentContext.getServletNames();
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
        return _parentContext.getRealPath(string);
    }

    public String getServerInfo()
    {
        return _parentContext.getServerInfo();
    }

    public String getInitParameter(String string)
    {
        String param = _initParameters.get(string);
        if (param == null)
            param = _parentContext.getInitParameter(string);
        return param;
    }

    public Enumeration getInitParameterNames()
    {
        return null;
    }

    public Object getAttribute(String string)
    {
        return _attributes.get(string);
    }

    public Enumeration getAttributeNames()
    {
        return null;
    }

    public void setAttribute(String string, Object object)
    {
        _attributes.put(string,object);
    }

    public void removeAttribute(String string)
    {
        _attributes.remove(string);
    }

    public String getServletContextName()
    {
        return null;
    }
}
