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

import org.labkey.api.reports.permissions.EditSharedReportPermission;
import org.labkey.api.reports.permissions.ShareReportPermission;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.ReadSomePermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.permissions.SharedParticipantGroupPermission;

/*
* User: Dave
* Date: Apr 27, 2009
* Time: 1:22:17 PM
*/
public class EditorRole extends AbstractRole
{
    public EditorRole()
    {
        super("Editor", "Editors may read, add, update and delete information.",
                ReadPermission.class, ReadSomePermission.class, 
                InsertPermission.class, UpdatePermission.class,
                DeletePermission.class,
                EditSharedViewPermission.class,
                ShareReportPermission.class, EditSharedReportPermission.class,
                SharedParticipantGroupPermission.class);
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return super.isApplicable(policy,resource) || resource instanceof Dataset;
    }
}
