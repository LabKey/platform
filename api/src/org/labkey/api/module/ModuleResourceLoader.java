/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.File;
import java.util.Set;

/*
* User: Dave
* Date: Dec 4, 2008
* Time: 2:39:11 PM
*/

/**
 * Interface to be implemented by all module resource loaders.
 * A module resource loader is any class that knows how to load
 * resources from a module at initialization time. These resources
 * might include reports, queries, assay definitions, etc.
 */
public interface ModuleResourceLoader
{
    public @NotNull Set<String> getModuleDependencies(Module module, File explodedModuleDir);

    public void registerResources(Module module) throws IOException, ModuleResourceLoadException;
}