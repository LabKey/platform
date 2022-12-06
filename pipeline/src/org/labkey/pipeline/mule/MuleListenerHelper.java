/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
package org.labkey.pipeline.mule;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.logging.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.module.SpringModule;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.URIUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.pipeline.api.PipelineServiceImpl;
import org.mule.config.ThreadingProfile;
import org.mule.config.builders.MuleXmlBuilderContextListener;
import org.mule.providers.service.TransportFactory;

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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * MuleInitializer class
 * <p/>
 * Created: Sep 28, 2007
 *
 * @author bmaclean
 */
public class MuleListenerHelper implements ServletContext
{
    private static final Logger _log = LogHelper.getLogger(MuleListenerHelper.class, "Initializes and configures Mule for pipelines");

    private final MuleXmlBuilderContextListener _muleContextListener;
    private final ServletContext _parentContext;
    private final HashMap<String, Object> _attributes = new HashMap<>();
    private final HashMap<String, String> _initParameters = new HashMap<>();

    public MuleListenerHelper(ServletContext parentContext)
    {
        _parentContext = parentContext;

        // Default to the version packaged in the pipeline module
        String muleConfigPath = "org/labkey/pipeline/mule/config/webserverMuleConfig.xml";

        // But check if there's a customized version in the pipeline config directory on the server
        File configDir = SpringModule.getSpringConfigDir(_parentContext.getInitParameter(SpringModule.INIT_PARAMETER_CONFIG_PATH));
        if (configDir.isDirectory())
        {
            File muleFile = new File(configDir, "webserverMuleConfig.xml");
            if (muleFile.isFile())
            {
                _log.info("Found Mule configuration file override at " + muleFile.getPath());
                muleConfigPath = muleFile.getName();
            }
        }

        _initParameters.put(MuleXmlBuilderContextListener.INIT_PARAMETER_MULE_CONFIG, muleConfigPath);
        _initParameters.put(MuleXmlBuilderContextListener.INIT_PARAMETER_WEBAPP_CLASSPATH,
                configDir.isDirectory() ? configDir.toString() : null);
        _muleContextListener = new MuleXmlBuilderContextListener();

        // HACK: Fix for MULE-2289
        final Converter conv = ConvertUtils.lookup(Integer.TYPE);
        ConvertUtils.register(new Converter() {
            private final Map<String, Integer> POOL_EXHAUSTED_ACTIONS = new CaseInsensitiveHashMap<>()
            {
                // static initializer
                {
                    Integer value = Integer.valueOf(ThreadingProfile.WHEN_EXHAUSTED_WAIT);
                    this.put("WHEN_EXHAUSTED_WAIT", value);
                    this.put("WAIT", value);

                    value = Integer.valueOf(ThreadingProfile.WHEN_EXHAUSTED_DISCARD);
                    this.put("WHEN_EXHAUSTED_DISCARD", value);
                    this.put("DISCARD", value);

                    value = Integer.valueOf(ThreadingProfile.WHEN_EXHAUSTED_DISCARD_OLDEST);
                    this.put("WHEN_EXHAUSTED_DISCARD_OLDEST", value);
                    this.put("DISCARD_OLDEST", value);

                    value = Integer.valueOf(ThreadingProfile.WHEN_EXHAUSTED_ABORT);
                    this.put("WHEN_EXHAUSTED_ABORT", value);
                    this.put("ABORT", value);

                    value = Integer.valueOf(ThreadingProfile.WHEN_EXHAUSTED_RUN);
                    this.put("WHEN_EXHAUSTED_RUN", value);
                    this.put("RUN", value);
                }
            };

            @Override
            public Object convert(Class clazz, Object obj)
            {
                // MULE-2289: Threading-profile tag always sets poolExhaustedAction to WAIT.
                //            String value for poolExhaustedAction will always pass through
                //            this conversion.  Our registered converter will throw an
                //            exception in this case, so we catch it, and do what Mule
                //            intends but fails to do.
                Integer val = POOL_EXHAUSTED_ACTIONS.get(obj);
                if (val != null)
                {
                    _log.info("Hack for MULE-2289, converting " + obj + " to integer value " + val);
                    return val.intValue();
                }
                return conv.convert(clazz, obj);
            }
        }, Integer.TYPE);

        try
        {
            _muleContextListener.initialize(this);
            PipelineServiceImpl.get().refreshLocalJobs();
        }
        finally
        {
            ConvertUtils.register(conv, Integer.TYPE);
        }
    }
    
