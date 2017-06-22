/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.study.query.studydesign;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.study.query.BaseStudyTable;
import org.labkey.study.query.StudyQuerySchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * User: cnathe
 * Date: 7/23/13
 */
public class StudyDesignLookupBaseTable extends BaseStudyTable
{
    public StudyDesignLookupBaseTable(StudyQuerySchema schema, TableInfo tableInfo)
    {
        super(schema, tableInfo);
        setDescription("Contains lookup values for dropdown options in the study designer.");

        for (ColumnInfo col : getRealTable().getColumns())
        {
            if (!col.getName().equalsIgnoreCase("Container"))
            {
                ColumnInfo newCol = addWrapColumn(col);
                if (col.isHidden())
                    newCol.setHidden(col.isHidden());

                if (newCol.getName().equalsIgnoreCase("CreatedBy") || newCol.getName().equalsIgnoreCase("ModifiedBy"))
                    UserIdQueryForeignKey.initColumn(schema.getUser(), schema.getContainer(), newCol, true);
            }
            else
                addContainerColumn();
        }


        List<FieldKey> defaultColumns = new ArrayList<>(Arrays.asList(
                FieldKey.fromParts("Name"),
                FieldKey.fromParts("Label"),
                FieldKey.fromParts("Inactive")
        ));
        setDefaultVisibleColumns(defaultColumns);
    }

    public StudyDesignLookupBaseTable(StudyQuerySchema schema, TableInfo tableInfo, @Nullable ContainerFilter containerFilter)
    {
        this(schema, tableInfo);
        if (null != containerFilter)
            _setContainerFilter(containerFilter);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (table.getTableType() == DatabaseTableType.TABLE)
            return new StudyDesignLookupsQueryUpdateService(this, table);
        return null;
    }

    @Override
    public boolean hasPermissionOverridable(UserPrincipal user, Class<? extends Permission> perm)
    {
        // Only admins are allowed to insert into these tables at the project level
        if (getContainer().isProject())
            return canReadOrIsAdminPermission(user, perm);
        else
            return getContainer().hasPermission(user, perm);
    }

    private class StudyDesignLookupsQueryUpdateService extends DefaultQueryUpdateService
    {
        public StudyDesignLookupsQueryUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }

        @Override
        public int loadRows(User user, Container container, DataIteratorBuilder rows, DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext) throws SQLException
        {
            return importRows(user, container, rows, context.getErrors(), context.getConfigParameters(), extraScriptContext);
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            if (container.isProject() && !hasPermission(user, AdminPermission.class))
                throw new QueryUpdateServiceException("Only admins are allowed to insert into this table at the project level.");

            validateValues(row);
            return super.insertRow(user, container, row);
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            if (container.isProject() && !hasPermission(user, AdminPermission.class))
                throw new QueryUpdateServiceException("Only admins are allowed to update records in this table at the project level.");

            validateValues(row);
            return super.updateRow(user, container, row, oldRow);
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            if (container.isProject() && !hasPermission(user, AdminPermission.class))
                throw new QueryUpdateServiceException("Only admins are allowed to delete records from this table at the project level.");

            return super.deleteRow(user, container, oldRowMap);
        }

        private void validateValues(Map<String, Object> row) throws ValidationException
        {
            // TODO: add validation that the same key value doesn't already exist at the project level
        }
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        // These are editable in Dataspace, but not in a folder within a Dataspace
        if (null == getContainer() || null == getContainer().getProject() || (getContainer().getProject().isDataspace() && !getContainer().isDataspace()))
            return false;
        return hasPermissionOverridable(user, perm);
    }
}
