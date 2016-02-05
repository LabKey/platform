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
package org.labkey.api.audit.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.permissions.CanSeeAuditLogPermission;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.CanSeeAuditLogRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/11/13
 */
public class DefaultAuditTypeTable extends FilteredTable<UserSchema>
{
    protected AuditTypeProvider _provider;
    protected Map<FieldKey, String> _legacyNameMap;
    protected Map<String, String> _dbSchemaToColumnMap;


    @Deprecated
    public DefaultAuditTypeTable(AuditTypeProvider provider, Domain domain, UserSchema schema)
    {
        this(provider, StorageProvisioner.createTableInfo(domain), schema, null);
    }


    public DefaultAuditTypeTable(AuditTypeProvider provider, TableInfo storage, UserSchema schema, List<FieldKey> defaultVisibleColumns)
    {
        super(storage, schema, ContainerFilter.Type.CurrentWithUser.create(schema.getUser()));

        _provider = provider;

        setName(_provider.getEventName());
        if (_provider.getDescription() != null)
            setDescription(_provider.getDescription());

        _legacyNameMap = provider.legacyNameMap();

        // Create a mapping from the real dbTable names to the legacy query table names for QueryUpdateService.
        _dbSchemaToColumnMap = new CaseInsensitiveHashMap<>();

        for (FieldKey legacyName : _legacyNameMap.keySet())
        {
            String newName = _legacyNameMap.get(legacyName);
            _dbSchemaToColumnMap.put(newName, legacyName.getName());
        }

        setTitle(provider.getEventName());

        wrapAllColumns(true);

        if (null == defaultVisibleColumns || defaultVisibleColumns.isEmpty())
        {
            defaultVisibleColumns = new ArrayList<>();
            defaultVisibleColumns.add(FieldKey.fromParts("Created"));
            defaultVisibleColumns.add(FieldKey.fromParts("CreatedBy"));
            defaultVisibleColumns.add(FieldKey.fromParts("ImpersonatedBy"));
            defaultVisibleColumns.add(FieldKey.fromParts("ProjectId"));
            defaultVisibleColumns.add(FieldKey.fromParts("Comment"));
        }
        setDefaultVisibleColumns(defaultVisibleColumns);

        ColumnInfo rowIdColumn = getColumn(FieldKey.fromParts("rowId"));
        rowIdColumn.setSortDirection(Sort.SortDirection.DESC);

        // setup lookups for the standard fields
        ColumnInfo created = getColumn("Created");
        created.setLabel("Date");
        created.setFormat("DateTime");

        ColumnInfo container = getColumn("Container");
        ContainerForeignKey.initColumn(container, schema);

        ColumnInfo project = getColumn("ProjectId");
        project.setLabel("Project");
        ContainerForeignKey.initColumn(project, schema);

        ColumnInfo createdBy = getColumn(FieldKey.fromParts("CreatedBy"));
        createdBy.setLabel("Created By");
        UserIdForeignKey.initColumn(createdBy);

        ColumnInfo impersonatedBy = getColumn(FieldKey.fromParts("ImpersonatedBy"));
        impersonatedBy.setLabel("Impersonated By");
        UserIdForeignKey.initColumn(impersonatedBy);

        initColumns();
    }

    protected void initColumns()
    {
        for (ColumnInfo col : getColumns())
            initColumn(col);
    }

    /**
     * issue 19515:  be sure to apply the CanSeeAuditLog permission to the container filter so that folder filters
     * do not show unauthorized events
     */
    @Override
    protected SimpleFilter.FilterClause getContainerFilterClause(ContainerFilter filter, FieldKey fieldKey)
    {
        User user = (null == getUserSchema()) ? null : getUserSchema().getUser();
        Set<Role> roles = SecurityManager.canSeeAuditLog(user) ? RoleManager.roleSet(CanSeeAuditLogRole.class) : null;
        return filter.createFilterClause(getSchema(), fieldKey, getContainer(), CanSeeAuditLogPermission.class, roles);
    }

    // Subclasses may override this to provide customizations to the column
    protected void initColumn(ColumnInfo col)
    {
    }

    @Nullable
    @Override
    public Domain getDomain()
    {
        return _provider.getDomain();
    }

    @Nullable
    @Override
    public AbstractAuditDomainKind getDomainKind()
    {
        return (AbstractAuditDomainKind) super.getDomainKind();
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo col = super.resolveColumn(name);
        if (col != null)
            return col;

        // Handle the old style 'intKey1' and 'key1' columns
        String newName = _legacyNameMap.get(FieldKey.fromParts(name));
        col = super.resolveColumn(newName);
        if (col != null)
        {
            // Wrap the column with AliasedColumn so the requested name is used as the column name
            // and copy the real column's label to the aliased column so it will be used in the DataRegion grid.
            AliasedColumn a = new AliasedColumn(this, name, col);
            a.setLabel(col.getLabel());
            addColumn(a);
            return a;
        }


        // Now check for 'Property/...' columns
        if (name.equalsIgnoreCase("Property"))
        {
            // UNDONE: backwards compat to "Property/*" columns
//            col = new ColumnInfo("Property", this);
//            col.setFk(new LookupForeignKey()
//            {
//                @Override
//                public TableInfo getLookupTableInfo()
//                {
//                    return new VirtualPropertiesTable();
//                }
//            });
        }

        // Other legacy audit columns

        return null;
    }

//    private class VirtualPropertiesTable extends VirtualTable
//    {
//        public VirtualPropertiesTable(DbSchema schema)
//        {
//            super(schema, DefaultAuditTypeTable.this.getUserSchema());
//
//            for (FieldKey fieldKey : _legacyNameMap.keySet())
//            {
//                if (fieldKey.getParent().equals("Property"))
//                    this.addColumn(...)
//            }
//        }
//    }

    @Nullable
    @Override
    public QueryUpdateService getUpdateService()
    {
        return new DefaultQueryUpdateService(this, this.getRealTable(), _dbSchemaToColumnMap);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        // Allow read, but not insert, update, or delete.
        return perm.equals(ReadPermission.class) && getContainer().hasPermission(user, perm);
    }
}
