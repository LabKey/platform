/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
package org.labkey.api.action;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.Module;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UrlProviderService
{
    private static final UrlProviderService instance = new UrlProviderService();
    private static final Map<Class, Class<? extends UrlProvider>> _urlProviderToImpl = new HashMap<>();
    private static final Map<Class<? extends UrlProvider>, List<Pair<Module, UrlProvider>>> _urlProviderToOverrideImpls = new HashMap<>();

    public static UrlProviderService getInstance()
    {
        return instance;
    }

    public <P extends UrlProvider> void registerUrlProvider(Class<P> inter, Class innerClass)
    {
        _urlProviderToImpl.put(inter, innerClass);
    }

    /** @return true if the UrlProvider exists. */
    public <P extends UrlProvider> boolean hasUrlProvider(Class<P> inter)
    {
        return _urlProviderToImpl.get(inter) != null;
    }

    @Nullable
    public <P extends UrlProvider> P getUrlProvider(Class<P> inter)
    {
        Class<? extends UrlProvider> clazz = _urlProviderToImpl.get(inter);

        if (clazz == null)
            return null;

        try
        {
            P impl = (P) clazz.newInstance();
            return impl;
        }
        catch (InstantiationException e)
        {
            throw new RuntimeException("Failed to instantiate provider class " + clazz.getName() + " for " + inter.getName(), e);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException("Illegal access of provider class " + clazz.getName() + " for " + inter.getName(), e);
        }
    }

    /**
     * Register an implementation class to use for overrides to a URLProvider interface.
     * @param inter the URLProvider interface
     * @param impl the override URLProvider implementation class
     * @param module the module providing the override
     */
    public void registerUrlProviderOverride(Class<? extends UrlProvider> inter, UrlProvider impl, Module module)
    {
        List<Pair<Module, UrlProvider>> impls = new ArrayList<>();
        if (_urlProviderToOverrideImpls.containsKey(inter))
            impls = _urlProviderToOverrideImpls.get(inter);

        impls.add(new Pair<>(module, impl));
        _urlProviderToOverrideImpls.put(inter, impls);
    }

    public List<Pair<Module, UrlProvider>> getUrlProviderOverrides(Class<? extends UrlProvider> inter)
    {
        return _urlProviderToOverrideImpls.get(inter);
    }
}
