/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.files.FileSystemDirectoryListener;

/**
 * User: adam
 * Date: 1/10/14
 * Time: 9:53 PM
 */
public interface ModuleResourceCacheHandler<T>
{
    boolean isResourceFile(String filename);
    String getResourceName(Module module, String filename);
    String createCacheKey(Module module, String resourceName);
    CacheLoader<String, T> getResourceLoader();

    /**
     * If needed, return a FileSystemDirectoryListener that implements resource-specific change handling. The standard
     * listener clears the resources and resource names from the cache (as appropriate), if isResourceFile() returns
     * true. It will then invoke the corresponding method of the chained listener.
     *
     * @param module Module for which to create the listener
     * @return A directory listener with implementation specific handling. Return null for default behavior.
     */
    @Nullable FileSystemDirectoryListener createChainedDirectoryListener(Module module);
}
