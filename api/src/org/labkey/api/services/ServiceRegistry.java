/*
 * Copyright (c) 2008 LabKey Corporation
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
    private static final ServiceRegistry _instance = new ServiceRegistry();
    private ConcurrentMap<Class,Object> _services = new ConcurrentHashMap<Class,Object>();

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
        return (T)(_services.get(type));
    }

    /**
     * Registers a service implementation. Modules that expose services should call this method
     * at load time, passing the service interface class and the implemenation instance.
     * @param type The service interface class (MyService.class)
     * @param instance An insance of a class that implements that service.
     */
    public <T> void registerService(@NotNull Class<T> type, @NotNull T instance)
    {
        //warn about double-registration
        assert null == _services.get(type) : "A service instance for type " + type.toString() + " is already registered!";
        _services.put(type, instance);
    }

    /**
     * Unregisters a service.
     * @param type The service interface type.
     */
    public void unregisterService(@NotNull Class type)
    {
        getServices().remove(type);
    }

    /**
     * Returns the services concurrent map. Override to return a different map implementation.
     * @return The services concurrent map
     */
    @NotNull
    protected ConcurrentMap<Class, Object> getServices()
    {
        return _services;
    }

    /**
     * Private constructor for singleton pattern--use static get() to get an instance.
     */
    private ServiceRegistry()  {}
}