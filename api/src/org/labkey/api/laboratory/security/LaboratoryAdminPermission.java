package org.labkey.api.laboratory.security;


import org.labkey.api.security.permissions.AbstractPermission;

/**

 */
public class LaboratoryAdminPermission extends AbstractPermission
{
    public LaboratoryAdminPermission()
    {
        super("LaboratoryAdminPermission", "This allows users to edit folder-level settings in the laboratory module");
    }
}