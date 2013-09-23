package org.labkey.api.query;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created with IntelliJ IDEA.
 * User: matthew
 * Date: 9/17/13
 * Time: 4:47 PM
 */
public @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
@interface Queryable
{
}