/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.DataspaceContainerFilter;
import org.labkey.api.study.Study;
import org.labkey.study.CohortForeignKey;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyManager;

public class VisitTable extends BaseStudyTable
{
    public VisitTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoVisit());

        ContainerFilter cf = schema.getDefaultContainerFilter();
        Study study = schema.getStudy();
        if (null != study)
        {
            if (cf instanceof DataspaceContainerFilter)
            {
                // fix up container filter to include project if dataspace study with shard visits
                if (schema.getContainer().isProject() && study.getShareVisitDefinitions())
                    cf = new ContainerFilter.Project(schema.getUser());
            }
            else
            {
                // If we are in a sub-folder (DataspaceContainerFilter not set), check for a shared visit study.
                // If shared visits are enabled, only show visits from the project level.
                Study visitStudy = StudyManager.getInstance().getSharedStudy(study);
                if (visitStudy != null && visitStudy.getShareVisitDefinitions())
                    cf = new ContainerFilter.Project(schema.getUser());
            }

            _setContainerFilter(cf);
        }

        addColumn(new AliasedColumn(this, "RowId", _rootTable.getColumn("RowId")));
        addColumn(new AliasedColumn(this, "TypeCode", _rootTable.getColumn("TypeCode")));
        addColumn(new AliasedColumn(this, "SequenceNumMin", _rootTable.getColumn("SequenceNumMin")));
        addColumn(new AliasedColumn(this, "SequenceNumMax", _rootTable.getColumn("SequenceNumMax")));
        addColumn(new AliasedColumn(this, "ProtocolDay", _rootTable.getColumn("ProtocolDay")));
        addColumn(new AliasedColumn(this, "Label", _rootTable.getColumn("Label")));
        addColumn(new AliasedColumn(this, "Description", _rootTable.getColumn("Description")));
        addFolderColumn();
        addColumn(new AliasedColumn(this, "ShowByDefault", _rootTable.getColumn("ShowByDefault")));
        addWrapColumn(_rootTable.getColumn("DisplayOrder"));
        addWrapColumn(_rootTable.getColumn("ChronologicalOrder"));
        addWrapColumn(_rootTable.getColumn("SequenceNumHandling"));

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
        setTitleColumn("Label");
        setDeleteURL(LINK_DISABLER);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new VisitUpdateService(this);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return hasPermissionOverridable(user, perm);
    }

    @Override
    protected boolean hasPermissionOverridable(UserPrincipal user, Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }
}
