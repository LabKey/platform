package org.labkey.api.security.roles;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ViewContext;
import org.labkey.api.security.RequiresPermissionClass;

import java.util.Set;

/**
 * User: kevink
 * Date: Jun 1, 2009 11:35:01 AM
 *
 * Get any contextual {@link Role}s for a given {@link ViewContext}.
 * You may annotate an action with {@link RequiresPermissionClass#contextual()}
 * and have contextual roles added during permissions checking.
 *
 * @see RequiresPermissionClass
 */
public interface HasContextualRoles
{
    @Nullable
    public Set<Role> getContextualRoles(ViewContext context);
}
