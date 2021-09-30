/*
 * Copyright (c) 2013-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Pair;
import org.labkey.api.webdav.WebdavResource;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * User: kevink
 * Date: 8/17/13
 */
public interface CloudStoreService
{
    /** Root node for containers that are exposing cloud-backed storage in WebDAV */
    String CLOUD_NAME = "@cloud";

    static @Nullable CloudStoreService get()
    {
        return ServiceRegistry.get().getService(CloudStoreService.class);
    }

    static void setInstance(CloudStoreService impl)
    {
        ServiceRegistry.get().registerService(CloudStoreService.class, impl);
    }

    Path downloadExpandedArchive(PipelineJob job) throws PipelineJobException;

    void executeWatchJob(int cloudWatcherJobId);

    void registerCloudWatcher(CloudWatcherConfig config, Function<Path, Boolean> eventProcessor);

    void unregisterCloudWatcher(int watcherConfigId);

    void shutdownWatchers();

    Collection<Integer> getWatcherJobs();

    void deleteMessage(String messageId, int watcherId);

    class StoreInfo
    {
        private final boolean _isEnabled;
        private final boolean _isEnabledInContainer;
        private final boolean _isLabKeyManaged;
        private final String _name;

        public StoreInfo(String name, boolean isEnabled, boolean isEnabledInContainer, boolean isLabKeyManaged)
        {
            _isEnabled = isEnabled;
            _isEnabledInContainer = isEnabledInContainer;
            _isLabKeyManaged = isLabKeyManaged;
            _name = name;
        }

        public boolean isEnabled()
        {
            return _isEnabled;
        }

        public boolean isEnabledInContainer()
        {
            return _isEnabledInContainer;
        }

        public boolean isLabKeyManaged()
        {
            return _isLabKeyManaged;
        }

        public String getName()
        {
            return _name;
        }
    }

    class ServiceException extends RuntimeException
    {
        public ServiceException(String message, Throwable t)
        {
            super(message, t);
        }
    }

    /**
     * Returns a list of blob store provider (id, name) pairs.
     */
    Iterable<Pair<String, String>> providers();

    /**
     * Returns true if store is enabled at the site level.
     */
    boolean isEnabled(String storeName);

    /**
     * Returns true if store is enabled within the container.
     */
    boolean isEnabled(String storeName, Container container);

    /**
     * Returns true if bucket associated with store exists.
     */
    boolean containerFolderExists(String storeName, Container container);

    /**
     * Returns a list of all store names.
     */
    public Collection<String> getCloudStores();

    /**
     * Returns a list of enabled store names in the container.
     */
    Collection<String> getEnabledCloudStores(Container container);

    /**
     * Returns a list of enabled store names in the container, excluding 'except'.
     */
    default Collection<String> getEnabledCloudStores(Container container, boolean exceptFileRoot)
    {
        return getEnabledCloudStores(container);            // Only so old CloudStoreServiceImpl works
    }

    /**
     * Set the enabled stores within the container -- other stores not included will be disabled.
     */
    void setEnabledCloudStores(Container c, Set<String> enabledCloudStores);

    /**
     * Return nio.Path to cloud file/directory
     */
    @Nullable
    Path getPath(Container container, String storeName, org.labkey.api.util.Path path);

    /**
     * Return path relative to cloud store
     */
    @Nullable
    String getRelativePath(Container container, String storeName, String url);

    /**
     * Return nio.Path matching url (which has bucket, etc.)
     */
    @Nullable
    Path getPathFromUrl(Container container, String url);

    /**
     * Return nio.Path matching url (which has bucket, etc.)
     */
    @Nullable
    Path getPathFromUrl(String url);

    /**
     * Return nio.Path for otherContainer, given a cloud url/container
     */
    @Nullable
    Path getPathForOtherContainer(@NotNull Container container, @NotNull Container otherContainer, @NotNull String url,
                                  @NotNull org.labkey.api.util.Path path);

    @Nullable
    default WebdavResource getWebFilesResource(@NotNull WebdavResource parent, @NotNull Container container, @NotNull String name)      // TODO: remove this when implementation switches to below
    {
        return null;
    }
    @Nullable
    default WebdavResource getWebFilesResource(@NotNull WebdavResource parent, @NotNull Container container, @NotNull String name, @NotNull String nameDisplay)
    {
        return getWebFilesResource(parent, container, name);
    }

    default Map<String, StoreInfo> getStoreInfos(@Nullable Container container)
    {
        return Collections.emptyMap();
    }
}
