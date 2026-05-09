package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.security.permissions.AdminPermission;

public abstract class BaseFieldsTable extends FilteredTable<ExpSchema>
{
    private final String _tableName;

    public BaseFieldsTable(String tableName, @NotNull ExpSchema userSchema, @Nullable ContainerFilter containerFilter)
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
        addWrapColumn(_rootTable.getColumn("StorageColumnName"));
        addWrapColumn(_rootTable.getColumn("ConceptURI"));

        // Add the other columns in case they're useful for future troubleshooting, but keep them out
        // of the way by default
        for (ColumnInfo column : _rootTable.getColumns())
        {
            if (getColumn(column.getName()) == null)
            {
                addWrapColumn(column).setHidden(true);
            }
        }

    }

    protected MutableColumnInfo addColumn(String name, JdbcType type)
    {
        return addColumn(new BaseColumnInfo(FieldKey.fromParts(name), this, type));
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
            .appendIdentifier(alias)
            .appendComment("</" + _tableName + ">", getSqlDialect());

        return result;
    }

    protected abstract void addColumnSQL(SQLFragment sql);
}
