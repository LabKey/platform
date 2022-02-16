package org.labkey.api.security.permissions;

// We extend ReadPermission because in FilteredTable.hasPermission, we check
//      if (ReadPermission.class.isAssignableFrom(perm)) ...
// as a gate to checking the user schema and contextual roles
public class DataClassReadPermission extends ReadPermission
{
    public DataClassReadPermission()
    {
        super("Data Class Read", "Can read data class data.");
    }
}
