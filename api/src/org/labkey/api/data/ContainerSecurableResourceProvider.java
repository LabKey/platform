package org.labkey.api.data;

import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;

import java.util.Collection;

public interface ContainerSecurableResourceProvider
{
    Collection<? extends SecurableResource> getSecurableResources(Container c, User user);
}
