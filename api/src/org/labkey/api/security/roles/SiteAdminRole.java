/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
import org.labkey.api.security.permissions.*;

/*
* User: Dave
* Date: Apr 23, 2009
* Time: 12:17:37 PM
*/

/**
 * The Site Administrator role
 */
public class SiteAdminRole extends AbstractContextualRole
{
    public SiteAdminRole()
    {
        super("Site Administrator", "Site Administrators have full control over the entire system.",
                ReadPermission.class,
                ReadSomePermission.class,
                UpdatePermission.class,
                InsertPermission.class, 
                DeletePermission.class,
                AdminReadPermission.class,
                AdminPermission.class,
                EditSharedViewPermission.class,
                SeeUserEmailAddressesPermission.class,
                EmailNonUsersPermission.class,
                EnableRestrictedModules.class,
                CanSeeAuditLogPermission.class);
    }
}