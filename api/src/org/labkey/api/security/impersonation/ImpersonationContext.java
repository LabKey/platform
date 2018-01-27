/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.api.security.impersonation;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.Role;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.io.Serializable;
import java.util.Set;

/**
 * Context that describes the way in which a user is operating within the system. They may be logged in normally,
 * or they may be impersonating a specific user or a group, depending on the implementation.
 *
 * User: adam
 * Date: 11/8/11
 */
public interface ImpersonationContext extends Serializable
{
    /** @return whether the user is impersonating someone or some group, or working as their normal self */
    boolean isImpersonating();
    boolean isAllowedGlobalRoles();
    /** @return if non-null, the container to which the impersonation should be restricted */
    @Nullable Container getImpersonationProject();
    /** @return the user who is actually performing the operation, not the user that they might be impersonating */
    User getAdminUser();
    String getCacheKey();  // Caching permission-related state is very tricky with impersonation; context provides a cache key suffix that captures the current impersonation state
    /** @return the URL to which the user should be returned when impersonation is over */
    ActionURL getReturnURL();
    int[] getGroups(User user);
    Set<Role> getContextualRoles(User user, SecurityPolicy policy);
    ImpersonationContextFactory getFactory();
    /** Responsible for adding menu items to allow the user to initiate or stop impersonating, based on the current state */
    void addMenu(NavTree menu, Container c, User user, ActionURL currentURL);
}
