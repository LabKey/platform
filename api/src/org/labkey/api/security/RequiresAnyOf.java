package org.labkey.api.security;

import org.labkey.api.security.permissions.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Created by adam on 6/6/2015.
 */

/**
 * Specifies a set of allowed permissions for an action, for situations where ANY ONE of the permissions is sufficient.
 * It does not imply that the user needs to be logged in or otherwise authenticated.
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface RequiresAnyOf
{
    Class<? extends Permission>[] value();
}