package org.labkey.list.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;

/**
 * Created by Joe on 8/19/2014.
 */
public class ListManagerTable extends FilteredTable<ListManagerSchema>
{
    public ListManagerTable(ListManagerSchema userSchema, TableInfo table)
    {
        super(table, userSchema);
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("ListID"))).setHidden(true);
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Name")));
        ColumnInfo container =addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Container")));
        ContainerForeignKey.initColumn(container, userSchema);
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Created"))).setHidden(true);
        ColumnInfo createdBy = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("CreatedBy")));
        createdBy.setHidden(true);
        UserIdForeignKey.initColumn(createdBy);
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Modified"))).setHidden(true);
        ColumnInfo modifiedBy = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("ModifiedBy")));
        modifiedBy.setHidden(true);
        UserIdForeignKey.initColumn(modifiedBy);
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Description")));
    }
    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return _userSchema.getContainer().hasPermission(this.getClass().getName() + " " + getName(), user, perm);
    }
}
