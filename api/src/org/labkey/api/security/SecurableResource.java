/*
 * Copyright (c) 2009-2018 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.Permission;

import java.util.List;
import java.util.Set;

/**
 * A resource to which users and groups may be assigned roles. Examples include {@link org.labkey.api.data.Container} and {@link org.labkey.api.study.Dataset}.
 * User: Dave
 * Date: Apr 9, 2009
 */
public interface SecurableResource extends HasPermission
{
    /**@return a GUID, unique to this resource */
    @NotNull
    String getResourceId();

    /** @return a short name for this resource */
    @NotNull String getResourceName();

    /** @return a short description of what this resource represents */
    @NotNull String getResourceDescription();

    /** @return the module that is responsible for the resource */
    @NotNull Module getSourceModule();

    /** @return if available, the resource that is the logical parent of this resource. Should be consistent with what the parent returns from getChildResources() */
    @Nullable SecurableResource getParentResource();

    /** @return the container to which this resource belongs */
    @NotNull Container getResourceContainer();

    /** @return resources that are logical children of this resource. Should be consistent with what the children return from getParentResources() */
    @NotNull List<SecurableResource> getChildResources(User user);

    /** @return whether this resource may inherit the security policy associated with its parent */
    boolean mayInheritPolicy();

    default String getDebugName()
    {
        return getClass().getName() + ":" + getResourceName();
    }

    @Override
    default boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return SecurityManager.hasAllPermissions(this.getClass().getName() + ":" + getResourceName(),
                this, user, Set.of(perm), Set.of());
    }
}