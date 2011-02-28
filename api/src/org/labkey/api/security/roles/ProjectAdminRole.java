/*
 * Copyright (c) 2009-2011 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.*;

/*
* User: Dave
* Date: Apr 28, 2009
* Time: 10:13:55 AM
*/
public class ProjectAdminRole extends AbstractRole
{
    public ProjectAdminRole()
    {
        super("Project Administrator",
                "Project Administrators have full control over the project, but not the entire system. " +
                        "By default, Project Administrators are added to the Folder Administrator role within all project folders.",
                ReadPermission.class,
                ReadSomePermission.class,
                InsertPermission.class,
                UpdatePermission.class,
                DeletePermission.class,
                AdminPermission.class,
                EditSharedViewPermission.class);

        addExcludedPrincipal(SecurityManager.getGroup(Group.groupGuests));
        addExcludedPrincipal(SecurityManager.getGroup(Group.groupUsers));
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return resource instanceof Container && ((Container)resource).isProject();
    }
}