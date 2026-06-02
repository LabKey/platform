/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
import org.labkey.api.security.permissions.Permission;

// Normal permission checking operates on secure resources, but some unit tests need to update and interrogate
// security policies directly. This class allows those tests to permission check directly on the policy.
public class TestSecurityPolicy extends MutableSecurityPolicy
{
    public TestSecurityPolicy(@NotNull SecurableResource resource)
    {
        super(resource);
    }

    public boolean hasPermission(@NotNull UserPrincipal principal, @NotNull Class<? extends Permission> permission)
    {
        return getOwnPermissions(principal).anyMatch(permClass -> permClass == permission);
    }
}
