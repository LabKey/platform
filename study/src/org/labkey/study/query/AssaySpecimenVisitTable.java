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
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DelegatingContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.study.StudySchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: cnathe
 * Date: 12/13/13
 */
public class AssaySpecimenVisitTable extends BaseStudyTable
{
    public AssaySpecimenVisitTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoAssaySpecimenVisit());
        setName(StudyQuerySchema.ASSAY_SPECIMEN_VISIT_TABLE_NAME);

        addWrapColumn(_rootTable.getColumn("RowId"));

        ColumnInfo visitCol = new AliasedColumn(this, "VisitId", _rootTable.getColumn("VisitId"));
        visitCol.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new VisitTable(_userSchema);
            }
        });
        addColumn(visitCol);

        ColumnInfo assaySpecimenCol = new AliasedColumn(this, "AssaySpecimenId", _rootTable.getColumn("AssaySpecimenId"));
        assaySpecimenCol.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new AssaySpecimenTable(_userSchema);
            }
        });
        addColumn(assaySpecimenCol);

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
