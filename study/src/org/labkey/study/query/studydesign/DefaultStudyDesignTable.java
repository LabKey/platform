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
package org.labkey.study.query.studydesign;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyManager;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by klum on 12/12/13.
 */
public class DefaultStudyDesignTable extends FilteredTable<UserSchema>
{
    protected List<FieldKey> _defaultVisibleColumns = new ArrayList<>();
    private Domain _domain;

    public DefaultStudyDesignTable(Domain domain, DbSchema dbSchema, UserSchema schema)
    {
        super(StorageProvisioner.createTableInfo(domain, dbSchema), schema);

        _domain = domain;
        wrapAllColumns(true);

        _defaultVisibleColumns.add(FieldKey.fromParts("Container"));
        _defaultVisibleColumns.add(FieldKey.fromParts("Created"));
        _defaultVisibleColumns.add(FieldKey.fromParts("CreatedBy"));

        // setup lookups for the standard fields
        ColumnInfo container = getColumn("Container");
        ContainerForeignKey.initColumn(container, schema);

        ColumnInfo created = getColumn("Created");
        created.setFormat("DateTime");

        ColumnInfo createdBy = getColumn(FieldKey.fromParts("CreatedBy"));
        createdBy.setLabel("Created By");
        UserIdForeignKey.initColumn(createdBy);

        ColumnInfo modified = getColumn("Modified");
        modified.setFormat("DateTime");

        ColumnInfo modifiedBy = getColumn(FieldKey.fromParts("ModifiedBy"));
        modifiedBy.setLabel("Modified By");
        UserIdForeignKey.initColumn(modifiedBy);

        setPublicSchemaName("study");

        initColumns();
    }

    public DefaultStudyDesignTable(Domain domain, DbSchema dbSchema, UserSchema schema, @Nullable ContainerFilter containerFilter)
    {
        this(domain, dbSchema, schema);
        if (null != containerFilter)
            _setContainerFilter(containerFilter);
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return _defaultVisibleColumns;
    }

    protected void initColumns()
    {
        for (ColumnInfo col : getColumns())
            initColumn(col);
    }

    // Subclasses may override this to provide customizations to the column
    protected void initColumn(ColumnInfo col)
    {
    }

    @Nullable
    @Override
    public Domain getDomain()
    {
        return _domain;
    }

    @Nullable
    @Override
    public QueryUpdateService getUpdateService()
    {
        return new DefaultQueryUpdateService(this, this.getRealTable());
    }

    @Override
    public String getDescription()
    {
        return super.getDescription();
    }

    @Override
    // ONLY OVERRIDE THIS IF TABLE SHOULD BE VISIBLE IN DATASPACE PROJECT-LEVEL CONTAINER
    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        // Most tables should not editable in Dataspace
        if (getContainer().isDataspace())
            return false;
        return hasPermissionOverridable(user, perm);
    }

    public boolean hasPermissionOverridable(UserPrincipal user, Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    /**
     * Deletes all data for the specified container.
     * @param c
     * @param user
     */
    public void deleteData(Container c, User user)
    {
        TableInfo data = getRealTable();
        DbScope scope =  StudySchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            Table.delete(data, new SimpleFilter().addWhereClause("Container=?", new Object[] {getContainer()}));
            transaction.commit();
        }
        catch (SQLException s)
        {
            throw new RuntimeSQLException(s);
        }
    }
}
