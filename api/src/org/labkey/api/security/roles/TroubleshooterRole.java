/*
 * Copyright (c) 2010-2019 LabKey Corporation
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
package org.labkey.api.security.roles;

import org.labkey.api.audit.permissions.CanSeeAuditLogPermission;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.TroubleShooterPermission;

/**
 * User: adam
 * Date: Jan 22, 2010
 */
public class TroubleshooterRole extends AbstractRootContainerRole
{
    public TroubleshooterRole()
    {
        super("Troubleshooter", "Troubleshooters may view administration settings but may not change them.",
                TroubleShooterPermission.class,
                CanSeeAuditLogPermission.class);

        addExcludedPrincipal(SecurityManager.getGroup(Group.groupGuests));
        addExcludedPrincipal(SecurityManager.getGroup(Group.groupUsers));
    }
}