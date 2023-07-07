package org.labkey.assay.plate.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.assay.query.AssayDbSchema;

import java.util.ArrayList;
import java.util.List;

public class WellTable extends SimpleUserSchema.SimpleTable<UserSchema>
{
    public static final String NAME = "Well";
    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts("PlateId"));
        defaultVisibleColumns.add(FieldKey.fromParts("Row"));
        defaultVisibleColumns.add(FieldKey.fromParts("Col"));
    }

    public WellTable(PlateSchema schema, @Nullable ContainerFilter cf)
    {
        super(schema, AssayDbSchema.getInstance().getTableInfoWell(), cf);
        // set as readonly for now
        _readOnly = true;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }
}
