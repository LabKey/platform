/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.api.services;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.ModuleLoader;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AbstractRefreshableWebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/*
* User: Dave
* Date: Nov 19, 2008
* Time: 10:50:17 AM
*/

/**
 * Provides a central registry for service interface implementations.
 * Modules that supply cross-module services should register their service
 * instances at startup by calling {@link #registerService}.
 * Other modules can then request that service at
 * runtime by calling {@link #getService(Class)}, specifying the
 * class of the service interface.
 */
public class ServiceRegistry
{
    private static class _ServiceDef
    {
        <T> _ServiceDef(Class<T> c, T i)
        {
            cls = c;
            longName = c.getName(); // full name with $ for inner classes
            String name = longName.substring(longName.lastIndexOf('.')+1);
            if (name.endsWith("$Service"))
                name = name.substring(0,name.length()-"$Service".length());
            else if (name.endsWith("$I"))
                name = name.substring(0,name.length()-"$I".length());
            shortName = name.substring(0,1).toLowerCase() + name.substring(1);
            instance = i;
            assert -1 == shortName.indexOf('.');
            assert -1 != longName.indexOf('.');
        }
        final String shortName;
        final String longName;
        final Class cls;
        final Object instance;
    }
    
    private static final ServiceRegistry _instance = new ServiceRegistry();
    private final ConcurrentMap<Class, _ServiceDef> _servicesByClass = new ConcurrentHashMap<>();

    static {ServiceRegistry._instance.registerService(ServiceRegistry.class, _instance);}

    /**
     * Returns the single instance of the ServiceRegistry
     * @return The single instance of the ServiceRegistry
     */
    @NotNull
    public static ServiceRegistry get()
    {
        return _instance;
    }

    /**
     * Returns a service implementation for a given service interface.
     * Note that if no implementation has been registered for the supplied
     * interface, null will be returned. Callers should check the return
     * value and handle null appropriately. Services may not be available if
     * modules have been excluded from the distribution.
     * @param type The Service interface class (MyService.class)
     * @return An implementation of the service interface, or null if no implementation has been registered
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T getService(Class<T> type)
    {
        _ServiceDef s = _servicesByClass.get(type);
        return null==s ? null : (T)s.instance;
    }

    public <T> boolean hasService(Class<T> type)
    {
        return getService(type) != null;
    }


    /** Returns a service implementation for a given service interface. */
    public static <T> T get(Class<T> type)
    {
        return get().getService(type);
    }


    /** Check a service has been registered. */
    public static <T> boolean has(Class<T> type)
    {
        return get().hasService(type);
    }


    /**
     * Registers a service implementation. Modules that expose services should call this method
     * at load time, passing the service interface class and the implementation instance.
     * @param type The service interface class (MyService.class)
     * @param instance An instance of a class that implements that service.
     */
    public <T> void registerService(@NotNull Class<T> type, @NotNull T instance)
    {
        //warn about double-registration
        assert null == _servicesByClass.get(type) : "A service instance for type " + type.toString() + " is already registered!";

        _ServiceDef s = new _ServiceDef(type, instance);
        _servicesByClass.put(s.cls, s);
        ((ConfigurableListableBeanFactory)getApplicationContext().getAutowireCapableBeanFactory()).registerSingleton(s.longName, s.instance);
        ((ConfigurableListableBeanFactory)getApplicationContext().getAutowireCapableBeanFactory()).registerSingleton(s.shortName, s.instance);
    }


    /**
     * Unregisters a service.
     * @param type The service interface type.
     */
    public void unregisterService(@NotNull Class type)
    {
        _ServiceDef sd = _servicesByClass.get(type);
        if (null != sd)
        {
            _servicesByClass.remove(sd.cls);
            // UNDONE: removeSingleton()?
        }
    }


    public ApplicationContext getApplicationContext()
    {
        return applicationContext;
    }


    ApplicationContext applicationContext = createWebApplicationContext();


    ApplicationContext createWebApplicationContext()
    {
        WebApplicationContext wac = null;

        if (null != ModuleLoader.getInstance() && null != ModuleLoader.getServletContext())
        {
            wac = WebApplicationContextUtils.getWebApplicationContext(ModuleLoader.getServletContext());

            // use global if we can, but there's no guarantee that it uses a ConfigurableListableBeanFactory
            if (wac.getAutowireCapableBeanFactory() instanceof ConfigurableListableBeanFactory)
            {
                return wac;
            }
        }

        AbstractRefreshableWebApplicationContext ac = new AbstractRefreshableWebApplicationContext()
        {
            @Override
            protected void loadBeanDefinitions(DefaultListableBeanFactory config) throws IOException, BeansException
            {
                for (_ServiceDef sd : _servicesByClass.values())
                {
                    config.registerSingleton(sd.shortName, sd.instance);
                    config.registerSingleton(sd.longName, sd.instance);
                }
            }
        };
        if (null != wac)
            ac.setParent(wac);
        ac.refresh();
        return ac;
    }
}
