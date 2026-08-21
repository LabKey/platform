/*
 * Copyright (c) 2013-2026 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

/**
 * User: cnathe
 * Date: 12/13/13
 */
public class AssaySpecimenVisitTable extends StudyDesignBaseTable
{
    public AssaySpecimenVisitTable(StudyDesignQuerySchema schema, ContainerFilter cf)
    {
        super(schema, StudyDesignSchema.getInstance().getTableInfoAssaySpecimenVisit(), cf);
        setName(StudyDesignQuerySchema.ASSAY_SPECIMEN_VISIT_TABLE_NAME);

        addWrapColumn(_rootTable.getColumn("RowId"));

        var visitCol = new AliasedColumn(this, "VisitId", _rootTable.getColumn("VisitId"));
        visitCol.setFk(QueryForeignKey.from(schema, cf).schema("study").to("visit", "rowId", null));
        addColumn(visitCol);

        var assaySpecimenCol = new AliasedColumn(this, "AssaySpecimenId", _rootTable.getColumn("AssaySpecimenId"));
        assaySpecimenCol.setFk(new LookupForeignKey(cf, "RowId", null)
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return new AssaySpecimenTable(_userSchema, getLookupContainerFilter());
            }
        });
        addColumn(assaySpecimenCol);

        addContainerColumn();
        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            BuiltInColumnTypes type = BuiltInColumnTypes.findBuiltInType(baseColumn);
            if (null != type && type != BuiltInColumnTypes.Container)
            {
                addWrapColumn(baseColumn);
            }
        }
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        checkedPermissions.add(perm);
        // Most tables should not be editable in Dataspace
        if (!perm.equals(ReadPermission.class) && getContainer().isDataspace())
            return false;
        return hasPermissionOverridable(user, perm);
    }

    @Override
    public boolean hasPermissionOverridable(UserPrincipal user, Class<? extends Permission> perm)
    {
        // see StudyDesignController.UpdateAssayScheduleAction @RequiresPermission(UpdatePermission.class)
        return checkContainerPermission(user, perm);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new DefaultQueryUpdateService(this, this.getRealTable());
    }
}
