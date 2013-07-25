package org.labkey.api.audit.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DbSchema;
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
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: 7/11/13
 */
public class DefaultAuditTypeTable extends FilteredTable<UserSchema>
{
    protected AuditTypeProvider _provider;
    protected Map<FieldKey, String> _legacyNameMap;
    protected List<FieldKey> _defaultVisibleColumns = new ArrayList<>();
    
    public DefaultAuditTypeTable(AuditTypeProvider provider, Domain domain, DbSchema dbSchema, UserSchema schema)
    {
        super(StorageProvisioner.createTableInfo(domain, dbSchema), schema);

        _provider = provider;
        this._legacyNameMap = provider.legacyNameMap();

        wrapAllColumns(true);

        _defaultVisibleColumns.add(FieldKey.fromParts("Created"));
        _defaultVisibleColumns.add(FieldKey.fromParts("CreatedBy"));
        _defaultVisibleColumns.add(FieldKey.fromParts("ImpersonatedBy"));
        _defaultVisibleColumns.add(FieldKey.fromParts("ProjectId"));
        _defaultVisibleColumns.add(FieldKey.fromParts("Comment"));

        // setup lookups for the standard fields
        ColumnInfo created = getColumn("Created");
        created.setLabel("Date");

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
        String newName = _legacyNameMap.get(name);
        col = super.resolveColumn(newName);
        if (col != null)
            return col;

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
        return new DefaultQueryUpdateService(this, this.getRealTable());
    }

    @Override
    public String getDescription()
    {
        if (_provider != null)
        {
            return StringUtils.defaultIfEmpty(_provider.getDescription(), super.getDescription());
        }
        return super.getDescription();
    }

    private boolean isGuest(UserPrincipal user)
    {
        return user instanceof User && user.isGuest();
    }

    @Override
    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        // Don't allow deletes or updates for audit events, and don't let guests insert
        return ((perm.equals(InsertPermission.class) && !isGuest(user)) || perm.equals(ReadPermission.class)) &&
            getContainer().hasPermission(user, perm);
    }
}
