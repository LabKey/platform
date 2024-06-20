package org.labkey.assay.plate.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.assay.query.AssayDbSchema;

import java.util.ArrayList;
import java.util.List;

public class PlateTypeTable extends SimpleUserSchema.SimpleTable<UserSchema>
{
    public static final String NAME = "PlateType";
    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts("Description"));
        defaultVisibleColumns.add(FieldKey.fromParts("Rows"));
        defaultVisibleColumns.add(FieldKey.fromParts("Columns"));
    }

    public PlateTypeTable(PlateSchema schema, @Nullable ContainerFilter cf)
    {
        super(schema, AssayDbSchema.getInstance().getTableInfoPlateType(), cf);
        setTitleColumn("Description");
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (InsertPermission.class.equals(perm) || UpdatePermission.class.equals(perm) || DeletePermission.class.equals(perm))
            return false;
        return super.hasPermission(user, perm);
    }
}
