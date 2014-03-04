/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.study.StudySchema;
import org.labkey.study.query.studydesign.StudyDesignLabsTable;
import org.labkey.study.query.studydesign.StudyDesignSampleTypesTable;

/**
 * User: cnathe
 * Date: 12/13/13
*/
public class AssaySpecimenTable extends BaseStudyTable
{
    public AssaySpecimenTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoAssaySpecimen());
        setName(StudyQuerySchema.ASSAY_SPECIMEN_TABLE_NAME);

        addWrapColumn(_rootTable.getColumn("RowId"));
        addWrapColumn(_rootTable.getColumn("AssayName"));
        addWrapColumn(_rootTable.getColumn("Description"));
        addWrapLocationColumn("LocationId", "LocationId");
        addWrapColumn(_rootTable.getColumn("Source"));
        //addWrapTypeColumn("PrimaryTypeId", "PrimaryTypeId");
        //addWrapTypeColumn("DerivativeTypeId", "DerivativeTypeId");
        addWrapColumn(_rootTable.getColumn("TubeType"));

        ColumnInfo labColumn = new AliasedColumn(this, "Lab", _rootTable.getColumn("Lab"));
        labColumn.setFk(new LookupForeignKey("Name")
        {
            public TableInfo getLookupTableInfo()
            {
                return new StudyDesignLabsTable(_userSchema);
            }
        });
        addColumn(labColumn);

        ColumnInfo sampleTypeColumn = new AliasedColumn(this, "SampleType", _rootTable.getColumn("SampleType"));
        sampleTypeColumn.setFk(new LookupForeignKey("Name")
        {
            public TableInfo getLookupTableInfo()
            {
                return new StudyDesignSampleTypesTable(_userSchema);
            }
        });
        addColumn(sampleTypeColumn);

        addContainerColumn();
        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            String name = baseColumn.getName();
            if (name.equalsIgnoreCase("Created") || name.equalsIgnoreCase("Modified") || name.equalsIgnoreCase("CreatedBy") || name.equalsIgnoreCase("ModifiedBy"))
            {
                ColumnInfo column = addWrapColumn(baseColumn);
                if (name.equalsIgnoreCase("CreatedBy") || name.equalsIgnoreCase("ModifiedBy"))
                    UserIdQueryForeignKey.initColumn(schema.getUser(), schema.getContainer(), column, true);
                column.setHidden(true);
                column.setUserEditable(false);
                column.setShownInInsertView(false);
                column.setShownInUpdateView(false);
            }
        }
    }

    @Override
    public String getTitleColumn()
    {
        return "AssayName";
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