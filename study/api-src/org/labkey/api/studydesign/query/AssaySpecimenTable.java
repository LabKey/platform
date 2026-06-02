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
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

import static org.labkey.api.studydesign.query.StudyDesignQuerySchema.STUDY_DESIGN_ASSAYS_TABLE_NAME;
import static org.labkey.api.studydesign.query.StudyDesignQuerySchema.STUDY_DESIGN_LABS_TABLE_NAME;
import static org.labkey.api.studydesign.query.StudyDesignQuerySchema.STUDY_DESIGN_SAMPLE_TYPES_TABLE_NAME;
import static org.labkey.api.studydesign.query.StudyDesignQuerySchema.STUDY_DESIGN_UNITS_TABLE_NAME;

/**
 * User: cnathe
 * Date: 12/13/13
*/
public class AssaySpecimenTable extends StudyDesignBaseTable
{
    private QueryForeignKey.Builder studyFK()
    {
        return QueryForeignKey.from(_userSchema,getContainerFilter());
    }

    public AssaySpecimenTable(StudyDesignQuerySchema schema, ContainerFilter cf)
    {
        super(schema, StudyDesignSchema.getInstance().getTableInfoAssaySpecimen(), cf);
        setName(StudyDesignQuerySchema.ASSAY_SPECIMEN_TABLE_NAME);

        addWrapColumn(_rootTable.getColumn("RowId"));

        var assayColumn = new AliasedColumn(this, "AssayName", _rootTable.getColumn("AssayName"));
        assayColumn.setFk(studyFK().to(STUDY_DESIGN_ASSAYS_TABLE_NAME, "Name", null));
        addColumn(assayColumn);

        addWrapColumn(_rootTable.getColumn("Description"));

        var locationCol = new AliasedColumn(this, "LocationId", _rootTable.getColumn("LocationId"));
        locationCol.setFk(QueryForeignKey.from(schema, cf).schema("study").to("location", "rowId", null));
        addColumn(locationCol);

        var dataSetColumn = new AliasedColumn(this, "DataSet", _rootTable.getColumn("DataSet"));
        dataSetColumn.setFk(studyFK().to("DataSets", "DataSetId", null));
        addColumn(dataSetColumn);

        addWrapColumn(_rootTable.getColumn("Source"));
        addWrapColumn(_rootTable.getColumn("TubeType"));

        var labColumn = new AliasedColumn(this, "Lab", _rootTable.getColumn("Lab"));
        labColumn.setFk(studyFK().to(STUDY_DESIGN_LABS_TABLE_NAME, "Name", null));
        addColumn(labColumn);

        var sampleTypeColumn = new AliasedColumn(this, "SampleType", _rootTable.getColumn("SampleType"));
        sampleTypeColumn.setFk(studyFK().to(STUDY_DESIGN_SAMPLE_TYPES_TABLE_NAME, "Name", null));
        addColumn(sampleTypeColumn);

        addWrapColumn(_rootTable.getColumn("SampleQuantity"));
        var sampleUnitsColumn = new AliasedColumn(this, "SampleUnits", _rootTable.getColumn("SampleUnits"));
        sampleUnitsColumn.setFk(studyFK().to(STUDY_DESIGN_UNITS_TABLE_NAME, "Name", null));
        addColumn(sampleUnitsColumn);

        addContainerColumn();
        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            BuiltInColumnTypes type = BuiltInColumnTypes.findBuiltInType(baseColumn);
            if (null != type && type != BuiltInColumnTypes.Container)
                addWrapColumn(baseColumn);
        }
    }

    @Override
    public String getTitleColumn()
    {
        return "AssayName";
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
        // see StudyDesignController.UpdateAssayScheduleAction @RequiresPermission(UpdatePermission.class)
        return checkContainerPermission(user, perm);
    }
}