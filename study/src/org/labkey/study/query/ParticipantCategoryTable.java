/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserIdRenderer;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.DataspaceContainerFilter;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.ParticipantGroupController;

import java.util.Collections;

/**
 * User: brittp
 * Date: Jun 28, 2011 12:50:30 PM
 */
public class ParticipantCategoryTable extends BaseStudyTable
{
    public ParticipantCategoryTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoParticipantCategory());
        setName(StudyService.get().getSubjectCategoryTableName(schema.getContainer()));
        setDescription("This table contains one row for each study group category");

        // fix up container filter to include project if dataspace study
        if (schema.getContainer().isProject() && getContainerFilter() instanceof DataspaceContainerFilter)
            _setContainerFilter(((DataspaceContainerFilter)getContainerFilter()).getIncludeProjectDatasetContainerFilter());

        ColumnInfo rowIdColumn = addWrapColumn(_rootTable.getColumn("RowId"));
        rowIdColumn.setHidden(true);
        rowIdColumn.setUserEditable(false);
        rowIdColumn.setKeyField(true);

        addWrapColumn(_rootTable.getColumn("Label"));
        addWrapColumn(_rootTable.getColumn("Type"));
        addWrapColumn(_rootTable.getColumn("Created"));
        addWrapColumn(_rootTable.getColumn("OwnerId"));

        ColumnInfo createdBy = wrapColumn("CreatedBy", getRealTable().getColumn("CreatedBy"));
        createdBy.setFk(new UserIdForeignKey(getUserSchema()));
        createdBy.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new UserIdRenderer(colInfo);
            }
        });
        addColumn(createdBy);

        ActionURL deleteRowsURL = new ActionURL(ParticipantGroupController.DeleteParticipantCategories.class, schema.getContainer());
        setDeleteURL(new DetailsURL(deleteRowsURL, Collections.singletonMap("rowId", FieldKey.fromString("RowId"))));
    }

    @Override
    public boolean hasPermissionOverridable(UserPrincipal user, Class<? extends Permission> perm)
    {
        if (perm.equals(DeletePermission.class))
            return getContainer().hasPermission(user, perm);
        else
            return false;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (table != null && table.getTableType() == DatabaseTableType.TABLE)
            return new DefaultQueryUpdateService(this, table);
        return null;
    }
}
