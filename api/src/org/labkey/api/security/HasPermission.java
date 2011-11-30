package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.permissions.Permission;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2011-11-29
 * Time: 12:25 PM
 */
public interface HasPermission
{
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm);
}
