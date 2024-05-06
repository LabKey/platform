package org.labkey.assay.plate.query;

import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.data.EnumTableInfo;
import org.labkey.api.query.UserSchema;

public class WellTypeTable extends EnumTableInfo<WellGroup.Type>
{
    public static final String NAME = "WellType";

    public WellTypeTable(UserSchema schema)
    {
        super(WellGroup.Type.class, schema, "All supported well types", true);
        setName(NAME);
        setPublicName(NAME);
    }
}
