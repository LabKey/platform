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

import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.resource.Resource;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * User: adam
 * Date: 1/10/14
 * Time: 9:53 PM
 */
public interface ModuleResourceCacheHandler<V>
{
    /**
     * Converts a stream of resources into a data structure that is cached and retrieved by callers that access the cache
     *
     * @param resources A Stream of all file Resources in the cache's resource root(s)
     * @param module The Module that owns the Resources
     * @return A Map, Collection, or other data structure that gives callers access to those resources
     */
    V load(Stream<? extends Resource> resources, Module module);

    /**
     * If needed, returns a FileSystemDirectoryListener that implements resource-specific file change handling. The
     * standard listener clears the module's resource map and then invokes the appropriate method of the chained listener,
     * if present. Note that the listener will be called for every change in the resource roots and all their parent
     * directories up to /resources. Methods will be invoked for changes to directories, resource files, and non-resource
     * files, so listeners may need to filter entries carefully.
     *
     * @param module Module for which to create the listener
     * @return A directory listener with implementation-specific handling.
     */
    default @Nullable FileSystemDirectoryListener createChainedDirectoryListener(Module module)
    {
        return null;
    }

    // Convenience methods that wrap maps and collections to make them unmodifiable

    default <K2, V2> Map<K2, V2> unmodifiable(Map<K2, V2> map)
    {
        return map.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(map);
    }

    default <V2> Collection<V2> unmodifiable(Collection<V2> collection)
    {
        return collection.isEmpty() ? Collections.emptyList() : Collections.unmodifiableCollection(collection);
    }

    default <K2, V2> MultiValuedMap<K2, V2> unmodifiable(MultiValuedMap<K2, V2> mmap)
    {
        return mmap.isEmpty() ? MultiMapUtils.emptyMultiValuedMap() : MultiMapUtils.unmodifiableMultiValuedMap(mmap);
    }

    /**
     * Returns a standard filter for resources that end in the specified suffix. Filename must be at least one character
     * longer than the suffix.
     */
    default Predicate<Resource> getFilter(String suffix)
    {
        return resource ->
        {
            String filename = resource.getName();
            return StringUtils.endsWithIgnoreCase(filename, suffix) && filename.length() > suffix.length();
        };
    }

    /**
     * Returns a standard filter for filenames that end in the specified suffix. Filename must be at least one character
     * longer than the suffix.
     */
    default Predicate<String> getFilenameFilter(String suffix)
    {
        return filename -> StringUtils.endsWithIgnoreCase(filename, suffix) && filename.length() > suffix.length();
    }
}
