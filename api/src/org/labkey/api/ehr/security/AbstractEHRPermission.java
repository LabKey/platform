package org.labkey.api.ehr.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.permissions.AbstractPermission;
import org.labkey.api.security.permissions.Permission;

/**
 * User: bimber
 * Date: 8/6/13
 * Time: 7:42 PM
 */
abstract public class AbstractEHRPermission extends AbstractPermission
{
    protected AbstractEHRPermission(@NotNull String name, @NotNull String description)
    {
        super(name, description);
    }
}
