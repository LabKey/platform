/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.study.StudySchema;

/**
 * Created by cnathe on 4/9/14.
 */
public class VisitTagTable extends BaseStudyTable
{
    public VisitTagTable(StudyQuerySchema schema, @Nullable ContainerFilter containerFilter)
    {
        super(schema, StudySchema.getInstance().getTableInfoVisitTag(), null, true);

        // NOTE: BaseStudyTable is maybe not the right base class for VisitTagTable? Its logic for CF doesn't quite work here
        if (null != containerFilter)
            _setContainerFilter(containerFilter);

        setName(StudyQuerySchema.VISIT_TAG_TABLE_NAME);
        setDescription("Contains one row per visit tag");

        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Name")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Caption")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Description")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Category")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("SingleUse")));

        // setup lookups for the standard fields
        var container = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Container")));

        var created = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Created")));
        created.setFormat("DateTime");
        created.setHidden(true);

        var createdBy = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("CreatedBy")));
        createdBy.setLabel("Created By");
        createdBy.setHidden(true);

        var modified = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Modified")));
        modified.setFormat("DateTime");
        modified.setHidden(true);

        var modifiedBy = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("ModifiedBy")));
        modifiedBy.setLabel("Modified By");
        modifiedBy.setHidden(true);

        setTitleColumn("Caption");
        setPublicSchemaName("study");
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (perm.equals(ReadPermission.class))
            return hasPermissionOverridable(user, perm);
        // This is editable in Dataspace, but not in a folder within a Dataspace
        if (getContainer().getProject().isDataspace() && !getContainer().isDataspace())
            return false;
        return hasPermissionOverridable(user, perm);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new DefaultQueryUpdateService(this, this.getRealTable());
    }


    @Override
    protected void _setContainerFilter(@NotNull ContainerFilter filter)
    {
        super._setContainerFilter(filter);
    }

    @Override
    protected boolean hasPermissionOverridable(UserPrincipal user, Class<? extends Permission> perm)
    {
        return checkReadOrIsAdminPermission(user, perm);
    }
}
