package org.labkey.api.action;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * User: kevink
 * Date: 4/6/14
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Marshal
{
    Marshaller value() default Marshaller.Jackson;
}
