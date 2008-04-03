package org.labkey.api.security;

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;

/**
 * User: adam
 * Date: Dec 20, 2007
 * Time: 1:13:03 PM
 */
public @Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target({ElementType.METHOD,ElementType.TYPE})
@interface ActionNames
{
    String value();
}
