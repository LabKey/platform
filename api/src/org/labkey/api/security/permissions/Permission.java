/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.api.security.permissions;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;
import org.labkey.api.security.roles.Role;

/**
 * Represents a particular permission defined by a module. The most granular level of access that a user might be allowed.
 * Permissions are assigned to {@link org.labkey.api.security.UserPrincipal} via the assignment of {@link org.labkey.api.security.roles.Role}.
 * User: Dave
 * Date: Apr 9, 2009
 */
public interface Permission extends Role
{
    /**
     * Returns a short, friendly name for the permission suitable for a user interface.
     * @return The short name.
     */
    @NotNull
    public String getName();

    /**
     * Returns a longer description for the permission suitable for a user interface.
     * @return A longer description.
     */
    @NotNull
    public String getDescription();

    /**
     * Returns a reference to the module in which this permission is defined.
     * @return The source module.
     */
    @NotNull
    public Module getSourceModule();
}