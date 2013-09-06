package org.labkey.api.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// Remove these once we no longer support Tomcat 6. Also, "Find in Path..." of "Tomcat6Hack" and eliminate the
// hack in LabKeyBootstrapClassLoader annotated with a comment.

@Retention(RetentionPolicy.SOURCE)
public @interface Tomcat6Hack
{
}