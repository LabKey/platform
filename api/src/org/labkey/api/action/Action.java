package org.labkey.api.action;

import org.labkey.api.security.permissions.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * User: adam
 * Date: 2/12/14
 * Time: 5:45 PM
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface Action
{
    ActionType value();
}
