/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.security;

import org.labkey.api.util.GUID;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.data.Container;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/*
* User: Dave
* Date: Apr 9, 2009
* Time: 2:03:59 PM
*/

/**
 * Represents a securable resource
 */
public interface SecurableResource
{
    @NotNull
    String getResourceId();

    @NotNull
    public String getName();

    @NotNull
    public String getDescription();

    @NotNull
    public Set<Class<? extends Permission>> getRelevantPermissions();

    @Nullable
    public Module getSourceModule();

    @Nullable
    public SecurableResource getParent();

    @NotNull
    public Container getContainer();
}