    public void contextDestroyed()
    {
        boolean hasJms;
        try
        {
            hasJms = (TransportFactory.getConnectorByProtocol("jms") != null);
        }
        catch (IllegalStateException e)
        {
            hasJms = true;
        }

        _muleContextListener.contextDestroyed(null);
    }

    @Override
    public ServletContext getContext(String string)
    {
        return null;
    }

    @Override
    public int getMajorVersion()
    {
        return 0;
    }

    @Override
    public int getMinorVersion()
    {
        return 0;
    }

    @Override
    public String getMimeType(String string)
    {
        return "text/html";
    }

    @Override
    public Set<String> getResourcePaths(String string)
    {
        return _parentContext.getResourcePaths(string);
    }

    @Override
    public URL getResource(String string) throws MalformedURLException
    {
        return _parentContext.getResource(string);
    }

    @Override
    public InputStream getResourceAsStream(String string)
    {
        InputStream is = _parentContext.getResourceAsStream(string);
        if (is == null)
        {
            // If the path starts with the mule config root, try creating
            // a raw FileInputStream for it.
            String muleConfigPath = getInitParameter(MuleXmlBuilderContextListener.INIT_PARAMETER_WEBAPP_CLASSPATH);
            if (muleConfigPath == null)
                return null;

            File muleConfigRoot = new File(muleConfigPath);
            File muleConfigFile = new File(string);
            if (!URIUtil.isDescendant(muleConfigRoot.toURI(), muleConfigFile.toURI()))
                return null;

            try
            {
                is = new FileInputStream(muleConfigFile);
            }
            catch (FileNotFoundException e)
            {
                _log.debug("Could not find mule config override " + string);
            }
        }

        return is;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String string)
    {
        return _parentContext.getRequestDispatcher(string);
    }

    @Override
    public RequestDispatcher getNamedDispatcher(String string)
    {
        return _parentContext.getNamedDispatcher(string);
    }

    @Override
    public Servlet getServlet(String string) throws ServletException
    {
        return _parentContext.getServlet(string);
    }

    @Override
    public Enumeration<Servlet> getServlets()
    {
        return _parentContext.getServlets();
    }

    @Override
    public Enumeration<String> getServletNames()
    {
        return _parentContext.getServletNames();
    }

    @Override
    public void log(String string)
    {
        _log.info(string);
    }

    @Override
    public void log(Exception exception, String string)
    {
        _log.error(string, exception);
    }

    @Override
    public void log(String string, Throwable throwable)
    {
        _log.error(string, throwable);
    }

    @Override
    public String getRealPath(String string)
    {
        return _parentContext.getRealPath(string);
    }

    @Override
    public String getServerInfo()
    {
        return _parentContext.getServerInfo();
    }

    @Override
    public String getInitParameter(String string)
    {
        String param = _initParameters.get(string);
        if (param == null)
            param = _parentContext.getInitParameter(string);
        return param;
    }

    @Override
    public Enumeration<String> getInitParameterNames()
    {
        return null;
    }

    @Override
    public Object getAttribute(String string)
    {
        return _attributes.get(string);
    }

    @Override
    public Enumeration<String> getAttributeNames()
    {
        return null;
    }

    @Override
    public void setAttribute(String string, Object object)
    {
        _attributes.put(string,object);
    }

    @Override
    public void removeAttribute(String string)
    {
        _attributes.remove(string);
    }

    @Override
    public String getServletContextName()
    {
        return null;
    }

    @Override
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
    public <T extends Servlet> T createServlet(Class<T> aClass)
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
    public <T extends Filter> T createFilter(Class<T> aClass)
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
    public <T extends EventListener> T createListener(Class<T> aClass)
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

    @Override
    public String getVirtualServerName()
    {
        return null;
    }

    String responseCharacterEncoding = "UTF-8";
    String requestCharacterEncoding = "UTF-8";

    @Override
    public void setResponseCharacterEncoding(String s)
    {
        responseCharacterEncoding = s;
    }
    @Override
    public String getResponseCharacterEncoding() {return responseCharacterEncoding; }

    @Override
    public void setRequestCharacterEncoding(String s)
    {
        requestCharacterEncoding = s;
    }
    @Override
    public String getRequestCharacterEncoding() {return requestCharacterEncoding; }

    @Override
    public void setSessionTimeout(int i)
    {
    }
    @Override
    public int getSessionTimeout()
    {
        return 60*60*1000;
    }
    @Override
    public ServletRegistration.Dynamic addJspFile(String a, String b) {return null;}
}
