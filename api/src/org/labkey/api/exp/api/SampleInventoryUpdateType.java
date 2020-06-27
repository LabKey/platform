package org.labkey.api.exp.api;

import java.util.Map;

public enum SampleInventoryUpdateType
{
    CheckIn,
    CheckOut,
    VolumeChange,
    FreezeThawChange,
    MetadataChange;

    public static SampleInventoryUpdateType getUpdateType(Map<String, Object> row, Map<String, Object> updatedRow)
    {
        var origCheckedOut = row.get("checkedout");
        var updatedCheckedOut = updatedRow.get("checkedout");

        if (origCheckedOut == null && updatedCheckedOut != null)
            return CheckOut;
        else if (origCheckedOut != null && updatedCheckedOut == null)
            return CheckIn;
        else if (row.get("volume") != updatedRow.get("volume"))
            return VolumeChange;
        else if (row.get("freezethawcount") != updatedRow.get("freezethawcount"))
            return FreezeThawChange;
        return MetadataChange;
    }
}
