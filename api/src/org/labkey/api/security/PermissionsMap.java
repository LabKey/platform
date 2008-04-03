package org.labkey.api.security;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Apr 24, 2006
 * Time: 11:46:37 AM
 *
 * Compute a set of permissions given a User u and an Object o.  Often this will
 * be done by finding an ACL a and calling a.getPermssions(u)
 */
public interface PermissionsMap<T>
{
    int getPermissions(User u, T t);
}
