package org.labkey.api.security.roles;

import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.Permission;

/**
 * A base class for Roles that are only available in the root container.
 * Created by Josh on 1/25/2016.
 */
public abstract class AbstractRootContainerRole extends AbstractRole
{
    protected AbstractRootContainerRole(String name, String description, Class<? extends Permission>... perms)
    {
        super(name, description, perms);
    }

    protected AbstractRootContainerRole(String name, String description, Class<? extends Module> sourceModuleClass, Class<? extends Permission>... perms)
    {
        super(name, description, sourceModuleClass, perms);
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return resource instanceof Container && ((Container)resource).isRoot();
    }
}
