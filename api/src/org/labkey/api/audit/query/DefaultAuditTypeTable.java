package org.labkey.api.audit.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

import java.util.Map;

/**
 * User: klum
 * Date: 7/11/13
 */
public class DefaultAuditTypeTable extends FilteredTable<UserSchema>
{
    protected AuditTypeProvider _provider;
    protected Map<String, String> _legacyNameMap = _provider.legacyNameMap();

    public DefaultAuditTypeTable(AuditTypeProvider provider, Domain domain, DbSchema dbSchema, UserSchema schema)
    {
        super(StorageProvisioner.createTableInfo(domain, dbSchema), schema);

        _provider = provider;

        wrapAllColumns(true);
        initColumns();
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

        return null;
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
