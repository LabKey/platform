/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.security.permissions.MoveEntitiesPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.SampleWorkflowJobPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.permissions.SharedParticipantGroupPermission;

import java.util.Collection;
import java.util.stream.Stream;

public class EditorWithoutDeleteRole extends AbstractRole
{
    static final Collection<Class<? extends Permission>> PERMISSIONS = Stream.concat(
        AuthorRole.PERMISSIONS.stream(),
        Stream.of(
            EditSharedReportPermission.class,
            EditSharedViewPermission.class,
            ManagePicklistsPermission.class,
            MoveEntitiesPermission.class,
            SampleWorkflowJobPermission.class,
            SharedParticipantGroupPermission.class,
            UpdatePermission.class
        )
    ).toList();

    public EditorWithoutDeleteRole()
    {
        super("Editor without Delete", "Editors in this role may read, add, and update information but not delete.", PERMISSIONS);
    }

    protected EditorWithoutDeleteRole(String name, String description, Iterable<Class<? extends Permission>>... permCollections)
    {
        super(name, description, permCollections);
    }

    @Override
    public boolean isApplicable(SecurableResource resource)
    {
        return super.isApplicable(resource) || resource instanceof PipeRoot || resource instanceof Study || resource instanceof Dataset;
    }
}
