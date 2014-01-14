/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.query.olap;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceLoadException;
import org.labkey.api.module.ModuleResourceLoader;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * User: matthew
 * Date: 10/30/13
 */

// TODO: Use or remove!
public class OlapSchemaLoader implements ModuleResourceLoader
{
    @NotNull
    @Override
    public Set<String> getModuleDependencies(Module module, File explodedModuleDir)
    {
        return Collections.emptySet();
    }

    @Override
    public void registerResources(Module module) throws IOException, ModuleResourceLoadException
    {
        ServerManager.SCHEMA_DESCRIPTOR_CACHE.registerModule(module);
    }
}
