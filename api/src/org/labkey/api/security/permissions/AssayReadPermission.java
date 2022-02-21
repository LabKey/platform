package org.labkey.api.security.permissions;

// We extend ReadPermission because in FilteredTable.hasPermission, we check
//      if (ReadPermission.class.isAssignableFrom(perm)) ...
// as a gate to checking the user schema and contextual roles
@AllowedForReadOnlyUser
public class AssayReadPermission extends ReadPermission
{
    public AssayReadPermission()
    {
        super("Assay Read", "Can read assay run, batch, and result data.");
    }
}
