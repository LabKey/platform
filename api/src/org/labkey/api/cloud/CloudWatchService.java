package org.labkey.api.cloud;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.files.FileSystemWatcherImpl;
import org.labkey.api.services.ServiceRegistry;

import java.nio.file.Path;
import java.util.function.BiConsumer;

public interface CloudWatchService
{
    static @Nullable CloudWatchService get()
    {
        return ServiceRegistry.get().getService(CloudWatchService.class);
    }
    static void setInstance(CloudWatchService impl)
    {
        ServiceRegistry.get().registerService(CloudWatchService.class, impl);
    }

    // Listeners are Path/ Store based. These are the notification processors.
    void registerCloudListener(Path resolvedPath, CloudWatcherConfig config, FileSystemWatcherImpl.PathListenerManager plm); // BiConsumer<Path, Runnable> eventProcessor);
    void unregisterCloudListener(int watcherConfigId);

    void close();
}
