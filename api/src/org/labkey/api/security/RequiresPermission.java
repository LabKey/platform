package org.labkey.api.security;

import org.labkey.api.security.permissions.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Specifies the required permission for an action. It does not imply that the user needs to be logged in or otherwise
 * authenticated.
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface RequiresPermission
{
    Class<? extends Permission> value();
}