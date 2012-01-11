package org.labkey.api.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jan 10, 2012
 * Time: 4:00:15 PM
 */

/**
 * Annotation to indicate which (if any) methods from an UpgradeCode implementation should be run after
 * module startup.
 */
public @Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
@interface DeferredUpgrade
{
}
