/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.cloud;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.files.FileSystemWatcherImpl;
import org.labkey.api.services.ServiceRegistry;

import java.nio.file.Path;

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
