package org.labkey.api.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Timeout for server-side unit tests, in seconds
 * User: jeckels
 * Date: 1/14/13
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface TestTimeout
{
    /** One minute is our standard timeout */
    public static final int DEFAULT = 60;

    int value() default DEFAULT;
}