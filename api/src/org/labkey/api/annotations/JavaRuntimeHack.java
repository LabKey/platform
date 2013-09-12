package org.labkey.api.annotations;

/**
 * User: adam
 * Date: 9/11/13
 * Time: 7:54 PM
 */

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// Used to mark hacks that work around problems in the Java runtime. Re-evaluate these when new versions of the
// Java runtime are released or deprecated.

@Retention(RetentionPolicy.SOURCE)
public @interface JavaRuntimeHack
{
}