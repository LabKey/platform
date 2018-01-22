/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Pair;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

/**
 * User: kevink
 * Date: 8/17/13
 */
public interface CloudStoreService
{
    /** Root node for containers that are exposing cloud-backed storage in WebDAV */
    String CLOUD_NAME = "@cloud";

    static CloudStoreService get()
    {
        return ServiceRegistry.get(CloudStoreService.class);
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
     * Set the enabled stores within the container -- other stores not included will be disabled.
     */
    void setEnabledCloudStores(Container c, Set<String> enabledCloudStores);

    /**
     * Add cloud storage portal tab with web part
     */
    void addCloudStorageTab(Container container);

    /**
     * Return nio.Path to cloud file/directory
     */
    Path getPath(Container container, String storeName, org.labkey.api.util.Path path);

    /**
     * Return path relative to cloud store
     */
    String getRelativePath(Container container, String storeName, String url);

    /**
     * Return nio.Path matching url (which has bucket, etc.)
     */
    Path getPathFromUrl(Container container, String url);

    /**
     * Return nio.Path for otherContainer, given a cloud url/container
     */
    Path getPathForOtherContainer(@NotNull Container container, @NotNull Container otherContainer, @NotNull String url,
                                  @NotNull org.labkey.api.util.Path path);

}
