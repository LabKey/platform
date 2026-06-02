/*
 * Copyright (c) 2014-2026 LabKey Corporation
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
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

/**
 * User: cnathe
 * Date: 1/9/14
 */
public class StudyTreatmentVisitMapTable extends StudyDesignBaseTable
{
    public StudyTreatmentVisitMapTable(StudyDesignQuerySchema schema, ContainerFilter cf)
    {
        super(schema, StudyDesignSchema.getInstance().getTableInfoTreatmentVisitMap(), cf);
        setName(StudyDesignQuerySchema.TREATMENT_VISIT_MAP_TABLE_NAME);
        setDescription("Contains one row per cohort/treatment/visit mapping");

        var cohortCol = new AliasedColumn(this, "CohortId", _rootTable.getColumn("CohortId"));
        cohortCol.setFk(QueryForeignKey.from(schema.getParentSchema(), cf).to("Cohort", "RowId", null));
        addColumn(cohortCol);

        var treatmentCol = new AliasedColumn(this, "TreatmentId", _rootTable.getColumn("TreatmentId"));
        treatmentCol.setFk(new LookupForeignKey("RowId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return QueryService.get().getUserSchema(_userSchema.getUser(), _userSchema.getContainer(), StudyDesignQuerySchema.STUDY_SCHEMA_NAME).getTable(StudyDesignQuerySchema.TREATMENT_TABLE_NAME, cf);
            }
        });
        addColumn(treatmentCol);

        var visitCol = new AliasedColumn(this, "VisitId", _rootTable.getColumn("VisitId"));
        visitCol.setFk(QueryForeignKey.from(schema.getParentSchema(), cf).to("Visit", "RowId", null));

        addColumn(visitCol);
        addContainerColumn();
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new DefaultQueryUpdateService(this, this.getRealTable());
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
    protected boolean hasPermissionOverridable(UserPrincipal user, Class<? extends Permission> perm)
    {
        // see StudyDesignController.UpdateTreatmentScheduleAction    @RequiresPermission(UpdatePermission.class)
        return checkContainerPermission(user, perm);
    }
}
