/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
import org.labkey.api.util.Path;

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
     * Create a new ModuleResourceCache.
     *
     * @param path Path representing the root of the resource directory
     * @param handler ModuleResourceCacheHandler that customizes this cache's behavior
     * @param description Short description of the cache
     * @param <T> Object type of the resource map that this cache manages
     * @return A ModuleResourceCache
     */
    public static <T> ModuleResourceCache<T> create(Path path, ModuleResourceCacheHandler<T> handler, String description)
    {
        return new ModuleResourceCache<>(path, handler, description);
    }

    public static <T> ModuleResourceCache<T> create(String description, ModuleResourceCacheHandler<T> handler, ResourceRootProvider provider, ResourceRootProvider... extraProviders)
    {
        return new ModuleResourceCache<>(description, handler, provider, extraProviders);
    }

    /**
     * Create a new QueryBasedModuleResourceCache. This is used to cache file-system resources that are associated with
     * specific queries.
     *
     * @param root Path representing the module resource directory
     * @param description Short description of the cache
     * @param handler QueryBasedModuleResourceCacheHandler that customizes this cache's behavior
     * @param <T> Object type that this cache handles
     * @return A QueryBasedModuleResourceCache
     */
    public static <T> QueryBasedModuleResourceCache<T> createQueryBasedCache(Path root, String description, QueryBasedModuleResourceCacheHandler<T> handler)
    {
        return new QueryBasedModuleResourceCache<>(root, description, handler);
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
