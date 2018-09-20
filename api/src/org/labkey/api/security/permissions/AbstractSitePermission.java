package org.labkey.api.security.permissions;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;

public abstract class AbstractSitePermission extends AbstractPermission
{
    protected AbstractSitePermission(@NotNull String name, @NotNull String description)
    {
        super(name, description);
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return resource instanceof Container && ((Container)resource).isRoot();
    }
}
