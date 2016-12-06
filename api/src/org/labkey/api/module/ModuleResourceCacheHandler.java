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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.resource.Resource;

/**
 * User: adam
 * Date: 1/10/14
 * Time: 9:53 PM
 */
public interface ModuleResourceCacheHandler<V>
{
    /**
     * Loads all resources of the appropriate type from the specified module and root directory.
     *
     * @return A Map or similar data structure that gives callers access to those resources
     */
    V load(@Nullable Resource dir, Module module);

    /**
     * If needed, returns a FileSystemDirectoryListener that implements resource-specific file change handling. The
     * standard listener clears the module's resource map and then invokes the appropriate method of the chained listener,
     * if present.
     *
     * @param module Module for which to create the listener
     * @return A directory listener with implementation-specific handling.
     */
    default @Nullable FileSystemDirectoryListener createChainedDirectoryListener(Module module)
    {
        return null;
    }
}
