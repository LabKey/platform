package org.labkey.api.action;

import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleDependencySorter;
import org.labkey.api.util.Pair;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UrlProviderOverrideHandler implements InvocationHandler
{
    private final Class<? extends UrlProvider> _inter;
    private final UrlProvider _impl;

    public UrlProviderOverrideHandler(Class<? extends UrlProvider> inter, UrlProvider impl)
    {
        _inter = inter;
        _impl = impl;
    }

    /**
     * Note that if the overriding implementation is only to be applicable when the given module is enabled in
     * the container being checked, then it is up to that overriding implementation to make that check.
     */
    @Override
    public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
        // check to see if the given interface has any module registered overrides
        List<Pair<Module, UrlProvider>> impls = UrlProviderService.getInstance().getUrlProviderOverrides(_inter);
        if (impls != null)
        {
            // convert the pairs of module/impl to a map from module -> impl and a list of all module dependencies
            Map<Module, UrlProvider> moduleToImpl = new HashMap<>();
            Set<Module> modulesWithDependentModules = new HashSet<>();
            for (Pair<Module, UrlProvider> impl : impls)
            {
                moduleToImpl.put(impl.first, impl.second);
                modulesWithDependentModules.add(impl.first);
                modulesWithDependentModules.addAll(impl.first.getResolvedModuleDependencies());
            }

            // sort the modules in dependency order so that we can iterate through them looking for a non-null result
            // for the given method being invoked
            ModuleDependencySorter sorter = new ModuleDependencySorter();
            List<Module> orderedModules = sorter.sortModulesByDependencies(new ArrayList<>(modulesWithDependentModules));
            Collections.reverse(orderedModules);
            for (Module module : orderedModules)
            {
                if (moduleToImpl.containsKey(module))
                {
                    Object result = m.invoke(moduleToImpl.get(module), args);
                    if (result != null)
                        return result;
                }
            }
        }

        // fall back to invoking the given method on the default URLProvider implementation for the interface
        return m.invoke(_impl, args);
    }
}
