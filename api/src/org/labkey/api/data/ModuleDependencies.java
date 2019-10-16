package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;

import java.util.Collection;

public interface ModuleDependencies
{
    static ModuleDependencies of(@NotNull Collection<Module> modules)
    {
        return modules::add;
    }

    void add(Module module);
}
