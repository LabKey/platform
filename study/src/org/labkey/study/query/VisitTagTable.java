/*
 * Copyright (c) 2014-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.study.StudySchema;

/**
 * Created by cnathe on 4/9/14.
 */
public class VisitTagTable extends BaseStudyTable
{
    public VisitTagTable(StudyQuerySchema schema, @Nullable ContainerFilter containerFilter)
    {
        super(schema, StudySchema.getInstance().getTableInfoVisitTag(), true);
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
        ColumnInfo container = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Container")));
        ContainerForeignKey.initColumn(container, schema);

        ColumnInfo created = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Created")));
        created.setFormat("DateTime");
        created.setHidden(true);

        ColumnInfo createdBy = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("CreatedBy")));
        createdBy.setLabel("Created By");
        createdBy.setHidden(true);
        UserIdForeignKey.initColumn(createdBy);

        ColumnInfo modified = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Modified")));
        modified.setFormat("DateTime");
        modified.setHidden(true);

        ColumnInfo modifiedBy = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("ModifiedBy")));
        modifiedBy.setLabel("Modified By");
        modifiedBy.setHidden(true);
        UserIdForeignKey.initColumn(modifiedBy);

        setTitleColumn("Caption");
        setPublicSchemaName("study");
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        // This is editable in Dataspace, but not in a folder within a Dataspace
        if (getContainer().getProject().isDataspace() && !getContainer().isDataspace())
            return false;
        return hasPermissionOverridable(user, perm);
    }

    @Override
    public boolean hasPermissionOverridable(UserPrincipal user, Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new DefaultQueryUpdateService(this, this.getRealTable());
    }
}
