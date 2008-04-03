package org.labkey.api.exp.api;

import org.labkey.api.security.User;

/**
 * An object which holds properties for a parent owner object.
 */
public interface ExpChildObject extends ExpObject
{
    public void delete(User user) throws Exception;
}
