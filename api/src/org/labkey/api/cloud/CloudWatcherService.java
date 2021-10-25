package org.labkey.api.cloud;

import org.jetbrains.annotations.NotNull;

import java.nio.file.WatchKey;
import java.util.Collection;

public interface CloudWatcherService
{
    WatchKey register(CloudWatcherJob watcher);

    @NotNull Collection<CloudWatcherJob> getQueues();
}
