package org.labkey.api.security.permissions;

public class AssayReadPermission extends AbstractPermission
{
    public AssayReadPermission()
    {
        super("Assay Read", "Can read assay run, batch, and result data.");
    }
}
