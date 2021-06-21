package org.labkey.api.security.permissions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * This annotation is used to mark permissions that are allowed for a user that is logged in as a "ReadOnly" user.
*/
public @Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
@interface AllowedForReadOnlyUser
{
}
