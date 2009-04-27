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

import org.labkey.api.security.roles.Role;
import org.jetbrains.annotations.NotNull;

/*
* User: Dave
* Date: Apr 27, 2009
* Time: 10:58:34 AM
*/
public class ResourceSecurityPolicy extends SecurityPolicy
{
    private SecurableResource _resource;

    public ResourceSecurityPolicy(@NotNull SecurableResource resource)
    {
        super();
        _resource = resource;
    }

    public ResourceSecurityPolicy(@NotNull SecurableResource resource, @NotNull RoleAssignment[] assignments)
    {
        super(assignments);
        _resource = resource;
    }

    public SecurableResource getResource()
    {
        return _resource;
    }

    public void addRoleAssignment(@NotNull UserPrincipal principal, @NotNull Role role)
    {
        RoleAssignment assignment = new RoleAssignment(_resource, principal, role);
        assignment.setUserId(principal.getUserId());
        assignment.setRole(role);
        addAssignment(assignment);
    }
}