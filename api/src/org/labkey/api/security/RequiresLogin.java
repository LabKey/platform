package org.labkey.api.security;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Jan 11, 2007
 * Time: 11:35:28 AM
 */
public @Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target({ElementType.METHOD,ElementType.TYPE})
@interface RequiresLogin
{
}
