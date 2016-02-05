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
package org.labkey.study.query.studydesign;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.study.StudySchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by klum on 12/12/13.
 */
public class DefaultStudyDesignTable extends FilteredTable<UserSchema>
{
    protected List<FieldKey> _defaultVisibleColumns = new ArrayList<>();
    private Domain _domain;


    protected DefaultStudyDesignTable(Domain domain, TableInfo storageTableInfo,  UserSchema schema)
    {
        super(storageTableInfo, schema);
        _domain = domain;

        for (ColumnInfo baseColumn : getRealTable().getColumns())
        {
            ColumnInfo col = addWrapColumn(baseColumn);

            if (baseColumn.isHidden())
                col.setHidden(true);

            String propertyURI = col.getPropertyURI();
            if (null != propertyURI)
            {
                DomainProperty property = domain.getPropertyByURI(propertyURI);
                if (property != null)
                {
                    PropertyDescriptor pd = property.getPropertyDescriptor();
                    if (pd != null)
                    {
                        if (pd.getLookupQuery() != null || pd.getConceptURI() != null)
                            col.setFk(new PdLookupForeignKey(schema.getUser(), pd, schema.getContainer()));

                        if (pd.getPropertyType() == PropertyType.MULTI_LINE)
                        {
                            col.setDisplayColumnFactory(colInfo -> {
                                DataColumn dc = new DataColumn(colInfo);
                                dc.setPreserveNewlines(true);
                                return dc;
                            });
                        }
                        col.setName(pd.getName());
                    }
                }
            }
        }
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


    protected DefaultStudyDesignTable(Domain domain, TableInfo storageTableInfo,  UserSchema schema, @Nullable ContainerFilter containerFilter)
    {
        this(domain, storageTableInfo, schema);
        if (null != containerFilter)
            _setContainerFilter(containerFilter);
    }


    public static DefaultStudyDesignTable create(Domain domain, UserSchema schema, @Nullable ContainerFilter containerFilter)
    {
        TableInfo storageTableInfo = StorageProvisioner.createTableInfo(domain);
        return new DefaultStudyDesignTable(domain, storageTableInfo, schema, containerFilter);
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
    // ONLY OVERRIDE THIS IF TABLE SHOULD BE VISIBLE IN DATASPACE PROJECT-LEVEL CONTAINER
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
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
    }
}
