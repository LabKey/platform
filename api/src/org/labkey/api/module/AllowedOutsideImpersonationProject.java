package org.labkey.api.module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Designates actions that should not enforce impersonation container restrictions.
 * Use with caution. Allows project admins to perform actions outside of the project they administer.
 *
 * @see org.labkey.api.action.PermissionCheckableAction#checkPermissions()
 * @see org.labkey.api.data.Container#isForbiddenProject(org.labkey.api.security.User)
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AllowedOutsideImpersonationProject
{
}
