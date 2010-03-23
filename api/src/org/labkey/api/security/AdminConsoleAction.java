package org.labkey.api.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * User: adam
 * Date: Mar 22, 2010
 * Time: 2:08:18 PM
 */

/*
    Provides custom permissions handling for standard admin console actions:

    - Current container must be the root
    - Requires AdminReadPermissions for GET operations
    - Requires SiteAdminRole for POST operations 
 */
public @Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target({ElementType.METHOD,ElementType.TYPE})
@interface AdminConsoleAction
{
}
