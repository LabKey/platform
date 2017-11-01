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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.security.User;

/**
 * User: adam
 * Date: 10/11/13
 * Time: 4:55 PM
 */
public interface PropertyStore
{
    /** @return a read-only copy of the properties */
    @NotNull PropertyMap getProperties(User user, Container container, String category);
    /** @return a read-only copy of the properties */
    @NotNull PropertyMap getProperties(Container container, String category);
    /** @return a read-only copy of the properties */
    @NotNull PropertyMap getProperties(String category);

    // If create == true, then never returns null. If create == false, will return null if property set doesn't exist.
    PropertyMap getWritableProperties(User user, Container container, String category, boolean create);
    PropertyMap getWritableProperties(Container container, String category, boolean create);
    PropertyMap getWritableProperties(String category, boolean create);

    void deletePropertySet(User user, Container container, String category);
    void deletePropertySet(Container container, String category);
    void deletePropertySet(String category);
}
