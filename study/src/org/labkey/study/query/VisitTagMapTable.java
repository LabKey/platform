/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.study.CohortForeignKey;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyManager;

import java.util.HashMap;
import java.util.Map;

public class VisitTagMapTable extends BaseStudyTable
{
    public VisitTagMapTable(final StudyQuerySchema schema, @Nullable ContainerFilter containerFilter)
    {
        super(schema, StudySchema.getInstance().getTableInfoVisitTagMap(), true);
        if (null != containerFilter)
            _setContainerFilter(containerFilter);

        ColumnInfo rowIdColumn = wrapColumn( _rootTable.getColumn("RowId"));
        rowIdColumn.setHidden(true);
        rowIdColumn.setUserEditable(false);
        addColumn(rowIdColumn);

        ColumnInfo visitTagColumn = wrapColumn( _rootTable.getColumn("VisitTag"));
        LookupForeignKey visitTagFk = new LookupForeignKey()
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return schema.createTable(StudyQuerySchema.VISIT_TAG_TABLE_NAME);
            }
        };
        visitTagColumn.setFk(visitTagFk);
        addColumn(visitTagColumn);

        ColumnInfo visitIdColumn = wrapColumn("Visit", _rootTable.getColumn("VisitId"));
        LookupForeignKey visitIdFk = new LookupForeignKey()
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return schema.createTable("Visit");
            }
        };
        visitIdColumn.setFk(visitIdFk);
        addColumn(visitIdColumn);

        boolean showCohorts = StudyManager.getInstance().showCohorts(schema.getContainer(), schema.getUser());
        ColumnInfo cohortColumn;
        if (showCohorts)
        {
            cohortColumn = new AliasedColumn(this, "Cohort", _rootTable.getColumn("CohortId"));
        }
        else
        {
            cohortColumn = new NullColumnInfo(this, "Cohort", JdbcType.INTEGER);
        }
        cohortColumn.setFk(new CohortForeignKey(schema, showCohorts, cohortColumn.getLabel()));
        addColumn(cohortColumn);

        addContainerColumn();
    }

    @Override
    public boolean hasPermissionOverridable(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        // Only allow admins to edit directly.
        return getContainer().hasPermission(user, AdminPermission.class) && getContainer().hasPermission(user, perm);
    }

    private static Map<String, String> _columnMappings = new HashMap<>();
    static
    {
        _columnMappings.put("VisitId", "Visit");
        _columnMappings.put("CohortId", "Cohort");
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new VisitTagMapQueryUpdateService(this, this.getRealTable(), _columnMappings);
    }
}
