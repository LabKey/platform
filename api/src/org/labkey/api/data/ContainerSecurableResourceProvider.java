package org.labkey.api.data;

import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;

import java.util.Collection;

/** A provider for SecurableResource implementations that are scoped to a container */
public interface ContainerSecurableResourceProvider
{
    /** @return all of the resources scoped to the container that are visible by the user */
    Collection<? extends SecurableResource> getSecurableResources(Container c, User user);
}
