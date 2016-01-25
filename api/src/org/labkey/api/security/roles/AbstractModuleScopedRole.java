package org.labkey.api.security.roles;

import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.Permission;

/**
 * An {@link AbstractRole} that only shows up as an option when its providing module is enabled in the current container.
 * Created by Josh on 1/25/2016.
 */
public abstract class AbstractModuleScopedRole extends AbstractRole
{
    protected AbstractModuleScopedRole(String name, String description, Class<? extends Module> sourceModuleClass, Class<? extends Permission>... perms)
    {
        super(name, description, sourceModuleClass, perms);
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return resource instanceof Container && ((Container) resource).getActiveModules().contains(getSourceModule());
    }
}
