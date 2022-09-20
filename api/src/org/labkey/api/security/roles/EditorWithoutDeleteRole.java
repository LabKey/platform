/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.labkey.api.lists.permissions.ManagePicklistsPermission;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.reports.permissions.EditSharedReportPermission;
import org.labkey.api.reports.permissions.ShareReportPermission;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.AssayReadPermission;
import org.labkey.api.security.permissions.DataClassReadPermission;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.MediaReadPermission;
import org.labkey.api.security.permissions.NotebookReadPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.ReadSomePermission;
import org.labkey.api.security.permissions.SampleWorkflowJobPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.permissions.SharedParticipantGroupPermission;

public class EditorWithoutDeleteRole extends AbstractRole
{
    public EditorWithoutDeleteRole()
    {
        super("Editor without Delete", "Editors in this role may read, add, and update information but not delete.",
                ReadPermission.class,
                ReadSomePermission.class,
                AssayReadPermission.class,
                DataClassReadPermission.class,
                MediaReadPermission.class,
                NotebookReadPermission.class,
                InsertPermission.class,
                UpdatePermission.class,
                EditSharedViewPermission.class,
                ShareReportPermission.class,
                EditSharedReportPermission.class,
                SharedParticipantGroupPermission.class,
                ManagePicklistsPermission.class,
                SampleWorkflowJobPermission.class
        );
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return super.isApplicable(policy,resource) || resource instanceof PipeRoot || resource instanceof Study || resource instanceof Dataset;
    }
}
