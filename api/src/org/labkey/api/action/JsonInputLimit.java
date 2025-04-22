package org.labkey.api.action;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Sets a limit for the length in characters of the JSON string that an API is willing to consume */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonInputLimit
{
    /** No limit */
    long DEFAULT = -1;

    long value() default DEFAULT;
}
