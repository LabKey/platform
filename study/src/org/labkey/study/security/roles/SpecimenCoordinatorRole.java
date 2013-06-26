/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
package org.labkey.study.security.roles;

import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.security.permissions.EditSpecimenDataPermission;
import org.labkey.study.security.permissions.*;

/*
* User: Dave
* Date: May 13, 2009
* Time: 3:16:54 PM
*/
public class SpecimenCoordinatorRole extends AbstractSpecimenRole
{
    public SpecimenCoordinatorRole()
    {
        super("Specimen Coordinator",
                "Specimen Coordinators may perform a number of management tasks related to specimens",
                ManageRequestsPermission.class,
                LockSpecimensPermission.class,
                ManageDisplaySettingsPermission.class,
                ManageNewRequestFormPermission.class,
                ManageNotificationsPermission.class,
                ManageRequestRequirementsPermission.class,
                ManageRequestSettingsPermission.class,
                ManageRequestStatusesPermission.class,
                ManageSpecimenActorsPermission.class,
                ManageStudyPermission.class,
                RequestSpecimensPermission.class,
                SetSpecimenCommentsPermission.class,
                EditSharedViewPermission.class, //spec-coord needs to be able to set global default view for specimens queries
                EditSpecimenDataPermission.class
                );
        addExcludedPrincipal(SecurityManager.getGroup(Group.groupGuests));
        addExcludedPrincipal(SecurityManager.getGroup(Group.groupUsers));
    }
}