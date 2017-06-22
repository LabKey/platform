/*
 * Copyright (c) 2014-2017 LabKey Corporation
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

import org.labkey.api.util.PageFlowUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: adam
 * Date: 1/13/14
 * Time: 6:40 PM
 */
public class ModuleResourceCaches
{
    /**
     * Create a new ModuleResourceCache that finds its resources via one or more ResourceRootProviders.
     *
     * @param description Short description of the cache
     * @param handler ModuleResourceCacheHandler that converts resources into a resource map that can be cached and retrieved
     * @param provider A ResourceRootProvider that defines the layout of these resources in the module
     * @param extraProviders Optional ResourceRootProviders that can provide additional resource roots
     * @param <T> Object type of the resource map that this cache manages
     * @return A new ModuleResourceCache
     */
    public static <T> ModuleResourceCache<T> create(String description, ModuleResourceCacheHandler<T> handler, ResourceRootProvider provider, ResourceRootProvider... extraProviders)
    {
        return new ModuleResourceCache<>(description, handler, provider, extraProviders);
    }


    public static String createCacheKey(Module module, String resourceName)
    {
        // URL encode the parts and concatenate. See #21930.
        return PageFlowUtil.encode(module.getName()) + "/" + PageFlowUtil.encode(resourceName);
    }


    public static class CacheId
    {
        private final String _moduleName;
        private final String _name;

        public CacheId(String module, String name)
        {
            _moduleName = module;
            _name = name;
        }

        public Module getModule()
        {
            return ModuleLoader.getInstance().getModule(_moduleName);
        }

        public String getName()
        {
            return _name;
        }

        public String getModuleName()
        {
            return _moduleName;
        }

        @Override
        public String toString()
        {
            return "{" + _moduleName + "}/" + _name;
        }
    }


    @Deprecated // Standard cache keys use URL encode/decode, but this doesn't  TODO: Switch usages to standard cache key
    public static CacheId parseCacheKey(String cacheKey, Pattern pattern)
    {
        // Parse out the module name and the config name
        Matcher matcher = pattern.matcher(cacheKey);

        if (!matcher.matches() || matcher.groupCount() != 2)
            throw new IllegalStateException("Unrecognized cache key format: " + cacheKey);

        String moduleName = matcher.group(1);
        String filename = matcher.group(2);
        return new CacheId(moduleName, filename);
    }
}
