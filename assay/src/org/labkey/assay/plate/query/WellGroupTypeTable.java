package org.labkey.assay.plate.query;

import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.data.EnumTableInfo;
import org.labkey.api.query.UserSchema;

public class WellGroupTypeTable extends EnumTableInfo<WellGroup.Type>
{
    public static final String NAME = "WellGroupType";

    public WellGroupTypeTable(UserSchema schema)
    {
        super(WellGroup.Type.class, schema, "All supported well group types", true);
        setName(NAME);
        setPublicName(NAME);
    }
}
