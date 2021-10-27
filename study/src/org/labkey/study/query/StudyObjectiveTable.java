/*
 * Copyright (c) 2013-2019 LabKey Corporation
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

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
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
    public StudyObjectiveTable(StudyQuerySchema schema, ContainerFilter cf)
    {
        super(schema, StudySchema.getInstance().getTableInfoObjective(), cf, true);

        setDescription("Contains one row per study objective");
        var rowIdColumn = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("RowId")));
        rowIdColumn.setKeyField(true);
        rowIdColumn.setHidden(true);

        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Label")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Type")));

        var descriptionColumn = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Description")));
        final var descriptionRendererTypeColumn = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("DescriptionRendererType")));

        WikiService ws = WikiService.get();

        if (null != ws)
        {
            descriptionRendererTypeColumn.setFk(new LookupForeignKey("Value")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    return ws.getRendererTypeTable(_userSchema.getUser(), _userSchema.getContainer());
                }
            });
        }

        descriptionRendererTypeColumn.setHidden(true);
        descriptionColumn.setDisplayColumnFactory(colInfo -> new WikiRendererDisplayColumn(colInfo, descriptionRendererTypeColumn.getName(), WikiRendererType.TEXT_WITH_LINKS));

        // setup lookups for the standard fields
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Container")));

        var created = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Created")));
        created.setFormat("DateTime");
        created.setHidden(true);

        var createdBy = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("CreatedBy")));
        createdBy.setLabel("Created By");
        createdBy.setHidden(true);

        var modified = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Modified")));
        modified.setFormat("DateTime");
        modified.setHidden(true);

        var modifiedBy = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("ModifiedBy")));
        modifiedBy.setLabel("Modified By");
        modifiedBy.setHidden(true);
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

    @Override
    protected boolean hasPermissionOverridable(UserPrincipal user, Class<? extends Permission> perm)
    {
        return checkReadOrIsAdminPermission(user, perm);
    }
}
