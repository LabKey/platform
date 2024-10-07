/*
 * Copyright (c) 2013-2018 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.security.User;

import java.util.stream.Stream;

public interface PropertyStore
{
    /** @return a read-only copy of the properties */
    @NotNull PropertyMap getProperties(User user, Container container, String category);
    /** @return a read-only copy of the properties */
    @NotNull PropertyMap getProperties(User user, String category);
    /** @return a read-only copy of the properties */
    @NotNull PropertyMap getProperties(Container container, String category);
    /** @return a read-only copy of the properties */
    @NotNull PropertyMap getProperties(String category);

    // If create == true, then never returns null. If create == false, will return null if property set doesn't exist.
    WritablePropertyMap getWritableProperties(User user, Container container, String category, boolean create);
    WritablePropertyMap getWritableProperties(User user, String category, boolean create);
    WritablePropertyMap getWritableProperties(Container container, String category, boolean create);
    WritablePropertyMap getWritableProperties(String category, boolean create);

    void deletePropertySet(User user, Container container, String category);
    void deletePropertySet(User user, String category);
    void deletePropertySet(Container container, String category);
    void deletePropertySet(String category);

    /**
     * Returns a sequential, CLOSEABLE Stream of all Containers that have a saved property map matching the specified user
     * and category. This is intended primarily for gathering usage metrics across a LabKey instance; this approach is likely
     * more efficient than enumerating all containers and loading property maps, since it pre-filters and avoids filling the
     * cache with misses.
     *
     * @param user The user of interest
     * @param category The property set category
     * @return A stream of container objects. This stream MUST BE CLOSED via try-with-resources or in a finally block.
     */
    Stream<Container> streamMatchingContainers(User user, String category);
}
