/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.api.moduleeditor.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.Module;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;

import java.io.File;
import java.util.List;

public interface ModuleEditorService
{
    static @NotNull ModuleEditorService get()
    {
        ModuleEditorService service = ServiceRegistry.get().getService(ModuleEditorService.class);
        if (null == service)
            service = new ModuleEditorService(){};
        return service;
    }

    @Nullable
    default ActionURL getUpdateModuleURL(String module)
    {
        return null;
    }

    @Nullable
    default ActionURL getCreateModuleURL()
    {
        return null;
    }

    @Nullable
    default ActionURL getDeleteModuleURL(String module)
    {
        return null;
    }

    default boolean canEditSourceModule(Module module)
    {
        return false;
    }

    @Nullable
    default ActionURL getModuleEditorURL(String module)
    {
        return null;
    }

    /*
     * NOTE: the Module interface is designed for loading resources, not updating them, here are some helpers
     * return non-null File if this module has updatable resources, returns a message string if is not.
     */
    default File getUpdatableResourcesRoot(Module module, @Nullable List<String> messages)
    {
        return null;
    }

    default File getFileForModuleResource(Module module, Path path)
    {
        File resources = getUpdatableResourcesRoot(module, null);
        if (null == resources)
            return null;
        return FileUtil.appendPath(resources, path);
    }

    // used by ModuleResourceProvider@AllModuleResourcesRoot() (_webdav/@modules) which wants to soo
    // the whole module source directory (e.g. when using SourcePath)
    default File getModuleRoot(Module module, @Nullable List<String> messages)
    {
        return null;
    }
}