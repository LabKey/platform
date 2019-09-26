package org.labkey.api.vcs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;

import java.io.File;
import java.util.Objects;

public interface VcsService
{
    static @NotNull VcsService get()
    {
        return Objects.requireNonNull(ServiceRegistry.get().getService(VcsService.class));
    }

    static void setInstance(VcsService instance)
    {
        ServiceRegistry.get().registerService(VcsService.class, instance);
    }

    /**
     * Return the appropriate Vcs implementation if the directory is under version control
     * @param directory Directory to test
     * @return The corresponding Vcs implementation or null
     */
    @Nullable Vcs getVcs(File directory);
}
