/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.wiki.WikiRendererDisplayColumn;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.study.StudySchema;

/**
 * Created by klum on 12/17/13.
 */
public class StudyObjectiveTable extends BaseStudyTable
{
    public StudyObjectiveTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoObjective(), true);

        setDescription("Contains one row per study objective");
        ColumnInfo rowIdColumn = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("RowId")));
        rowIdColumn.setKeyField(true);
        rowIdColumn.setHidden(true);

        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Label")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Type")));

        ColumnInfo descriptionColumn = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Description")));
        final ColumnInfo descriptionRendererTypeColumn = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("DescriptionRendererType")));
        descriptionRendererTypeColumn.setFk(new LookupForeignKey("Value")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return QueryService.get().getUserSchema(_userSchema.getUser(), _userSchema.getContainer(), WikiService.SCHEMA_NAME).getTable(WikiService.RENDERER_TYPE_TABLE_NAME);
            }
        });
        descriptionRendererTypeColumn.setHidden(true);
        descriptionColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new WikiRendererDisplayColumn(colInfo, descriptionRendererTypeColumn.getName(), WikiRendererType.TEXT_WITH_LINKS);
            }
        });

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

    @Override
    public boolean supportsContainerFilter()
    {
        return true;
    }
}
