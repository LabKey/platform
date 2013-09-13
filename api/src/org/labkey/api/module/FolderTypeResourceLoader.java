/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
import org.labkey.api.resource.Resource;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * User: brittp
 * Date: May 7, 2010 5:38:56 PM
 */
public class FolderTypeResourceLoader implements ModuleResourceLoader
{
    private static final String FOLDER_TYPES_NAME = "folderTypes";

    @NotNull
    @Override
    public Set<String> getModuleDependencies(Module module, File explodedModuleDir)
    {
        return Collections.emptySet();
    }

    @Override
    public void registerResources(Module module) throws IOException, ModuleResourceLoadException
    {
        Resource folderTypesDir = module.getModuleResource(FOLDER_TYPES_NAME);
        if (folderTypesDir != null && folderTypesDir.exists() && folderTypesDir.isCollection())
        {
            for (FolderType folderType : SimpleFolderType.createFromDirectory(folderTypesDir))
                ModuleLoader.getInstance().registerFolderType(module, folderType);
        }
    }
}
