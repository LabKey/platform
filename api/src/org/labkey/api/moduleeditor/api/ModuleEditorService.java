package org.labkey.api.moduleeditor.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.Module;
import org.labkey.api.services.ServiceRegistry;
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
        return new File(resources, path.toString("",""));
    }
}