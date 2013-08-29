/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.util.Pair;

import java.util.Collection;
import java.util.Set;

/**
 * User: kevink
 * Date: 8/17/13
 */
public interface CloudStoreService
{
    String CLOUD_NAME = "@cloud";

    /**
     * Returns a list of blob store provider (id, name) pairs.
     */
    Iterable<Pair<String, String>> providers();

    /**
     * Returns true if the Cloud module is enabled in the container.
     */
    boolean isEnabled(Container container);

    /**
     * Returns true if store is enabled at the site level.
     */
    boolean isEnabled(String storeName);

    /**
     * Returns true if store is enabled within the container.
     */
    boolean isEnabled(String storeName, Container container);

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

}
