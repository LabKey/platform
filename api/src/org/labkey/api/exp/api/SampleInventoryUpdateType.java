package org.labkey.api.exp.api;

import java.util.Map;

public enum SampleInventoryUpdateType
{
    CheckIn("Item checked in"),
    CheckOut("Item checked out"),
    VolumeChange("Volume updated"),
    FreezeThawChange("Freeze/Thaw count updated"),
    MetadataChange("Megadata changed");

    private final String _message;

    SampleInventoryUpdateType(String message)
    {
        _message = message;
    }

    public String getMessage()
    {
        return _message;
    }

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
