package org.labkey.api.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;


@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TestWhen
{
    public enum When
    {
        DRT, BVT, DAILY, WEEKLY
    }
    When value() default When.DRT;
}
