/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.api.studydesign.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;

import java.util.Set;

public abstract class StudyDesignBaseTable extends FilteredTable<StudyDesignQuerySchema>
{
    public StudyDesignBaseTable(StudyDesignQuerySchema schema, TableInfo realTable, ContainerFilter cf)
    {
        this(schema, realTable, cf, false);
    }

    public StudyDesignBaseTable(StudyDesignQuerySchema schema, TableInfo realTable, ContainerFilter cf, boolean includeSourceStudyData)
    {
        super(realTable, schema);

        if (includeSourceStudyData && null != schema._study && !schema._study.isDataspaceStudy())
        {
            boolean hasReaderRole = getContextualRoles().contains(RoleManager.getRole(ReaderRole.class));
            _setContainerFilter(new ContainerFilter.StudyAndSourceStudy(schema.getContainer(), schema.getUser(), hasReaderRole));
        }
        else if (null != cf && supportsContainerFilter())
            _setContainerFilter(cf);
        else
            _setContainerFilter(getDefaultContainerFilter());
    }

    protected MutableColumnInfo addContainerColumn()
    {
        BaseColumnInfo containerCol = new AliasedColumn(this, "Container", _rootTable.getColumn("Container"));
        containerCol.setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);

        return addColumn(containerCol);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (perm.equals(ReadPermission.class))
            return hasPermissionOverridable(user, perm);
        // These are editable in Dataspace, but not in a folder within a Dataspace
        if (null == getContainer() || null == getContainer().getProject() || (getContainer().getProject().isDataspace() && !getContainer().isDataspace()))
            return false;
        return hasPermissionOverridable(user, perm);
    }

    protected boolean hasPermissionOverridable(UserPrincipal user, Class<? extends Permission> perm)
    {
        // Only admins are allowed to insert into these tables at the project level
        if (getContainer().isProject())
            return checkReadOrIsAdminPermission(user, perm);
        else
            return checkContainerPermission(user, perm);
    }

    protected boolean checkReadOrIsAdminPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return ReadPermission.class == perm && _userSchema.getContainer().hasPermission(user, perm, getContextualRoles()) ||
                _userSchema.getContainer().hasPermission(user, AdminPermission.class, getContextualRoles());
    }

    protected Set<Role> getContextualRoles()
    {
        return getUserSchema().getContextualRoles();
    }

    protected boolean checkContainerPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return _userSchema.getContainer().hasPermission(user, perm, getContextualRoles());
    }
}
