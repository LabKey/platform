package org.labkey.api.security.roles;

import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.AbstractRole;

import java.util.Set;

/**
 * Created by Marty on 12/17/2014.
 */
public abstract class AbstractArgosRole extends AbstractRole
{
    protected AbstractArgosRole(String name, String description, Class<? extends Permission>... perms)
    {
        super(name, description, perms);
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        if( resource instanceof Container && !((Container)resource).isRoot() )
        {
            Set<Module> modules = ((Container)resource).getActiveModules();
            for(Module mod : modules )
            {
                if(mod.getName().equals("Argos"))
                    return true;
            }
        }

        return false;
    }

}
