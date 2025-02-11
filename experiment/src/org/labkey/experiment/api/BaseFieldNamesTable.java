package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;

public abstract class BaseFieldNamesTable extends FilteredTable<ExpSchema>
{
    private final String _tableName;

    public BaseFieldNamesTable(String tableName, @NotNull ExpSchema userSchema, @Nullable ContainerFilter containerFilter)
    {
        super(OntologyManager.getTinfoPropertyDescriptor(), userSchema, containerFilter);
        _tableName = tableName;

        setName(tableName);
        addWrapColumn(_rootTable.getColumn("Container"));
        addColumn("DomainURI", JdbcType.VARCHAR);
        addColumn("DomainName", JdbcType.VARCHAR);
        addWrapColumn(_rootTable.getColumn("Name"));
        addWrapColumn(_rootTable.getColumn("Label"));
        addWrapColumn(_rootTable.getColumn("Description"));
        addWrapColumn(_rootTable.getColumn("RangeURI"));
    }

    protected MutableColumnInfo addColumn(String name, JdbcType type)
    {
        return addColumn(new BaseColumnInfo(FieldKey.fromParts(name), this, type));
    }

    // This is a hack, required because ContainerFilter.Type.Current doesn't fill in the user
    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, AdminPermission.class);
    }

    @Override
    protected SimpleFilter.FilterClause getContainerFilterClause(ContainerFilter filter, FieldKey fieldKey)
    {
        // Show only folders where current user has admin permissions
        return filter.createFilterClause(getSchema(), fieldKey, AdminPermission.class, null);
    }

    @Override
    @NotNull
    public SQLFragment getFromSQL(String alias)
    {
        SQLFragment result = new SQLFragment();
        result.appendComment("<" + _tableName + ">", getSqlDialect());
        result.append("(SELECT dd.Name AS DomainName, dd.DomainUri, pd.*");

        addColumnSQL(result);

        result.append(" FROM ")
            .append(super.getFromSQL("pd"))
            .append("""

                JOIN exp.PropertyDomain AS pdom ON pdom.PropertyId = pd.PropertyId
                JOIN exp.DomainDescriptor AS dd ON dd.DomainId = pdom.DomainId""");

        result.append(")\n")
            .append(alias)
            .appendComment("</" + _tableName + ">", getSqlDialect());

        return result;
    }

    protected abstract void addColumnSQL(SQLFragment sql);
}
