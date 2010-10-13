/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.security.Group;
import org.labkey.api.security.roles.AbstractRole;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.study.StudyModule;

/*
* User: Dave
* Date: May 19, 2009
* Time: 2:23:17 PM
*/
public class AssayDesignerRole extends AbstractRole
{
    public AssayDesignerRole()
    {
        super("Assay Designer",
                "Assay designers may perform several actions related to designing assays.",
                StudyModule.class,
                DesignAssayPermission.class,
                DesignListPermission.class
        );

        addExcludedPrincipal(SecurityManager.getGroup(Group.groupGuests));
    }
}