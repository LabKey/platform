/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.study.query.studydesign.DefaultStudyDesignTable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by klum on 12/17/13.
 */
public class StudyPersonnelTable extends DefaultStudyDesignTable
{
    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts("Container"));
        defaultVisibleColumns.add(FieldKey.fromParts("Label"));
        defaultVisibleColumns.add(FieldKey.fromParts("Role"));
        defaultVisibleColumns.add(FieldKey.fromParts("URL"));
        defaultVisibleColumns.add(FieldKey.fromParts("UserId"));
    }


    public static StudyPersonnelTable create(Domain domain, UserSchema schema, @Nullable ContainerFilter containerFilter)
    {
        TableInfo storageTableInfo = StorageProvisioner.createTableInfo(domain);
        if (null == storageTableInfo)
        {
            throw new IllegalStateException("Could not create provisioned table for domain: " + domain.getTypeURI());
        }
        return new StudyPersonnelTable(domain, storageTableInfo, schema, containerFilter);
    }

    private StudyPersonnelTable(Domain domain, TableInfo storageTableInfo, UserSchema schema, @Nullable ContainerFilter containerFilter)
    {
        super(domain, storageTableInfo, schema, containerFilter);

        setName(StudyQuerySchema.PERSONNEL_TABLE_NAME);
        setDescription("Contains one row per each study personnel");
    }

    @Override
    protected void initColumn(ColumnInfo col)
    {
        if ("UserId".equalsIgnoreCase(col.getName()))
        {
            UserIdForeignKey.initColumn(col);
        }
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        // This is editable in Dataspace, but not in a folder within a Dataspace
        if (getContainer().getProject().isDataspace() && !getContainer().isDataspace())
            return false;
        return hasPermissionOverridable(user, perm);
    }
}
