/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.CohortController;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: brittp
 * Created: Jan 18, 2008 12:53:27 PM
 */
public class CohortTable extends BaseStudyTable
{
    private Domain _domain;

    public CohortTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoCohort());

        addFolderColumn();
        addStudyColumn();

        ColumnInfo labelColumn = addWrapColumn(_rootTable.getColumn("Label"));
        labelColumn.setNullable(false);

        ColumnInfo lsidColumn = addWrapColumn(_rootTable.getColumn("lsid"));
        lsidColumn.setHidden(true);
        lsidColumn.setUserEditable(false);
        
        ColumnInfo rowIdColumn = addWrapColumn(_rootTable.getColumn("RowId"));
        rowIdColumn.setHidden(true);
        rowIdColumn.setUserEditable(false);
        rowIdColumn.setKeyField(true);

        ColumnInfo enrolledColumn = addWrapColumn(_rootTable.getColumn("Enrolled"));
        enrolledColumn.setNullable(false);

        ColumnInfo subjectCountColumn = addWrapColumn(_rootTable.getColumn("SubjectCount"));
        ColumnInfo descriptionColumn = addWrapColumn(_rootTable.getColumn("Description"));

        // Add extended columns
        List<FieldKey> visibleColumns = new ArrayList<>();

        // visible columns from the hard table
        visibleColumns.add(FieldKey.fromParts(labelColumn.getName()));
        visibleColumns.add(FieldKey.fromParts(enrolledColumn.getName()));
        visibleColumns.add(FieldKey.fromParts(subjectCountColumn.getName()));
        visibleColumns.add(FieldKey.fromParts(descriptionColumn.getName()));

        String domainURI = CohortImpl.DOMAIN_INFO.getDomainURI(schema.getContainer());

        _domain = PropertyService.get().getDomain(schema.getContainer(), domainURI);
        if (_domain != null)
        {
            for (ColumnInfo extraColumn : _domain.getColumns(this, lsidColumn, schema.getContainer(), schema.getUser()))
            {
                safeAddColumn(extraColumn);
                visibleColumns.add(FieldKey.fromParts(extraColumn.getName()));
            }
        }
        
        setDefaultVisibleColumns(visibleColumns);

        if (!StudyManager.getInstance().showCohorts(schema.getContainer(), schema.getUser()))
            addCondition(new SQLFragment("0=1"));

        ActionURL updateURL = new ActionURL(CohortController.UpdateAction.class, getContainer());
        setUpdateURL(new DetailsURL(updateURL, Collections.singletonMap("rowId", "rowId")));

        ActionURL deleteURL = new ActionURL(CohortController.DeleteCohortAction.class, getContainer());
        setDeleteURL(new DetailsURL(deleteURL, Collections.singletonMap("rowId", "rowId")));
    }

    @Override
    public Domain getDomain()
    {
        return _domain;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        User user = _userSchema.getUser();
        if (!getContainer().hasPermission(user, AdminPermission.class))
            return null;
        return new CohortUpdateService(this);
    }

    @Override
    public boolean hasPermissionOverridable(UserPrincipal user, Class<? extends Permission> perm)
    {
        if (!(user instanceof User) || !StudyManager.getInstance().showCohorts(_userSchema.getContainer(), (User)user))
            return false;
        return canReadOrIsAdminPermission(user, perm);
    }
}
