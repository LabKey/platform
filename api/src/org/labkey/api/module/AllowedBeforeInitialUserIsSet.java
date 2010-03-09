package org.labkey.api.module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * User: adam
 * Date: Mar 9, 2010
 * Time: 12:21:34 PM
 */
public @Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target({ElementType.METHOD,ElementType.TYPE})
@interface AllowedBeforeInitialUserIsSet
{
}