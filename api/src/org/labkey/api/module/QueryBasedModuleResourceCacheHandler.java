/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
import org.labkey.api.util.Path;

import java.util.Collection;

/**
 * Created by adam on 10/24/2015.
 */
public interface QueryBasedModuleResourceCacheHandler<V>
{
    /**
     * Returns true if this file is used in the loading of this resource, either as the primary file or a secondary
     * dependency. This is called on every file notification, to determine if an associated resource should be removed
     * from the cache. Note that isResourceFile() and getResourceName() must be consistent.
     *
     * @param filename A filename to test
     * @return True if the file is used in the creation of this handler's resources, otherwise false.
     */
    boolean isResourceFile(String filename);

    /**
     * Returns a cache loader for this resource that, given a cache key returned by createCacheKey, will load
     * that resource from the file system and transform it into a cacheable object.
     *
     * @return A CacheLoader implementation
     */
    CacheLoader<Path, Collection<V>> getResourceLoader();

    /**
     * If needed, returns a FileSystemDirectoryListener that implements resource-specific file change handling. The
     * standard listener clears the resources and resource names from the cache (as appropriate), if isResourceFile()
     * returns true. It will then invoke the corresponding method of the chained listener.
     *
     * @param module Module for which to create the listener
     * @return A directory listener with implementation specific handling. Return null for default behavior.
     */
    @Nullable
    default FileSystemDirectoryListener createChainedDirectoryListener(Module module)
    {
        return null;
    }
}
