/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.study.query.studydesign;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.study.StudySchema;
import org.labkey.study.query.BaseStudyTable;
import org.labkey.study.query.CohortTable;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.query.VisitTable;

/**
 * User: cnathe
 * Date: 1/9/14
 */
public class StudyTreatmentVisitMapTable extends BaseStudyTable
{
    public StudyTreatmentVisitMapTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoTreatmentVisitMap());
        setName(StudyQuerySchema.TREATMENT_VISIT_MAP_TABLE_NAME);
        setDescription("Contains one row per cohort/treatment/visit mapping");

        ColumnInfo cohortCol = new AliasedColumn(this, "CohortId", _rootTable.getColumn("CohortId"));
        cohortCol.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new CohortTable(_userSchema);
            }
        });
        addColumn(cohortCol);

        ColumnInfo treatmentCol = new AliasedColumn(this, "TreatmentId", _rootTable.getColumn("TreatmentId"));
        treatmentCol.setFk(new LookupForeignKey("RowId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return QueryService.get().getUserSchema(_userSchema.getUser(), _userSchema.getContainer(), StudyQuerySchema.SCHEMA_NAME).getTable(StudyQuerySchema.TREATMENT_TABLE_NAME);
            }
        });
        addColumn(treatmentCol);

        ColumnInfo visitCol = new AliasedColumn(this, "VisitId", _rootTable.getColumn("VisitId"));
        visitCol.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new VisitTable(_userSchema);
            }
        });
        addColumn(visitCol);

        addContainerColumn();
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
